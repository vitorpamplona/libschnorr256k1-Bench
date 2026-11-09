// Copyright (c) 2025 Vitor Pamplona

// The go layer: Go's BIP-340 implementations, and libsecp256k1 through cgo, timed by a twin of
// c/main.c (same derivation, checks, operations, timing, known-issue probes and JSON).
//
//	bench run   --vectors FILE --dataset DATA --dataset-digests FILE [opts]
//	            check every library, time every operation, print a result (schema 3) as JSON
//	bench check --vectors FILE --dataset DATA --dataset-digests FILE [--library NAME]
//	            the same checks and probes without the timing; exits 1 on a wrong result that
//	            no observed known issue explains (Runner.COVERS), or if the reference shows one
//	  --library NAME     only this library     --device TEXT   context.device
//	  --machine M        dedicated | shared    --harness ID    context.harness
//	  --quick            short warm-up and samples (numbers not comparable)
package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"os"
	"os/exec"
	"runtime"
	"runtime/debug"
	"sort"
	"strconv"
	"strings"
	"time"
)

// Set by run.sh through -ldflags -X.
var (
	libsecp256k1Version = "unknown" // the submodule's commit
	cgoCompiler         = "unknown" // the C compiler cgo used, and its version
)

// sink keeps every result alive, so the compiler cannot drop a call.
var sink uint64

// ---------------------------------------------------------------- digests

const (
	dPubCreate = iota
	dPubCompress
	dSign
	dEcdh
	dTweakMul
	dTweakAdd
	dVerify
	dVerifyTampered
	nDigests
)

var digestNames = [nDigests]string{"pubkey.create", "pubkey.compress", "sign", "ecdh.xOnly", "tweak.mul", "tweak.add", "verify", "verify.tampered"}

type digestResult struct {
	digest string
	ok     bool // every call succeeded
}

func b2u(ok bool) byte {
	if ok {
		return 1
	}
	return 0
}

func digests(b *backend) [nDigests]digestResult {
	var out [nDigests]digestResult
	h := make([]interface {
		Write([]byte) (int, error)
		Sum([]byte) []byte
	}, nDigests)
	for d := range h {
		h[d] = sha256.New()
		out[d].ok = true
	}
	buf := make([]byte, 65)
	bad := make([]byte, 64)
	for i := 0; i < vecN; i++ {
		out[dPubCreate].ok = b.pubkeyCreate(buf, V.seckey[i]) && out[dPubCreate].ok
		h[dPubCreate].Write(buf[:65])
		out[dPubCompress].ok = b.pubkeyCompress(buf[:33], V.pub65[i]) && out[dPubCompress].ok
		h[dPubCompress].Write(buf[:33])
		out[dSign].ok = b.sign(buf[:64], V.msg[i], V.seckey[i], V.aux[i]) && out[dSign].ok
		h[dSign].Write(buf[:64])
		out[dEcdh].ok = b.ecdhXOnly(buf[:32], V.xonly[(i+1)%vecN], V.seckey[i]) && out[dEcdh].ok
		h[dEcdh].Write(buf[:32])
		out[dTweakMul].ok = b.tweakMul(buf[:33], V.tpub33[i], V.seckey[i]) && out[dTweakMul].ok
		h[dTweakMul].Write(buf[:33])
		out[dTweakAdd].ok = b.tweakAdd(buf[:32], V.seckey[i], V.tweak[i]) && out[dTweakAdd].ok
		h[dTweakAdd].Write(buf[:32])
		h[dVerify].Write([]byte{b2u(b.verify(V.sig[i], V.msg[i], V.xonly[i]))})
		copy(bad, V.sig[i])
		bad[63]++
		h[dVerifyTampered].Write([]byte{b2u(b.verify(bad, V.msg[i], V.xonly[i]))})
	}
	for d := range h {
		out[d].digest = hex.EncodeToString(h[d].Sum(nil))
	}
	return out
}

