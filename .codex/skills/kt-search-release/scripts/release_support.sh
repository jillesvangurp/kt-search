#!/usr/bin/env bash
set -euo pipefail

die() {
  echo "ERROR: $*" >&2
  exit 1
}

info() {
  echo "[release-support] $*"
}

usage() {
  cat <<'EOF'
usage:
  release_support.sh check <version>
  release_support.sh package <version> [output-dir]
EOF
}

[[ $# -ge 2 ]] || {
  usage
  exit 1
}

MODE="$1"
VERSION="$2"
OUT_DIR="${3:-/tmp/ktsearch-assets}"
ROOT_DIR="$(git rev-parse --show-toplevel)"
STAGE_DIR="/tmp/ktsearch-release-${VERSION}"
BRANCH="master"

echo "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' \
  || die "version must look like x.y.z"

command -v git >/dev/null || die "git is required"
command -v gh >/dev/null || die "gh is required"
command -v python3 >/dev/null || die "python3 is required"

check_release_readiness() {
  git rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || die "not inside a git repository"

  [[ -z "$(git status --porcelain)" ]] || die "git worktree is not clean"

  CURRENT_BRANCH="$(git branch --show-current)"
  [[ "$CURRENT_BRANCH" == "$BRANCH" ]] \
    || die "release must be cut from ${BRANCH}; current branch is ${CURRENT_BRANCH}"

  info "fetching origin state"
  git fetch origin --tags --quiet

  LOCAL_HEAD="$(git rev-parse HEAD)"
  REMOTE_HEAD="$(git rev-parse "origin/${BRANCH}")"
  [[ "$LOCAL_HEAD" == "$REMOTE_HEAD" ]] \
    || die "HEAD is not identical to origin/${BRANCH}; push or sync first"

  git rev-parse -q --verify "refs/tags/${VERSION}" >/dev/null \
    && die "tag ${VERSION} already exists locally"

  git ls-remote --exit-code --tags origin "refs/tags/${VERSION}" >/dev/null 2>&1 \
    && die "tag ${VERSION} already exists on origin"

  gh auth status >/dev/null 2>&1 || die "gh is not authenticated"

  REPO_SLUG="$(
    git remote get-url origin |
    sed -E 's#^git@github.com:##; s#^https://github.com/##; s#\.git$##'
  )"
  [[ -n "$REPO_SLUG" ]] || die "could not determine GitHub repo slug"

  if gh release view "$VERSION" -R "$REPO_SLUG" >/dev/null 2>&1; then
    die "GitHub release ${VERSION} already exists"
  fi

  info "checking GitHub Actions for ${LOCAL_HEAD}"
  RUNS_JSON="$(gh run list \
    -R "$REPO_SLUG" \
    --branch "$BRANCH" \
    --limit 50 \
    --json databaseId,headSha,status,conclusion,name,workflowName,url)"

  python3 - "$LOCAL_HEAD" <<'PY' <<<"$RUNS_JSON"
import json
import sys

head = sys.argv[1]
runs = json.load(sys.stdin)
matching = [r for r in runs if r.get("headSha") == head]
if not matching:
    print("ERROR: no GitHub Actions runs found for HEAD", file=sys.stderr)
    sys.exit(1)

bad = [
    r for r in matching
    if r.get("status") != "completed" or r.get("conclusion") != "success"
]
if bad:
    print("ERROR: GitHub Actions is not green for HEAD", file=sys.stderr)
    for run in bad:
        name = run.get("workflowName") or run.get("name") or "unknown"
        status = run.get("status")
        conclusion = run.get("conclusion")
        url = run.get("url")
        print(f" - {name}: status={status} conclusion={conclusion} {url}",
              file=sys.stderr)
    sys.exit(1)

print(f"[release-support] verified {len(matching)} successful run(s) for HEAD")
PY

  info "release readiness checks passed for ${VERSION}"
}

package_cli_assets() {
  command -v tar >/dev/null || die "tar is required"
  command -v shasum >/dev/null || die "shasum is required"

  mkdir -p "$OUT_DIR"
  rm -rf "$STAGE_DIR"
  mkdir -p \
    "$STAGE_DIR"/darwin-arm64 \
    "$STAGE_DIR"/darwin-x64 \
    "$STAGE_DIR"/linux-x64

  copy_target() {
    local src="$1"
    local target_dir="$2"

    [[ -f "$src" ]] || die "missing binary: $src"
    cp "$src" "$target_dir/ktsearch"
    cp "$ROOT_DIR/ktsearch-cli/install.sh" "$target_dir/"
    cp "$ROOT_DIR/ktsearch-cli/uninstall.sh" "$target_dir/"
    cp "$ROOT_DIR/ktsearch-cli/alias-and-completions.sh" "$target_dir/"
    chmod +x "$target_dir/ktsearch"
  }

  copy_target \
    "$ROOT_DIR/ktsearch-cli/build/bin/macosArm64/releaseExecutable/ktsearch.kexe" \
    "$STAGE_DIR/darwin-arm64"
  copy_target \
    "$ROOT_DIR/ktsearch-cli/build/bin/macosX64/releaseExecutable/ktsearch.kexe" \
    "$STAGE_DIR/darwin-x64"
  copy_target \
    "$ROOT_DIR/ktsearch-cli/build/bin/linuxX64/releaseExecutable/ktsearch.kexe" \
    "$STAGE_DIR/linux-x64"

  tar -C "$STAGE_DIR/darwin-arm64" -czf \
    "$OUT_DIR/ktsearch-${VERSION}-darwin-arm64.tar.gz" .
  tar -C "$STAGE_DIR/darwin-x64" -czf \
    "$OUT_DIR/ktsearch-${VERSION}-darwin-x64.tar.gz" .
  tar -C "$STAGE_DIR/linux-x64" -czf \
    "$OUT_DIR/ktsearch-${VERSION}-linux-x64.tar.gz" .

  (
    cd "$OUT_DIR"
    shasum -a 256 \
      "ktsearch-${VERSION}-darwin-arm64.tar.gz" \
      "ktsearch-${VERSION}-darwin-x64.tar.gz" \
      "ktsearch-${VERSION}-linux-x64.tar.gz" \
      > checksums.txt
  )

  info "packaged release assets in ${OUT_DIR}"
}

case "$MODE" in
  check)
    [[ $# -eq 2 ]] || die "check mode only accepts <version>"
    check_release_readiness
    ;;
  package)
    [[ $# -ge 2 && $# -le 3 ]] || die "package mode accepts <version> [output-dir]"
    package_cli_assets
    ;;
  *)
    usage
    exit 1
    ;;
esac
