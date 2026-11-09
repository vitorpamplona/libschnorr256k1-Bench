/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * The C layer: libsecp256k1 and libschnorr256k1 called directly from C, no runtime in between.
 *
 *   bench vectors                      print the v1 digests (vectors/v1.txt), from libsecp256k1
 *   bench dataset FILE                 print the digests of a dataset (datasets/nostr-v1.txt)
 *   bench run --vectors FILE --dataset DATA --dataset-digests FILE [opts]
 *                                      check every library against both, time every operation,
 *                                      print a schema-v3 result (results/SCHEMA.md) as JSON
 *     --library NAME     only this library (libsecp256k1 | libschnorr256k1)
 *     --device TEXT      context.device       --machine dedicated|shared   context.machine
 *     --harness COMMIT   context.harness (default: this checkout's commit at configure time)
 *     --quick            short warm-up and samples, for CI smoke runs (numbers not comparable)
 */
#define _POSIX_C_SOURCE 200809L
#define _DARWIN_C_SOURCE /* sysctlbyname, which _POSIX_C_SOURCE hides on macOS */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#if defined(__APPLE__)
#include <sys/sysctl.h>
#include <sys/types.h>
#elif defined(__ANDROID__)
#include <sys/system_properties.h>
#endif

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

/* ---------------------------------------------------------------- dataset (datasets/README.md) */

#define DS_RECORD 128 /* id (32, the signed message) || pubkey (32) || sig (64) */
#define DS_PAGE 200   /* events per relay page, for nostr.verifyBatchByAuthor */

static uint8_t *ds;           /* the records */
static unsigned ds_n;         /* events */
static unsigned ds_pages;     /* whole pages; the remainder is left out of the batch op */
static const uint8_t **ds_keys; /* distinct pubkeys, in order of first appearance */
static unsigned ds_n_keys;
/* Per page, its authors' groups: group g covers ds_order[g_start[g] .. g_start[g+1]). */
static unsigned *ds_order, *g_start, *page_first_group;
static unsigned ds_n_groups;
static const uint8_t **g_sigs, **g_msgs; /* per event in ds_order */

#define DS_ID(i) (ds + (size_t)(i) * DS_RECORD)
#define DS_PUB(i) (ds + (size_t)(i) * DS_RECORD + 32)
#define DS_SIG(i) (ds + (size_t)(i) * DS_RECORD + 64)

static void die(const char *what);

static uint8_t *read_bin(const char *path, size_t *len) {
    FILE *f = fopen(path, "rb");
    if (!f) return NULL;
    fseek(f, 0, SEEK_END);
    long l = ftell(f);
    fseek(f, 0, SEEK_SET);
    uint8_t *b = malloc((size_t)l + 1);
    if (fread(b, 1, (size_t)l, f) != (size_t)l) { fclose(f); free(b); return NULL; }
    fclose(f);
    *len = (size_t)l;
    return b;
}

static void load_dataset(const char *path, char sha_hex[65]) {
    size_t len;
    ds = read_bin(path, &len);
    if (!ds) die("cannot read the dataset");
    if (len == 0 || len % DS_RECORD) die("the dataset is not whole 128-byte records");
    ds_n = (unsigned)(len / DS_RECORD);
    uint8_t h[32];
    sha256(h, ds, len);
    for (int i = 0; i < 32; i++) sprintf(sha_hex + 2 * i, "%02x", h[i]);

    /* distinct pubkeys, first appearance first (open addressing on the key bytes) */
    unsigned cap = 1;
    while (cap < 2 * ds_n) cap <<= 1;
    const uint8_t **table = calloc(cap, sizeof *table);
    ds_keys = malloc(ds_n * sizeof *ds_keys);
    for (unsigned i = 0; i < ds_n; i++) {
        const uint8_t *k = DS_PUB(i);
        unsigned slot;
        memcpy(&slot, k, sizeof slot);
        for (slot &= cap - 1; table[slot] && memcmp(table[slot], k, 32); slot = (slot + 1) & (cap - 1)) {}
        if (!table[slot]) { table[slot] = k; ds_keys[ds_n_keys++] = k; }
    }
    free(table);

    /* pages of DS_PAGE events, each split into its authors' groups (first appearance first) */
    ds_pages = ds_n / DS_PAGE;
    ds_order = malloc(ds_n * sizeof *ds_order);
    g_start = malloc((ds_n + 1) * sizeof *g_start);
    page_first_group = malloc((ds_pages + 1) * sizeof *page_first_group);
    g_sigs = malloc(ds_n * sizeof *g_sigs);
    g_msgs = malloc(ds_n * sizeof *g_msgs);
    unsigned w = 0;
    for (unsigned p = 0; p < ds_pages; p++) {
        page_first_group[p] = ds_n_groups;
        unsigned base = p * DS_PAGE;
        char used[DS_PAGE] = {0};
        for (unsigned a = 0; a < DS_PAGE; a++) {
            if (used[a]) continue;
            g_start[ds_n_groups++] = w;
            for (unsigned c = a; c < DS_PAGE; c++) {
                if (used[c] || memcmp(DS_PUB(base + a), DS_PUB(base + c), 32)) continue;
                used[c] = 1;
                ds_order[w] = base + c;
                g_sigs[w] = DS_SIG(base + c);
                g_msgs[w] = DS_ID(base + c);
                w++;
            }
        }
    }
    page_first_group[ds_pages] = ds_n_groups;
    g_start[ds_n_groups] = w;
}

/* The page's groups through the library: batch verify for groups of two or more. */
static int verify_page(const backend *b, unsigned p) {
    int all = 1;
    for (unsigned g = page_first_group[p]; g < page_first_group[p + 1]; g++) {
        unsigned s = g_start[g], n = g_start[g + 1] - s;
        const uint8_t *pub = DS_PUB(ds_order[s]);
        all &= n == 1 ? b->verify(g_sigs[s], g_msgs[s], pub) : b->verify_batch(pub, g_sigs + s, g_msgs + s, n);
    }
    return all;
}

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

enum { DD_VERIFY, DD_VERIFY_TAMPERED, DD_ECDH, N_DS_DIGESTS };
static const char *const DS_DIGEST_NAMES[N_DS_DIGESTS] = {"nostr.verify", "nostr.verify.tampered", "nostr.ecdh"};

/* Over the dataset, in order: verify's result per event, the result for the event with the
 * last byte of s incremented, and x-only ECDH with each distinct pubkey (vector key d mod N). */
static void dataset_digests(const backend *b, digest_result out[N_DS_DIGESTS]) {
    sha256_ctx c[N_DS_DIGESTS];
    for (int d = 0; d < N_DS_DIGESTS; d++) {
        sha256_init(&c[d]);
        out[d].check = DS_DIGEST_NAMES[d];
        out[d].ok = 1;
    }
    for (unsigned i = 0; i < ds_n; i++) {
        uint8_t ok = (uint8_t)(b->verify(DS_SIG(i), DS_ID(i), DS_PUB(i)) ? 1 : 0);
        sha256_update(&c[DD_VERIFY], &ok, 1);
        uint8_t bad[64];
        memcpy(bad, DS_SIG(i), 64);
        bad[63]++;
        ok = (uint8_t)(b->verify(bad, DS_ID(i), DS_PUB(i)) ? 1 : 0);
        sha256_update(&c[DD_VERIFY_TAMPERED], &ok, 1);
    }
    for (unsigned d = 0; d < ds_n_keys; d++) {
        uint8_t x[32];
        out[DD_ECDH].ok &= b->ecdh_xonly(x, ds_keys[d], V.seckey[d % VEC_N]);
        sha256_update(&c[DD_ECDH], x, 32);
    }
    for (int d = 0; d < N_DS_DIGESTS; d++) sha256_final(&c[d], out[d].digest);
}

static void hex(char *out, const uint8_t *in, size_t n) {
    for (size_t i = 0; i < n; i++) sprintf(out + 2 * i, "%02x", in[i]);
}

/* ---------------------------------------------------------------- operations */

typedef enum {
    OP_VERIFY_SAME, OP_VERIFY_MANY, OP_VERIFY_FAST_SAME, OP_VERIFY_BATCH, OP_SIGN, OP_SIGN_KNOWN_PUB,
    OP_PUB_CREATE, OP_PUB_COMPRESS, OP_SECKEY_VERIFY, OP_ECDH, OP_TWEAK_MUL, OP_TWEAK_ADD,
    OP_NOSTR_VERIFY, OP_NOSTR_VERIFY_FAST, OP_NOSTR_BATCH, OP_NOSTR_ECDH, N_OPS
} op_id;

static const char *const OP_NAMES[N_OPS] = {
    "verify.sameKey", "verify.manyKeys", "verifyFast.sameKey", "verifyBatch200.perSig", "sign", "sign.knownPub",
    "pubkey.create", "pubkey.compress", "seckey.verify", "ecdh.xOnly", "tweak.mul", "tweak.add",
    "nostr.verify", "nostr.verifyFast", "nostr.verifyBatchByAuthor", "nostr.ecdh",
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
    case OP_NOSTR_VERIFY:
        for (long r = 0; r < n; r++, k++) { unsigned i = k % ds_n; acc += (uint64_t)b->verify(DS_SIG(i), DS_ID(i), DS_PUB(i)); }
        break;
    case OP_NOSTR_VERIFY_FAST: {
        int (*f)(const uint8_t *, const uint8_t *, const uint8_t *) = b->verify_fast ? b->verify_fast : b->verify;
        for (long r = 0; r < n; r++, k++) { unsigned i = k % ds_n; acc += (uint64_t)f(DS_SIG(i), DS_ID(i), DS_PUB(i)); }
        break;
    }
    case OP_NOSTR_BATCH: /* one call is one page */
        if (!b->verify_batch) return 0;
        for (long r = 0; r < n; r++, k++) acc += (uint64_t)verify_page(b, k % ds_pages);
        break;
    case OP_NOSTR_ECDH:
        for (long r = 0; r < n; r++, k++) {
            unsigned d = k % ds_n_keys;
            b->ecdh_xonly(out, ds_keys[d], V.seckey[d % VEC_N]);
            acc += out[7];
        }
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
    double per_call = op == OP_VERIFY_BATCH ? VEC_BATCH : op == OP_NOSTR_BATCH ? DS_PAGE : 1;
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
#if defined(__APPLE__)
    size_t len = cap;
    if (sysctlbyname("machdep.cpu.brand_string", out, &len, NULL, 0) == 0) return;
#elif defined(__ANDROID__)
    /* As the Android layer's Build.SOC_MANUFACTURER / SOC_MODEL (API 31+), else the board. */
    char man[PROP_VALUE_MAX] = "", model[PROP_VALUE_MAX] = "";
    __system_property_get("ro.soc.manufacturer", man);
    __system_property_get("ro.soc.model", model);
    if (!model[0]) __system_property_get("ro.board.platform", model);
    if (model[0]) {
        snprintf(out, cap, "%s%s%s", man, man[0] ? " " : "", model);
        return;
    }
#endif
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
    if (argc < 2) die("usage: bench vectors | bench run --vectors FILE [--library NAME] [--device TEXT] [--machine dedicated|shared] [--harness COMMIT] [--quick]");
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
    if (strcmp(argv[1], "dataset") == 0) {
        if (argc < 3) die("usage: bench dataset FILE");
        char sha[65];
        load_dataset(argv[2], sha);
        digest_result d[N_DS_DIGESTS];
        dataset_digests(&backend_libsecp256k1, d);
        if (!d[DD_ECDH].ok) die("reference ecdh failed");
        printf("file %s\nevents %u\npubkeys %u\n", sha, ds_n, ds_n_keys);
        for (int i = 0; i < N_DS_DIGESTS; i++) {
            char h[65];
            hex(h, d[i].digest, 32);
            printf("%s %s\n", d[i].check, h);
        }
        return 0;
    }
    if (strcmp(argv[1], "run") != 0) die("unknown command");

    const char *vec_path = NULL, *ds_path = NULL, *dsd_path = NULL, *only = NULL, *device = "unknown", *machine = "unknown", *harness = BENCH_HARNESS_VERSION;
    int quick = 0;
    for (int i = 2; i < argc; i++) {
        if (!strcmp(argv[i], "--vectors") && i + 1 < argc) vec_path = argv[++i];
        else if (!strcmp(argv[i], "--dataset") && i + 1 < argc) ds_path = argv[++i];
        else if (!strcmp(argv[i], "--dataset-digests") && i + 1 < argc) dsd_path = argv[++i];
        else if (!strcmp(argv[i], "--library") && i + 1 < argc) only = argv[++i];
        else if (!strcmp(argv[i], "--device") && i + 1 < argc) device = argv[++i];
        else if (!strcmp(argv[i], "--machine") && i + 1 < argc) machine = argv[++i];
        else if (!strcmp(argv[i], "--harness") && i + 1 < argc) harness = argv[++i];
        else if (!strcmp(argv[i], "--quick")) quick = 1;
        else die("unknown option");
    }
    if (!vec_path) die("--vectors FILE is required");
    if (!ds_path || !dsd_path) die("--dataset FILE and --dataset-digests FILE are required");
    char ds_sha[65];
    load_dataset(ds_path, ds_sha);
    char *dsd = read_file(dsd_path);
    if (!dsd) die("cannot read the dataset digests");
    {
        const char *want = expected_digest(dsd, "file");
        if (!want || strcmp(want, ds_sha)) die("the dataset is not the one its digests file describes");
    }
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
    fprintf(o, "{\n  \"schema\": 3,\n  \"vectors\": \"v1\",\n  \"vectorsSha256\": \"%s\",\n", vec_sha);
    fprintf(o, "  \"dataset\": \"nostr-v1\",\n  \"datasetSha256\": \"%s\",\n  \"layer\": \"c\",\n", ds_sha);
    fprintf(o, "  \"quick\": %s,\n  \"context\": {\n    \"date\": \"%s\",\n    \"device\": ", quick ? "true" : "false", date);
    json_str(o, device);
    fprintf(o, ",\n    \"cpu\": ");
    json_str(o, cpu);
    fprintf(o, ",\n    \"os\": ");
#if defined(__ANDROID__)
    {
        char rel[PROP_VALUE_MAX] = "", sdk[PROP_VALUE_MAX] = "", os[96];
        __system_property_get("ro.build.version.release", rel);
        __system_property_get("ro.build.version.sdk", sdk);
        snprintf(os, sizeof os, "Android %s (API %s)", rel, sdk);
        json_str(o, os);
    }
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
    fprintf(o, ",\n    \"harness\": ");
    json_str(o, harness);
    fprintf(o, ",\n    \"libraries\": {");
    for (size_t bi = 0, n = 0; bi < N_BACKENDS; bi++) {
        if (only && strcmp(only, BACKENDS[bi]->name)) continue;
        fprintf(o, "%s\"%s\": \"%s\"", n++ ? ", " : " ", BACKENDS[bi]->name, BACKENDS[bi]->version);
    }
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

        digest_result dd[N_DS_DIGESTS];
        dataset_digests(b, dd);
        int dgood[N_DS_DIGESTS];
        for (int i = 0; i < N_DS_DIGESTS; i++) {
            char h[65];
            hex(h, dd[i].digest, 32);
            const char *want = expected_digest(dsd, dd[i].check);
            if (!want) die("dataset digests file lacks a check");
            dgood[i] = strcmp(h, want) == 0;
            if (!dgood[i]) fprintf(stderr, "%s: %s digest differs from the dataset's\n", b->name, dd[i].check);
        }
        int ds_fast_ok = 1, ds_batch_ok = 1;
        for (unsigned i = 0; i < ds_n && b->verify_fast; i++)
            if (!b->verify_fast(DS_SIG(i), DS_ID(i), DS_PUB(i))) ds_fast_ok = 0;
        for (unsigned p = 0; p < ds_pages && b->verify_batch; p++)
            if (!verify_page(b, p)) ds_batch_ok = 0;
        int ds_verify_ok = dgood[DD_VERIFY] && dgood[DD_VERIFY_TAMPERED];
        int ok[N_OPS] = {
            [OP_VERIFY_SAME] = verify_ok, [OP_VERIFY_MANY] = verify_ok, [OP_VERIFY_FAST_SAME] = verify_ok && fast_ok,
            [OP_VERIFY_BATCH] = batch_ok, [OP_SIGN] = good[D_SIGN], [OP_SIGN_KNOWN_PUB] = known_pub_ok,
            [OP_PUB_CREATE] = good[D_PUB_CREATE], [OP_PUB_COMPRESS] = good[D_PUB_COMPRESS], [OP_SECKEY_VERIFY] = seckey_ok,
            [OP_ECDH] = good[D_ECDH], [OP_TWEAK_MUL] = good[D_TWEAK_MUL], [OP_TWEAK_ADD] = good[D_TWEAK_ADD],
            [OP_NOSTR_VERIFY] = ds_verify_ok, [OP_NOSTR_VERIFY_FAST] = ds_verify_ok && ds_fast_ok,
            [OP_NOSTR_BATCH] = ds_batch_ok, [OP_NOSTR_ECDH] = dgood[DD_ECDH] && dd[DD_ECDH].ok,
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
            if ((op == OP_VERIFY_BATCH || op == OP_NOSTR_BATCH) && is.ki1) ki = "\"KI-1\"";
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
    free(dsd);
    return (int)(sink & 0); /* keep the sink alive */
}
