/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * libschnorr256k1, through its public header (include/schnorr256k1.h).
 */
#include "backend.h"
#include "schnorr256k1.h"

#ifndef LIBSCHNORR256K1_VERSION
#define LIBSCHNORR256K1_VERSION "unknown"
#endif

static void init(void) { secp256k1c_init(); }

static int pubkey_create(uint8_t pub65[65], const uint8_t seckey[32]) { return secp256k1c_pubkey_create(pub65, seckey); }

static int pubkey_compress(uint8_t pub33[33], const uint8_t pub65[65]) { return secp256k1c_pubkey_compress(pub33, pub65); }

static int seckey_verify(const uint8_t seckey[32]) { return secp256k1c_seckey_verify(seckey); }

static int sign(uint8_t sig[64], const uint8_t msg[32], const uint8_t seckey[32], const uint8_t aux[32]) {
    return secp256k1c_schnorr_sign(sig, msg, 32, seckey, aux);
}

/* KI-2: invalid for keys whose public key has odd y. */
static int sign_known_pub(uint8_t sig[64], const uint8_t msg[32], const uint8_t seckey[32],
                          const uint8_t pub33[33], const uint8_t xonly[32], const uint8_t aux[32]) {
    (void)pub33;
    return secp256k1c_schnorr_sign_xonly(sig, msg, 32, seckey, xonly, aux);
}

static int verify(const uint8_t sig[64], const uint8_t msg[32], const uint8_t xonly[32]) {
    return secp256k1c_schnorr_verify(sig, msg, 32, xonly);
}

static int verify_fast(const uint8_t sig[64], const uint8_t msg[32], const uint8_t xonly[32]) {
    return secp256k1c_schnorr_verify_fast(sig, msg, 32, xonly);
}

static int verify_batch(const uint8_t xonly[32], const uint8_t *const *sigs, const uint8_t *const *msgs, size_t count) {
    size_t lens[256];
    if (count > 256) return 0;
    for (size_t i = 0; i < count; i++) lens[i] = 32;
    return secp256k1c_schnorr_verify_batch(xonly, sigs, msgs, lens, count);
}

static int ecdh_xonly(uint8_t out[32], const uint8_t xonly[32], const uint8_t seckey[32]) {
    return secp256k1c_ecdh_xonly(out, xonly, seckey);
}

static int tweak_mul(uint8_t out33[33], const uint8_t pub33[33], const uint8_t tweak[32]) {
    return secp256k1c_pubkey_tweak_mul(out33, 33, pub33, 33, tweak);
}

static int tweak_add(uint8_t out32[32], const uint8_t seckey[32], const uint8_t tweak[32]) {
    return secp256k1c_privkey_tweak_add(out32, seckey, tweak);
}

const backend backend_libschnorr256k1 = {
    .name = "libschnorr256k1",
    .version = LIBSCHNORR256K1_VERSION,
    .init = init,
    .pubkey_create = pubkey_create,
    .pubkey_compress = pubkey_compress,
    .seckey_verify = seckey_verify,
    .sign = sign,
    .sign_known_pub = sign_known_pub,
    .verify = verify,
    .verify_fast = verify_fast,
    .verify_batch = verify_batch,
    .ecdh_xonly = ecdh_xonly,
    .tweak_mul = tweak_mul,
    .tweak_add = tweak_add,
};
