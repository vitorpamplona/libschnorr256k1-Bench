// Copyright (c) 2025 Vitor Pamplona

package main

// The adapters: each operation through the library's own public API only (results/SCHEMA.md,
// go/README.md). Where a library lacks an operation as one call, the composition below is of
// its own calls; the harness only copies bytes.

import (
	"bytes"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/btcsuite/btcd/btcec/v2/schnorr"
	ss "github.com/splitsecure/secp256k1-go"
	ssecdh "github.com/splitsecure/secp256k1-go/ecdh"
	ssschnorr "github.com/splitsecure/secp256k1-go/schnorr"
	voi "gitlab.com/yawning/secp256k1-voi"
	voisecec "gitlab.com/yawning/secp256k1-voi/secec"
	voibtc "gitlab.com/yawning/secp256k1-voi/secec/bitcoin"
	"p256k1.mleku.dev"

	"github.com/vitorpamplona/libschnorr256k1-bench/go/libsecp256k1"
)

// ---------------------------------------------------------------- libsecp256k1 (cgo)

var libsecp256k1Backend = &backend{
	name:           "libsecp256k1",
	binding:        "cgo",
	pubkeyCreate:   libsecp256k1.PubkeyCreate,
	pubkeyCompress: libsecp256k1.PubkeyCompress,
	seckeyVerify:   libsecp256k1.SeckeyVerify,
	sign:           libsecp256k1.Sign,
	// libsecp256k1's API always derives the key pair from the secret.
	signKnownPub: func(sig, msg, seckey, _, _, aux []byte) bool { return libsecp256k1.Sign(sig, msg, seckey, aux) },
	verify:       libsecp256k1.Verify,
	ecdhXOnly:    libsecp256k1.EcdhXOnly,
	tweakMul:     libsecp256k1.TweakMul,
	tweakAdd:     libsecp256k1.TweakAdd,
}

// ---------------------------------------------------------------- btcec (btcd)

// btcecSecret parses a secret key the way btcec does (ModNScalar.SetByteSlice), rejecting what
// overflows or is zero, without deriving its public key.
func btcecSecret(k *btcec.ModNScalar, b []byte) bool {
	overflow := k.SetByteSlice(b)
	return !overflow && !k.IsZero()
}

func btcecSign(sig, msg, seckey, aux []byte) bool {
	var k btcec.ModNScalar
	if !btcecSecret(&k, seckey) {
		return false
	}
	s, err := schnorr.Sign(btcec.PrivKeyFromScalar(&k), msg, schnorr.CustomNonce([32]byte(aux)), schnorr.FastSign())
	if err != nil {
		return false
	}
	copy(sig, s.Serialize())
	return true
}

var btcecBackend = &backend{
	name:    "btcec",
	binding: "go",
	module:  "github.com/btcsuite/btcd/btcec/v2",
	pubkeyCreate: func(pub65, seckey []byte) bool {
		var k btcec.ModNScalar
		if !btcecSecret(&k, seckey) {
			return false
		}
		copy(pub65, btcec.PrivKeyFromScalar(&k).PubKey().SerializeUncompressed())
		return true
	},
	pubkeyCompress: func(pub33, pub65 []byte) bool {
		p, err := btcec.ParsePubKey(pub65)
		if err != nil {
			return false
		}
		copy(pub33, p.SerializeCompressed())
		return true
	},
	seckeyVerify: func(seckey []byte) bool {
		var k btcec.ModNScalar
		return btcecSecret(&k, seckey)
	},
	sign: btcecSign,
	// schnorr.Sign takes only the private key and derives P itself.
	signKnownPub: func(sig, msg, seckey, _, _, aux []byte) bool { return btcecSign(sig, msg, seckey, aux) },
	verify: func(sig, msg, xonly []byte) bool {
		p, err := schnorr.ParsePubKey(xonly)
		if err != nil {
			return false
		}
		s, err := schnorr.ParseSignature(sig)
		if err != nil {
			return false
		}
		return s.Verify(msg, p)
	},
	ecdhXOnly: func(out, xonly, seckey []byte) bool {
		var k btcec.ModNScalar
		if !btcecSecret(&k, seckey) {
			return false
		}
		p, err := schnorr.ParsePubKey(xonly)
		if err != nil {
			return false
		}
		copy(out, btcec.GenerateSharedSecret(btcec.PrivKeyFromScalar(&k), p))
		return true
	},
	tweakMul: func(out33, pub33, tweak []byte) bool {
		var k btcec.ModNScalar
		if !btcecSecret(&k, tweak) {
			return false
		}
		p, err := btcec.ParsePubKey(pub33)
		if err != nil {
			return false
		}
		var pj, r btcec.JacobianPoint
		p.AsJacobian(&pj)
		btcec.ScalarMultNonConst(&k, &pj, &r)
		r.ToAffine()
		copy(out33, btcec.NewPublicKey(&r.X, &r.Y).SerializeCompressed())
		return true
	},
	tweakAdd: func(out32, seckey, tweak []byte) bool {
		var k, t btcec.ModNScalar
		if !btcecSecret(&k, seckey) || t.SetByteSlice(tweak) {
			return false
		}
		k.Add(&t)
		if k.IsZero() {
			return false
		}
		k.PutBytesUnchecked(out32)
		return true
	},
}