const (
	ddVerify = iota
	ddVerifyTampered
	ddEcdh
	nDsDigests
)

var dsDigestNames = [nDsDigests]string{"nostr.verify", "nostr.verify.tampered", "nostr.ecdh"}

// Over the dataset, in order: verify's result per event, the result for the event with the
// last byte of s incremented, and x-only ECDH with each distinct pubkey (vector key d mod N).
func datasetDigests(b *backend) [nDsDigests]digestResult {
	var out [nDsDigests]digestResult
	hv, ht, he := sha256.New(), sha256.New(), sha256.New()
	out[ddEcdh].ok = true
	bad := make([]byte, 64)
	for i := 0; i < D.n; i++ {
		hv.Write([]byte{b2u(b.verify(D.sigs[i], D.ids[i], D.pubs[i]))})
		copy(bad, D.sigs[i])
		bad[63]++
		ht.Write([]byte{b2u(b.verify(bad, D.ids[i], D.pubs[i]))})
	}
	x := make([]byte, 32)
	for d, k := range D.keys {
		out[ddEcdh].ok = b.ecdhXOnly(x, k, V.seckey[d%vecN]) && out[ddEcdh].ok
		he.Write(x)
	}
	out[ddVerify].digest = hex.EncodeToString(hv.Sum(nil))
	out[ddVerifyTampered].digest = hex.EncodeToString(ht.Sum(nil))
	out[ddEcdh].digest = hex.EncodeToString(he.Sum(nil))
	return out
}

// ---------------------------------------------------------------- operations

const (
	opVerifySame = iota
	opVerifyMany
	opVerifyFastSame
	opVerifyBatch
	opSign
	opSignKnownPub
	opPubCreate
	opPubCompress
	opSeckeyVerify
	opEcdh
	opTweakMul
	opTweakAdd
	opNostrVerify
	opNostrVerifyFast
	opNostrBatch
	opNostrEcdh
	nOps
)

var opNames = [nOps]string{
	"verify.sameKey", "verify.manyKeys", "verifyFast.sameKey", "verifyBatch200.perSig", "sign", "sign.knownPub",
	"pubkey.create", "pubkey.compress", "seckey.verify", "ecdh.xOnly", "tweak.mul", "tweak.add",
	"nostr.verify", "nostr.verifyFast", "nostr.verifyBatchByAuthor", "nostr.ecdh",
}

var (
	nextIndex int
	out       = make([]byte, 65)
)

