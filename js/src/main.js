/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * The node and bun layers: the BIP-340 libraries JavaScript apps use, checked and timed by the
 * twin of c/main.c. The runtime running this file is the layer (`node` or `bun`).
 *
 *   main.js run   --vectors F --dataset F --dataset-digests F [--library NAME] [--device TEXT]
 *                 [--machine dedicated|shared] [--harness COMMIT] [--quick]
 *       check every library, time every operation, print a result (results/SCHEMA.md) as JSON.
 *       Each library runs in its own process (`measure`), so no library's JIT profile, inline
 *       caches or heap reach another's numbers.
 *   main.js check --vectors F --dataset F --dataset-digests F [--library NAME]
 *       the checks and the known-issue probes without the timing; exits 1 on a wrong result no
 *       observed known issue explains.
 */
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import os from 'node:os';
import { deriveVectors, parseDigests, sha256, toHex } from './vectors.js';
import { LIBRARIES, REFERENCE } from './impls.js';
import { Runner, loadDataset, sink } from './runner.js';

const SCHEMA = 3;
const here = dirname(fileURLToPath(import.meta.url));
const log = (s) => process.stderr.write(s + '\n');
const die = (s) => { log(`error: ${s}`); process.exit(2); };

function parseArgs(argv) {
  const a = { quick: false, device: 'unknown', machine: 'unknown', harness: 'unknown' };
  for (let i = 0; i < argv.length; i++) {
    const k = argv[i];
    const val = () => (i + 1 < argv.length ? argv[++i] : die(`${k} needs a value`));
    if (k === '--vectors') a.vectors = val();
    else if (k === '--dataset') a.dataset = val();
    else if (k === '--dataset-digests') a.datasetDigests = val();
    else if (k === '--library') a.library = val();
    else if (k === '--device') a.device = val();
    else if (k === '--machine') a.machine = val();
    else if (k === '--harness') a.harness = val();
    else if (k === '--quick') a.quick = true;
    else die(`unknown option ${k}`);
  }
  if (!a.vectors) die('--vectors FILE is required');
  if (!a.dataset || !a.datasetDigests) die('--dataset FILE and --dataset-digests FILE are required');
  if (a.library && !(a.library in LIBRARIES)) die(`unknown library ${a.library} (${Object.keys(LIBRARIES).join(', ')})`);
  return a;
}

export const layer = process.versions.bun ? 'bun' : 'node';

function runtime() {
  if (process.versions.bun) return `Bun ${process.versions.bun} (JavaScriptCore${process.versions.webkit ? ' ' + process.versions.webkit : ''})`;
  return `Node.js ${process.version} (V8 ${process.versions.v8})`;
}

function osName() {
  if (process.platform === 'linux') return `Linux ${os.release()}`;
  if (process.platform === 'darwin') return `macOS (Darwin ${os.release()})`;
  return `${os.type()} ${os.release()}`;
}

/** "<package> <version>" from js/package-lock.json, the exact version installed. */
function libraryVersion(pkg) {
  const lock = JSON.parse(readFileSync(join(here, '..', 'package-lock.json'), 'utf8'));
  const e = lock.packages[`node_modules/${pkg}`];
  if (!e) die(`${pkg} is not in package-lock.json`);
  return `${pkg} ${e.version}`;
}

async function setup(a) {
  const vecText = readFileSync(a.vectors, 'utf8');
  const dsText = readFileSync(a.datasetDigests, 'utf8');
  const ds = loadDataset(a.dataset);
  const dsDigests = parseDigests(dsText);
  if (dsDigests.get('file') !== ds.sha256Hex) die('the dataset is not the one its digests file describes');
  const ref = await LIBRARIES[REFERENCE]();
  const v = deriveVectors(ref);
  const runner = new Runner({ ref, v, ds, vecDigests: parseDigests(vecText), dsDigests, quick: a.quick, log });
  return { runner, ref, ds, vecSha: toHex(sha256(new TextEncoder().encode(vecText))) };
}

