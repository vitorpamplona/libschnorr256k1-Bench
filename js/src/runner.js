/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * The JavaScript twin of c/main.c (and bench/.../Runner.kt): check a library against
 * vectors/v1.txt and the nostr-v1 dataset, time the sixteen operations with the same method
 * (warm-up, samples of at least sampleNs, nearest-rank percentiles of per-sample time ÷ calls),
 * then probe the known issues on keys apart from the vectors.
 */
import { readFileSync } from 'node:fs';
import { N, BATCH, ORDER, sha256, sha256Stream, toHex } from './vectors.js';

/** Keeps every timed call's result alive, as c/main.c's `sink`. */
export const sink = { value: 0 };

export const OPS = [
  'verify.sameKey', 'verify.manyKeys', 'verifyFast.sameKey', 'verifyBatch200.perSig', 'sign', 'sign.knownPub',
  'pubkey.create', 'pubkey.compress', 'seckey.verify', 'ecdh.xOnly', 'tweak.mul', 'tweak.add',
  'nostr.verify', 'nostr.verifyFast', 'nostr.verifyBatchByAuthor', 'nostr.ecdh',
];

/** The ops each known issue makes wrong on the vectors (Runner.COVERS). */
export const COVERS = { 'sign.knownPub': 'KI-2' };

// ------------------------------------------------------------------ dataset (datasets/README.md)

const DS_RECORD = 128; // id (32, the signed message) || pubkey (32) || sig (64)
export const DS_PAGE = 200; // events per relay page, for nostr.verifyBatchByAuthor

export function loadDataset(path) {
  const bytes = new Uint8Array(readFileSync(path));
  if (bytes.length === 0 || bytes.length % DS_RECORD) throw new Error('the dataset is not whole 128-byte records');
  const n = bytes.length / DS_RECORD;
  const id = [], pub = [], sig = [];
  for (let i = 0; i < n; i++) {
    const o = i * DS_RECORD;
    id.push(bytes.subarray(o, o + 32));
    pub.push(bytes.subarray(o + 32, o + 64));
    sig.push(bytes.subarray(o + 64, o + 128));
  }
  // distinct pubkeys, first appearance first
  const seen = new Set();
  const keys = [];
  for (const p of pub) {
    const h = toHex(p);
    if (!seen.has(h)) { seen.add(h); keys.push(p); }
  }
  // whole pages of DS_PAGE events, each split into its authors' groups (first appearance first)
  const pages = Math.floor(n / DS_PAGE);
  const pageGroups = [];
  for (let p = 0; p < pages; p++) {
    const byAuthor = new Map();
    for (let e = p * DS_PAGE; e < (p + 1) * DS_PAGE; e++) {
      const h = toHex(pub[e]);
      let g = byAuthor.get(h);
      if (!g) byAuthor.set(h, (g = { pub: pub[e], sigs: [], msgs: [] }));
      g.sigs.push(sig[e]);
      g.msgs.push(id[e]);
    }
    pageGroups.push([...byAuthor.values()]);
  }
  return { sha256Hex: toHex(sha256(bytes)), n, id, pub, sig, keys, pages, pageGroups };
}

// ------------------------------------------------------------------ runner

export class Runner {
  constructor({ ref, v, ds, vecDigests, dsDigests, quick = false, log = () => {} }) {
    this.ref = ref;
    this.v = v;
    this.ds = ds;
    this.vecDigests = vecDigests;
    this.dsDigests = dsDigests;
    this.quick = quick;
    this.log = log;
    this.warmNs = quick ? 0.2e9 : 1e9;
    this.sampleNs = quick ? 20e6 : 100e6;
    this.nSamples = quick ? 5 : 20;
    this.next = 0;
  }

  // ---------------------------------------------------------------- correctness

