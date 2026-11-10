//! The rust layer: BIP-340 libraries called from Rust. The twin of c/main.c: the same inputs,
//! checks (BIP-340's own vectors, read from bip340/test-vectors.csv beside the vectors file,
//! included), operations, timing, known-issue probes and JSON (schema 4: results/SCHEMA.md).
//!
//!   bench run --vectors FILE --dataset DATA --dataset-digests FILE [opts]
//!                            check every library against both, time every operation, print
//!                            the result as JSON
//!     --library NAME         only this library (libsecp256k1 | k256 | secp256kfun)
//!     --device TEXT          context.device       --machine dedicated|shared   context.machine
//!     --harness COMMIT       context.harness (default: this checkout's commit at build time)
//!     --libsecp256k1-commit C  the libsecp256k1 commit secp256k1-sys bundles, for context
//!     --quick                short warm-up and samples, for CI smoke runs (numbers not comparable)
//!   bench check --vectors FILE --dataset DATA --dataset-digests FILE [--library NAME]
//!                            the same checks and probes without the timing; exits 1 when a
//!                            result is wrong and no observed known issue explains it

mod backend;
mod k256_backend;
mod libsecp256k1;
mod secp256kfun_backend;
mod vectors;

use std::collections::HashMap;
use std::fs;
use std::hint::black_box;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::Path;
use std::process::exit;
use std::time::Instant;

use backend::Backend;
use vectors::{hex, sha256, Sha256, Vectors, BATCH, N, ORDER};

fn die(what: &str) -> ! {
    eprintln!("error: {what}");
    exit(2);
}

// ---------------------------------------------------------------- dataset (datasets/README.md)

const DS_RECORD: usize = 128; // id (32, the signed message) || pubkey (32) || sig (64)
const DS_PAGE: usize = 200; // events per relay page, for nostr.verifyBatchByAuthor

struct Dataset {
    sha_hex: String,
    id: Vec<[u8; 32]>,
    pubkey: Vec<[u8; 32]>,
    sig: Vec<[u8; 64]>,
    /// distinct pubkeys, in order of first appearance
    keys: Vec<[u8; 32]>,
    /// whole pages; the remainder is left out of the batch op
    pages: usize,
    /// Per page, its authors' groups: group g covers g_sigs[g_start[g] .. g_start[g + 1]].
    g_start: Vec<usize>,
    g_pub: Vec<[u8; 32]>,
    page_first_group: Vec<usize>,
    g_sigs: Vec<[u8; 64]>,
    g_msgs: Vec<[u8; 32]>,
}

fn load_dataset(path: &str) -> Dataset {
    let bytes = fs::read(path).unwrap_or_else(|_| die("cannot read the dataset"));
    if bytes.is_empty() || !bytes.len().is_multiple_of(DS_RECORD) {
        die("the dataset is not whole 128-byte records");
    }
    let n = bytes.len() / DS_RECORD;
    let rec = |i: usize| &bytes[i * DS_RECORD..(i + 1) * DS_RECORD];
    let id: Vec<[u8; 32]> = (0..n).map(|i| rec(i)[0..32].try_into().unwrap()).collect();
    let pubkey: Vec<[u8; 32]> = (0..n).map(|i| rec(i)[32..64].try_into().unwrap()).collect();
    let sig: Vec<[u8; 64]> = (0..n).map(|i| rec(i)[64..128].try_into().unwrap()).collect();

    let mut seen = std::collections::HashSet::new();
    let keys: Vec<[u8; 32]> = pubkey.iter().filter(|k| seen.insert(**k)).copied().collect();

    // pages of DS_PAGE events, each split into its authors' groups (first appearance first)
    let pages = n / DS_PAGE;
    let (mut g_start, mut g_pub, mut page_first_group, mut g_sigs, mut g_msgs) =
        (Vec::new(), Vec::new(), Vec::new(), Vec::new(), Vec::new());
    for p in 0..pages {
        page_first_group.push(g_start.len());
        let base = p * DS_PAGE;
        let mut used = [false; DS_PAGE];
        for a in 0..DS_PAGE {
            if used[a] {
                continue;
            }
            g_start.push(g_sigs.len());
            g_pub.push(pubkey[base + a]);
            for c in a..DS_PAGE {
                if used[c] || pubkey[base + a] != pubkey[base + c] {
                    continue;
                }
                used[c] = true;
                g_sigs.push(sig[base + c]);
                g_msgs.push(id[base + c]);
            }
        }
    }
    page_first_group.push(g_start.len());
    g_start.push(g_sigs.len());
    Dataset {
        sha_hex: hex(&sha256(&bytes)),
        id,
        pubkey,
        sig,
        keys,
        pages,
        g_start,
        g_pub,
        page_first_group,
        g_sigs,
        g_msgs,
    }
}

/// The page's groups through the library: batch verify for groups of two or more.
fn verify_page(b: &dyn Backend, ds: &Dataset, p: usize) -> bool {
    let mut all = true;
    for g in ds.page_first_group[p]..ds.page_first_group[p + 1] {
        let (s, e) = (ds.g_start[g], ds.g_start[g + 1]);
        all &= if e - s == 1 {
            b.verify(&ds.g_sigs[s], &ds.g_msgs[s], &ds.g_pub[g])
        } else {
            b.verify_batch(&ds.g_pub[g], &ds.g_sigs[s..e], &ds.g_msgs[s..e])
        };
    }
    all
}

// ---------------------------------------------------------------- vectors

