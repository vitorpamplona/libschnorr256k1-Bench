# Shared by the run scripts: argument checks, the harness id and result file names.

# The harness commit, with -dirty when the measuring code differs from it (results/ excluded).
harness_id() {
  echo "$(git rev-parse --short HEAD)$(git diff --quiet HEAD -- . ':!results' || echo "-dirty")"
}

# "Pixel 8 Pro" -> pixel-8-pro
slugify() {
  echo "$1" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-//; s/-$//'
}

require_machine() {
  case "$1" in dedicated|shared) ;; *) echo "--machine dedicated|shared is required (results/SCHEMA.md)" >&2; exit 2 ;; esac
}

# result_file <out> <layer> <device> <quick flag>: results/<layer>/<date>-<device>[-quick].json
result_file() {
  local suffix=""
  [ -z "$4" ] || suffix="-quick"
  mkdir -p "$1/$2"
  echo "$1/$2/$(date -u +%F)-$(slugify "$3")$suffix.json"
}

# Checks out the submodules that are missing, at their pinned commits; leaves one that was moved
# on purpose (to measure another commit) where it is: the libraries' versions in the result say so.
ensure_submodules() {
  if git submodule status --recursive | grep -q '^-'; then
    git submodule update --init --recursive
  fi
}
