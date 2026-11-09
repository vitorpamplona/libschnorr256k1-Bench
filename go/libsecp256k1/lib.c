/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * libsecp256k1 from the bench's submodule (third_party/secp256k1), compiled into the Go binary
 * by cgo as one translation unit, the way go-nostr's and fiatjaf.com/nostr's `libsecp256k1`
 * build tag compiles it. The defines in secp256k1.go are the ones its CMake build uses on the c
 * layer (c/CMakeLists.txt), so the two layers run the same C code.
 */
#include "../../third_party/secp256k1/src/secp256k1.c"
#include "../../third_party/secp256k1/src/precomputed_ecmult.c"
#include "../../third_party/secp256k1/src/precomputed_ecmult_gen.c"