/// The derived values, made with the reference library (vectors/README.md).
fn derive(r: &dyn Backend) -> Vectors {
    let mut v = Vectors::derive_inputs();
    for i in 0..N {
        let (mut p65, mut p33, mut t65, mut t33, mut s) = ([0u8; 65], [0u8; 33], [0u8; 65], [0u8; 33], [0u8; 64]);
        if !r.pubkey_create(&mut p65, &v.seckey[i]) {
            die("reference pubkey_create");
        }
        if !r.pubkey_compress(&mut p33, &p65) {
            die("reference pubkey_compress");
        }
        if !r.pubkey_create(&mut t65, &v.tweak[i]) || !r.pubkey_compress(&mut t33, &t65) {
            die("reference tweak pubkey");
        }
        if !r.sign(&mut s, &v.msg[i], &v.seckey[i], &v.aux[i]) {
            die("reference sign");
        }
        v.pub65.push(p65);
        v.pub33.push(p33);
        v.xonly.push(p33[1..].try_into().unwrap());
        v.tpub33.push(t33);
        v.sig.push(s);
    }
    for j in 0..BATCH {
        let mut s = [0u8; 64];
        if !r.sign(&mut s, &v.msg[j], &v.seckey[0], &v.aux[j]) {
            die("reference batch sign");
        }
        v.batch_sig.push(s);
    }
    v
}

const DIGEST_NAMES: [&str; 8] =
    ["pubkey.create", "pubkey.compress", "sign", "ecdh.xOnly", "tweak.mul", "tweak.add", "verify", "verify.tampered"];
const D_PUB_CREATE: usize = 0;
const D_PUB_COMPRESS: usize = 1;
const D_SIGN: usize = 2;
const D_ECDH: usize = 3;
const D_TWEAK_MUL: usize = 4;
const D_TWEAK_ADD: usize = 5;
const D_VERIFY: usize = 6;
const D_VERIFY_TAMPERED: usize = 7;

/// A failed call contributes zeros, so its digest cannot match.
fn digests(b: &dyn Backend, v: &Vectors) -> [String; 8] {
    let mut c: [Sha256; 8] = std::array::from_fn(|_| Sha256::new());
    for i in 0..N {
        let mut o65 = [0u8; 65];
        if !b.pubkey_create(&mut o65, &v.seckey[i]) {
            o65 = [0; 65];
        }
        c[D_PUB_CREATE].update(&o65);
        let mut o33 = [0u8; 33];
        if !b.pubkey_compress(&mut o33, &v.pub65[i]) {
            o33 = [0; 33];
        }
        c[D_PUB_COMPRESS].update(&o33);
        let mut o64 = [0u8; 64];
        if !b.sign(&mut o64, &v.msg[i], &v.seckey[i], &v.aux[i]) {
            o64 = [0; 64];
        }
        c[D_SIGN].update(&o64);
        let mut o32 = [0u8; 32];
        if !b.ecdh_xonly(&mut o32, &v.xonly[(i + 1) % N], &v.seckey[i]) {
            o32 = [0; 32];
        }
        c[D_ECDH].update(&o32);
        let mut o33 = [0u8; 33];
        if !b.tweak_mul(&mut o33, &v.tpub33[i], &v.seckey[i]) {
            o33 = [0; 33];
        }
        c[D_TWEAK_MUL].update(&o33);
        let mut o32 = [0u8; 32];
        if !b.tweak_add(&mut o32, &v.seckey[i], &v.tweak[i]) {
            o32 = [0; 32];
        }
        c[D_TWEAK_ADD].update(&o32);
        c[D_VERIFY].update(&[b.verify(&v.sig[i], &v.msg[i], &v.xonly[i]) as u8]);
        let mut bad = v.sig[i];
        bad[63] = bad[63].wrapping_add(1);
        c[D_VERIFY_TAMPERED].update(&[b.verify(&bad, &v.msg[i], &v.xonly[i]) as u8]);
    }
    c.map(|h| hex(&h.finalize()))
}

const DS_DIGEST_NAMES: [&str; 3] = ["nostr.verify", "nostr.verify.tampered", "nostr.ecdh"];

/// Over the dataset, in order: verify's result per event, the result for the event with the
/// last byte of s incremented, and x-only ECDH with each distinct pubkey (vector key d mod N).
/// The third element is whether every ECDH call succeeded.
fn dataset_digests(b: &dyn Backend, v: &Vectors, ds: &Dataset) -> ([String; 3], bool) {
    let mut c: [Sha256; 3] = std::array::from_fn(|_| Sha256::new());
    for i in 0..ds.id.len() {
        c[0].update(&[b.verify(&ds.sig[i], &ds.id[i], &ds.pubkey[i]) as u8]);
        let mut bad = ds.sig[i];
        bad[63] = bad[63].wrapping_add(1);
        c[1].update(&[b.verify(&bad, &ds.id[i], &ds.pubkey[i]) as u8]);
    }
    let mut ecdh_ok = true;
    for (d, k) in ds.keys.iter().enumerate() {
        let mut x = [0u8; 32];
        if !b.ecdh_xonly(&mut x, k, &v.seckey[d % N]) {
            ecdh_ok = false;
            x = [0; 32];
        }
        c[2].update(&x);
    }
    (c.map(|h| hex(&h.finalize())), ecdh_ok)
}

/// `<check> <hex>` lines, as vectors/v1.txt and datasets/nostr-v1.txt.
fn parse_digests(text: &str) -> HashMap<String, String> {
    text.lines().filter_map(|l| l.split_once(' ')).map(|(k, v)| (k.to_string(), v.trim().to_string())).collect()
}

