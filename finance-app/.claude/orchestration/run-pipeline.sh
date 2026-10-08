#!/usr/bin/env bash
# Thin pre-flight wrapper around orchestrate.sh.
# Single entry point: run-pipeline <FeatureName>
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$REPO_ROOT/finance-app"

CTX=".claude/context"
FEATURE="${1:-}"

# --- Help / feature discovery ---
if [[ -z "$FEATURE" || "$FEATURE" == "--help" || "$FEATURE" == "-h" ]]; then
  echo "Usage: run-pipeline <FeatureName>"
  echo
  echo "Available features (have a REQUEST or PLAN file):"
  for f in "$CTX"/REQUEST_*.md "$CTX"/PLAN_*.md; do
    [[ -f "$f" ]] || continue
    base="$(basename "$f")"
    name="${base#REQUEST_}"; name="${name#PLAN_}"; name="${name%.md}"
    echo "  $name"
  done | sort -u
  exit 0
fi

# --- Pre-flight: require REQUEST or PLAN file ---
if [[ ! -f "$CTX/REQUEST_${FEATURE}.md" && ! -f "$CTX/PLAN_${FEATURE}.md" ]]; then
  echo "ERROR: no REQUEST_${FEATURE}.md or PLAN_${FEATURE}.md found in $CTX/" >&2
  echo "Run the requirements-agent first to create a request file." >&2
  exit 1
fi

# --- Pre-flight: warn on uncommitted changes ---
if git status --porcelain | grep -qv "^\?\?"; then
  echo "WARNING: uncommitted changes detected in the working tree."
  echo "The pipeline will commit on branch feature/$FEATURE — unrelated changes may be included."
  read -rp "Continue? (y/N) " reply
  [[ "$reply" =~ ^[Yy]$ ]] || { echo "Aborted."; exit 1; }
fi

# --- Pre-flight: check required tools ---
for cmd in jq mvn npx; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "ERROR: required tool '$cmd' not found in PATH." >&2; exit 1; }
done

echo "[run-pipeline] Pre-flight passed. Handing off to orchestrate.sh for feature: $FEATURE"
exec .claude/orchestration/orchestrate.sh "$FEATURE"
