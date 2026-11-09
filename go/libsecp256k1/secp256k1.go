// Copyright (c) 2025 Vitor Pamplona

// Package libsecp256k1 calls bitcoin-core's libsecp256k1 (the bench's submodule) through cgo,
// one cgo call per libsecp256k1 function, as go-nostr's `libsecp256k1` build tag does. The
// calls and their composition are those of c/backend_libsecp256k1.c: one context without
// randomization, keys parsed from bytes on every call, x-only ECDH as a tweak-mul of 02 || x.
//
// The C structs live in package-level scratch, so a call allocates nothing on the Go heap
// (a local passed to C escapes); the runner is single-goroutine.
package libsecp256k1

/*
#cgo CFLAGS: -O2 -I${SRCDIR}/../../third_party/secp256k1/include
#cgo CFLAGS: -DCOMB_BLOCKS=43 -DCOMB_TEETH=6 -DECMULT_WINDOW_SIZE=15
#cgo CFLAGS: -DENABLE_MODULE_EXTRAKEYS=1 -DENABLE_MODULE_SCHNORRSIG=1
#cgo amd64 CFLAGS: -DUSE_ASM_X86_64=1
#include "secp256k1.h"
#include "secp256k1_extrakeys.h"
#include "secp256k1_schnorrsig.h"
*/
import "C"

import "unsafe"

var (
	ctx    *C.secp256k1_context
	pk     C.secp256k1_pubkey
	xpk    C.secp256k1_xonly_pubkey
	kp     C.secp256k1_keypair
	outLen C.size_t
	buf33  [33]byte
	res33  [33]byte
)

func init() {
	ctx = C.secp256k1_context_create(C.SECP256K1_CONTEXT_NONE)
}

func u(b []byte) *C.uchar { return (*C.uchar)(unsafe.Pointer(&b[0])) }

// PubkeyCreate: ec_pubkey_create + serialize (65).
func PubkeyCreate(pub65, seckey []byte) bool {
	if C.secp256k1_ec_pubkey_create(ctx, &pk, u(seckey)) != 1 {
		return false
	}
	outLen = 65
	return C.secp256k1_ec_pubkey_serialize(ctx, u(pub65), &outLen, &pk, C.SECP256K1_EC_UNCOMPRESSED) == 1
}

// PubkeyCompress: ec_pubkey_parse (65) + serialize (33).
func PubkeyCompress(pub33, pub65 []byte) bool {
	if C.secp256k1_ec_pubkey_parse(ctx, &pk, u(pub65), 65) != 1 {
		return false
	}
	outLen = 33
	return C.secp256k1_ec_pubkey_serialize(ctx, u(pub33), &outLen, &pk, C.SECP256K1_EC_COMPRESSED) == 1
}

// SeckeyVerify: ec_seckey_verify.
func SeckeyVerify(seckey []byte) bool { return C.secp256k1_ec_seckey_verify(ctx, u(seckey)) == 1 }

// Sign: keypair_create + schnorrsig_sign32.
func Sign(sig, msg, seckey, aux []byte) bool {
	if C.secp256k1_keypair_create(ctx, &kp, u(seckey)) != 1 {
		return false
	}
	return C.secp256k1_schnorrsig_sign32(ctx, u(sig), u(msg), &kp, u(aux)) == 1
}

// Verify: xonly_pubkey_parse + schnorrsig_verify.
func Verify(sig, msg, xonly []byte) bool {
	if C.secp256k1_xonly_pubkey_parse(ctx, &xpk, u(xonly)) != 1 {
		return false
	}
	return C.secp256k1_schnorrsig_verify(ctx, u(sig), u(msg), 32, &xpk) == 1
}

// EcdhXOnly: ec_pubkey_parse(02 || x) + ec_pubkey_tweak_mul + serialize (33), take x.
func EcdhXOnly(out, xonly, seckey []byte) bool {
	buf33[0] = 0x02
	copy(buf33[1:], xonly)
	if C.secp256k1_ec_pubkey_parse(ctx, &pk, u(buf33[:]), 33) != 1 {
		return false
	}
	if C.secp256k1_ec_pubkey_tweak_mul(ctx, &pk, u(seckey)) != 1 {
		return false
	}
	outLen = 33
	if C.secp256k1_ec_pubkey_serialize(ctx, u(res33[:]), &outLen, &pk, C.SECP256K1_EC_COMPRESSED) != 1 {
		return false
	}
	copy(out, res33[1:])
	return true
}

// TweakMul: ec_pubkey_parse (33) + ec_pubkey_tweak_mul + serialize (33).
func TweakMul(out33, pub33, tweak []byte) bool {
	if C.secp256k1_ec_pubkey_parse(ctx, &pk, u(pub33), 33) != 1 {
		return false
	}
	if C.secp256k1_ec_pubkey_tweak_mul(ctx, &pk, u(tweak)) != 1 {
		return false
	}
	outLen = 33
	return C.secp256k1_ec_pubkey_serialize(ctx, u(out33), &outLen, &pk, C.SECP256K1_EC_COMPRESSED) == 1
}

// TweakAdd: ec_seckey_tweak_add on a copy.
func TweakAdd(out32, seckey, tweak []byte) bool {
	copy(out32, seckey)
	return C.secp256k1_ec_seckey_tweak_add(ctx, u(out32), u(tweak)) == 1
}
