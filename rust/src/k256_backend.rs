//! k256 (RustCrypto's pure-Rust secp256k1), through its public API: `schnorr::SigningKey` /
//! `VerifyingKey` for BIP-340, `SecretKey` / `PublicKey` with SEC1 encoding for keys,
//! `ecdh::diffie_hellman` for ECDH, and its `ProjectivePoint` / `Scalar` / `NonZeroScalar`
//! operators for the two tweaks (the crate has no tweak functions: these operators are how a
//! k256 app tweaks, e.g. the `bip32` crate).

use k256::elliptic_curve::ff::PrimeField;
use k256::elliptic_curve::sec1::ToSec1Point;
use k256::schnorr::signature::hazmat::PrehashVerifier;
use k256::schnorr::{Signature, SigningKey, VerifyingKey};
use k256::{FieldBytes, NonZeroScalar, PublicKey, Scalar, SecretKey};

use crate::backend::Backend;

pub struct K256 {
    pub version: String,
}

fn fb(b: &[u8; 32]) -> FieldBytes {
    FieldBytes::from(*b)
}

impl Backend for K256 {
    fn name(&self) -> &'static str {
        "k256"
    }
    fn binding(&self) -> &'static str {
        "rust"
    }
    fn version(&self) -> String {
        self.version.clone()
    }

    fn pubkey_create(&self, out65: &mut [u8; 65], seckey: &[u8; 32]) -> bool {
        let Ok(sk) = SecretKey::from_bytes(&fb(seckey)) else { return false };
        out65.copy_from_slice(sk.public_key().to_sec1_point(false).as_bytes());
        true
    }

    fn pubkey_compress(&self, out33: &mut [u8; 33], pub65: &[u8; 65]) -> bool {
        let Ok(pk) = PublicKey::from_sec1_bytes(pub65) else { return false };
        out33.copy_from_slice(pk.to_sec1_point(true).as_bytes());
        true
    }

    fn seckey_verify(&self, seckey: &[u8; 32]) -> bool {
        SecretKey::from_bytes(&fb(seckey)).is_ok()
    }

    /// `SigningKey::sign_raw` is the call that takes BIP-340's aux bytes explicitly (the
    /// `RandomizedPrehashSigner` impl draws them from an RNG and then calls it).
    fn sign(&self, out64: &mut [u8; 64], msg: &[u8; 32], seckey: &[u8; 32], aux: &[u8; 32]) -> bool {
        let Ok(key) = SigningKey::from_bytes(&fb(seckey)) else { return false };
        let Ok(sig) = key.sign_raw(msg, aux) else { return false };
        *out64 = sig.to_bytes();
        true
    }

    /// k256 has no signing key built from a known public key: `SigningKey` always derives it.
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

    /// `verify_prehash` is BIP-340 verification of a 32-byte message as given (`Verifier::verify`
    /// would SHA-256 it first).
    fn verify(&self, sig: &[u8; 64], msg: &[u8; 32], xonly: &[u8; 32]) -> bool {
        let Ok(key) = VerifyingKey::from_bytes(&fb(xonly)) else { return false };
        let Ok(sig) = Signature::from_bytes(sig) else { return false };
        key.verify_prehash(msg, &sig).is_ok()
    }

    fn ecdh_xonly(&self, out32: &mut [u8; 32], xonly: &[u8; 32], seckey: &[u8; 32]) -> bool {
        let mut in33 = [0u8; 33];
        in33[0] = 0x02;
        in33[1..].copy_from_slice(xonly);
        let Ok(pk) = PublicKey::from_sec1_bytes(&in33) else { return false };
        let Some(sk) = NonZeroScalar::from_repr(fb(seckey)).into_option() else { return false };
        let shared = k256::ecdh::diffie_hellman(sk, pk.as_affine());
        out32.copy_from_slice(shared.raw_secret_bytes());
        true
    }

    fn tweak_mul(&self, out33: &mut [u8; 33], pub33: &[u8; 33], tweak: &[u8; 32]) -> bool {
        let Ok(pk) = PublicKey::from_sec1_bytes(pub33) else { return false };
        let Some(t) = NonZeroScalar::from_repr(fb(tweak)).into_option() else { return false };
        let Ok(r) = PublicKey::from_affine((pk.to_projective() * *t).to_affine()) else { return false };
        out33.copy_from_slice(r.to_sec1_point(true).as_bytes());
        true
    }

    fn tweak_add(&self, out32: &mut [u8; 32], seckey: &[u8; 32], tweak: &[u8; 32]) -> bool {
        let Some(sk) = NonZeroScalar::from_repr(fb(seckey)).into_option() else { return false };
        let Some(t) = Scalar::from_repr(fb(tweak)).into_option() else { return false };
        let Some(r) = NonZeroScalar::new(*sk + t).into_option() else { return false };
        out32.copy_from_slice(&r.to_repr());
        true
    }
}