// run makes n calls of op. Returns false if the library lacks it.
func run(b *backend, op int, n int) bool {
	var acc uint64
	k := nextIndex
	fast := b.verifyFast
	if fast == nil {
		fast = b.verify
	}
	switch op {
	case opVerifySame:
		for r := 0; r < n; r++ {
			acc += uint64(b2u(b.verify(V.sig[0], V.msg[0], V.xonly[0])))
		}
	case opVerifyMany:
		for r := 0; r < n; r, k = r+1, k+1 {
			i := k % vecN
			acc += uint64(b2u(b.verify(V.sig[i], V.msg[i], V.xonly[i])))
		}
	case opVerifyFastSame:
		for r := 0; r < n; r++ {
			acc += uint64(b2u(fast(V.sig[0], V.msg[0], V.xonly[0])))
		}
	case opVerifyBatch:
		if b.verifyBatch == nil {
			return false
		}
		for r := 0; r < n; r++ {
			acc += uint64(b2u(b.verifyBatch(V.xonly[0], V.batchSig[:], V.batchMsg[:])))
		}
	case opSign:
		for r := 0; r < n; r, k = r+1, k+1 {
			i := k % vecN
			b.sign(out[:64], V.msg[i], V.seckey[i], V.aux[i])
			acc += uint64(out[7])
		}
	case opSignKnownPub:
		for r := 0; r < n; r, k = r+1, k+1 {
			i := k % vecN
			b.signKnownPub(out[:64], V.msg[i], V.seckey[i], V.pub33[i], V.xonly[i], V.aux[i])
			acc += uint64(out[7])
		}
	case opPubCreate:
		for r := 0; r < n; r, k = r+1, k+1 {
			b.pubkeyCreate(out, V.seckey[k%vecN])
			acc += uint64(out[7])
		}
	case opPubCompress:
		for r := 0; r < n; r, k = r+1, k+1 {
			b.pubkeyCompress(out[:33], V.pub65[k%vecN])
			acc += uint64(out[7])
		}
	case opSeckeyVerify:
		for r := 0; r < n; r, k = r+1, k+1 {
			acc += uint64(b2u(b.seckeyVerify(V.seckey[k%vecN])))
		}
	case opEcdh:
		for r := 0; r < n; r, k = r+1, k+1 {
			i := k % vecN
			b.ecdhXOnly(out[:32], V.xonly[(i+1)%vecN], V.seckey[i])
			acc += uint64(out[7])
		}
	case opTweakMul:
		for r := 0; r < n; r, k = r+1, k+1 {
			i := k % vecN
			b.tweakMul(out[:33], V.tpub33[i], V.seckey[i])
			acc += uint64(out[7])
		}
	case opTweakAdd:
		for r := 0; r < n; r, k = r+1, k+1 {
			i := k % vecN
			b.tweakAdd(out[:32], V.seckey[i], V.tweak[i])
			acc += uint64(out[7])
		}
	case opNostrVerify:
		for r := 0; r < n; r, k = r+1, k+1 {
			i := k % D.n
			acc += uint64(b2u(b.verify(D.sigs[i], D.ids[i], D.pubs[i])))
		}
	case opNostrVerifyFast:
		for r := 0; r < n; r, k = r+1, k+1 {
			i := k % D.n
			acc += uint64(b2u(fast(D.sigs[i], D.ids[i], D.pubs[i])))
		}
	case opNostrBatch: // one call is one page
		if b.verifyBatch == nil {
			return false
		}
		for r := 0; r < n; r, k = r+1, k+1 {
			acc += uint64(b2u(verifyPage(b, k%D.pages)))
		}
	case opNostrEcdh:
		for r := 0; r < n; r, k = r+1, k+1 {
			d := k % len(D.keys)
			b.ecdhXOnly(out[:32], D.keys[d], V.seckey[d%vecN])
			acc += uint64(out[7])
		}
	default:
		return false
	}
	nextIndex = k
	sink += acc
	return true
}

// pct is the nearest-rank percentile of sorted v.
func pct(v []float64, p float64) float64 {
	n := len(v)
	r := int(p/100.0*float64(n) + 0.999999)
	if r < 1 {
		r = 1
	}
	if r > n {
		r = n
	}
	return v[r-1]
}

type timing struct {
	timed           bool
	median, p5, p95 float64
	samples         int
}

func timeOp(b *backend, op int, quick bool) timing {
	var t timing
	warm, sample, nSamples := time.Second, 100*time.Millisecond, 20
	if quick {
		warm, sample, nSamples = 200*time.Millisecond, 20*time.Millisecond, 5
	}
	perCall := 1.0
	switch op {
	case opVerifyBatch:
		perCall = vecBatch
	case opNostrBatch:
		perCall = dsPage
	}
	if !run(b, op, 1) {
		return t
	}
	start := time.Now()
	for time.Since(start) < warm {
		run(b, op, 16)
	}
	n := 1
	for { // calibrate: n calls take at least one sample's time
		s := time.Now()
		run(b, op, n)
		if time.Since(s) >= sample {
			break
		}
		n *= 2
	}
	v := make([]float64, nSamples)
	for s := range v {
		a := time.Now()
		run(b, op, n)
		v[s] = float64(time.Since(a).Nanoseconds()) / (float64(n) * perCall)
	}
	sort.Float64s(v)
	t.timed, t.samples = true, nSamples
	t.median, t.p5, t.p95 = pct(v, 50), pct(v, 5), pct(v, 95)
	return t
}

