/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * One library under test. Every function returns 1 on success, 0 on failure (invalid input or a
 * failed verification). Byte formats as in results/SCHEMA.md; messages are 32 bytes.
 */
#ifndef BENCH_BACKEND_H
#define BENCH_BACKEND_H

#include <stddef.h>
#include <stdint.h>

typedef struct {
    const char *name;    /* results/SCHEMA.md "library" */
    const char *version; /* commit or release, for the result's context */

    void (*init)(void);
    int (*pubkey_create)(uint8_t pub65[65], const uint8_t seckey[32]);
    int (*pubkey_compress)(uint8_t pub33[33], const uint8_t pub65[65]);
    int (*seckey_verify)(const uint8_t seckey[32]);
    int (*sign)(uint8_t sig[64], const uint8_t msg[32], const uint8_t seckey[32], const uint8_t aux[32]);
    /* The cheapest signing call when the caller already holds its public key. */
    int (*sign_known_pub)(uint8_t sig[64], const uint8_t msg[32], const uint8_t seckey[32],
                          const uint8_t pub33[33], const uint8_t xonly[32], const uint8_t aux[32]);
    int (*verify)(const uint8_t sig[64], const uint8_t msg[32], const uint8_t xonly[32]);
    /* NULL when the library has no fast verify: the full verify is used and timed instead. */
    int (*verify_fast)(const uint8_t sig[64], const uint8_t msg[32], const uint8_t xonly[32]);
    /* NULL when the library has no batch verify. */
    int (*verify_batch)(const uint8_t xonly[32], const uint8_t *const *sigs, const uint8_t *const *msgs, size_t count);
    int (*ecdh_xonly)(uint8_t out[32], const uint8_t xonly[32], const uint8_t seckey[32]);
    int (*tweak_mul)(uint8_t out33[33], const uint8_t pub33[33], const uint8_t tweak[32]);
    int (*tweak_add)(uint8_t out32[32], const uint8_t seckey[32], const uint8_t tweak[32]);
} backend;

extern const backend backend_libsecp256k1;
extern const backend backend_libschnorr256k1;

#endif