// ---------------------------------------------------------------- p256k1 (mleku, ORLY)

func p256k1Sign(sig, msg, seckey, aux []byte) bool {
	kp, err := p256k1.KeyPairCreate(seckey)
	if err != nil {
		return false
	}
	return p256k1.SchnorrSign(sig, msg, kp, aux) == nil
}

func p256k1Verify(sig, msg, xonly []byte) bool {
	pk, err := p256k1.XOnlyPubkeyParse(xonly)
	if err != nil {
		return false
	}
	return p256k1.SchnorrVerify(sig, msg, pk)
}

var p256k1Items []p256k1.BatchSchnorrItem

var p256k1Backend = &backend{
	name:    "p256k1",
	binding: "go",
	module:  "p256k1.mleku.dev",
	pubkeyCreate: func(pub65, seckey []byte) bool {
		var pk p256k1.PublicKey
		if p256k1.ECPubkeyCreate(&pk, seckey) != nil {
			return false
		}
		return p256k1.ECPubkeySerialize(pub65, &pk, p256k1.ECUncompressed) == 65
	},
	pubkeyCompress: func(pub33, pub65 []byte) bool {
		var pk p256k1.PublicKey
		if p256k1.ECPubkeyParse(&pk, pub65) != nil {
			return false
		}
		return p256k1.ECPubkeySerialize(pub33, &pk, p256k1.ECCompressed) == 33
	},
	seckeyVerify: p256k1.ECSeckeyVerify,
	sign:         p256k1Sign,
	// KeyPair has no constructor that takes a known public key.
	signKnownPub: func(sig, msg, seckey, _, _, aux []byte) bool { return p256k1Sign(sig, msg, seckey, aux) },
	verify:       p256k1Verify,
	// SchnorrBatchVerify takes (key, message, signature) items: the one key, parsed once.
	verifyBatch: func(xonly []byte, sigs, msgs [][]byte) bool {
		pk, err := p256k1.XOnlyPubkeyParse(xonly)
		if err != nil {
			return false
		}
		items := p256k1Items[:0]
		for i := range sigs {
			items = append(items, p256k1.BatchSchnorrItem{Pubkey: pk, Message: msgs[i], Signature: sigs[i]})
		}
		p256k1Items = items
		return p256k1.SchnorrBatchVerify(items)
	},
	ecdhXOnly: func(out, xonly, seckey []byte) bool {
		var in [33]byte
		in[0] = 0x02
		copy(in[1:], xonly)
		var pk p256k1.PublicKey
		if p256k1.ECPubkeyParse(&pk, in[:]) != nil {
			return false
		}
		return p256k1.ECDHXOnly(out, &pk, seckey) == nil
	},
	tweakMul: func(out33, pub33, tweak []byte) bool {
		var pk p256k1.PublicKey
		if p256k1.ECPubkeyParse(&pk, pub33) != nil || p256k1.ECPubkeyTweakMul(&pk, tweak) != nil {
			return false
		}
		return p256k1.ECPubkeySerialize(out33, &pk, p256k1.ECCompressed) == 33
	},
	tweakAdd: func(out32, seckey, tweak []byte) bool {
		copy(out32, seckey)
		return p256k1.ECSeckeyTweakAdd(out32, tweak) == nil
	},
}

// ---------------------------------------------------------------- secp256k1-voi (Yawning Angel)

// voiScalar parses a scalar as secec.NewPrivateKey does: no reduction, not zero.
func voiScalar(b []byte) (*voi.Scalar, bool) {
	s, reduced := voi.NewScalarFromBytes((*[voi.ScalarSize]byte)(b))
	return s, reduced == 0 && s.IsZero() == 0
}

var voiAux bytes.Reader

func voiSign(sig, msg, seckey, aux []byte) bool {
	k, err := voibtc.NewSchnorrPrivateKey(seckey)
	if err != nil {
		return false
	}
	voiAux.Reset(aux) // Sign reads its 32 bytes of auxiliary randomness from this reader
	s, err := k.Sign(&voiAux, msg, nil)
	if err != nil {
		return false
	}
	copy(sig, s)
	return true
}

