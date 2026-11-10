package main

// BIP-340's own test vectors (vectors/bip340/test-vectors.csv; vectors/README.md, "BIP-340
// vectors"): the rows with a 32-byte message, the size every operation here takes.

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

type bip340Vector struct {
	index                      int
	seckey, aux, pub, msg, sig []byte // seckey and aux are nil on verification-only rows
	expected                   bool
}

// bip340OddR is the vector whose only fault is R's odd y (KI-4).
const bip340OddR = 6

var bip340Vectors []bip340Vector

// loadBip340 reads the CSV beside the vectors file and returns its SHA-256.
func loadBip340(vecPath string) string {
	raw, err := os.ReadFile(filepath.Join(filepath.Dir(vecPath), "bip340", "test-vectors.csv"))
	if err != nil {
		die("cannot read vectors/bip340/test-vectors.csv")
	}
	unhex := func(s string, n int) []byte {
		b, err := hex.DecodeString(s)
		if err != nil || len(b) != n {
			die("a BIP-340 vector does not parse")
		}
		return b
	}
	for _, line := range strings.Split(string(raw), "\n")[1:] {
		line = strings.TrimRight(line, "\r")
		if line == "" {
			continue
		}
		// index, secret key, public key, aux_rand, message, signature, verification result, comment
		f := strings.SplitN(line, ",", 8)
		if len(f) < 7 {
			die("a BIP-340 vector line has too few fields")
		}
		if len(f[4]) != 64 {
			continue
		}
		idx, err := strconv.Atoi(f[0])
		if err != nil {
			die("a BIP-340 vector index is not a number")
		}
		v := bip340Vector{index: idx, pub: unhex(f[2], 32), msg: unhex(f[4], 32), sig: unhex(f[5], 64)}
		if f[1] != "" {
			v.seckey = unhex(f[1], 32)
			v.aux = unhex(f[3], 32)
		}
		switch f[6] {
		case "TRUE":
			v.expected = true
		case "FALSE":
			v.expected = false
		default:
			die("a BIP-340 vector's result is neither TRUE nor FALSE")
		}
		bip340Vectors = append(bip340Vectors, v)
	}
	if len(bip340Vectors) == 0 {
		die("no BIP-340 vector has a 32-byte message")
	}
	h := sha256.Sum256(raw)
	return hex.EncodeToString(h[:])
}

// guard runs a call; a panic on an invalid input counts as a rejection, on a valid one as a
// wrong result.
func guard(f func() bool) (ok bool) {
	defer func() {
		if recover() != nil {
			ok = false
		}
	}()
	return f()
}

type bip340Result struct {
	signOK, knownPubOK, knownPubOddOnly, verifyOK, fastOK, batchOK bool
	// KI-4: verifyFast accepts bip340OddR, and gets every other vector right
	fastAcceptsOddR, fastOKButOddR bool
}

// bip340Check runs BIP-340's vectors through b, and a batch with one bad signature, first then last.
func bip340Check(b *backend) bip340Result {
	ref := backends[0]
	r := bip340Result{signOK: true, knownPubOK: true, knownPubOddOnly: true, verifyOK: true, fastOK: true, batchOK: true, fastOKButOddR: true}
	what := func(expected bool) string {
		if expected {
			return "rejects"
		}
		return "accepts"
	}
	for _, v := range bip340Vectors {
		if v.seckey != nil {
			s := make([]byte, 64)
			if !guard(func() bool { return b.sign(s, v.msg, v.seckey, v.aux) }) || !bytes.Equal(s, v.sig) {
				fmt.Fprintf(os.Stderr, "%s: BIP-340 vector %d: sign differs\n", b.name, v.index)
				r.signOK = false
			}
			pub65, pub33 := make([]byte, 65), make([]byte, 33)
			if !ref.pubkeyCreate(pub65, v.seckey) || !ref.pubkeyCompress(pub33, pub65) {
				die("reference BIP-340 pubkey")
			}
			s = make([]byte, 64)
			if !guard(func() bool { return b.signKnownPub(s, v.msg, v.seckey, pub33, pub33[1:], v.aux) }) || !bytes.Equal(s, v.sig) {
				fmt.Fprintf(os.Stderr, "%s: BIP-340 vector %d: sign.knownPub differs\n", b.name, v.index)
				r.knownPubOK = false
				if pub33[0] != 0x03 {
					r.knownPubOddOnly = false
				}
			}
		}
		if guard(func() bool { return b.verify(v.sig, v.msg, v.pub) }) != v.expected {
			fmt.Fprintf(os.Stderr, "%s: BIP-340 vector %d: verify %s it\n", b.name, v.index, what(v.expected))
			r.verifyOK = false
		}
		if b.verifyFast != nil && guard(func() bool { return b.verifyFast(v.sig, v.msg, v.pub) }) != v.expected {
			fmt.Fprintf(os.Stderr, "%s: BIP-340 vector %d: verifyFast %s it\n", b.name, v.index, what(v.expected))
			r.fastOK = false
			if v.index == bip340OddR {
				r.fastAcceptsOddR = true
			} else {
				r.fastOKButOddR = false
			}
		}
	}
	if b.verifyBatch != nil {
		for _, at := range []int{0, vecBatch - 1} {
			sigs := make([][]byte, vecBatch)
			copy(sigs, V.batchSig[:])
			bad := bytes.Clone(sigs[at])
			bad[63]++
			sigs[at] = bad
			if guard(func() bool { return b.verifyBatch(V.xonly[0], sigs, V.batchMsg[:]) }) {
				fmt.Fprintf(os.Stderr, "%s: verifyBatch accepts a batch whose signature %d is bad\n", b.name, at)
				r.batchOK = false
			}
		}
	}
	return r
}