// ---------------------------------------------------------------- known issues

// addSmallToS adds d (±1) to the s half of a signature, as KnownIssuesTest does.
func addSmallToS(sig []byte, d int) {
	carry := d
	for i := 63; carry != 0 && i >= 32; i-- {
		x := int(sig[i]) + carry
		sig[i] = byte(x)
		switch {
		case x > 255:
			carry = 1
		case x < 0:
			carry = -1
		default:
			carry = 0
		}
	}
}

type issues struct {
	ki1, ki2, ki3 bool // observed
	ki1Tested     bool
}

// probeIssues runs after timing, on keys derived under their own labels, so their cache side
// effects (KI-3) cannot reach the timed rows.
func probeIssues(b *backend, knownPubWrong bool) issues {
	ref := backends[0]
	r := issues{ki2: knownPubWrong}
	if b.verifyBatch != nil {
		r.ki1Tested = true
		s1, s2 := bytes.Clone(V.batchSig[0]), bytes.Clone(V.batchSig[1])
		addSmallToS(s1, 1)
		addSmallToS(s2, -1)
		eachInvalid := !ref.verify(s1, V.msg[0], V.xonly[0]) && !ref.verify(s2, V.msg[1], V.xonly[0])
		r.ki1 = eachInvalid && b.verifyBatch(V.xonly[0], [][]byte{s1, s2}, [][]byte{V.msg[0], V.msg[1]})
	}
	// KI-3: on odd-y keys of a separate set, tweak-mul the 33-byte (odd-y) point, then verify a
	// valid signature for the x-only form.
	rejected := 0
	for i, found := 0, 0; found < 16 && i < 4096; i++ {
		h := sha256.Sum256([]byte("schnorr256k1-bench/v1/probe/" + strconv.Itoa(i)))
		sk := h[:]
		pub65, pub33, sig, o := make([]byte, 65), make([]byte, 33), make([]byte, 64), make([]byte, 33)
		if !ref.pubkeyCreate(pub65, sk) || !ref.pubkeyCompress(pub33, pub65) {
			continue
		}
		if pub33[0] != 0x03 {
			continue
		}
		found++
		m := sha256.Sum256(sk)
		msg := m[:]
		ref.sign(sig, msg, sk, msg)
		b.tweakMul(o, pub33, V.tweak[0])
		if !b.verify(sig, msg, pub33[1:]) {
			rejected++
		}
	}
	r.ki3 = rejected > 0
	return r
}

// ---------------------------------------------------------------- checks

type checked struct {
	ok     [nOps]bool
	issues issues
}

