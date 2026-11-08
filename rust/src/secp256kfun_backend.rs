//! secp256kfun + schnorr_fun (LLFourn's pure-Rust secp256k1 and its BIP-340 crate), through
//! their public API: `Schnorr::sign` / `verify` with `Message::raw` for BIP-340, `Point` /
//! `Scalar` encodings for keys, and the `g!` / `s!` operator macros for ECDH and the two tweaks
//! (secp256kfun has no tweak or ECDH functions: these macros are its API for them). Secret
//! scalars keep the default `Secret` marker, so the point multiplications take the library's
//! constant-time path, as an app's would.
//!
//! BIP-340 signing with given aux bytes: schnorr_fun's `Synthetic` nonce generator computes
//! BIP-340's nonce (tagged-hash of aux xor the key, then of the public key and message) from
//! the 32 bytes its `NonceRng` hands it. `AuxRng` is that `NonceRng`, holding the call's aux.

use std::cell::Cell;
use std::rc::Rc;

use schnorr_fun::fun::marker::{EvenY, NonZero, Normal, Public, Secret, Zero};
use schnorr_fun::fun::nonce::{NonceRng, Synthetic};
use schnorr_fun::fun::{g, s, KeyPair, Point, Scalar, G};
use schnorr_fun::{Message, Schnorr, Signature};
use sha2::Sha256;

use crate::backend::Backend;

pub struct AuxRng(Rc<Cell<[u8; 32]>>);

impl NonceRng for AuxRng {
    fn fill_bytes(&self, bytes: &mut [u8]) {
        bytes.copy_from_slice(&self.0.get());
    }
}

pub struct Secp256kfun {
    pub version: String,
    schnorr: Schnorr<Sha256, Synthetic<Sha256, AuxRng>>,
    aux: Rc<Cell<[u8; 32]>>,
}

impl Secp256kfun {
    pub fn new(version: String) -> Self {
        let aux = Rc::new(Cell::new([0; 32]));
        let nonce_gen = Synthetic::<Sha256, AuxRng>::new(AuxRng(aux.clone()));
        Secp256kfun { version, schnorr: Schnorr::new(nonce_gen), aux }
    }
}

fn secret(b: &[u8; 32]) -> Option<Scalar<Secret, NonZero>> {
    Scalar::<Secret, NonZero>::from_bytes(*b)
}

impl Backend for Secp256kfun {
    fn name(&self) -> &'static str {
        "secp256kfun"
    }
    fn binding(&self) -> &'static str {
        "rust"
    }
    fn version(&self) -> String {
        self.version.clone()
    }

    fn pubkey_create(&self, out65: &mut [u8; 65], seckey: &[u8; 32]) -> bool {
        let Some(sk) = secret(seckey) else { return false };
        *out65 = g!(sk * G).normalize().to_bytes_uncompressed();
        true
    }

    fn pubkey_compress(&self, out33: &mut [u8; 33], pub65: &[u8; 65]) -> bool {
        let Some(p) = Point::<Normal, Public, NonZero>::from_bytes_uncompressed(*pub65) else { return false };
        *out33 = p.to_bytes();
        true
    }

    fn seckey_verify(&self, seckey: &[u8; 32]) -> bool {
        secret(seckey).is_some()
    }

    fn sign(&self, out64: &mut [u8; 64], msg: &[u8; 32], seckey: &[u8; 32], aux: &[u8; 32]) -> bool {
        let Some(sk) = secret(seckey) else { return false };
        let keypair = KeyPair::new_xonly(sk);
        self.aux.set(*aux);
        *out64 = self.schnorr.sign(&keypair, Message::raw(msg)).to_bytes();
        true
    }

    /// `KeyPair` has no constructor from a known public key: it always derives it.
    fn sign_known_pub(
        &self,
        out64: &mut [u8; 64],
        msg: &[u8; 32],
        seckey: &[u8; 32],
        _pub33: &[u8; 33],
        _xonly: &[u8; 32],
        aux: &[u8; 32],
    ) -> bool {
        self.sign(out64, msg, seckey, aux)
    }

    fn verify(&self, sig: &[u8; 64], msg: &[u8; 32], xonly: &[u8; 32]) -> bool {
        let Some(pk) = Point::<EvenY, Public>::from_xonly_bytes(*xonly) else { return false };
        let Some(sig) = Signature::from_bytes(*sig) else { return false };
        self.schnorr.verify(&pk, Message::raw(msg), &sig)
    }

    fn ecdh_xonly(&self, out32: &mut [u8; 32], xonly: &[u8; 32], seckey: &[u8; 32]) -> bool {
        let Some(p) = Point::<EvenY, Public>::from_xonly_bytes(*xonly) else { return false };
        let Some(sk) = secret(seckey) else { return false };
        *out32 = g!(sk * p).normalize().coordinates().0;
        true
    }

    fn tweak_mul(&self, out33: &mut [u8; 33], pub33: &[u8; 33], tweak: &[u8; 32]) -> bool {
        let Some(p) = Point::<Normal, Public, NonZero>::from_bytes(*pub33) else { return false };
        let Some(t) = secret(tweak) else { return false };
        *out33 = g!(t * p).normalize().to_bytes();
        true
    }

    fn tweak_add(&self, out32: &mut [u8; 32], seckey: &[u8; 32], tweak: &[u8; 32]) -> bool {
        let Some(sk) = secret(seckey) else { return false };
        let Some(t) = Scalar::<Secret, Zero>::from_bytes(*tweak) else { return false };
        let Some(r) = s!(sk + t).non_zero() else { return false };
        *out32 = r.to_bytes();
        true
    }
}