  /** The v1 digests of [b]'s outputs (vectors/v1.txt's names); a check the library lacks is absent. */
  digests(b) {
    const v = this.v;
    const checks = [
      ['pubkey.create', b.pubkeyCreate && ((i) => b.pubkeyCreate(v.seckey[i])), 65],
      ['pubkey.compress', b.pubkeyCompress && ((i) => b.pubkeyCompress(v.pub65[i])), 33],
      ['sign', (i) => b.sign(v.msg[i], v.seckey[i], v.aux[i]), 64],
      ['ecdh.xOnly', b.ecdhXOnly && ((i) => b.ecdhXOnly(v.xonly[(i + 1) % N], v.seckey[i])), 32],
      ['tweak.mul', b.tweakMul && ((i) => b.tweakMul(v.tpub33[i], v.seckey[i])), 33],
      ['tweak.add', b.tweakAdd && ((i) => b.tweakAdd(v.seckey[i], v.tweak[i])), 32],
      ['verify', (i) => Uint8Array.of(b.verify(v.sig[i], v.msg[i], v.xonly[i]) ? 1 : 0), 1],
      ['verify.tampered', (i) => {
        const bad = v.sig[i].slice();
        bad[63]++;
        return Uint8Array.of(b.verify(bad, v.msg[i], v.xonly[i]) ? 1 : 0);
      }, 1],
    ];
    const out = new Map();
    for (const [name, f, len] of checks) {
      if (!f) continue;
      const h = sha256Stream();
      for (let i = 0; i < N; i++) {
        const r = f(i);
        h.update(r && r.length === len ? r : new Uint8Array(len));
      }
      out.set(name, h.digest('hex'));
    }
    return out;
  }

  /** The dataset digests of [b]'s outputs (datasets/nostr-v1.txt's names). */
  datasetDigests(b) {
    const ds = this.ds;
    const ok = new Uint8Array(ds.n), bad = new Uint8Array(ds.n);
    for (let e = 0; e < ds.n; e++) {
      ok[e] = b.verify(ds.sig[e], ds.id[e], ds.pub[e]) ? 1 : 0;
      const t = ds.sig[e].slice();
      t[63]++;
      bad[e] = b.verify(t, ds.id[e], ds.pub[e]) ? 1 : 0;
    }
    const out = new Map([
      ['nostr.verify', toHex(sha256(ok))],
      ['nostr.verify.tampered', toHex(sha256(bad))],
    ]);
    if (b.ecdhXOnly) {
      const h = sha256Stream();
      ds.keys.forEach((k, d) => {
        const x = b.ecdhXOnly(k, this.v.seckey[d % N]);
        h.update(x && x.length === 32 ? x : new Uint8Array(32));
      });
      out.set('nostr.ecdh', h.digest('hex'));
    }
    return out;
  }

  /** One page's author groups: batch verify for groups of two or more. */
  verifyPage(b, p) {
    let all = true;
    for (const g of this.ds.pageGroups[p]) {
      const ok = g.sigs.length === 1 ? b.verify(g.sigs[0], g.msgs[0], g.pub) : b.verifyBatch(g.pub, g.sigs, g.msgs);
      all = all && ok;
    }
    return all;
  }

