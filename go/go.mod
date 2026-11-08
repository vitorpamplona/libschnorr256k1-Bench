module github.com/vitorpamplona/libschnorr256k1-bench/go

go 1.26.10

replace p256k1.mleku.dev => git.mleku.dev/mleku/p256k1 v1.1.7

require (
	github.com/btcsuite/btcd/btcec/v2 v2.5.1
	github.com/splitsecure/secp256k1-go v0.1.0
	gitlab.com/yawning/secp256k1-voi v0.0.0-20230925100816-f2616030848b
	p256k1.mleku.dev v1.1.7
)

require (
	github.com/btcsuite/btcd/chainhash/v2 v2.0.0 // indirect
	github.com/decred/dcrd/crypto/blake256 v1.1.0 // indirect
	github.com/decred/dcrd/dcrec/secp256k1/v4 v4.4.1 // indirect
	github.com/ebitengine/purego v0.9.1 // indirect
	github.com/klauspost/cpuid/v2 v2.3.0 // indirect
	github.com/minio/sha256-simd v1.0.1 // indirect
	gitlab.com/yawning/tuplehash v0.0.0-20230713102510-df83abbf9a02 // indirect
	golang.org/x/crypto v0.11.0 // indirect
	golang.org/x/sys v0.37.0 // indirect
)
