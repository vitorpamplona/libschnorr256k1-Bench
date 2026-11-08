//! One library under test: the twin of c/backend.h. Every function returns true on success,
//! false on failure (invalid input or a failed verification). Byte formats as in
//! results/SCHEMA.md; messages are 32 bytes. Outputs go to caller-owned buffers, as in C.

pub trait Backend {
    /// results/SCHEMA.md "library"
    fn name(&self) -> &'static str;
    /// results/SCHEMA.md "binding"
    fn binding(&self) -> &'static str;
    /// the crate(s) and exact versions, for the result's context
    fn version(&self) -> String;

    fn pubkey_create(&self, out65: &mut [u8; 65], seckey: &[u8; 32]) -> bool;
    fn pubkey_compress(&self, out33: &mut [u8; 33], pub65: &[u8; 65]) -> bool;
    fn seckey_verify(&self, seckey: &[u8; 32]) -> bool;
    fn sign(&self, out64: &mut [u8; 64], msg: &[u8; 32], seckey: &[u8; 32], aux: &[u8; 32]) -> bool;
    /// The cheapest signing call when the caller already holds its public key.
    fn sign_known_pub(
        &self,
        out64: &mut [u8; 64],
        msg: &[u8; 32],
        seckey: &[u8; 32],
        pub33: &[u8; 33],
        xonly: &[u8; 32],
        aux: &[u8; 32],
    ) -> bool;
    fn verify(&self, sig: &[u8; 64], msg: &[u8; 32], xonly: &[u8; 32]) -> bool;
    /// false when the library has no fast verify: the full verify is used and timed instead.
    fn has_verify_fast(&self) -> bool {
        false
    }
    fn verify_fast(&self, sig: &[u8; 64], msg: &[u8; 32], xonly: &[u8; 32]) -> bool {
        self.verify(sig, msg, xonly)
    }
    /// false when the library has no batch verify: it has no batch rows.
    fn has_verify_batch(&self) -> bool {
        false
    }
    fn verify_batch(&self, _xonly: &[u8; 32], _sigs: &[[u8; 64]], _msgs: &[[u8; 32]]) -> bool {
        false
    }
    fn ecdh_xonly(&self, out32: &mut [u8; 32], xonly: &[u8; 32], seckey: &[u8; 32]) -> bool;
    fn tweak_mul(&self, out33: &mut [u8; 33], pub33: &[u8; 33], tweak: &[u8; 32]) -> bool;
    fn tweak_add(&self, out32: &mut [u8; 32], seckey: &[u8; 32], tweak: &[u8; 32]) -> bool;
}
