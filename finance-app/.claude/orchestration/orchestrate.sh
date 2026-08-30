#!/usr/bin/env bash
# Drives the Spec -> Unit Test -> Dev -> Reviewer -> E2E pipeline for one feature.
# Each stage runs as a separate headless `claude -p` process with its own
# settings.deny scope, so guardrails are enforced by the permission system,
# not by prompt convention.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$REPO_ROOT/finance-app"

CTX=".claude/context"
SETTINGS=".claude/orchestration/settings"
# .claude/** is a hardcoded-protected path: headless Write/Edit/Bash calls are
# denied there regardless of permissions.allow. Agents draft/write here instead;
# the orchestrator (a plain trusted script, not permission-gated) copies
# finished PLAN/REVIEW drafts into $CTX for the permanent, committed record.
PIPELINE="pipeline"
mkdir -p "$PIPELINE"
STATE_FILE="$PIPELINE/WORKFLOW_STATE.json"
RESULT_FILE="$PIPELINE/RESULT.json"
MAX_ATTEMPTS=3

FEATURE="${1:?Usage: orchestrate.sh <FeatureName>}"
PLAN_FILE="$CTX/PLAN_${FEATURE}.md"
REVIEW_FILE="$CTX/REVIEW_${FEATURE}.md"
DRAFT_PLAN_FILE="$PIPELINE/PLAN_${FEATURE}.md"
DRAFT_REVIEW_FILE="$PIPELINE/REVIEW_${FEATURE}.md"
BRANCH="feature/${FEATURE}"

log() { printf '[orchestrator] %s\n' "$*"; }

write_state() {
  jq -n --arg f "$FEATURE" --arg s "$1" --argjson a "${2:-0}" --arg b "$BRANCH" \
    '{feature:$f, stage:$s, attempt:$a, max_attempts:'"$MAX_ATTEMPTS"', branch:$b}' > "$STATE_FILE"
}

commit_stage() {
  git add -A
  if ! git diff --cached --quiet; then
    git commit -q -m "$1"
    log "committed: $1"
  else
    log "no changes to commit for: $1"
  fi
}

result_field() { jq -r ".$1 // empty" "$RESULT_FILE" 2>/dev/null; }

# role, prompt -> runs headless, requires the agent to write $RESULT_FILE
run_agent() {
  local role="$1" prompt="$2"
  rm -f "$RESULT_FILE"
  claude -p "$prompt" \
    --settings "$SETTINGS/${role}.settings.json" \
    --permission-mode acceptEdits \
    --output-format json > "$PIPELINE/.last_${role}.json" || true
  if [[ ! -f "$RESULT_FILE" ]]; then
    log "ERROR: $role exited without writing RESULT.json — see $PIPELINE/.last_${role}.json"
    exit 1
  fi
}

git rev-parse --verify "$BRANCH" >/dev/null 2>&1 && git checkout -q "$BRANCH" || git checkout -q -b "$BRANCH"

# --- Spec ---
REQUEST_FILE="$CTX/REQUEST_${FEATURE}.md"
if [[ ! -f "$PLAN_FILE" ]]; then
  if [[ ! -f "$REQUEST_FILE" ]]; then
    log "Neither $PLAN_FILE nor $REQUEST_FILE exists."
    log "Write the feature requirements in plain English to $REQUEST_FILE, then re-run."
    exit 1
  fi
  write_state "spec"
  spec_prompt=$(cat <<EOF
Follow .claude/rules/spec.md. Feature: $FEATURE.

Requirements (from $REQUEST_FILE):
$(cat "$REQUEST_FILE")

Write the plan to $DRAFT_PLAN_FILE using $CTX/PLAN_template.md as the structure.
When done, write $RESULT_FILE as {"stage":"spec","result":"PASS","summary":"<one line>"}.
EOF
)
  run_agent "spec-agent" "$spec_prompt"
  [[ "$(result_field result)" == "PASS" ]] || { log "spec stage failed: $(result_field summary)"; exit 1; }
  cp "$DRAFT_PLAN_FILE" "$PLAN_FILE"
  commit_stage "spec: draft $PLAN_FILE from $REQUEST_FILE"
