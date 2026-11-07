/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * libsecp256k1, called the way ACINQ's secp256k1-kmp (JNI and cinterop) calls it, so this
 * layer and the JNI/cinterop layers differ only by the binding: one context without
 * randomization, keys parsed from bytes on every call, x-only ECDH as a tweak-mul of 02 || x.
 */
#include <string.h>

#include "backend.h"
#include "secp256k1.h"
#include "secp256k1_extrakeys.h"
#include "secp256k1_schnorrsig.h"

#ifndef LIBSECP256K1_VERSION
#define LIBSECP256K1_VERSION "unknown"
#endif

static secp256k1_context *ctx;

static void init(void) {
    if (!ctx) ctx = secp256k1_context_create(SECP256K1_CONTEXT_NONE);
}

static int pubkey_create(uint8_t pub65[65], const uint8_t seckey[32]) {
    secp256k1_pubkey pk;
    size_t len = 65;
    if (!secp256k1_ec_pubkey_create(ctx, &pk, seckey)) return 0;
    return secp256k1_ec_pubkey_serialize(ctx, pub65, &len, &pk, SECP256K1_EC_UNCOMPRESSED);
}

static int pubkey_compress(uint8_t pub33[33], const uint8_t pub65[65]) {
    secp256k1_pubkey pk;
    size_t len = 33;
    if (!secp256k1_ec_pubkey_parse(ctx, &pk, pub65, 65)) return 0;
    return secp256k1_ec_pubkey_serialize(ctx, pub33, &len, &pk, SECP256K1_EC_COMPRESSED);
}

static int seckey_verify(const uint8_t seckey[32]) { return secp256k1_ec_seckey_verify(ctx, seckey); }

static int sign(uint8_t sig[64], const uint8_t msg[32], const uint8_t seckey[32], const uint8_t aux[32]) {
    secp256k1_keypair kp;
    if (!secp256k1_keypair_create(ctx, &kp, seckey)) return 0;
    return secp256k1_schnorrsig_sign32(ctx, sig, msg, &kp, aux);
}

/* libsecp256k1's API always derives the key pair from the secret. */
static int sign_known_pub(uint8_t sig[64], const uint8_t msg[32], const uint8_t seckey[32],
                          const uint8_t pub33[33], const uint8_t xonly[32], const uint8_t aux[32]) {
    (void)pub33;
    (void)xonly;
    return sign(sig, msg, seckey, aux);
}

static int verify(const uint8_t sig[64], const uint8_t msg[32], const uint8_t xonly[32]) {
    secp256k1_xonly_pubkey pk;
    if (!secp256k1_xonly_pubkey_parse(ctx, &pk, xonly)) return 0;
    return secp256k1_schnorrsig_verify(ctx, sig, msg, 32, &pk);
}

static int ecdh_xonly(uint8_t out[32], const uint8_t xonly[32], const uint8_t seckey[32]) {
    uint8_t in[33], res[33];
    secp256k1_pubkey pk;
    size_t len = 33;
    in[0] = 0x02;
    memcpy(in + 1, xonly, 32);
    if (!secp256k1_ec_pubkey_parse(ctx, &pk, in, 33)) return 0;
    if (!secp256k1_ec_pubkey_tweak_mul(ctx, &pk, seckey)) return 0;
    if (!secp256k1_ec_pubkey_serialize(ctx, res, &len, &pk, SECP256K1_EC_COMPRESSED)) return 0;
    memcpy(out, res + 1, 32);
    return 1;
}

static int tweak_mul(uint8_t out33[33], const uint8_t pub33[33], const uint8_t tweak[32]) {
    secp256k1_pubkey pk;
    size_t len = 33;
    if (!secp256k1_ec_pubkey_parse(ctx, &pk, pub33, 33)) return 0;
    if (!secp256k1_ec_pubkey_tweak_mul(ctx, &pk, tweak)) return 0;
    return secp256k1_ec_pubkey_serialize(ctx, out33, &len, &pk, SECP256K1_EC_COMPRESSED);
}

static int tweak_add(uint8_t out32[32], const uint8_t seckey[32], const uint8_t tweak[32]) {
    memcpy(out32, seckey, 32);
    return secp256k1_ec_seckey_tweak_add(ctx, out32, tweak);
}

const backend backend_libsecp256k1 = {
    .name = "libsecp256k1",
    .version = LIBSECP256K1_VERSION,
    .init = init,
    .pubkey_create = pubkey_create,
    .pubkey_compress = pubkey_compress,
    .seckey_verify = seckey_verify,
    .sign = sign,
    .sign_known_pub = sign_known_pub,
    .verify = verify,
    .verify_fast = NULL,
    .verify_batch = NULL,
    .ecdh_xonly = ecdh_xonly,
    .tweak_mul = tweak_mul,
    .tweak_add = tweak_add,
};