async function load(name, ref) {
  return name === REFERENCE ? ref : LIBRARIES[name]();
}

const fmt = (x) => x.toFixed(1);
const js = (s) => JSON.stringify(s);

async function main() {
  const [cmd, ...rest] = process.argv.slice(2);
  const a = parseArgs(rest);
  const names = a.library ? [a.library] : Object.keys(LIBRARIES);

  if (cmd === 'check') {
    const { runner, ref } = await setup(a);
    const failures = [];
    for (const name of names) {
      const b = await load(name, ref);
      const c = runner.check(b);
      log(`${b.library} (${b.binding}): known issues ${js(c.knownIssues)}`);
      failures.push(...c.unexplained);
    }
    if (failures.length) {
      log(`${layer}: wrong results no known issue explains: ${failures.join(', ')}`);
      process.exit(1);
    }
    log(`${layer}: every library matches the vectors and the dataset`);
    return;
  }

  if (cmd === 'measure') { // one library, in this process
    if (!a.library) die('measure needs --library');
    const { runner, ref } = await setup(a);
    const b = await load(a.library, ref);
    const m = runner.measure(b);
    process.stdout.write(JSON.stringify({ library: b.library, pkg: b.pkg, ...m, sink: sink.value }) + '\n');
    return;
  }

  if (cmd !== 'run') die('usage: main.js run|check --vectors F --dataset F --dataset-digests F [options]');

  const vecSha = toHex(sha256(readFileSync(a.vectors)));
  const dsSha = toHex(sha256(readFileSync(a.dataset)));
  const parts = [];
  for (const name of names) {
    const args = [fileURLToPath(import.meta.url), 'measure', ...rest.filter((x, i, all) => x !== '--library' && all[i - 1] !== '--library'), '--library', name];
    const r = spawnSync(process.execPath, args, { stdio: ['ignore', 'pipe', 'inherit'], maxBuffer: 64 << 20 });
    if (r.status !== 0) die(`${name}: the measuring process failed (exit ${r.status}${r.signal ? ', ' + r.signal : ''})`);
    parts.push(JSON.parse(r.stdout.toString()));
  }

  const date = new Date().toISOString().slice(0, 10);
  const ctx = [
    ['date', date], ['device', a.device], ['cpu', os.cpus()[0]?.model?.trim() || 'unknown'], ['os', osName()],
    ['runtime', runtime()], ['machine', a.machine], ['harness', a.harness],
  ];
  const libs = parts.map((p) => `${js(p.library)}: ${js(libraryVersion(p.pkg))}`).join(', ');
  const rows = parts.flatMap((p) => p.rows).map((r) =>
    `    { "op": ${js(r.op)}, "library": ${js(r.library)}, "binding": ${js(r.binding)}, "median_ns": ${fmt(r.median_ns)}, ` +
    `"p5_ns": ${fmt(r.p5_ns)}, "p95_ns": ${fmt(r.p95_ns)}, "samples": ${r.samples}, "correct": ${r.correct}, ` +
    `"known_issues": [${r.known_issues.map(js).join(', ')}] }`);
  const issues = parts.map((p) =>
    `    ${js(p.library)}: { ${Object.entries(p.knownIssues).map(([k, x]) => `${js(k)}: ${x === null ? 'null' : x}`).join(', ')} }`);
  process.stdout.write(
    `{\n  "schema": ${SCHEMA},\n  "vectors": "v1",\n  "vectorsSha256": "${vecSha}",\n` +
    `  "dataset": "nostr-v1",\n  "datasetSha256": "${dsSha}",\n  "layer": "${layer}",\n  "quick": ${a.quick},\n` +
    `  "context": {\n${ctx.map(([k, x]) => `    ${js(k)}: ${js(x)}`).join(',\n')},\n    "libraries": { ${libs} }\n  },\n` +
    `  "rows": [\n${rows.join(',\n')}\n  ],\n  "known_issues_observed": {\n${issues.join(',\n')}\n  }\n}\n`);
}

await main();