// ---------------------------------------------------------------- operations

#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug)]
enum Op {
    VerifySame,
    VerifyMany,
    VerifyFastSame,
    VerifyBatch,
    Sign,
    SignKnownPub,
    PubCreate,
    PubCompress,
    SeckeyVerify,
    Ecdh,
    TweakMul,
    TweakAdd,
    NostrVerify,
    NostrVerifyFast,
    NostrBatch,
    NostrEcdh,
}

const OPS: [(Op, &str); 16] = [
    (Op::VerifySame, "verify.sameKey"),
    (Op::VerifyMany, "verify.manyKeys"),
    (Op::VerifyFastSame, "verifyFast.sameKey"),
    (Op::VerifyBatch, "verifyBatch200.perSig"),
    (Op::Sign, "sign"),
    (Op::SignKnownPub, "sign.knownPub"),
    (Op::PubCreate, "pubkey.create"),
    (Op::PubCompress, "pubkey.compress"),
    (Op::SeckeyVerify, "seckey.verify"),
    (Op::Ecdh, "ecdh.xOnly"),
    (Op::TweakMul, "tweak.mul"),
    (Op::TweakAdd, "tweak.add"),
    (Op::NostrVerify, "nostr.verify"),
    (Op::NostrVerifyFast, "nostr.verifyFast"),
    (Op::NostrBatch, "nostr.verifyBatchByAuthor"),
    (Op::NostrEcdh, "nostr.ecdh"),
];

/// What the timing loops need, and the index that carries over from one run to the next (as
/// c/main.c's `next_index`).
struct Bench<'a> {
    v: &'a Vectors,
    ds: &'a Dataset,
    next_index: u32,
    sink: u64,
}

impl Bench<'_> {
    /// Runs `n` calls of `op`. Returns false if the library lacks it.
    fn run(&mut self, b: &dyn Backend, op: Op, n: u64) -> bool {
        let (v, ds) = (self.v, self.ds);
        let mut acc: u64 = 0;
        let mut k = self.next_index;
        let (mut o64, mut o65, mut o33, mut o32) = ([0u8; 64], [0u8; 65], [0u8; 33], [0u8; 32]);
        let ds_n = ds.id.len() as u32;
        let ds_keys = ds.keys.len() as u32;
        match op {
            Op::VerifySame => {
                for _ in 0..n {
                    acc += b.verify(&v.sig[0], &v.msg[0], &v.xonly[0]) as u64;
                }
            }
            Op::VerifyMany => {
                for _ in 0..n {
                    let i = k as usize % N;
                    acc += b.verify(&v.sig[i], &v.msg[i], &v.xonly[i]) as u64;
                    k = k.wrapping_add(1);
                }
            }
            Op::VerifyFastSame => {
                for _ in 0..n {
                    acc += b.verify_fast(&v.sig[0], &v.msg[0], &v.xonly[0]) as u64;
                }
            }
            Op::VerifyBatch => {
                if !b.has_verify_batch() {
                    return false;
                }
                for _ in 0..n {
                    acc += b.verify_batch(&v.xonly[0], &v.batch_sig, &v.msg[..BATCH]) as u64;
                }
            }
            Op::Sign => {
                for _ in 0..n {
                    let i = k as usize % N;
                    b.sign(&mut o64, &v.msg[i], &v.seckey[i], &v.aux[i]);
                    acc += o64[7] as u64;
                    k = k.wrapping_add(1);
                }
            }
            Op::SignKnownPub => {
                for _ in 0..n {
                    let i = k as usize % N;
                    b.sign_known_pub(&mut o64, &v.msg[i], &v.seckey[i], &v.pub33[i], &v.xonly[i], &v.aux[i]);
                    acc += o64[7] as u64;
                    k = k.wrapping_add(1);
                }
            }
            Op::PubCreate => {
                for _ in 0..n {
                    b.pubkey_create(&mut o65, &v.seckey[k as usize % N]);
                    acc += o65[7] as u64;
                    k = k.wrapping_add(1);
                }
            }
            Op::PubCompress => {
                for _ in 0..n {
                    b.pubkey_compress(&mut o33, &v.pub65[k as usize % N]);
                    acc += o33[7] as u64;
                    k = k.wrapping_add(1);
                }
            }
            Op::SeckeyVerify => {
                for _ in 0..n {
                    acc += b.seckey_verify(&v.seckey[k as usize % N]) as u64;
                    k = k.wrapping_add(1);
                }
            }
            Op::Ecdh => {
                for _ in 0..n {
                    let i = k as usize % N;
                    b.ecdh_xonly(&mut o32, &v.xonly[(i + 1) % N], &v.seckey[i]);
                    acc += o32[7] as u64;
                    k = k.wrapping_add(1);
                }
            }
            Op::TweakMul => {
                for _ in 0..n {
                    let i = k as usize % N;
                    b.tweak_mul(&mut o33, &v.tpub33[i], &v.seckey[i]);
                    acc += o33[7] as u64;
                    k = k.wrapping_add(1);
                }
            }
            Op::TweakAdd => {
                for _ in 0..n {
                    let i = k as usize % N;
                    b.tweak_add(&mut o32, &v.seckey[i], &v.tweak[i]);
                    acc += o32[7] as u64;
                    k = k.wrapping_add(1);
                }
            }
            Op::NostrVerify => {
                for _ in 0..n {
                    let i = (k % ds_n) as usize;
                    acc += b.verify(&ds.sig[i], &ds.id[i], &ds.pubkey[i]) as u64;
                    k = k.wrapping_add(1);
                }
            }
            Op::NostrVerifyFast => {
                for _ in 0..n {
                    let i = (k % ds_n) as usize;
                    acc += b.verify_fast(&ds.sig[i], &ds.id[i], &ds.pubkey[i]) as u64;
                    k = k.wrapping_add(1);
                }
            }
            Op::NostrBatch => {
                // one call is one page
                if !b.has_verify_batch() {
                    return false;
                }
                for _ in 0..n {
                    acc += verify_page(b, ds, (k % ds.pages as u32) as usize) as u64;
                    k = k.wrapping_add(1);
                }
            }
            Op::NostrEcdh => {
                for _ in 0..n {
                    let d = (k % ds_keys) as usize;
                    b.ecdh_xonly(&mut o32, &ds.keys[d], &v.seckey[d % N]);
                    acc += o32[7] as u64;
                    k = k.wrapping_add(1);
                }
            }
        }
        self.next_index = k;
        self.sink = self.sink.wrapping_add(black_box(acc));
        true
    }

    fn time_op(&mut self, b: &dyn Backend, op: Op, quick: bool) -> Option<Timing> {
        let (warm_ns, sample_ns) = if quick { (0.2e9, 20e6) } else { (1e9, 100e6) };
        let n_samples = if quick { 5 } else { 20 };
        let per_call = match op {
            Op::VerifyBatch => BATCH as f64,
            Op::NostrBatch => DS_PAGE as f64,
            _ => 1.0,
        };
        if !self.run(b, op, 1) {
            return None;
        }
        let start = Instant::now();
        while (start.elapsed().as_nanos() as f64) < warm_ns {
            self.run(b, op, 16);
        }
        let mut n: u64 = 1;
        loop {
            // calibrate: n calls take at least one sample's time
            let s = Instant::now();
            self.run(b, op, n);
            if s.elapsed().as_nanos() as f64 >= sample_ns {
                break;
            }
            n *= 2;
        }
        let mut v: Vec<f64> = (0..n_samples)
            .map(|_| {
                let a = Instant::now();
                self.run(b, op, n);
                a.elapsed().as_nanos() as f64 / (n as f64 * per_call)
            })
            .collect();
        v.sort_by(|x, y| x.partial_cmp(y).unwrap());
        Some(Timing { median: pct(&v, 50.0), p5: pct(&v, 5.0), p95: pct(&v, 95.0), samples: n_samples })
    }
}

