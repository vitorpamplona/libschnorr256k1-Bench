/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * The C layer: libsecp256k1 and libschnorr256k1 called directly from C, no runtime in between.
 *
 *   bench vectors                      print the v1 digests (vectors/v1.txt), from libsecp256k1
 *   bench run --vectors FILE [opts]    check every library against FILE, time every operation,
 *                                      print a schema-v1 result (results/SCHEMA.md) as JSON
 *     --library NAME     only this library (libsecp256k1 | libschnorr256k1)
 *     --device TEXT      context.device       --machine dedicated|shared   context.machine
 *     --quick            short warm-up and samples, for CI smoke runs (numbers not comparable)
 */
#define _POSIX_C_SOURCE 200809L
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "backend.h"
#include "vectors.h"

#ifndef BENCH_COMPILER_FLAGS
#define BENCH_COMPILER_FLAGS "unknown"
#endif
#ifndef BENCH_HARNESS_VERSION
#define BENCH_HARNESS_VERSION "unknown"
#endif

static const backend *const BACKENDS[] = {&backend_libsecp256k1, &backend_libschnorr256k1};
#define N_BACKENDS (sizeof BACKENDS / sizeof BACKENDS[0])

static vectors V;
static const uint8_t *batch_sigs[VEC_BATCH];
static const uint8_t *batch_msgs[VEC_BATCH];
static volatile uint64_t sink;

/* ---------------------------------------------------------------- vectors */

static void die(const char *what) {
    fprintf(stderr, "error: %s\n", what);
    exit(2);
}

/* The derived values, made with the reference library (vectors/README.md). */
static void derive(void) {
    const backend *ref = &backend_libsecp256k1;
    ref->init();
    vectors_derive_inputs(&V);
    for (int i = 0; i < VEC_N; i++) {
        if (!ref->pubkey_create(V.pub65[i], V.seckey[i])) die("reference pubkey_create");
        if (!ref->pubkey_compress(V.pub33[i], V.pub65[i])) die("reference pubkey_compress");
        memcpy(V.xonly[i], V.pub33[i] + 1, 32);
        uint8_t t65[65];
        if (!ref->pubkey_create(t65, V.tweak[i]) || !ref->pubkey_compress(V.tpub33[i], t65)) die("reference tweak pubkey");
        if (!ref->sign(V.sig[i], V.msg[i], V.seckey[i], V.aux[i])) die("reference sign");
    }
    for (int j = 0; j < VEC_BATCH; j++) {
        if (!ref->sign(V.batch_sig[j], V.msg[j], V.seckey[0], V.aux[j])) die("reference batch sign");
        batch_sigs[j] = V.batch_sig[j];
        batch_msgs[j] = V.msg[j];
    }
}

typedef struct {
    const char *check;
    uint8_t digest[32];
    int ok; /* every call succeeded */
} digest_result;

enum { D_PUB_CREATE, D_PUB_COMPRESS, D_SIGN, D_ECDH, D_TWEAK_MUL, D_TWEAK_ADD, D_VERIFY, D_VERIFY_TAMPERED, N_DIGESTS };
static const char *const DIGEST_NAMES[N_DIGESTS] = {
    "pubkey.create", "pubkey.compress", "sign", "ecdh.xOnly", "tweak.mul", "tweak.add", "verify", "verify.tampered",
};