var voiBackend = &backend{
	name:    "secp256k1-voi",
	binding: "go",
	module:  "gitlab.com/yawning/secp256k1-voi",
	pubkeyCreate: func(pub65, seckey []byte) bool {
		k, err := voisecec.NewPrivateKey(seckey)
		if err != nil {
			return false
		}
		copy(pub65, k.PublicKey().Bytes())
		return true
	},
	pubkeyCompress: func(pub33, pub65 []byte) bool {
		p, err := voisecec.NewPublicKey(pub65)
		if err != nil {
			return false
		}
		copy(pub33, p.CompressedBytes())
		return true
	},
	seckeyVerify: func(seckey []byte) bool {
		_, ok := voiScalar(seckey)
		return ok
	},
	sign: voiSign,
	// SchnorrPrivateKey is built from the secret alone.
	signKnownPub: func(sig, msg, seckey, _, _, aux []byte) bool { return voiSign(sig, msg, seckey, aux) },
	verify: func(sig, msg, xonly []byte) bool {
		p, err := voibtc.NewSchnorrPublicKey(xonly)
		if err != nil {
			return false
		}
		return p.Verify(msg, sig)
	},
	// secec.PrivateKey.ECDH is ScalarMult + XBytes, after NewPrivateKey derives a public key
	// ECDH does not use; these are the same calls without that derivation.
	ecdhXOnly: func(out, xonly, seckey []byte) bool {
		s, ok := voiScalar(seckey)
		if !ok {
			return false
		}
		p, err := voibtc.NewSchnorrPublicKey(xonly)
		if err != nil {
			return false
		}
		x, err := voi.NewIdentityPoint().ScalarMult(s, p.Point()).XBytes()
		if err != nil {
			return false
		}
		copy(out, x)
		return true
	},
	tweakMul: func(out33, pub33, tweak []byte) bool {
		s, ok := voiScalar(tweak)
		if !ok {
			return false
		}
		p, err := voi.NewPointFromBytes(pub33)
		if err != nil {
			return false
		}
		copy(out33, voi.NewIdentityPoint().ScalarMult(s, p).CompressedBytes())
		return true
	},
	tweakAdd: func(out32, seckey, tweak []byte) bool {
		k, ok := voiScalar(seckey)
		if !ok {
			return false
		}
		t, reduced := voi.NewScalarFromBytes((*[voi.ScalarSize]byte)(tweak))
		if reduced != 0 {
			return false
		}
		r := voi.NewScalar().Add(k, t)
		if r.IsZero() != 0 {
			return false
		}
		copy(out32, r.Bytes())
		return true
	},
}

// ---------------------------------------------------------------- splitsecure secp256k1-go

func ssSign(sig, msg, seckey, aux []byte) bool {
	sk := ss.SecretKey(seckey)
	kp, err := ss.NewKeypair(&sk)
	if err != nil {
		return false
	}
	s, err := ssschnorr.Sign32(kp, [32]byte(msg), [32]byte(aux))
	if err != nil {
		return false
	}
	copy(sig, s[:])
	return true
}

// ssX is the ECDH hash that returns x itself: x-only ECDH through ecdh.ECDHWithHash, as
// libsecp256k1's secp256k1_ecdh is used with a custom hash function.
func ssX(x, _ [32]byte) ([]byte, error) { return x[:], nil }

var splitsecureBackend = &backend{
	name:    "splitsecure",
	binding: "go",
	module:  "github.com/splitsecure/secp256k1-go",
	pubkeyCreate: func(pub65, seckey []byte) bool {
		sk := ss.SecretKey(seckey)
		p, err := sk.PublicKey()
		if err != nil {
			return false
		}
		b := p.SerializeUncompressed()
		copy(pub65, b[:])
		return true
	},
	pubkeyCompress: func(pub33, pub65 []byte) bool {
		p, err := ss.ParsePublicKey(pub65)
		if err != nil {
			return false
		}
		b := p.SerializeCompressed()
		copy(pub33, b[:])
		return true
	},
	seckeyVerify: func(seckey []byte) bool {
		sk := ss.SecretKey(seckey)
		return sk.IsValid()
	},
	sign: ssSign,
	// Keypair is built from the secret alone.
	signKnownPub: func(sig, msg, seckey, _, _, aux []byte) bool { return ssSign(sig, msg, seckey, aux) },
	verify: func(sig, msg, xonly []byte) bool {
		p, err := ss.ParseXOnlyPublicKey([32]byte(xonly))
		if err != nil {
			return false
		}
		return ssschnorr.Verify(p, msg, [64]byte(sig))
	},
	ecdhXOnly: func(out, xonly, seckey []byte) bool {
		p, err := ss.ParseXOnlyPublicKey([32]byte(xonly))
		if err != nil {
			return false
		}
		sk := ss.SecretKey(seckey)
		x, err := ssecdh.ECDHWithHash(&sk, p.PublicKey(), ssX)
		if err != nil {
			return false
		}
		copy(out, x)
		return true
	},
	tweakMul: func(out33, pub33, tweak []byte) bool {
		p, err := ss.ParsePublicKey(pub33)
		if err != nil || p.TweakMul([32]byte(tweak)) != nil {
			return false
		}
		b := p.SerializeCompressed()
		copy(out33, b[:])
		return true
	},
	tweakAdd: func(out32, seckey, tweak []byte) bool {
		sk := ss.SecretKey(seckey)
		if sk.TweakAdd([32]byte(tweak)) != nil {
			return false
		}
		copy(out32, sk[:])
		return true
	},
}