  /** Per op, whether [b]'s outputs were right; and whether sign.knownPub was wrong only for odd-y keys. */
  correctness(b) {
    const v = this.v, ds = this.ds;
    const d = this.digests(b);
    const good = {};
    for (const [k, h] of d) {
      const want = this.vecDigests.get(k);
      if (!want) throw new Error(`vectors file lacks ${k}`);
      good[k] = h === want;
      if (!good[k]) this.log(`${b.library}: ${k} digest differs from vectors`);
    }
    let fastOk = true, knownPubOk = true, knownPubOddOnly = true, seckeyOk = true;
    for (let i = 0; i < N; i++) {
      if (b.verifyFast && !b.verifyFast(v.sig[i], v.msg[i], v.xonly[i])) fastOk = false;
      const s = b.signKnownPub(v.msg[i], v.seckey[i], v.pub33[i], v.xonly[i], v.aux[i]);
      if (!s || toHex(s) !== toHex(v.sig[i])) {
        knownPubOk = false;
        if (v.pub33[i][0] !== 0x03) knownPubOddOnly = false;
      }
      if (b.seckeyVerify && !b.seckeyVerify(v.seckey[i])) seckeyOk = false;
    }
    if (b.seckeyVerify && (b.seckeyVerify(new Uint8Array(32)) || b.seckeyVerify(ORDER.slice()))) seckeyOk = false;
    const batchOk = !b.verifyBatch || b.verifyBatch(v.xonly[0], v.batchSig, v.batchMsg);
    const verifyOk = good['verify'] && good['verify.tampered'];

    const dd = this.datasetDigests(b);
    const dgood = {};
    for (const [k, h] of dd) {
      const want = this.dsDigests.get(k);
      if (!want) throw new Error(`dataset digests file lacks ${k}`);
      dgood[k] = h === want;
      if (!dgood[k]) this.log(`${b.library}: ${k} digest differs from the dataset's`);
    }
    let dsFastOk = true, dsBatchOk = true;
    for (let e = 0; e < ds.n && b.verifyFast; e++) if (!b.verifyFast(ds.sig[e], ds.id[e], ds.pub[e])) dsFastOk = false;
    for (let p = 0; p < ds.pages && b.verifyBatch; p++) if (!this.verifyPage(b, p)) dsBatchOk = false;
    const dsVerifyOk = dgood['nostr.verify'] && dgood['nostr.verify.tampered'];
    const ok = {
      'verify.sameKey': verifyOk,
      'verify.manyKeys': verifyOk,
      'verifyFast.sameKey': verifyOk && fastOk,
      'verifyBatch200.perSig': batchOk,
      'sign': good['sign'] === true,
      'sign.knownPub': knownPubOk,
      'pubkey.create': good['pubkey.create'] === true,
      'pubkey.compress': good['pubkey.compress'] === true,
      'seckey.verify': seckeyOk,
      'ecdh.xOnly': good['ecdh.xOnly'] === true,
      'tweak.mul': good['tweak.mul'] === true,
      'tweak.add': good['tweak.add'] === true,
      'nostr.verify': dsVerifyOk,
      'nostr.verifyFast': dsVerifyOk && dsFastOk,
      'nostr.verifyBatchByAuthor': dsBatchOk,
      'nostr.ecdh': dgood['nostr.ecdh'] === true,
    };
    return { ok, knownPubWrongOnlyOddY: !knownPubOk && knownPubOddOnly };
  }

  // ---------------------------------------------------------------- timing

  /** Whether [b] has [op]. */
  has(b, op) {
    switch (op) {
      case 'verifyBatch200.perSig': case 'nostr.verifyBatchByAuthor': return !!b.verifyBatch;
      case 'pubkey.create': return !!b.pubkeyCreate;
      case 'pubkey.compress': return !!b.pubkeyCompress;
      case 'seckey.verify': return !!b.seckeyVerify;
      case 'ecdh.xOnly': case 'nostr.ecdh': return !!b.ecdhXOnly;
      case 'tweak.mul': return !!b.tweakMul;
      case 'tweak.add': return !!b.tweakAdd;
      default: return true;
    }
  }