// check runs every correctness check on b, before any timing: the digests against the vectors
// and the dataset, and the direct checks of vectors/README.md. It returns the per-op
// correctness; the known-issue probes are run by the caller after timing.
func check(b *backend, vec, dsd map[string]string) ([nOps]bool, bool) {
	d := digests(b)
	var good [nDigests]bool
	for i := range d {
		want, okw := vec[digestNames[i]]
		if !okw {
			die("vectors file lacks a check")
		}
		good[i] = d[i].digest == want
		if !good[i] {
			fmt.Fprintf(os.Stderr, "%s: %s digest differs from vectors\n", b.name, digestNames[i])
		}
	}
	fastOK, batchOK, knownPubOK, knownPubOddOnly, seckeyOK := true, true, true, true, true
	s := make([]byte, 64)
	for i := 0; i < vecN; i++ {
		if b.verifyFast != nil && !b.verifyFast(V.sig[i], V.msg[i], V.xonly[i]) {
			fastOK = false
		}
		if !b.signKnownPub(s, V.msg[i], V.seckey[i], V.pub33[i], V.xonly[i], V.aux[i]) || !bytes.Equal(s, V.sig[i]) {
			knownPubOK = false
			if V.pub33[i][0] != 0x03 {
				knownPubOddOnly = false
			}
		}
		if !b.seckeyVerify(V.seckey[i]) {
			seckeyOK = false
		}
	}
	if b.seckeyVerify(make([]byte, 32)) || b.seckeyVerify(bytes.Clone(order)) {
		seckeyOK = false
	}
	if b.verifyBatch != nil && !b.verifyBatch(V.xonly[0], V.batchSig[:], V.batchMsg[:]) {
		batchOK = false
	}
	verifyOK := good[dVerify] && good[dVerifyTampered]

	dd := datasetDigests(b)
	var dgood [nDsDigests]bool
	for i := range dd {
		want, okw := dsd[dsDigestNames[i]]
		if !okw {
			die("dataset digests file lacks a check")
		}
		dgood[i] = dd[i].digest == want
		if !dgood[i] {
			fmt.Fprintf(os.Stderr, "%s: %s digest differs from the dataset's\n", b.name, dsDigestNames[i])
		}
	}
	dsFastOK, dsBatchOK := true, true
	for i := 0; i < D.n && b.verifyFast != nil; i++ {
		if !b.verifyFast(D.sigs[i], D.ids[i], D.pubs[i]) {
			dsFastOK = false
		}
	}
	for p := 0; p < D.pages && b.verifyBatch != nil; p++ {
		if !verifyPage(b, p) {
			dsBatchOK = false
		}
	}
	dsVerifyOK := dgood[ddVerify] && dgood[ddVerifyTampered]
	var ok [nOps]bool
	ok[opVerifySame], ok[opVerifyMany], ok[opVerifyFastSame] = verifyOK, verifyOK, verifyOK && fastOK
	ok[opVerifyBatch], ok[opSign], ok[opSignKnownPub] = batchOK, good[dSign], knownPubOK
	ok[opPubCreate], ok[opPubCompress], ok[opSeckeyVerify] = good[dPubCreate], good[dPubCompress], seckeyOK
	ok[opEcdh], ok[opTweakMul], ok[opTweakAdd] = good[dEcdh], good[dTweakMul], good[dTweakAdd]
	ok[opNostrVerify], ok[opNostrVerifyFast] = dsVerifyOK, dsVerifyOK && dsFastOK
	ok[opNostrBatch], ok[opNostrEcdh] = dsBatchOK, dgood[ddEcdh] && dd[ddEcdh].ok
	return ok, !knownPubOK && knownPubOddOnly
}

// covers: the ops each known issue makes wrong (Runner.COVERS, COVERS in scripts/table.py).
var covers = map[int]string{opSignKnownPub: "KI-2"}

func (i issues) observed(ki string) bool {
	switch ki {
	case "KI-1":
		return i.ki1
	case "KI-2":
		return i.ki2
	case "KI-3":
		return i.ki3
	}
	return false
}

// ---------------------------------------------------------------- context

func readDigests(path string) (map[string]string, string) {
	raw, err := os.ReadFile(path)
	if err != nil {
		die("cannot read " + path)
	}
	m := map[string]string{}
	for _, line := range strings.Split(string(raw), "\n") {
		if f := strings.Fields(line); len(f) == 2 {
			m[f[0]] = f[1]
		}
	}
	h := sha256.Sum256(raw)
	return m, hex.EncodeToString(h[:])
}

func cpuModel() string {
	if runtime.GOOS == "darwin" {
		if o, err := exec.Command("sysctl", "-n", "machdep.cpu.brand_string").Output(); err == nil {
			return strings.TrimSpace(string(o))
		}
	}
	raw, err := os.ReadFile("/proc/cpuinfo")
	if err == nil {
		for _, line := range strings.Split(string(raw), "\n") {
			for _, key := range []string{"model name", "Hardware"} {
				if strings.HasPrefix(line, key) {
					if c := strings.Index(line, ":"); c >= 0 {
						return strings.TrimSpace(line[c+1:])
					}
				}
			}
		}
	}
	return "unknown"
}