static void digests(const backend *b, digest_result out[N_DIGESTS]) {
    sha256_ctx c[N_DIGESTS];
    for (int d = 0; d < N_DIGESTS; d++) {
        sha256_init(&c[d]);
        out[d].check = DIGEST_NAMES[d];
        out[d].ok = 1;
    }
    uint8_t buf[65];
    for (int i = 0; i < VEC_N; i++) {
        out[D_PUB_CREATE].ok &= b->pubkey_create(buf, V.seckey[i]);
        sha256_update(&c[D_PUB_CREATE], buf, 65);
        out[D_PUB_COMPRESS].ok &= b->pubkey_compress(buf, V.pub65[i]);
        sha256_update(&c[D_PUB_COMPRESS], buf, 33);
        out[D_SIGN].ok &= b->sign(buf, V.msg[i], V.seckey[i], V.aux[i]);
        sha256_update(&c[D_SIGN], buf, 64);
        out[D_ECDH].ok &= b->ecdh_xonly(buf, V.xonly[(i + 1) % VEC_N], V.seckey[i]);
        sha256_update(&c[D_ECDH], buf, 32);
        out[D_TWEAK_MUL].ok &= b->tweak_mul(buf, V.tpub33[i], V.seckey[i]);
        sha256_update(&c[D_TWEAK_MUL], buf, 33);
        out[D_TWEAK_ADD].ok &= b->tweak_add(buf, V.seckey[i], V.tweak[i]);
        sha256_update(&c[D_TWEAK_ADD], buf, 32);
        uint8_t ok = (uint8_t)(b->verify(V.sig[i], V.msg[i], V.xonly[i]) ? 1 : 0);
        sha256_update(&c[D_VERIFY], &ok, 1);
        uint8_t bad[64];
        memcpy(bad, V.sig[i], 64);
        bad[63]++;
        ok = (uint8_t)(b->verify(bad, V.msg[i], V.xonly[i]) ? 1 : 0);
        sha256_update(&c[D_VERIFY_TAMPERED], &ok, 1);
    }
    for (int d = 0; d < N_DIGESTS; d++) sha256_final(&c[d], out[d].digest);
}

static void hex(char *out, const uint8_t *in, size_t n) {
    for (size_t i = 0; i < n; i++) sprintf(out + 2 * i, "%02x", in[i]);
}

/* ---------------------------------------------------------------- operations */

typedef enum {
    OP_VERIFY_SAME, OP_VERIFY_MANY, OP_VERIFY_FAST_SAME, OP_VERIFY_BATCH, OP_SIGN, OP_SIGN_KNOWN_PUB,
    OP_PUB_CREATE, OP_PUB_COMPRESS, OP_SECKEY_VERIFY, OP_ECDH, OP_TWEAK_MUL, OP_TWEAK_ADD, N_OPS
} op_id;

static const char *const OP_NAMES[N_OPS] = {
    "verify.sameKey", "verify.manyKeys", "verifyFast.sameKey", "verifyBatch200.perSig", "sign", "sign.knownPub",
    "pubkey.create", "pubkey.compress", "seckey.verify", "ecdh.xOnly", "tweak.mul", "tweak.add",
};

static unsigned next_index;

