# Datasets

## nostr-v1

`nostr-v1.bin`: 29,614 real Nostr events, as signature verification sees them. Each is a
128-byte record, in the order a relay sent them:

| Bytes | Field |
|---|---|
| 0–31 | `id`: the SHA-256 of the event's NIP-01 serialization, which the signature signs |
| 32–63 | `pubkey`: the author's x-only public key |
| 64–127 | `sig`: the BIP-340 signature |

**Source:** nostrLibBench's corpus (github.com/vitorpamplona/nostrLibBench, commit `aa91e85`,
`corpus/nostr-2024-startup.ndjson.gz`, SHA-256 `bfc8f0cd…b357146`, MIT). It is the 31,161 events an
Amethyst startup session downloaded in 2024, byte-for-byte as relays sent them. The 1,547 with no
signature (`"sig": ""`, unsigned NIP-17 rumors) are left out; nothing else is.
`extract-nostr.py` rebuilds the file from that corpus:

```bash
python3 datasets/extract-nostr.py PATH/TO/nostrLibBench/corpus/nostr-2024-startup.ndjson.gz
```

**Shape:** 4,926 distinct authors, more than the 1,024-entry per-key caches of libschnorr256k1
and libschnorr256k1-kotlin. One author has 6,394 events, 30 have 100 or more, and 3,392 appear
once. 83% of the events are by an author seen earlier in the stream. Kinds: 4 (DMs, 9,600),
1 (notes, 3,565), 1311, 7, 42, 1059, 4550, 1984, …

`nostr-v1.txt`: the file's SHA-256, its counts, and the digests every library must reproduce,
printed by libsecp256k1 through `build/c/bench dataset datasets/nostr-v1.bin`:

- `nostr.verify`: SHA-256 of one byte per event, 1 if it verifies. libsecp256k1 accepts all
  29,614, so this is SHA-256 of 29,614 bytes of `01`.
- `nostr.verify.tampered`: the same with the last byte of `s` incremented; all rejected.
- `nostr.ecdh`: SHA-256 of x-only ECDH of each distinct pubkey (first-appearance order) with
  `seckey[d mod 4096]` from the vectors.

A new dataset, or any change to this one, is a new name (`nostr-v2`) and a new schema version.