  /** Runs [n] calls of [op], cycling as c/main.c's run(). */
  run(b, op, n) {
    const v = this.v, ds = this.ds;
    let acc = 0;
    let k = this.next;
    switch (op) {
      case 'verify.sameKey':
        for (let r = 0; r < n; r++) if (b.verify(v.sig[0], v.msg[0], v.xonly[0])) acc++;
        break;
      case 'verify.manyKeys':
        for (let r = 0; r < n; r++, k++) { const i = k % N; if (b.verify(v.sig[i], v.msg[i], v.xonly[i])) acc++; }
        break;
      case 'verifyFast.sameKey': {
        const f = b.verifyFast ?? b.verify;
        for (let r = 0; r < n; r++) if (f(v.sig[0], v.msg[0], v.xonly[0])) acc++;
        break;
      }
      case 'verifyBatch200.perSig':
        for (let r = 0; r < n; r++) if (b.verifyBatch(v.xonly[0], v.batchSig, v.batchMsg)) acc++;
        break;
      case 'sign':
        for (let r = 0; r < n; r++, k++) { const i = k % N; const o = b.sign(v.msg[i], v.seckey[i], v.aux[i]); acc += o ? o[7] : 0; }
        break;
      case 'sign.knownPub':
        for (let r = 0; r < n; r++, k++) {
          const i = k % N;
          const o = b.signKnownPub(v.msg[i], v.seckey[i], v.pub33[i], v.xonly[i], v.aux[i]);
          acc += o ? o[7] : 0;
        }
        break;
      case 'pubkey.create':
        for (let r = 0; r < n; r++, k++) { const o = b.pubkeyCreate(v.seckey[k % N]); acc += o ? o[7] : 0; }
        break;
      case 'pubkey.compress':
        for (let r = 0; r < n; r++, k++) { const o = b.pubkeyCompress(v.pub65[k % N]); acc += o ? o[7] : 0; }
        break;
      case 'seckey.verify':
        for (let r = 0; r < n; r++, k++) if (b.seckeyVerify(v.seckey[k % N])) acc++;
        break;
      case 'ecdh.xOnly':
        for (let r = 0; r < n; r++, k++) { const i = k % N; const o = b.ecdhXOnly(v.xonly[(i + 1) % N], v.seckey[i]); acc += o ? o[7] : 0; }
        break;
      case 'tweak.mul':
        for (let r = 0; r < n; r++, k++) { const i = k % N; const o = b.tweakMul(v.tpub33[i], v.seckey[i]); acc += o ? o[7] : 0; }
        break;
      case 'tweak.add':
        for (let r = 0; r < n; r++, k++) { const i = k % N; const o = b.tweakAdd(v.seckey[i], v.tweak[i]); acc += o ? o[7] : 0; }
        break;
      case 'nostr.verify':
        for (let r = 0; r < n; r++, k++) { const e = k % ds.n; if (b.verify(ds.sig[e], ds.id[e], ds.pub[e])) acc++; }
        break;
      case 'nostr.verifyFast': {
        const f = b.verifyFast ?? b.verify;
        for (let r = 0; r < n; r++, k++) { const e = k % ds.n; if (f(ds.sig[e], ds.id[e], ds.pub[e])) acc++; }
        break;
      }
      case 'nostr.verifyBatchByAuthor': // one call is one page
        for (let r = 0; r < n; r++, k++) if (this.verifyPage(b, k % ds.pages)) acc++;
        break;
      case 'nostr.ecdh':
        for (let r = 0; r < n; r++, k++) {
          const d = k % ds.keys.length;
          const o = b.ecdhXOnly(ds.keys[d], v.seckey[d % N]);
          acc += o ? o[7] : 0;
        }
        break;
      default:
        throw new Error(`unknown op ${op}`);
    }
    this.next = k;
    sink.value += acc;
  }

  /** c/main.c's time_op: null when the library lacks [op]. */
  timeOp(b, op) {
    if (!this.has(b, op)) return null;
    const now = () => process.hrtime.bigint();
    const perCall = op === 'verifyBatch200.perSig' ? BATCH : op === 'nostr.verifyBatchByAuthor' ? DS_PAGE : 1;
    this.run(b, op, 1);
    const start = now();
    while (Number(now() - start) < this.warmNs) this.run(b, op, 16);
    let n = 1;
    for (;;) { // calibrate: n calls take at least one sample's time
      const s = now();
      this.run(b, op, n);
      if (Number(now() - s) >= this.sampleNs) break;
      n *= 2;
    }
    const values = [];
    for (let s = 0; s < this.nSamples; s++) {
      const a = now();
      this.run(b, op, n);
      values.push(Number(now() - a) / (n * perCall));
    }
    values.sort((x, y) => x - y);
    return { median: pct(values, 50), p5: pct(values, 5), p95: pct(values, 95), samples: this.nSamples };
  }

  // ---------------------------------------------------------------- known issues