else
  log "$PLAN_FILE already exists, skipping spec stage"
fi

# --- Unit Test (write failing tests) ---
write_state "unit_test"
run_agent "unit-test-agent" "Follow .claude/rules/testing.md. Read $PLAN_FILE section 4 (Test Scenarios).
Write unit/MockMvc tests for feature $FEATURE, expected to fail (no production code exists yet).
Run mvn test and confirm the new tests fail for the expected reason, not a compile error.
Write $RESULT_FILE as {\"stage\":\"unit_test\",\"result\":\"PASS|FAIL\",\"summary\":\"<one line, note test IDs written>\"}."
[[ "$(result_field result)" == "PASS" ]] || { log "unit_test stage failed: $(result_field summary)"; exit 1; }
commit_stage "test: add failing tests for $FEATURE"

# --- Dev (retry loop) ---
attempt=0
feedback=""
while true; do
  attempt=$((attempt + 1))
  write_state "dev" "$attempt"
  run_agent "dev-agent" "Follow .claude/rules/dev.md. Read $PLAN_FILE sections 1-3.
Implement production code so all tests for feature $FEATURE pass. Run mvn test to verify.
$feedback
Write $RESULT_FILE as {\"stage\":\"dev\",\"result\":\"PASS|FAIL\",\"summary\":\"<one line>\"}."
  if [[ "$(result_field result)" == "PASS" ]]; then
    commit_stage "feat: implement $FEATURE (attempt $attempt)"
    break
  fi
  if (( attempt >= MAX_ATTEMPTS )); then
    log "dev stage failed after $MAX_ATTEMPTS attempts: $(result_field summary) — escalating to human"
    exit 2
  fi
  feedback="Previous attempt failed: $(result_field summary). Fix the specific failing tests, do not restart from scratch."
  log "dev attempt $attempt failed, retrying"
done

# --- Reviewer (retry loop, bounces back to Dev) ---
round=0
while true; do
  round=$((round + 1))
  write_state "reviewer" "$round"
  run_agent "reviewer-agent" "Follow .claude/rules/reviewer.md. Compare \`git diff main...$BRANCH\` against $PLAN_FILE.
Write your verdict to $DRAFT_REVIEW_FILE.
Write $RESULT_FILE as {\"stage\":\"reviewer\",\"result\":\"PASS|FAIL\",\"summary\":\"APPROVE or REQUEST_CHANGES, one line\"}."
  cp "$DRAFT_REVIEW_FILE" "$REVIEW_FILE"
  if [[ "$(result_field result)" == "PASS" ]]; then
    log "reviewer approved"
    break
  fi
  if (( round >= MAX_ATTEMPTS )); then
    log "reviewer requested changes $MAX_ATTEMPTS times — escalating to human. See $REVIEW_FILE"
    exit 2
  fi
  write_state "dev" "$round"
  run_agent "dev-agent" "Follow .claude/rules/dev.md. Reviewer requested changes, see $REVIEW_FILE.
Address every point, then run mvn test.
Write $RESULT_FILE as {\"stage\":\"dev\",\"result\":\"PASS|FAIL\",\"summary\":\"<one line>\"}."
  commit_stage "fix: address review feedback for $FEATURE (round $round)"
done

# --- E2E ---
write_state "e2e"
run_agent "e2e-agent" "Follow .claude/rules/e2e.md. Read $PLAN_FILE section 5.
Start the backend (mvn spring-boot:run, background) if not already running, then write/run Playwright specs for $FEATURE.
Write $RESULT_FILE as {\"stage\":\"e2e\",\"result\":\"PASS|FAIL\",\"summary\":\"<one line, e.g. 8/8 passed>\"}."
commit_stage "test(e2e): add e2e specs for $FEATURE"
if [[ "$(result_field result)" != "PASS" ]]; then
  log "E2E failed: $(result_field summary) — inspect $PIPELINE/.last_e2e-agent.json; route back to dev or unit-test manually"
  exit 2
fi

write_state "done"
log "Feature $FEATURE complete on branch $BRANCH."
log "Orchestrator does not push or merge — review the branch and merge to main yourself."