struct Timing {
    median: f64,
    p5: f64,
    p95: f64,
    samples: usize,
}

/// Nearest-rank percentile of sorted v.
fn pct(v: &[f64], p: f64) -> f64 {
    let n = v.len() as i64;
    let r = ((p / 100.0 * n as f64 + 0.999999) as i64).clamp(1, n);
    v[(r - 1) as usize]
}

// ---------------------------------------------------------------- BIP-340 vectors

/// A row of vectors/bip340/test-vectors.csv with a 32-byte message, the size every operation here
/// takes (vectors/README.md, "BIP-340 vectors").
struct Bip340Vector {
    index: u32,
    seckey: Option<[u8; 32]>,
    aux: Option<[u8; 32]>,
    pubkey: [u8; 32],
    msg: [u8; 32],
    sig: [u8; 64],
    expected: bool,
}

/// The vector whose only fault is R's odd y (KI-4).
const BIP340_ODD_R: u32 = 6;

fn unhex<const L: usize>(s: &str) -> [u8; L] {
    if s.len() != 2 * L {
        die("a BIP-340 vector field has the wrong length");
    }
    let mut out = [0u8; L];
    for (i, o) in out.iter_mut().enumerate() {
        *o = u8::from_str_radix(&s[2 * i..2 * i + 2], 16).unwrap_or_else(|_| die("a BIP-340 vector is not hex"));
    }
    out
}

fn load_bip340(text: &str) -> Vec<Bip340Vector> {
    let mut out = Vec::new();
    for line in text.lines().skip(1) {
        let line = line.trim_end_matches('\r');
        if line.is_empty() {
            continue;
        }
        // index, secret key, public key, aux_rand, message, signature, verification result, comment
        let f: Vec<&str> = line.splitn(8, ',').collect();
        if f.len() < 7 {
            die("a BIP-340 vector line has too few fields");
        }
        if f[4].len() != 64 {
            continue;
        }
        out.push(Bip340Vector {
            index: f[0].parse().unwrap_or_else(|_| die("a BIP-340 vector index is not a number")),
            seckey: (!f[1].is_empty()).then(|| unhex(f[1])),
            aux: (!f[3].is_empty()).then(|| unhex(f[3])),
            pubkey: unhex(f[2]),
            msg: unhex(f[4]),
            sig: unhex(f[5]),
            expected: match f[6] {
                "TRUE" => true,
                "FALSE" => false,
                _ => die("a BIP-340 vector's result is neither TRUE nor FALSE"),
            },
        });
    }
    if out.is_empty() {
        die("no BIP-340 vector has a 32-byte message");
    }
    out
}

/// A call that panics on an invalid input counts as a rejection; on a valid one, as a wrong result.
fn guard(f: impl FnOnce() -> bool) -> bool {
    catch_unwind(AssertUnwindSafe(f)).unwrap_or(false)
}

