//! Records, for the result's context: each library's exact version from Cargo.lock, the rustc
//! that builds the harness, and this checkout's commit (the default `context.harness`).
use std::env;
use std::fs;
use std::process::Command;

fn main() {
    println!("cargo:rerun-if-changed=Cargo.lock");
    println!("cargo:rerun-if-changed=build.rs");
    let lock = parse_lock(&fs::read_to_string("Cargo.lock").unwrap_or_default());
    // Each library by its path from this package, as Cargo.lock lists several versions of some
    // crates (the optional secp256k1 compat dependencies of secp256kfun) that are never built.
    for path in [
        &["secp256k1"][..],
        &["secp256k1", "secp256k1-sys"],
        &["k256"],
        &["schnorr_fun"],
        &["schnorr_fun", "secp256kfun"],
    ] {
        let version = resolve(&lock, path).unwrap_or_else(|| "unknown".into());
        let name = path.last().unwrap().replace('-', "_").to_uppercase();
        println!("cargo:rustc-env=LOCKED_{name}={version}");
    }

    let rustc = env::var("RUSTC").unwrap_or_else(|_| "rustc".into());
    let rustc_version = Command::new(rustc)
        .arg("-V")
        .output()
        .ok()
        .and_then(|o| String::from_utf8(o.stdout).ok())
        .map(|s| s.trim().to_string())
        .unwrap_or_else(|| "rustc unknown".into());
    println!("cargo:rustc-env=BENCH_RUSTC_VERSION={rustc_version}");

    let profile = env::var("PROFILE").unwrap_or_default();
    let opt = env::var("OPT_LEVEL").unwrap_or_default();
    let arch = env::var("CARGO_CFG_TARGET_ARCH").unwrap_or_default();
    println!("cargo:rustc-env=BENCH_BUILD={arch}, {profile} profile, opt-level {opt}");

    let harness = Command::new("git")
        .args(["rev-parse", "--short", "HEAD"])
        .output()
        .ok()
        .filter(|o| o.status.success())
        .and_then(|o| String::from_utf8(o.stdout).ok())
        .map(|s| s.trim().to_string())
        .unwrap_or_else(|| "unknown".into());
    println!("cargo:rustc-env=BENCH_HARNESS_VERSION={harness}");
}

struct Package {
    name: String,
    version: String,
    deps: Vec<String>,
}

fn parse_lock(lock: &str) -> Vec<Package> {
    let mut out = Vec::new();
    for block in lock.split("[[package]]").skip(1) {
        let field =
            |key: &str| block.lines().find_map(|l| l.trim().strip_prefix(key)).map(|v| v.trim_matches('"').to_string());
        let (Some(name), Some(version)) = (field("name = "), field("version = ")) else { continue };
        let deps = match block.split_once("dependencies = [") {
            Some((_, rest)) => rest
                .split(']')
                .next()
                .unwrap_or("")
                .lines()
                .map(|l| l.trim().trim_end_matches(',').trim_matches('"').to_string())
                .filter(|l| !l.is_empty())
                .collect(),
            None => Vec::new(),
        };
        out.push(Package { name, version, deps });
    }
    out
}

/// The version of the package reached from this one through `path` (dependency names).
fn resolve(lock: &[Package], path: &[&str]) -> Option<String> {
    let me = env::var("CARGO_PKG_NAME").ok()?;
    let mut cur = lock.iter().find(|p| p.name == me)?;
    for name in path {
        // a dependency entry is "name" (one version in the lock) or "name version"
        let entry = cur.deps.iter().find(|d| d.split(' ').next() == Some(name))?;
        let pinned = entry.split(' ').nth(1);
        let candidates: Vec<&Package> =
            lock.iter().filter(|p| p.name == *name && pinned.is_none_or(|v| p.version == v)).collect();
        if candidates.len() != 1 {
            return None;
        }
        cur = candidates[0];
    }
    Some(cur.version.clone())
}
