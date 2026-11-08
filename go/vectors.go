// Copyright (c) 2025 Vitor Pamplona

package main

// Input vectors v1 (vectors/README.md) and the dataset (datasets/README.md), the twin of
// c/vectors.c and the dataset half of c/main.c. SHA-256 is Go's crypto/sha256, not a library
// under test.

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"os"
	"strconv"
)

const (
	vecN     = 4096
	vecBatch = 200
	dsRecord = 128 // id (32, the signed message) || pubkey (32) || sig (64)
	dsPage   = 200 // events per relay page, for nostr.verifyBatchByAuthor
)

// n, the secp256k1 group order, big-endian.
var order = []byte{
	0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFE,
	0xBA, 0xAE, 0xDC, 0xE6, 0xAF, 0x48, 0xA0, 0x3B, 0xBF, 0xD2, 0x5E, 0x8C, 0xD0, 0x36, 0x41, 0x41,
}

type vectors struct {
	seckey, tweak, msg, aux [vecN][]byte
	// derived with the reference library (libsecp256k1)
	pub65, pub33, xonly, tpub33, sig [vecN][]byte
	batchSig, batchMsg               [vecBatch][]byte
}

var V vectors

func labelHash(field string, i int) []byte {
	h := sha256.Sum256([]byte("schnorr256k1-bench/v1/" + field + "/" + strconv.Itoa(i)))
	return h[:]
}

func validScalar(s []byte) bool {
	return !bytes.Equal(s, make([]byte, 32)) && bytes.Compare(s, order) < 0
}

func scalarFromLabel(field string, i int) []byte {
	h := labelHash(field, i)
	for !validScalar(h) {
		s := sha256.Sum256(h)
		h = s[:]
	}
	return h
}

// derive fills the vectors: the labelled inputs, then the derived values through ref.
func derive(ref *backend) {
	for i := 0; i < vecN; i++ {
		V.seckey[i] = scalarFromLabel("seckey", i)
		V.tweak[i] = scalarFromLabel("tweak", i)
		V.msg[i] = labelHash("msg", i)
		V.aux[i] = labelHash("aux", i)
		V.pub65[i], V.pub33[i], V.tpub33[i], V.sig[i] = make([]byte, 65), make([]byte, 33), make([]byte, 33), make([]byte, 64)
		if !ref.pubkeyCreate(V.pub65[i], V.seckey[i]) || !ref.pubkeyCompress(V.pub33[i], V.pub65[i]) {
			die("reference pubkey")
		}
		V.xonly[i] = V.pub33[i][1:33]
		t65 := make([]byte, 65)
		if !ref.pubkeyCreate(t65, V.tweak[i]) || !ref.pubkeyCompress(V.tpub33[i], t65) {
			die("reference tweak pubkey")
		}
		if !ref.sign(V.sig[i], V.msg[i], V.seckey[i], V.aux[i]) {
			die("reference sign")
		}
	}
	for j := 0; j < vecBatch; j++ {
		V.batchSig[j] = make([]byte, 64)
		if !ref.sign(V.batchSig[j], V.msg[j], V.seckey[0], V.aux[j]) {
			die("reference batch sign")
		}
		V.batchMsg[j] = V.msg[j]
	}
}

// ---------------------------------------------------------------- dataset

type dataset struct {
	raw    []byte
	n      int
	pages  int      // whole pages; the remainder is left out of the batch op
	keys   [][]byte // distinct pubkeys, in order of first appearance
	sha    string
	order  []int // per page, its authors' groups: group g covers order[gStart[g]:gStart[g+1]]
	gStart []int
	pageG  []int // the first group of each page, and the end
	gSigs  [][]byte
	gMsgs  [][]byte
	ids    [][]byte
	pubs   [][]byte
	sigs   [][]byte
}

var D dataset

func loadDataset(path string) {
	raw, err := os.ReadFile(path)
	if err != nil {
		die("cannot read the dataset")
	}
	if len(raw) == 0 || len(raw)%dsRecord != 0 {
		die("the dataset is not whole 128-byte records")
	}
	D.raw = raw
	D.n = len(raw) / dsRecord
	h := sha256.Sum256(raw)
	D.sha = hex.EncodeToString(h[:])
	D.ids, D.pubs, D.sigs = make([][]byte, D.n), make([][]byte, D.n), make([][]byte, D.n)
	seen := map[[32]byte]bool{}
	for i := 0; i < D.n; i++ {
		r := raw[i*dsRecord : (i+1)*dsRecord]
		D.ids[i], D.pubs[i], D.sigs[i] = r[0:32:32], r[32:64:64], r[64:128:128]
		k := [32]byte(D.pubs[i])
		if !seen[k] {
			seen[k] = true
			D.keys = append(D.keys, D.pubs[i])
		}
	}
	// pages of dsPage events, each split into its authors' groups (first appearance first)
	D.pages = D.n / dsPage
	for p := 0; p < D.pages; p++ {
		D.pageG = append(D.pageG, len(D.gStart))
		base := p * dsPage
		var used [dsPage]bool
		for a := 0; a < dsPage; a++ {
			if used[a] {
				continue
			}
			D.gStart = append(D.gStart, len(D.order))
			for c := a; c < dsPage; c++ {
				if used[c] || !bytes.Equal(D.pubs[base+a], D.pubs[base+c]) {
					continue
				}
				used[c] = true
				D.order = append(D.order, base+c)
				D.gSigs = append(D.gSigs, D.sigs[base+c])
				D.gMsgs = append(D.gMsgs, D.ids[base+c])
			}
		}
	}
	D.pageG = append(D.pageG, len(D.gStart))
	D.gStart = append(D.gStart, len(D.order))
}

// verifyPage verifies a page's groups through the library: batch verify for groups of two or more.
func verifyPage(b *backend, p int) bool {
	all := true
	for g := D.pageG[p]; g < D.pageG[p+1]; g++ {
		s, e := D.gStart[g], D.gStart[g+1]
		pub := D.pubs[D.order[s]]
		if e-s == 1 {
			all = b.verify(D.gSigs[s], D.gMsgs[s], pub) && all
		} else {
			all = b.verifyBatch(pub, D.gSigs[s:e], D.gMsgs[s:e]) && all
		}
	}
	return all
}

func die(what string) {
	fmt.Fprintf(os.Stderr, "error: %s\n", what)
	os.Exit(2)
}