struct Bip340Result {
    sign_ok: bool,
    known_pub_ok: bool,
    known_pub_odd_only: bool,
    verify_ok: bool,
    fast_ok: bool,
    /// KI-4: verify_fast accepts BIP340_ODD_R...
    fast_accepts_odd_r: bool,
    /// ...and gets every other vector right.
    fast_ok_but_odd_r: bool,
    batch_ok: bool,
}

/// BIP-340's vectors through the library, and a batch with one bad signature, first then last.
fn bip340(b: &dyn Backend, r: &dyn Backend, v: &Vectors, bip: &[Bip340Vector]) -> Bip340Result {
    let mut res = Bip340Result {
        sign_ok: true,
        known_pub_ok: true,
        known_pub_odd_only: true,
        verify_ok: true,
        fast_ok: true,
        fast_accepts_odd_r: false,
        fast_ok_but_odd_r: true,
        batch_ok: true,
    };
    for t in bip {
        if let (Some(sk), Some(aux)) = (t.seckey, t.aux) {
            let mut s = [0u8; 64];
            if !guard(|| b.sign(&mut s, &t.msg, &sk, &aux)) || s != t.sig {
                eprintln!("{}: BIP-340 vector {}: sign differs", b.name(), t.index);
                res.sign_ok = false;
            }
            let (mut p65, mut p33) = ([0u8; 65], [0u8; 33]);
            if !r.pubkey_create(&mut p65, &sk) || !r.pubkey_compress(&mut p33, &p65) {
                die("reference BIP-340 pubkey");
            }
            let x: [u8; 32] = p33[1..].try_into().unwrap();
            let mut s = [0u8; 64];
            if !guard(|| b.sign_known_pub(&mut s, &t.msg, &sk, &p33, &x, &aux)) || s != t.sig {
                eprintln!("{}: BIP-340 vector {}: sign.knownPub differs", b.name(), t.index);
                res.known_pub_ok = false;
                if p33[0] != 0x03 {
                    res.known_pub_odd_only = false;
                }
            }
        }
        if guard(|| b.verify(&t.sig, &t.msg, &t.pubkey)) != t.expected {
            let what = if t.expected { "rejects" } else { "accepts" };
            eprintln!("{}: BIP-340 vector {}: verify {what} it", b.name(), t.index);
            res.verify_ok = false;
        }
        if b.has_verify_fast() && guard(|| b.verify_fast(&t.sig, &t.msg, &t.pubkey)) != t.expected {
            let what = if t.expected { "rejects" } else { "accepts" };
            eprintln!("{}: BIP-340 vector {}: verifyFast {what} it", b.name(), t.index);
            res.fast_ok = false;
            if t.index == BIP340_ODD_R {
                res.fast_accepts_odd_r = true;
            } else {
                res.fast_ok_but_odd_r = false;
            }
        }
    }
    if b.has_verify_batch() {
        for at in [0, BATCH - 1] {
            let mut sigs = v.batch_sig.clone();
            sigs[at][63] = sigs[at][63].wrapping_add(1);
            if guard(|| b.verify_batch(&v.xonly[0], &sigs, &v.msg[..BATCH])) {
                eprintln!("{}: verifyBatch accepts a batch whose signature {at} is bad", b.name());
                res.batch_ok = false;
            }
        }
    }
    res
}

// ---------------------------------------------------------------- correctness per op

struct Correctness {
    ok: HashMap<Op, bool>,
    /// sign.knownPub was wrong, and only for keys whose public key has odd y (KI-2)
    known_pub_wrong_odd_only: bool,
    /// KI-4 as observed (None without a fast verify), and the ops it explains: the fast verifies
    /// whose only fault is the odd-y R.
    fast_accepts_odd_r: Option<bool>,
    explained_by_odd_r: Vec<Op>,
}