/* Runs [n] calls of [op]. Returns 0 if the library lacks it. */
static int run(const backend *b, op_id op, long n) {
    uint8_t out[65];
    uint64_t acc = 0;
    unsigned k = next_index;
    switch (op) {
    case OP_VERIFY_SAME:
        for (long r = 0; r < n; r++) acc += (uint64_t)b->verify(V.sig[0], V.msg[0], V.xonly[0]);
        break;
    case OP_VERIFY_MANY:
        for (long r = 0; r < n; r++, k++) { unsigned i = k % VEC_N; acc += (uint64_t)b->verify(V.sig[i], V.msg[i], V.xonly[i]); }
        break;
    case OP_VERIFY_FAST_SAME: {
        int (*f)(const uint8_t *, const uint8_t *, const uint8_t *) = b->verify_fast ? b->verify_fast : b->verify;
        for (long r = 0; r < n; r++) acc += (uint64_t)f(V.sig[0], V.msg[0], V.xonly[0]);
        break;
    }
    case OP_VERIFY_BATCH:
        if (!b->verify_batch) return 0;
        for (long r = 0; r < n; r++) acc += (uint64_t)b->verify_batch(V.xonly[0], batch_sigs, batch_msgs, VEC_BATCH);
        break;
    case OP_SIGN:
        for (long r = 0; r < n; r++, k++) { unsigned i = k % VEC_N; b->sign(out, V.msg[i], V.seckey[i], V.aux[i]); acc += out[7]; }
        break;
    case OP_SIGN_KNOWN_PUB:
        for (long r = 0; r < n; r++, k++) {
            unsigned i = k % VEC_N;
            b->sign_known_pub(out, V.msg[i], V.seckey[i], V.pub33[i], V.xonly[i], V.aux[i]);
            acc += out[7];
        }
        break;
    case OP_PUB_CREATE:
        for (long r = 0; r < n; r++, k++) { b->pubkey_create(out, V.seckey[k % VEC_N]); acc += out[7]; }
        break;
    case OP_PUB_COMPRESS:
        for (long r = 0; r < n; r++, k++) { b->pubkey_compress(out, V.pub65[k % VEC_N]); acc += out[7]; }
        break;
    case OP_SECKEY_VERIFY:
        for (long r = 0; r < n; r++, k++) acc += (uint64_t)b->seckey_verify(V.seckey[k % VEC_N]);
        break;
    case OP_ECDH:
        for (long r = 0; r < n; r++, k++) {
            unsigned i = k % VEC_N;
            b->ecdh_xonly(out, V.xonly[(i + 1) % VEC_N], V.seckey[i]);
            acc += out[7];
        }
        break;
    case OP_TWEAK_MUL:
        for (long r = 0; r < n; r++, k++) { unsigned i = k % VEC_N; b->tweak_mul(out, V.tpub33[i], V.seckey[i]); acc += out[7]; }
        break;
    case OP_TWEAK_ADD:
        for (long r = 0; r < n; r++, k++) { unsigned i = k % VEC_N; b->tweak_add(out, V.seckey[i], V.tweak[i]); acc += out[7]; }
        break;
    default:
        return 0;
    }
    next_index = k;
    sink += acc;
    return 1;
}

static double now_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double)ts.tv_sec * 1e9 + (double)ts.tv_nsec;
}

static int cmp_double(const void *a, const void *b) {
    double x = *(const double *)a, y = *(const double *)b;
    return (x > y) - (x < y);
}

/* Nearest-rank percentile of sorted v. */
static double pct(const double *v, int n, double p) {
    int r = (int)(p / 100.0 * n + 0.999999);
    if (r < 1) r = 1;
    if (r > n) r = n;
    return v[r - 1];
}

typedef struct {
    int timed;
    double median, p5, p95;
    int samples;
} timing;

static timing time_op(const backend *b, op_id op, int quick) {
    timing t = {0};
    double warm_ns = quick ? 0.2e9 : 1e9, sample_ns = quick ? 20e6 : 100e6;
    int n_samples = quick ? 5 : 20;
    double per_call = op == OP_VERIFY_BATCH ? VEC_BATCH : 1;
    if (!run(b, op, 1)) return t;
    double start = now_ns();
    while (now_ns() - start < warm_ns) run(b, op, 16);
    long n = 1;
    for (;;) { /* calibrate: n calls take at least one sample's time */
        double s = now_ns();
        run(b, op, n);
        if (now_ns() - s >= sample_ns) break;
        n *= 2;
    }
    double v[64];
    for (int s = 0; s < n_samples; s++) {
        double a = now_ns();
        run(b, op, n);
        v[s] = (now_ns() - a) / ((double)n * per_call);
    }
    qsort(v, (size_t)n_samples, sizeof v[0], cmp_double);
    t.timed = 1;
    t.samples = n_samples;
    t.median = pct(v, n_samples, 50);
    t.p5 = pct(v, n_samples, 5);
    t.p95 = pct(v, n_samples, 95);
    return t;
}

/* ---------------------------------------------------------------- known issues */

/* Adds d (±1) to the s half of a signature, as KnownIssuesTest does. */
static void add_small_to_s(uint8_t sig[64], int d) {
    int carry = d;
    for (int i = 63; carry != 0 && i >= 32; i--) {
        int x = sig[i] + carry;
        sig[i] = (uint8_t)x;
        carry = x > 255 ? 1 : x < 0 ? -1 : 0;
    }
}

typedef struct {
    int ki1, ki2, ki3; /* observed */
    int ki1_tested;
} issues;