// osName is "Linux 6.8.0", "Darwin 24.1.0": as the JVM layer's os.name and os.version.
func osName() string {
	if o, err := exec.Command("uname", "-sr").Output(); err == nil {
		return strings.TrimSpace(string(o))
	}
	return runtime.GOOS
}

// moduleVersions maps each dependency module to "<module> <version>", from the binary's build
// info (go.sum's exact versions; a replaced module names its replacement).
func moduleVersions() map[string]string {
	m := map[string]string{}
	bi, ok := debug.ReadBuildInfo()
	if !ok {
		return m
	}
	for _, d := range bi.Deps {
		v := d.Path + " " + d.Version
		if d.Replace != nil {
			v = d.Path + " " + d.Replace.Version + " (" + d.Replace.Path + ")"
		}
		m[d.Path] = v
	}
	return m
}

func jsonStr(s string) string {
	var sb strings.Builder
	sb.WriteByte('"')
	for _, c := range s {
		if c == '"' || c == '\\' {
			sb.WriteByte('\\')
		}
		sb.WriteRune(c)
	}
	sb.WriteByte('"')
	return sb.String()
}

// ---------------------------------------------------------------- main

func main() {
	if len(os.Args) < 2 || (os.Args[1] != "run" && os.Args[1] != "check") {
		die("usage: bench run|check --vectors FILE --dataset DATA --dataset-digests FILE [--library NAME] [--device TEXT] [--machine dedicated|shared] [--harness ID] [--quick]")
	}
	timed := os.Args[1] == "run"
	var vecPath, dsPath, dsdPath, only, harness string
	device, machine := "unknown", "unknown"
	harness = "unknown"
	quick := false
	args := os.Args[2:]
	for i := 0; i < len(args); i++ {
		a := args[i]
		val := func() string {
			if i+1 >= len(args) {
				die("missing value for " + a)
			}
			i++
			return args[i]
		}
		switch a {
		case "--vectors":
			vecPath = val()
		case "--dataset":
			dsPath = val()
		case "--dataset-digests":
			dsdPath = val()
		case "--library":
			only = val()
		case "--device":
			device = val()
		case "--machine":
			machine = val()
		case "--harness":
			harness = val()
		case "--quick":
			quick = true
		default:
			die("unknown option " + a)
		}
	}
	if vecPath == "" || dsPath == "" || dsdPath == "" {
		die("--vectors, --dataset and --dataset-digests are required")
	}
	derive(backends[0])
	loadDataset(dsPath)
	dsd, _ := readDigests(dsdPath)
	if dsd["file"] != D.sha {
		die("the dataset is not the one its digests file describes")
	}
	vec, vecSha := readDigests(vecPath)

	mods := moduleVersions()
	libsecp256k1Backend.version = "bitcoin-core/secp256k1 " + libsecp256k1Version
	var selected []*backend
	for _, b := range backends {
		if b.module != "" {
			b.version = mods[b.module]
			if b.version == "" {
				b.version = b.module + " unknown"
			}
		}
		if only == "" || only == b.name {
			selected = append(selected, b)
		}
	}
	if len(selected) == 0 {
		die("unknown library " + only)
	}

	var rows []string
	var observed []string
	var unexplained []string
	for _, b := range selected {
		ok, knownPubWrong := check(b, vec, dsd)
		var t [nOps]timing
		for op := 0; op < nOps; op++ {
			if !timed {
				t[op].timed = op != opVerifyBatch && op != opNostrBatch || b.verifyBatch != nil
				continue
			}
			fmt.Fprintf(os.Stderr, "%s %s...\n", b.name, opNames[op])
			runtime.GC()
			t[op] = timeOp(b, op, quick)
		}
		is := probeIssues(b, knownPubWrong)
		for op := 0; op < nOps; op++ {
			if !t[op].timed {
				continue
			}
			ki := ""
			if (op == opVerifyBatch || op == opNostrBatch) && is.ki1 {
				ki = `"KI-1"`
			}
			if op == opSignKnownPub && is.ki2 {
				ki = `"KI-2"`
			}
			rows = append(rows, fmt.Sprintf(`    { "op": "%s", "library": "%s", "binding": "%s", "median_ns": %.1f, "p5_ns": %.1f, "p95_ns": %.1f, "samples": %d, "correct": %t, "known_issues": [%s] }`,
				opNames[op], b.name, b.binding, t[op].median, t[op].p5, t[op].p95, t[op].samples, ok[op], ki))
			if !ok[op] {
				if c, has := covers[op]; has && is.observed(c) {
					fmt.Fprintf(os.Stderr, "%s %s: wrong, as %s says\n", b.name, opNames[op], c)
				} else {
					unexplained = append(unexplained, b.name+" "+opNames[op])
				}
			}
		}
		ki1 := "null"
		if is.ki1Tested {
			ki1 = strconv.FormatBool(is.ki1)
		}
		observed = append(observed, fmt.Sprintf(`    "%s": { "KI-1": %s, "KI-2": %t, "KI-3": %t }`, b.name, ki1, is.ki2, is.ki3))
		fmt.Fprintf(os.Stderr, "%s: KI-1 %s, KI-2 %t, KI-3 %t\n", b.name, ki1, is.ki2, is.ki3)
		if b == backends[0] && (is.ki1 || is.ki2 || is.ki3) {
			unexplained = append(unexplained, "the reference shows a known issue: the probes are wrong")
		}
	}

	if !timed {
		if len(unexplained) > 0 {
			fmt.Fprintf(os.Stderr, "wrong results no known issue explains: %s\n", strings.Join(unexplained, ", "))
			os.Exit(1)
		}
		fmt.Fprintln(os.Stderr, "every library is correct or explained by a known issue")
		return
	}

	var libs []string
	for _, b := range selected {
		libs = append(libs, fmt.Sprintf(`"%s": %s`, b.name, jsonStr(b.version)))
	}
	runtimeDesc := fmt.Sprintf("Go %s (%s, %s/%s), cgo: %s -O2", runtime.Version(), runtime.Compiler, runtime.GOOS, runtime.GOARCH, cgoCompiler)
	o := os.Stdout
	fmt.Fprintf(o, "{\n  \"schema\": 3,\n  \"vectors\": \"v1\",\n  \"vectorsSha256\": \"%s\",\n", vecSha)
	fmt.Fprintf(o, "  \"dataset\": \"nostr-v1\",\n  \"datasetSha256\": \"%s\",\n  \"layer\": \"go\",\n", D.sha)
	fmt.Fprintf(o, "  \"quick\": %t,\n  \"context\": {\n", quick)
	fmt.Fprintf(o, "    \"date\": \"%s\",\n", time.Now().UTC().Format("2006-01-02"))
	fmt.Fprintf(o, "    \"device\": %s,\n    \"cpu\": %s,\n    \"os\": %s,\n", jsonStr(device), jsonStr(cpuModel()), jsonStr(osName()))
	fmt.Fprintf(o, "    \"runtime\": %s,\n    \"machine\": %s,\n    \"harness\": %s,\n", jsonStr(runtimeDesc), jsonStr(machine), jsonStr(harness))
	fmt.Fprintf(o, "    \"libraries\": { %s }\n  },\n", strings.Join(libs, ", "))
	fmt.Fprintf(o, "  \"rows\": [\n%s\n  ],\n", strings.Join(rows, ",\n"))
	fmt.Fprintf(o, "  \"known_issues_observed\": {\n%s\n  }\n}\n", strings.Join(observed, ",\n"))
	if sink == 42 { // keep the sink observable
		fmt.Fprint(os.Stderr, "")
	}
}