fn correctness(
    b: &dyn Backend,
    r: &dyn Backend,
    v: &Vectors,
    ds: &Dataset,
    bip: &[Bip340Vector],
    want: &HashMap<String, String>,
    dwant: &HashMap<String, String>,
) -> Correctness {
    let d = digests(b, v);
    let mut good = [false; 8];
    for (i, name) in DIGEST_NAMES.iter().enumerate() {
        let w = want.get(*name).unwrap_or_else(|| die("vectors file lacks a check"));
        good[i] = &d[i] == w;
        if !good[i] {
            eprintln!("{}: {} digest differs from vectors", b.name(), name);
        }
    }
    let (mut fast_ok, mut known_pub_ok, mut known_pub_odd_only, mut seckey_ok) = (true, true, true, true);
    for i in 0..N {
        if b.has_verify_fast() && !b.verify_fast(&v.sig[i], &v.msg[i], &v.xonly[i]) {
            fast_ok = false;
        }
        let mut s = [0u8; 64];
        if !b.sign_known_pub(&mut s, &v.msg[i], &v.seckey[i], &v.pub33[i], &v.xonly[i], &v.aux[i]) || s != v.sig[i] {
            known_pub_ok = false;
            if v.pub33[i][0] != 0x03 {
                known_pub_odd_only = false;
            }
        }
        if !b.seckey_verify(&v.seckey[i]) {
            seckey_ok = false;
        }
    }
    if b.seckey_verify(&[0u8; 32]) || b.seckey_verify(&ORDER) {
        seckey_ok = false;
    }
    let br = bip340(b, r, v, bip);
    if !br.known_pub_ok {
        known_pub_ok = false;
        if !br.known_pub_odd_only {
            known_pub_odd_only = false;
        }
    }
    let batch_ok = (!b.has_verify_batch() || b.verify_batch(&v.xonly[0], &v.batch_sig, &v.msg[..BATCH])) && br.batch_ok;
    let fast_ok_but_odd_r = fast_ok && br.fast_ok_but_odd_r;
    fast_ok = fast_ok && br.fast_ok;
    let verify_ok = good[D_VERIFY] && good[D_VERIFY_TAMPERED] && br.verify_ok;

    let (dd, ecdh_calls_ok) = dataset_digests(b, v, ds);
    let mut dgood = [false; 3];
    for (i, name) in DS_DIGEST_NAMES.iter().enumerate() {
        let w = dwant.get(*name).unwrap_or_else(|| die("dataset digests file lacks a check"));
        dgood[i] = &dd[i] == w;
        if !dgood[i] {
            eprintln!("{}: {} digest differs from the dataset's", b.name(), name);
        }
    }
    let ds_fast_ok =
        !b.has_verify_fast() || (0..ds.id.len()).all(|i| b.verify_fast(&ds.sig[i], &ds.id[i], &ds.pubkey[i]));
    let ds_batch_ok = (!b.has_verify_batch() || (0..ds.pages).all(|p| verify_page(b, ds, p))) && br.batch_ok;
    let ds_verify_ok = dgood[0] && dgood[1] && br.verify_ok;
    let ok = HashMap::from([
        (Op::VerifySame, verify_ok),
        (Op::VerifyMany, verify_ok),
        (Op::VerifyFastSame, verify_ok && fast_ok),
        (Op::VerifyBatch, batch_ok),
        (Op::Sign, good[D_SIGN] && br.sign_ok),
        (Op::SignKnownPub, known_pub_ok),
        (Op::PubCreate, good[D_PUB_CREATE]),
        (Op::PubCompress, good[D_PUB_COMPRESS]),
        (Op::SeckeyVerify, seckey_ok),
        (Op::Ecdh, good[D_ECDH]),
        (Op::TweakMul, good[D_TWEAK_MUL]),
        (Op::TweakAdd, good[D_TWEAK_ADD]),
        (Op::NostrVerify, ds_verify_ok),
        (Op::NostrVerifyFast, ds_verify_ok && ds_fast_ok && br.fast_ok),
        (Op::NostrBatch, ds_batch_ok),
        (Op::NostrEcdh, dgood[2] && ecdh_calls_ok),
    ]);
    let mut explained_by_odd_r = Vec::new();
    if br.fast_accepts_odd_r && fast_ok_but_odd_r {
        if verify_ok {
            explained_by_odd_r.push(Op::VerifyFastSame);
        }
        if ds_verify_ok && ds_fast_ok {
            explained_by_odd_r.push(Op::NostrVerifyFast);
        }
    }
    Correctness {
        ok,
        known_pub_wrong_odd_only: !known_pub_ok && known_pub_odd_only,
        fast_accepts_odd_r: b.has_verify_fast().then_some(br.fast_accepts_odd_r),
        explained_by_odd_r,
    }
}

// ---------------------------------------------------------------- known issues

/// Adds d (±1) to the s half of a signature, as KnownIssuesTest does.
fn add_small_to_s(sig: &mut [u8; 64], d: i32) {
    let mut carry = d;
    let mut i = 63;
    while carry != 0 && i >= 32 {
        let x = sig[i] as i32 + carry;
        sig[i] = x as u8;
        carry = if x > 255 {
            1
        } else if x < 0 {
            -1
        } else {
            0
        };
        i -= 1;
    }
}

