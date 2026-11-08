//! Input vectors v1 (vectors/README.md), and a small SHA-256 used only to derive and digest
//! them, so the derivation does not depend on any library under test. The twin of c/vectors.c.

pub const N: usize = 4096;
pub const BATCH: usize = 200;

// ---- SHA-256 (FIPS 180-4) ----

const K: [u32; 64] = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98,
    0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786,
    0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8,
    0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819,
    0xd6990624, 0xf40e3585, 0x106aa070, 0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a,
    0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7,
    0xc67178f2,
];

#[derive(Clone)]
pub struct Sha256 {
    h: [u32; 8],
    len: u64,
    buf: [u8; 64],
    fill: usize,
}

impl Sha256 {
    pub fn new() -> Self {
        Sha256 {
            h: [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19],
            len: 0,
            buf: [0; 64],
            fill: 0,
        }
    }

    fn block(&mut self) {
        let p = &self.buf;
        let mut w = [0u32; 64];
        for i in 0..16 {
            w[i] = u32::from_be_bytes([p[4 * i], p[4 * i + 1], p[4 * i + 2], p[4 * i + 3]]);
        }
        for i in 16..64 {
            let s0 = w[i - 15].rotate_right(7) ^ w[i - 15].rotate_right(18) ^ (w[i - 15] >> 3);
            let s1 = w[i - 2].rotate_right(17) ^ w[i - 2].rotate_right(19) ^ (w[i - 2] >> 10);
            w[i] = w[i - 16].wrapping_add(s0).wrapping_add(w[i - 7]).wrapping_add(s1);
        }
        let [mut a, mut b, mut c, mut d, mut e, mut f, mut g, mut h] = self.h;
        for i in 0..64 {
            let t1 = h
                .wrapping_add(e.rotate_right(6) ^ e.rotate_right(11) ^ e.rotate_right(25))
                .wrapping_add((e & f) ^ (!e & g))
                .wrapping_add(K[i])
                .wrapping_add(w[i]);
            let t2 =
                (a.rotate_right(2) ^ a.rotate_right(13) ^ a.rotate_right(22)).wrapping_add((a & b) ^ (a & c) ^ (b & c));
            h = g;
            g = f;
            f = e;
            e = d.wrapping_add(t1);
            d = c;
            c = b;
            b = a;
            a = t1.wrapping_add(t2);
        }
        for (s, v) in self.h.iter_mut().zip([a, b, c, d, e, f, g, h]) {
            *s = s.wrapping_add(v);
        }
    }

    pub fn update(&mut self, mut data: &[u8]) {
        self.len += data.len() as u64;
        while !data.is_empty() {
            let take = (64 - self.fill).min(data.len());
            self.buf[self.fill..self.fill + take].copy_from_slice(&data[..take]);
            self.fill += take;
            data = &data[take..];
            if self.fill == 64 {
                self.block();
                self.fill = 0;
            }
        }
    }

    pub fn finalize(mut self) -> [u8; 32] {
        let bits = self.len.wrapping_mul(8);
        self.update(&[0x80]);
        while self.fill != 56 {
            self.update(&[0]);
        }
        self.update(&bits.to_be_bytes());
        let mut out = [0u8; 32];
        for i in 0..8 {
            out[4 * i..4 * i + 4].copy_from_slice(&self.h[i].to_be_bytes());
        }
        out
    }
}

pub fn sha256(data: &[u8]) -> [u8; 32] {
    let mut c = Sha256::new();
    c.update(data);
    c.finalize()
}

pub fn hex(b: &[u8]) -> String {
    b.iter().map(|x| format!("{x:02x}")).collect()
}

// ---- Inputs ----

/// n, the secp256k1 group order, big-endian.
pub const ORDER: [u8; 32] = [
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFE, 0xBA, 0xAE, 0xDC,
    0xE6, 0xAF, 0x48, 0xA0, 0x3B, 0xBF, 0xD2, 0x5E, 0x8C, 0xD0, 0x36, 0x41, 0x41,
];

fn valid_scalar(s: &[u8; 32]) -> bool {
    s.iter().any(|&b| b != 0) && s < &ORDER
}

fn label_hash(field: &str, i: usize) -> [u8; 32] {
    sha256(format!("schnorr256k1-bench/v1/{field}/{i}").as_bytes())
}

fn scalar_from_label(field: &str, i: usize) -> [u8; 32] {
    let mut h = label_hash(field, i);
    while !valid_scalar(&h) {
        h = sha256(&h);
    }
    h
}

pub struct Vectors {
    pub seckey: Vec<[u8; 32]>,
    pub tweak: Vec<[u8; 32]>,
    pub msg: Vec<[u8; 32]>,
    pub aux: Vec<[u8; 32]>,
    // derived with the reference library (libsecp256k1)
    pub pub65: Vec<[u8; 65]>,
    pub pub33: Vec<[u8; 33]>,
    pub xonly: Vec<[u8; 32]>,
    pub tpub33: Vec<[u8; 33]>,
    pub sig: Vec<[u8; 64]>,
    pub batch_sig: Vec<[u8; 64]>,
}

impl Vectors {
    /// seckey, tweak, msg and aux by the v1 rule; the derived values are left empty.
    pub fn derive_inputs() -> Vectors {
        Vectors {
            seckey: (0..N).map(|i| scalar_from_label("seckey", i)).collect(),
            tweak: (0..N).map(|i| scalar_from_label("tweak", i)).collect(),
            msg: (0..N).map(|i| label_hash("msg", i)).collect(),
            aux: (0..N).map(|i| label_hash("aux", i)).collect(),
            pub65: Vec::new(),
            pub33: Vec::new(),
            xonly: Vec::new(),
            tpub33: Vec::new(),
            sig: Vec::new(),
            batch_sig: Vec::new(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sha256_known_answers() {
        assert_eq!(hex(&sha256(b"")), "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assert_eq!(hex(&sha256(b"abc")), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        let long = vec![b'a'; 1000];
        let mut c = Sha256::new();
        for chunk in long.chunks(7) {
            c.update(chunk);
        }
        assert_eq!(c.finalize(), sha256(&long));
    }
}