/* Probes run after timing, on keys derived under their own labels, so their cache side effects
 * (KI-3) cannot reach the timed rows. */
static issues probe_issues(const backend *b, const int known_pub_wrong) {
    issues r = {0};
    r.ki2 = known_pub_wrong;
    if (b->verify_batch) {
        r.ki1_tested = 1;
        uint8_t s1[64], s2[64];
        memcpy(s1, V.batch_sig[0], 64);
        memcpy(s2, V.batch_sig[1], 64);
        add_small_to_s(s1, 1);
        add_small_to_s(s2, -1);
        const uint8_t *sigs[2] = {s1, s2}, *msgs[2] = {V.msg[0], V.msg[1]};
        int each_invalid = !backend_libsecp256k1.verify(s1, V.msg[0], V.xonly[0]) && !backend_libsecp256k1.verify(s2, V.msg[1], V.xonly[0]);
        r.ki1 = each_invalid && b->verify_batch(V.xonly[0], sigs, msgs, 2);
    }
    /* KI-3: on odd-y keys of a separate set, tweak-mul the 65-byte (odd-y) point, then verify a
     * valid signature for the x-only form. */
    int rejected = 0;
    for (int i = 0, found = 0; found < 16 && i < 4096; i++) {
        uint8_t sk[32], pub65[65], pub33[33], msg[32], sig[64], out[33];
        char label[64];
        int n = snprintf(label, sizeof label, "schnorr256k1-bench/v1/probe/%d", i);
        sha256(sk, (const uint8_t *)label, (size_t)n);
        if (!backend_libsecp256k1.pubkey_create(pub65, sk) || !backend_libsecp256k1.pubkey_compress(pub33, pub65)) continue;
        if (pub33[0] != 0x03) continue;
        found++;
        sha256(msg, sk, 32);
        backend_libsecp256k1.sign(sig, msg, sk, msg);
        b->tweak_mul(out, pub33, V.tweak[0]);
        if (!b->verify(sig, msg, pub33 + 1)) rejected++;
    }
    r.ki3 = rejected > 0;
    return r;
}

/* ---------------------------------------------------------------- main */

static char *read_file(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) return NULL;
    fseek(f, 0, SEEK_END);
    long len = ftell(f);
    fseek(f, 0, SEEK_SET);
    char *s = malloc((size_t)len + 1);
    if (fread(s, 1, (size_t)len, f) != (size_t)len) { fclose(f); free(s); return NULL; }
    s[len] = 0;
    fclose(f);
    return s;
}

static const char *expected_digest(const char *file, const char *check) {
    static char found[65];
    size_t cl = strlen(check);
    for (const char *line = file; line && *line; line = strchr(line, '\n') ? strchr(line, '\n') + 1 : NULL) {
        if (strncmp(line, check, cl) == 0 && line[cl] == ' ') {
            memcpy(found, line + cl + 1, 64);
            found[64] = 0;
            return found;
        }
    }
    return NULL;
}

static void cpu_model(char *out, size_t cap) {
    snprintf(out, cap, "unknown");
    FILE *f = fopen("/proc/cpuinfo", "r");
    if (!f) return;
    char line[512];
    while (fgets(line, sizeof line, f)) {
        const char *keys[] = {"model name", "Hardware"};
        for (int k = 0; k < 2; k++) {
            if (strncmp(line, keys[k], strlen(keys[k])) == 0) {
                char *c = strchr(line, ':');
                if (c) {
                    c++;
                    while (*c == ' ') c++;
                    c[strcspn(c, "\n")] = 0;
                    snprintf(out, cap, "%s", c);
                    fclose(f);
                    return;
                }
            }
        }
    }
    fclose(f);
}

static void json_str(FILE *o, const char *s) {
    fputc('"', o);
    for (; *s; s++) {
        if (*s == '"' || *s == '\\') fputc('\\', o);
        fputc(*s, o);
    }
    fputc('"', o);
}