/// KI-1 to KI-4 as observed: None when the library lacks the operations involved.
type Issues = [(&'static str, Option<bool>); 4];

/// Probes run after timing, on keys derived under their own labels, so their cache side effects
/// (KI-3) cannot reach the timed rows. `r` is the reference library (libsecp256k1).
fn probe_issues(b: &dyn Backend, r: &dyn Backend, v: &Vectors, c: &Correctness) -> Issues {
    let ki1 = if b.has_verify_batch() {
        let (mut s1, mut s2) = (v.batch_sig[0], v.batch_sig[1]);
        add_small_to_s(&mut s1, 1);
        add_small_to_s(&mut s2, -1);
        let each_invalid = !r.verify(&s1, &v.msg[0], &v.xonly[0]) && !r.verify(&s2, &v.msg[1], &v.xonly[0]);
        Some(each_invalid && b.verify_batch(&v.xonly[0], &[s1, s2], &[v.msg[0], v.msg[1]]))
    } else {
        None
    };
    // KI-3: on odd-y keys of a separate set, tweak-mul the odd-y point, then verify a valid
    // signature for the x-only form.
    let (mut rejected, mut found) = (0, 0);
    let mut i = 0;
    while found < 16 && i < 4096 {
        let sk = sha256(format!("schnorr256k1-bench/v1/probe/{i}").as_bytes());
        i += 1;
        let (mut p65, mut p33) = ([0u8; 65], [0u8; 33]);
        if !r.pubkey_create(&mut p65, &sk) || !r.pubkey_compress(&mut p33, &p65) {
            continue;
        }
        if p33[0] != 0x03 {
            continue;
        }
        found += 1;
        let msg = sha256(&sk);
        let mut sig = [0u8; 64];
        r.sign(&mut sig, &msg, &sk, &msg);
        let mut out = [0u8; 33];
        b.tweak_mul(&mut out, &p33, &v.tweak[0]);
        if !b.verify(&sig, &msg, p33[1..].try_into().unwrap()) {
            rejected += 1;
        }
    }
    [
        ("KI-1", ki1),
        ("KI-2", Some(c.known_pub_wrong_odd_only)),
        ("KI-3", Some(rejected > 0)),
        ("KI-4", c.fast_accepts_odd_r),
    ]
}

/// The ops each known issue makes wrong on the vectors (Runner.COVERS, COVERS in table.py).
/// KI-1 and KI-3 leave every row correct; KI-4 explains a fast verify only when the odd-y R is all
/// that is wrong with it (Correctness::explained_by_odd_r).
const COVERS: [(Op, &str); 3] =
    [(Op::SignKnownPub, "KI-2"), (Op::VerifyFastSame, "KI-4"), (Op::NostrVerifyFast, "KI-4")];

// ---------------------------------------------------------------- context

fn cpu_model() -> String {
    if cfg!(target_os = "macos") {
        if let Ok(o) = std::process::Command::new("sysctl").args(["-n", "machdep.cpu.brand_string"]).output() {
            let s = String::from_utf8_lossy(&o.stdout).trim().to_string();
            if !s.is_empty() {
                return s;
            }
        }
    }
    if let Ok(info) = fs::read_to_string("/proc/cpuinfo") {
        for line in info.lines() {
            for key in ["model name", "Hardware"] {
                if line.starts_with(key) {
                    if let Some((_, val)) = line.split_once(':') {
                        return val.trim().to_string();
                    }
                }
            }
        }
    }
    "unknown".into()
}

fn os_name() -> &'static str {
    if cfg!(target_os = "macos") {
        "macOS"
    } else if cfg!(target_os = "linux") {
        "Linux"
    } else {
        "unknown"
    }
}

fn json_str(s: &str) -> String {
    let mut o = String::from("\"");
    for c in s.chars() {
        if c == '"' || c == '\\' {
            o.push('\\');
        }
        o.push(c);
    }
    o.push('"');
    o
}

/// Days since 1970-01-01 to a civil date (UTC).
fn utc_date() -> String {
    let secs = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
    let z = (secs / 86400) as i64 + 719468;
    let era = z.div_euclid(146097);
    let doe = z - era * 146097;
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    let y = yoe + era * 400 + if m <= 2 { 1 } else { 0 };
    format!("{y:04}-{m:02}-{d:02}")
}

// ---------------------------------------------------------------- main

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let usage = "usage: bench run|check --vectors FILE --dataset DATA --dataset-digests FILE [--library NAME] [--device TEXT] [--machine dedicated|shared] [--harness COMMIT] [--libsecp256k1-commit C] [--quick]";
    if args.len() < 2 || (args[1] != "run" && args[1] != "check") {
        die(usage);
    }
    let check_only = args[1] == "check";
    let (mut vec_path, mut ds_path, mut dsd_path, mut only) = (None, None, None, None);
    let (mut device, mut machine) = ("unknown".to_string(), "unknown".to_string());
    let mut harness = env!("BENCH_HARNESS_VERSION").to_string();
    let mut libsecp_commit = "unknown".to_string();
    let mut quick = false;
    let mut i = 2;
    while i < args.len() {
        let a = args[i].as_str();
        let val = |i: usize| args.get(i + 1).cloned().unwrap_or_else(|| die(usage));
        match a {
            "--vectors" => vec_path = Some(val(i)),
            "--dataset" => ds_path = Some(val(i)),
            "--dataset-digests" => dsd_path = Some(val(i)),
            "--library" => only = Some(val(i)),
            "--device" => device = val(i),
            "--machine" => machine = val(i),
            "--harness" => harness = val(i),
            "--libsecp256k1-commit" => libsecp_commit = val(i),
            "--quick" => {
                quick = true;
                i += 1;
                continue;
            }
            _ => die("unknown option"),
        }
        i += 2;
    }
    let vec_path = vec_path.unwrap_or_else(|| die("--vectors FILE is required"));
    let (Some(ds_path), Some(dsd_path)) = (ds_path, dsd_path) else {
        die("--dataset FILE and --dataset-digests FILE are required")
    };

    let ds = load_dataset(&ds_path);
    let dsd_text = fs::read_to_string(&dsd_path).unwrap_or_else(|_| die("cannot read the dataset digests"));
    let dwant = parse_digests(&dsd_text);
    if dwant.get("file") != Some(&ds.sha_hex) {
        die("the dataset is not the one its digests file describes");
    }
    let vec_text = fs::read_to_string(&vec_path).unwrap_or_else(|_| die("cannot read the vectors file"));
    let vec_sha = hex(&sha256(vec_text.as_bytes()));
    let want = parse_digests(&vec_text);
    let bip_path = Path::new(&vec_path).parent().unwrap_or(Path::new(".")).join("bip340/test-vectors.csv");
    let bip_text = fs::read_to_string(&bip_path).unwrap_or_else(|_| die("cannot read vectors/bip340/test-vectors.csv"));
    let bip_sha = hex(&sha256(bip_text.as_bytes()));
    let bip = load_bip340(&bip_text);

    let reference = libsecp256k1::Libsecp256k1 {
        version: format!(
            "secp256k1 {} (secp256k1-sys {}, libsecp256k1 {})",
            env!("LOCKED_SECP256K1"),
            env!("LOCKED_SECP256K1_SYS"),
            libsecp_commit
        ),
    };
    let k256 = k256_backend::K256 { version: format!("k256 {}", env!("LOCKED_K256")) };
    let fun = secp256kfun_backend::Secp256kfun::new(format!(
        "secp256kfun {} + schnorr_fun {}",
        env!("LOCKED_SECP256KFUN"),
        env!("LOCKED_SCHNORR_FUN")
    ));
    let backends: [&dyn Backend; 3] = [&reference, &k256, &fun];
    if let Some(o) = &only {
        if !backends.iter().any(|b| b.name() == o) {
            die("unknown library");
        }
    }
    let selected: Vec<&dyn Backend> =
        backends.iter().copied().filter(|b| only.as_ref().is_none_or(|o| b.name() == o)).collect();

    let v = derive(&reference);

    if check_only {
        let mut unexplained = Vec::new();
        for b in &selected {
            let c = correctness(*b, &reference, &v, &ds, &bip, &want, &dwant);
            let issues = probe_issues(*b, &reference, &v, &c);
            eprintln!("{} ({}): known issues {:?}", b.name(), b.binding(), issues);
            for (op, name) in OPS {
                if c.ok[&op] {
                    continue;
                }
                let explained = COVERS.iter().any(|(o, ki)| {
                    *o == op
                        && issues.iter().any(|(k, seen)| k == ki && *seen == Some(true))
                        && (*ki != "KI-4" || c.explained_by_odd_r.contains(&op))
                });
                if explained {
                    eprintln!("{} {}: wrong, as a known issue says", b.name(), name);
                } else {
                    unexplained.push(format!("{} {}", b.name(), name));
                }
            }
        }
        if unexplained.is_empty() {
            eprintln!("every result is correct or explained by an observed known issue");
            return;
        }
        for u in &unexplained {
            eprintln!("wrong: {u}");
        }
        exit(1);
    }

    let mut out = String::new();
    out += &format!("{{\n  \"schema\": 4,\n  \"vectors\": \"v1\",\n  \"vectorsSha256\": \"{vec_sha}\",\n");
    out += &format!("  \"bip340Sha256\": \"{bip_sha}\",\n");
    out += &format!("  \"dataset\": \"nostr-v1\",\n  \"datasetSha256\": \"{}\",\n  \"layer\": \"rust\",\n", ds.sha_hex);
    out += &format!("  \"quick\": {quick},\n  \"context\": {{\n    \"date\": \"{}\",\n", utc_date());
    out += &format!(
        "    \"device\": {},\n    \"cpu\": {},\n    \"os\": {},\n",
        json_str(&device),
        json_str(&cpu_model()),
        json_str(os_name())
    );
    let runtime = format!("Rust, {}, {}", env!("BENCH_RUSTC_VERSION"), env!("BENCH_BUILD"));
    out += &format!(
        "    \"runtime\": {},\n    \"machine\": {},\n    \"harness\": {},\n",
        json_str(&runtime),
        json_str(&machine),
        json_str(&harness)
    );
    let libs: Vec<String> =
        selected.iter().map(|b| format!("{}: {}", json_str(b.name()), json_str(&b.version()))).collect();
    out += &format!("    \"libraries\": {{ {} }}\n  }},\n  \"rows\": [", libs.join(", "));

    let mut bench = Bench { v: &v, ds: &ds, next_index: 0, sink: 0 };
    let mut rows = Vec::new();
    let mut issues_json = Vec::new();
    for b in &selected {
        // correctness, before any timing
        let c = correctness(*b, &reference, &v, &ds, &bip, &want, &dwant);
        let mut t = Vec::new();
        for (op, name) in OPS {
            eprintln!("{} {}...", b.name(), name);
            t.push((op, name, bench.time_op(*b, op, quick)));
        }
        let is = probe_issues(*b, &reference, &v, &c);
        let seen = |k: &str| is.iter().any(|(id, s)| *id == k && *s == Some(true));
        for (op, name, tm) in t {
            let Some(tm) = tm else { continue };
            let ki = if (op == Op::VerifyBatch || op == Op::NostrBatch) && seen("KI-1") {
                "\"KI-1\""
            } else if op == Op::SignKnownPub && seen("KI-2") {
                "\"KI-2\""
            } else if (op == Op::VerifyFastSame || op == Op::NostrVerifyFast) && seen("KI-4") {
                "\"KI-4\""
            } else {
                ""
            };
            rows.push(format!(
                "\n    {{ \"op\": \"{name}\", \"library\": \"{}\", \"binding\": \"{}\", \"median_ns\": {:.1}, \"p5_ns\": {:.1}, \"p95_ns\": {:.1}, \"samples\": {}, \"correct\": {}, \"known_issues\": [{ki}] }}",
                b.name(),
                b.binding(),
                tm.median,
                tm.p5,
                tm.p95,
                tm.samples,
                c.ok[&op]
            ));
        }
        let fmt = |s: Option<bool>| s.map_or("null".to_string(), |x| x.to_string());
        issues_json.push(format!(
            "\n    \"{}\": {{ \"KI-1\": {}, \"KI-2\": {}, \"KI-3\": {}, \"KI-4\": {} }}",
            b.name(),
            fmt(is[0].1),
            fmt(is[1].1),
            fmt(is[2].1),
            fmt(is[3].1)
        ));
    }
    out += &rows.join(",");
    out += &format!("\n  ],\n  \"known_issues_observed\": {{{}\n  }}\n}}\n", issues_json.join(","));
    print!("{out}");
    black_box(bench.sink);
}
