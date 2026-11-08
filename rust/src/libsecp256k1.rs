//! libsecp256k1 through rust-bitcoin's `secp256k1` crate (which builds the C library bundled in
//! `secp256k1-sys` with the `cc` crate), called the way a Rust app calls it: the crate's
//! thread-local global context, keys parsed from bytes on every call, and x-only ECDH as a
//! tweak-mul of 02 || x, as the c layer does (backend_libsecp256k1.c).
//!
//! Since 0.33 the crate re-randomizes its thread-local context after every call that takes a
//! secret (key-pair and public-key creation, signing), seeded from that secret; the times
//! include it, because every app using the crate pays it.

use secp256k1::{schnorr, Keypair, PublicKey, Scalar, SecretKey, XOnlyPublicKey};

use crate::backend::Backend;

pub struct Libsecp256k1 {
    pub version: String,
}

impl Backend for Libsecp256k1 {
    fn name(&self) -> &'static str {
        "libsecp256k1"
    }
    fn binding(&self) -> &'static str {
        "rust-secp256k1"
    }
    fn version(&self) -> String {
        self.version.clone()
    }

    fn pubkey_create(&self, out65: &mut [u8; 65], seckey: &[u8; 32]) -> bool {
        let Ok(sk) = SecretKey::from_secret_bytes(*seckey) else { return false };
        *out65 = PublicKey::from_secret_key(&sk).serialize_uncompressed();
        true
    }

    fn pubkey_compress(&self, out33: &mut [u8; 33], pub65: &[u8; 65]) -> bool {
        let Ok(pk) = PublicKey::from_byte_array_uncompressed(*pub65) else { return false };
        *out33 = pk.serialize();
        true
    }

    fn seckey_verify(&self, seckey: &[u8; 32]) -> bool {
        SecretKey::from_secret_bytes(*seckey).is_ok()
    }

    fn sign(&self, out64: &mut [u8; 64], msg: &[u8; 32], seckey: &[u8; 32], aux: &[u8; 32]) -> bool {
        let Ok(kp) = Keypair::from_secret_bytes(*seckey) else { return false };
        *out64 = schnorr::sign_with_aux_rand(msg, &kp, aux).to_byte_array();
        true
    }

    /// The crate's API always derives the key pair from the secret.
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
        let Ok(pk) = XOnlyPublicKey::from_byte_array(*xonly) else { return false };
        schnorr::verify(&schnorr::Signature::from_byte_array(*sig), msg, &pk).is_ok()
    }

    fn ecdh_xonly(&self, out32: &mut [u8; 32], xonly: &[u8; 32], seckey: &[u8; 32]) -> bool {
        let mut in33 = [0u8; 33];
        in33[0] = 0x02;
        in33[1..].copy_from_slice(xonly);
        let Ok(pk) = PublicKey::from_byte_array_compressed(in33) else { return false };
        let Ok(t) = Scalar::from_be_bytes(*seckey) else { return false };
        let Ok(r) = pk.mul_tweak(&t) else { return false };
        out32.copy_from_slice(&r.serialize()[1..]);
        true
    }

    fn tweak_mul(&self, out33: &mut [u8; 33], pub33: &[u8; 33], tweak: &[u8; 32]) -> bool {
        let Ok(pk) = PublicKey::from_byte_array_compressed(*pub33) else { return false };
        let Ok(t) = Scalar::from_be_bytes(*tweak) else { return false };
        let Ok(r) = pk.mul_tweak(&t) else { return false };
        *out33 = r.serialize();
        true
    }

    fn tweak_add(&self, out32: &mut [u8; 32], seckey: &[u8; 32], tweak: &[u8; 32]) -> bool {
        let Ok(sk) = SecretKey::from_secret_bytes(*seckey) else { return false };
        let Ok(t) = Scalar::from_be_bytes(*tweak) else { return false };
        let Ok(r) = sk.add_tweak(&t) else { return false };
        *out32 = r.to_secret_bytes();
        true
    }
}