  /** c/main.c's probe_issues: after the timing, on keys apart from the vectors. */
  probeIssues(b, knownPubWrongOnlyOddY) {
    const v = this.v, ref = this.ref;
    let ki1 = null;
    if (b.verifyBatch) {
      const s1 = addSmallToS(v.batchSig[0], 1), s2 = addSmallToS(v.batchSig[1], -1);
      const eachInvalid = !ref.verify(s1, v.msg[0], v.xonly[0]) && !ref.verify(s2, v.msg[1], v.xonly[0]);
      ki1 = eachInvalid && b.verifyBatch(v.xonly[0], [s1, s2], [v.msg[0], v.msg[1]]);
    }
    // KI-3, on odd-y keys of a separate set: tweak-mul the odd-y point, then verify a valid
    // signature for the x-only form.
    let ki3 = null;
    if (b.tweakMul) {
      let rejected = 0;
      for (let i = 0, found = 0; found < 16 && i < 4096; i++) {
        const sk = sha256(new TextEncoder().encode(`schnorr256k1-bench/v1/probe/${i}`));
        const p65 = ref.pubkeyCreate(sk);
        const pub33 = p65 && ref.pubkeyCompress(p65);
        if (!pub33 || pub33[0] !== 0x03) continue;
        found++;
        const msg = sha256(sk);
        const sig = ref.sign(msg, sk, msg);
        b.tweakMul(pub33, v.tweak[0]);
        if (!b.verify(sig, msg, pub33.slice(1, 33))) rejected++;
      }
      ki3 = rejected > 0;
    }
    return { 'KI-1': ki1, 'KI-2': knownPubWrongOnlyOddY, 'KI-3': ki3 };
  }

  /** "library op" for every wrong result no observed known issue explains, and the issues seen. */
  check(b) {
    const c = this.correctness(b);
    const ki = this.probeIssues(b, c.knownPubWrongOnlyOddY);
    const unexplained = [];
    for (const op of OPS) {
      if (!this.has(b, op) || c.ok[op]) continue;
      const k = COVERS[op];
      if (k && ki[k] === true) this.log(`${b.library} ${op}: wrong, as ${k} says`);
      else unexplained.push(`${b.library} ${op}`);
    }
    return { correct: c.ok, knownIssues: ki, unexplained };
  }

  /** Checks, times and probes [b]: its rows and known issues, for the result. */
  measure(b) {
    this.log(`${b.library}: checking`);
    const c = this.correctness(b);
    const timings = {};
    for (const op of OPS) {
      this.log(`${b.library} ${op}`);
      timings[op] = this.timeOp(b, op);
    }
    // After the timing too: KI-3 is about state the calls leave behind.
    const ki = this.probeIssues(b, c.knownPubWrongOnlyOddY);
    const rows = [];
    for (const op of OPS) {
      const t = timings[op];
      if (!t) continue;
      const kis = [];
      if ((op === 'verifyBatch200.perSig' || op === 'nostr.verifyBatchByAuthor') && ki['KI-1'] === true) kis.push('KI-1');
      if (op === 'sign.knownPub' && ki['KI-2'] === true) kis.push('KI-2');
      rows.push({
        op, library: b.library, binding: b.binding,
        median_ns: t.median, p5_ns: t.p5, p95_ns: t.p95, samples: t.samples,
        correct: c.ok[op] === true, known_issues: kis,
      });
    }
    return { rows, knownIssues: ki };
  }
}

/** Nearest-rank percentile of sorted v, as c/main.c computes it. */
function pct(v, p) {
  let r = Math.trunc(p / 100 * v.length + 0.999999);
  if (r < 1) r = 1;
  if (r > v.length) r = v.length;
  return v[r - 1];
}

/** Adds d (±1) to the s half of a signature, as c/main.c's add_small_to_s. */
function addSmallToS(sig, d) {
  const s = sig.slice();
  let carry = d;
  for (let i = 63; carry !== 0 && i >= 32; i--) {
    const x = s[i] + carry;
    s[i] = x & 0xff;
    carry = x > 255 ? 1 : x < 0 ? -1 : 0;
  }
  return s;
}
