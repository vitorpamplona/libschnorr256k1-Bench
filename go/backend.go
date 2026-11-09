// Copyright (c) 2025 Vitor Pamplona

package main

// backend is one library under test, the twin of c/backend.h. Every function returns true on
// success, false on failure (invalid input or a failed verification). Byte formats as in
// results/SCHEMA.md; messages are 32 bytes. Outputs are written into the caller's slices.
type backend struct {
	name    string // results/SCHEMA.md "library"
	binding string // results/SCHEMA.md "binding"
	module  string // the Go module whose resolved version context.libraries reports ("" = set by version)
	version string // set at start: "<package> <exact version>"

	pubkeyCreate   func(pub65, seckey []byte) bool
	pubkeyCompress func(pub33, pub65 []byte) bool
	seckeyVerify   func(seckey []byte) bool
	sign           func(sig, msg, seckey, aux []byte) bool
	// The cheapest signing call when the caller already holds its public key.
	signKnownPub func(sig, msg, seckey, pub33, xonly, aux []byte) bool
	verify       func(sig, msg, xonly []byte) bool
	// nil when the library has no fast verify: the full verify is used and timed instead.
	verifyFast func(sig, msg, xonly []byte) bool
	// nil when the library has no batch verify.
	verifyBatch func(xonly []byte, sigs, msgs [][]byte) bool
	ecdhXOnly   func(out, xonly, seckey []byte) bool
	tweakMul    func(out33, pub33, tweak []byte) bool
	tweakAdd    func(out32, seckey, tweak []byte) bool
}

// backends, the reference (libsecp256k1) first: it derives the inputs.
var backends = []*backend{libsecp256k1Backend, btcecBackend, p256k1Backend, voiBackend, splitsecureBackend}