int main(int argc, char **argv) {
    if (argc < 2) die("usage: bench vectors | bench run --vectors FILE [--library NAME] [--device TEXT] [--machine dedicated|shared] [--quick]");
    derive();

    if (strcmp(argv[1], "vectors") == 0) {
        digest_result d[N_DIGESTS];
        digests(&backend_libsecp256k1, d);
        for (int i = 0; i < N_DIGESTS; i++) {
            char h[65];
            if (!d[i].ok && i != D_VERIFY_TAMPERED && i != D_VERIFY) die("reference call failed");
            hex(h, d[i].digest, 32);
            printf("%s %s\n", d[i].check, h);
        }
        return 0;
    }
    if (strcmp(argv[1], "run") != 0) die("unknown command");

    const char *vec_path = NULL, *only = NULL, *device = "unknown", *machine = "unknown";
    int quick = 0;
    for (int i = 2; i < argc; i++) {
        if (!strcmp(argv[i], "--vectors") && i + 1 < argc) vec_path = argv[++i];
        else if (!strcmp(argv[i], "--library") && i + 1 < argc) only = argv[++i];
        else if (!strcmp(argv[i], "--device") && i + 1 < argc) device = argv[++i];
        else if (!strcmp(argv[i], "--machine") && i + 1 < argc) machine = argv[++i];
        else if (!strcmp(argv[i], "--quick")) quick = 1;
        else die("unknown option");
    }
    if (!vec_path) die("--vectors FILE is required");
    char *vec = read_file(vec_path);
    if (!vec) die("cannot read the vectors file");
    char vec_sha[65];
    {
        uint8_t h[32];
        sha256(h, (const uint8_t *)vec, strlen(vec));
        hex(vec_sha, h, 32);
    }

    char cpu[256], date[32];
    cpu_model(cpu, sizeof cpu);
    time_t tt = time(NULL);
    strftime(date, sizeof date, "%Y-%m-%d", gmtime(&tt));

    FILE *o = stdout;
    fprintf(o, "{\n  \"schema\": 1,\n  \"vectors\": \"v1\",\n  \"vectorsSha256\": \"%s\",\n  \"layer\": \"c\",\n", vec_sha);
    fprintf(o, "  \"quick\": %s,\n  \"context\": {\n    \"date\": \"%s\",\n    \"device\": ", quick ? "true" : "false", date);
    json_str(o, device);
    fprintf(o, ",\n    \"cpu\": ");
    json_str(o, cpu);
    fprintf(o, ",\n    \"os\": ");
#if defined(__ANDROID__)
    json_str(o, "Android");
#elif defined(__APPLE__)
    json_str(o, "macOS");
#elif defined(__linux__)
    json_str(o, "Linux");
#else
    json_str(o, "unknown");
#endif
    fprintf(o, ",\n    \"runtime\": ");
    json_str(o, "C, " __VERSION__ ", " BENCH_COMPILER_FLAGS);
    fprintf(o, ",\n    \"machine\": ");
    json_str(o, machine);
    fprintf(o, ",\n    \"harness\": \"%s\",\n    \"libraries\": {", BENCH_HARNESS_VERSION);
    for (size_t bi = 0; bi < N_BACKENDS; bi++) fprintf(o, "%s\"%s\": \"%s\"", bi ? ", " : " ", BACKENDS[bi]->name, BACKENDS[bi]->version);
    fprintf(o, " }\n  },\n  \"rows\": [");

    int first_row = 1;
    char issues_json[512] = "";
    for (size_t bi = 0; bi < N_BACKENDS; bi++) {
        const backend *b = BACKENDS[bi];
        if (only && strcmp(only, b->name) != 0) continue;
        b->init();

        /* correctness, before any timing */
        digest_result d[N_DIGESTS];
        digests(b, d);
        int good[N_DIGESTS];
        for (int i = 0; i < N_DIGESTS; i++) {
            char h[65];
            hex(h, d[i].digest, 32);
            const char *want = expected_digest(vec, d[i].check);
            if (!want) die("vectors file lacks a check");
            good[i] = strcmp(h, want) == 0;
            if (!good[i]) fprintf(stderr, "%s: %s digest differs from vectors\n", b->name, d[i].check);
        }
        int fast_ok = 1, batch_ok = 1, known_pub_ok = 1, known_pub_odd_only = 1, seckey_ok = 1;
        for (int i = 0; i < VEC_N; i++) {
            if (b->verify_fast && !b->verify_fast(V.sig[i], V.msg[i], V.xonly[i])) fast_ok = 0;
            uint8_t s[64];
            if (!b->sign_known_pub(s, V.msg[i], V.seckey[i], V.pub33[i], V.xonly[i], V.aux[i]) || memcmp(s, V.sig[i], 64)) {
                known_pub_ok = 0;
                if (V.pub33[i][0] != 0x03) known_pub_odd_only = 0;
            }
            if (!b->seckey_verify(V.seckey[i])) seckey_ok = 0;
        }
        {
            uint8_t zero[32] = {0}, n[32] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFE,
                                             0xBA, 0xAE, 0xDC, 0xE6, 0xAF, 0x48, 0xA0, 0x3B, 0xBF, 0xD2, 0x5E, 0x8C, 0xD0, 0x36, 0x41, 0x41};
            if (b->seckey_verify(zero) || b->seckey_verify(n)) seckey_ok = 0;
        }
        if (b->verify_batch && !b->verify_batch(V.xonly[0], batch_sigs, batch_msgs, VEC_BATCH)) batch_ok = 0;
        int verify_ok = good[D_VERIFY] && good[D_VERIFY_TAMPERED];
        int ok[N_OPS] = {
            [OP_VERIFY_SAME] = verify_ok, [OP_VERIFY_MANY] = verify_ok, [OP_VERIFY_FAST_SAME] = verify_ok && fast_ok,
            [OP_VERIFY_BATCH] = batch_ok, [OP_SIGN] = good[D_SIGN], [OP_SIGN_KNOWN_PUB] = known_pub_ok,
            [OP_PUB_CREATE] = good[D_PUB_CREATE], [OP_PUB_COMPRESS] = good[D_PUB_COMPRESS], [OP_SECKEY_VERIFY] = seckey_ok,
            [OP_ECDH] = good[D_ECDH], [OP_TWEAK_MUL] = good[D_TWEAK_MUL], [OP_TWEAK_ADD] = good[D_TWEAK_ADD],
        };

        timing t[N_OPS];
        for (int op = 0; op < N_OPS; op++) {
            fprintf(stderr, "%s %s...\n", b->name, OP_NAMES[op]);
            t[op] = time_op(b, (op_id)op, quick);
        }
        issues is = probe_issues(b, !known_pub_ok && known_pub_odd_only);

        for (int op = 0; op < N_OPS; op++) {
            if (!t[op].timed) continue;
            const char *ki = "";
            if (op == OP_VERIFY_BATCH && is.ki1) ki = "\"KI-1\"";
            if (op == OP_SIGN_KNOWN_PUB && is.ki2) ki = "\"KI-2\"";
            fprintf(o, "%s\n    { \"op\": \"%s\", \"library\": \"%s\", \"binding\": \"c\", \"median_ns\": %.1f, \"p5_ns\": %.1f, \"p95_ns\": %.1f, \"samples\": %d, \"correct\": %s, \"known_issues\": [%s] }",
                    first_row ? "" : ",", OP_NAMES[op], b->name, t[op].median, t[op].p5, t[op].p95, t[op].samples,
                    ok[op] ? "true" : "false", ki);
            first_row = 0;
        }
        size_t used = strlen(issues_json);
        snprintf(issues_json + used, sizeof issues_json - used, "%s\n    \"%s\": { \"KI-1\": %s, \"KI-2\": %s, \"KI-3\": %s }",
                 used ? "," : "", b->name, is.ki1_tested ? (is.ki1 ? "true" : "false") : "null",
                 is.ki2 ? "true" : "false", is.ki3 ? "true" : "false");
    }
    fprintf(o, "\n  ],\n  \"known_issues_observed\": {%s\n  }\n}\n", issues_json);
    free(vec);
    return (int)(sink & 0); /* keep the sink alive */
}
