/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * The libraries under test, the JavaScript twin of c/backend.h: each call is the one an app makes
 * through that package's public API (js/README.md lists them). Returns null or false on failure;
 * a library that throws on invalid input is caught here, as an app would. Messages are 32 bytes.
 * An operation the library lacks is null: it gets no row (verifyFast falls back to verify).
 *
 * Each factory imports its package only when asked, so a library's process loads nothing else
 * but the reference (js/src/main.js runs each library in its own process).
 */

/** libsecp256k1, through tiny-secp256k1 (bitcoinjs): rust-secp256k1's secp256k1-sys compiled to WebAssembly. */
async function tinySecp256k1() {
  const ecc = await import('tiny-secp256k1');
  const in33 = new Uint8Array(33);
  in33[0] = 0x02;
  const sign = (msg, sk, aux) => {
    try { return ecc.signSchnorr(msg, sk, aux); } catch { return null; }
  };
  return {
    library: 'libsecp256k1',
    binding: 'wasm',
    pkg: 'tiny-secp256k1',
    pubkeyCreate(sk) {
      try { return ecc.pointFromScalar(sk, false); } catch { return null; }
    },
    pubkeyCompress(p65) {
      try { return ecc.pointCompress(p65, true); } catch { return null; }
    },
    seckeyVerify: (sk) => ecc.isPrivate(sk),
    sign,
    // No call takes the public key: as libsecp256k1's C API, signing always derives it.
    signKnownPub: (msg, sk, pub33, xonly, aux) => sign(msg, sk, aux),
    verify(sig, msg, xonly) {
      try { return ecc.verifySchnorr(msg, xonly, sig); } catch { return false; }
    },
    verifyFast: null,
    verifyBatch: null,
    // As the C layer's libsecp256k1: a tweak-mul of 02 || x, compressed, take x.
    ecdhXOnly(xonly, sk) {
      try {
        in33.set(xonly, 1);
        const p = ecc.pointMultiply(in33, sk, true);
        return p === null ? null : p.subarray(1, 33);
      } catch { return null; }
    },
    tweakMul(pub33, tweak) {
      try { return ecc.pointMultiply(pub33, tweak, true); } catch { return null; }
    },
    tweakAdd(sk, tweak) {
      try { return ecc.privateAdd(sk, tweak); } catch { return null; }
    },
  };
}

/** @noble/curves' secp256k1 and its BIP-340 `schnorr` (what nostr-tools, scure-btc-signer and bitcoinerlab use). */
async function nobleCurves() {
  const { secp256k1, schnorr } = await import('@noble/curves/secp256k1.js');
  const { Point } = secp256k1;
  const Fn = Point.Fn;
  const in33 = new Uint8Array(33);
  in33[0] = 0x02;
  const sign = (msg, sk, aux) => {
    try { return schnorr.sign(msg, sk, aux); } catch { return null; }
  };
  return {
    library: 'noble-curves',
    binding: 'js',
    pkg: '@noble/curves',
    pubkeyCreate(sk) {
      try { return secp256k1.getPublicKey(sk, false); } catch { return null; }
    },
    pubkeyCompress(p65) {
      try { return Point.fromBytes(p65).toBytes(true); } catch { return null; }
    },
    seckeyVerify: (sk) => secp256k1.utils.isValidSecretKey(sk),
    sign,
    // schnorr.sign takes no public key.
    signKnownPub: (msg, sk, pub33, xonly, aux) => sign(msg, sk, aux),
    verify(sig, msg, xonly) {
      try { return schnorr.verify(sig, msg, xonly); } catch { return false; }
    },
    verifyFast: null,
    verifyBatch: null,
    // As nostr-tools' NIP-04: getSharedSecret with 02 || x, take x.
    ecdhXOnly(xonly, sk) {
      try {
        in33.set(xonly, 1);
        return secp256k1.getSharedSecret(sk, in33, true).subarray(1, 33);
      } catch { return null; }
    },
    tweakMul(pub33, tweak) {
      try { return Point.fromBytes(pub33).multiply(Fn.fromBytes(tweak)).toBytes(true); } catch { return null; }
    },
    // The scalar field Point.Fn, as @bitcoinerlab/secp256k1's privateAdd does it.
    tweakAdd(sk, tweak) {
      try {
        const r = Fn.add(Fn.fromBytes(sk), Fn.fromBytes(tweak));
        return Fn.isValidNot0(r) ? Fn.toBytes(r) : null;
      } catch { return null; }
    },
  };
}

/** @noble/secp256k1, the separate 5 KB single-file library, with @noble/hashes' SHA-256 for its synchronous calls (its README's setup). */
async function nobleSecp256k1() {
  const secp = await import('@noble/secp256k1');
  const { sha256 } = await import('@noble/hashes/sha2.js');
  secp.hashes.sha256 = sha256;
  const { Point, schnorr, etc } = secp;
  const in33 = new Uint8Array(33);
  in33[0] = 0x02;
  const sign = (msg, sk, aux) => {
    try { return schnorr.sign(msg, sk, aux); } catch { return null; }
  };
  return {
    library: 'noble-secp256k1',
    binding: 'js',
    pkg: '@noble/secp256k1',
    pubkeyCreate(sk) {
      try { return secp.getPublicKey(sk, false); } catch { return null; }
    },
    pubkeyCompress(p65) {
      try { return Point.fromBytes(p65).toBytes(true); } catch { return null; }
    },
    seckeyVerify: (sk) => secp.utils.isValidSecretKey(sk),
    sign,
    signKnownPub: (msg, sk, pub33, xonly, aux) => sign(msg, sk, aux),
    verify(sig, msg, xonly) {
      try { return schnorr.verify(sig, msg, xonly); } catch { return false; }
    },
    verifyFast: null,
    verifyBatch: null,
    ecdhXOnly(xonly, sk) {
      try {
        in33.set(xonly, 1);
        return secp.getSharedSecret(sk, in33, true).subarray(1, 33);
      } catch { return null; }
    },
    tweakMul(pub33, tweak) {
      try { return Point.fromBytes(pub33).multiply(etc.bytesToNumberBE(tweak)).toBytes(true); } catch { return null; }
    },
    // No scalar addition in its API (etc.mod would leave the addition to the harness): no row.
    tweakAdd: null,
  };
}

/** In table order; the first is the reference (the layer's libsecp256k1 binding). */
export const LIBRARIES = {
  'libsecp256k1': tinySecp256k1,
  'noble-curves': nobleCurves,
  'noble-secp256k1': nobleSecp256k1,
};

export const REFERENCE = 'libsecp256k1';
