/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * The v1 inputs (vectors/README.md), the twin of c/vectors.c and c/main.c's derive(): SHA-256 of
 * labels for the secrets, tweaks, messages and aux, and the derived values (pub65, pub33, xonly,
 * tpub33, sig, batchSig) from the layer's libsecp256k1 binding.
 */
import { createHash } from 'node:crypto';

export const N = 4096;
export const BATCH = 200;

export function sha256(bytes) {
  return new Uint8Array(createHash('sha256').update(bytes).digest());
}

/** An incremental SHA-256, for the digests. */
export function sha256Stream() {
  return createHash('sha256');
}

const HEX = Array.from({ length: 256 }, (_, i) => i.toString(16).padStart(2, '0'));

export function toHex(bytes) {
  let s = '';
  for (let i = 0; i < bytes.length; i++) s += HEX[bytes[i]];
  return s;
}

export function fromHex(hex) {
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.substr(2 * i, 2), 16);
  return out;
}

/** n, the secp256k1 group order, big-endian. */
export const ORDER = fromHex('fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141');

function validScalar(s) {
  let nonzero = 0;
  for (let i = 0; i < 32; i++) nonzero |= s[i];
  if (!nonzero) return false;
  for (let i = 0; i < 32; i++) if (s[i] !== ORDER[i]) return s[i] < ORDER[i];
  return false; // equal to n
}

const ascii = (s) => new TextEncoder().encode(s);

function labelHash(field, i) {
  return sha256(ascii(`schnorr256k1-bench/v1/${field}/${i}`));
}

function scalarFromLabel(field, i) {
  let h = labelHash(field, i);
  while (!validScalar(h)) h = sha256(h);
  return h;
}

/** The inputs, and the values derived from them through [ref] (the layer's libsecp256k1). */
export function deriveVectors(ref) {
  const v = {
    seckey: [], tweak: [], msg: [], aux: [],
    pub65: [], pub33: [], xonly: [], tpub33: [], sig: [],
    batchSig: [], batchMsg: [],
  };
  const die = (what) => { throw new Error(`reference ${what} failed`); };
  for (let i = 0; i < N; i++) {
    v.seckey.push(scalarFromLabel('seckey', i));
    v.tweak.push(scalarFromLabel('tweak', i));
    v.msg.push(labelHash('msg', i));
    v.aux.push(labelHash('aux', i));
  }
  for (let i = 0; i < N; i++) {
    const p65 = ref.pubkeyCreate(v.seckey[i]) ?? die('pubkey_create');
    const p33 = ref.pubkeyCompress(p65) ?? die('pubkey_compress');
    v.pub65.push(p65);
    v.pub33.push(p33);
    v.xonly.push(p33.slice(1, 33));
    const t65 = ref.pubkeyCreate(v.tweak[i]) ?? die('tweak pubkey');
    v.tpub33.push(ref.pubkeyCompress(t65) ?? die('tweak pubkey'));
    v.sig.push(ref.sign(v.msg[i], v.seckey[i], v.aux[i]) ?? die('sign'));
  }
  for (let j = 0; j < BATCH; j++) {
    v.batchSig.push(ref.sign(v.msg[j], v.seckey[0], v.aux[j]) ?? die('batch sign'));
    v.batchMsg.push(v.msg[j]);
  }
  return v;
}

/** "<check> <hex>" lines (vectors/v1.txt, datasets/nostr-v1.txt) as a map. */
export function parseDigests(text) {
  const m = new Map();
  for (const line of text.split('\n')) {
    const sp = line.indexOf(' ');
    if (sp > 0) m.set(line.slice(0, sp), line.slice(sp + 1).trim());
  }
  return m;
}

/**
 * BIP-340's own test vectors (vectors/bip340/test-vectors.csv; vectors/README.md, "BIP-340
 * vectors"): the rows with a 32-byte message, the size every operation here takes.
 */
export function parseBip340(text) {
  const out = [];
  for (const raw of text.split('\n').slice(1)) {
    const line = raw.replace(/\r$/, '');
    if (!line) continue;
    // index, secret key, public key, aux_rand, message, signature, verification result, comment
    const f = line.split(',');
    if (f.length < 7) throw new Error('a BIP-340 vector line has too few fields');
    if (f[4].length !== 64) continue;
    if (f[6] !== 'TRUE' && f[6] !== 'FALSE') throw new Error(`BIP-340 vector ${f[0]}: result ${f[6]}`);
    out.push({
      index: Number(f[0]),
      seckey: f[1] ? fromHex(f[1]) : null,
      pub: fromHex(f[2]),
      aux: f[3] ? fromHex(f[3]) : null,
      msg: fromHex(f[4]),
      sig: fromHex(f[5]),
      expected: f[6] === 'TRUE',
    });
  }
  if (!out.length) throw new Error('no BIP-340 vector has a 32-byte message');
  return out;
}

/** The BIP-340 vector whose only fault is R's odd y (KI-4). */
export const BIP340_ODD_R = 6;
