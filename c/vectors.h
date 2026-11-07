/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Input vectors v1 (vectors/README.md), and a small SHA-256 used only to derive and digest
 * them, so the derivation does not depend on either library under test.
 */
#ifndef BENCH_VECTORS_H
#define BENCH_VECTORS_H

#include <stddef.h>
#include <stdint.h>

#define VEC_N 4096
#define VEC_BATCH 200

/* ---- SHA-256 (FIPS 180-4) ---- */
typedef struct {
    uint32_t h[8];
    uint64_t len;
    uint8_t buf[64];
    size_t fill;
} sha256_ctx;

void sha256_init(sha256_ctx *c);
void sha256_update(sha256_ctx *c, const uint8_t *data, size_t len);
void sha256_final(sha256_ctx *c, uint8_t out[32]);
void sha256(uint8_t out[32], const uint8_t *data, size_t len);

/* ---- Inputs ---- */
typedef struct {
    uint8_t seckey[VEC_N][32];
    uint8_t tweak[VEC_N][32];
    uint8_t msg[VEC_N][32];
    uint8_t aux[VEC_N][32];
    /* derived with the reference library (libsecp256k1) */
    uint8_t pub65[VEC_N][65];
    uint8_t pub33[VEC_N][33];
    uint8_t xonly[VEC_N][32];
    uint8_t tpub33[VEC_N][33];
    uint8_t sig[VEC_N][64];
    uint8_t batch_sig[VEC_BATCH][64];
} vectors;

/* Fills seckey, tweak, msg and aux by the v1 rule. */
void vectors_derive_inputs(vectors *v);

#endif
