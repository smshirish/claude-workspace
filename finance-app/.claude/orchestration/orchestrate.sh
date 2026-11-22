#!/usr/bin/env bash
# Drives the Spec -> Unit Test -> Dev -> Reviewer -> E2E pipeline for one feature.
# Each stage runs as a separate headless `claude -p` process with its own
# settings.deny scope, so guardrails are enforced by the permission system,
# not by prompt convention.
#
# Usage: orchestrate.sh <FeatureName> [--force|-f]
# Env: MAX_TURNS=<n> (override all per-role turn caps), AGENT_TIMEOUT=<dur> (default 30m)
# Exit codes: 0 done, 1 stage failed, 2 escalate to human, 3 checkpoint mismatch,
#             4 infrastructure failure (auth/network/rate limit) — retry later
# Before each stage, WORKFLOW_STATE.json (if present) is checked to confirm
# this feature/branch is at or just before that stage — this catches stale
# state left over from an escalated run (exit 2) or a state file for a
# different feature. --force/-f skips that check, for deliberately re-running
# or skipping ahead to a single stage (e.g. resuming manually after fixing an
# issue outside the stages the automated retry loop can reach).
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
# Dev attempts per review-fix round (worst case: 3 rounds x 2 = 6 extra dev calls).
MAX_FIX_ATTEMPTS=2

# Model assignment per pipeline role.
# reviewer-agent uses Haiku (read-only diff scan — no code generation needed).
# All other roles use Sonnet (code writing, test authoring, planning).
declare -A AGENT_MODEL=(
  [spec-agent]="claude-sonnet-4-6"
  [unit-test-agent]="claude-sonnet-4-6"
  [dev-agent]="claude-sonnet-4-6"
  [reviewer-agent]="claude-haiku-4-5-20251001"
  [e2e-agent]="claude-sonnet-4-6"
)

# Per-role turn cap (each tool call = one turn). Override all with MAX_TURNS=<n>.
declare -A AGENT_MAX_TURNS=(
  [spec-agent]=20
  [unit-test-agent]=30
  [dev-agent]=40
  [reviewer-agent]=15
  [e2e-agent]=40
)
# Wall-clock cap per agent call (coreutils `timeout` syntax).
AGENT_TIMEOUT="${AGENT_TIMEOUT:-30m}"

FORCE=0
ARGS=()
for arg in "$@"; do
  case "$arg" in
    --force|-f) FORCE=1 ;;
    *) ARGS+=("$arg") ;;
  esac
done
FEATURE="${ARGS[0]:?Usage: orchestrate.sh <FeatureName> [--force]}"
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

# Canonical pipeline order, used to validate WORKFLOW_STATE.json before each
# stage actually runs, so a stale/foreign state file can't cause a stage to
# silently redo already-completed work or skip required prior work.
STAGE_ORDER=(spec unit_test dev reviewer e2e done)
stage_index() {
  local s="$1" i
  for i in "${!STAGE_ORDER[@]}"; do
    [[ "${STAGE_ORDER[$i]}" == "$s" ]] && { echo "$i"; return 0; }
  done
  echo "-1"
}

# Verify WORKFLOW_STATE.json (if present) agrees this feature/branch is at or
# just before the stage we're about to run. --force / -f bypasses this check
# entirely, for deliberately re-running or skipping ahead to a single stage.
assert_checkpoint() {
  local expected="$1"
  if (( FORCE )); then
    log "checkpoint: --force given, skipping state validation for stage '$expected'"
    return 0
  fi
  [[ -f "$STATE_FILE" ]] || return 0

  local state_feature state_branch state_stage
  state_feature=$(jq -r '.feature // empty' "$STATE_FILE")
  state_branch=$(jq -r '.branch // empty' "$STATE_FILE")
  state_stage=$(jq -r '.stage // empty' "$STATE_FILE")

  if [[ -n "$state_feature" && "$state_feature" != "$FEATURE" ]]; then
    log "ERROR: $STATE_FILE belongs to feature '$state_feature', not '$FEATURE'. Refusing to run stage '$expected'. Re-run with --force to override."
    exit 3
  fi
  if [[ -n "$state_branch" && "$state_branch" != "$BRANCH" ]]; then
    log "ERROR: $STATE_FILE recorded branch '$state_branch', not '$BRANCH'. Refusing to run stage '$expected'. Re-run with --force to override."
    exit 3
  fi

  local cur_idx exp_idx
  cur_idx=$(stage_index "$state_stage")
  exp_idx=$(stage_index "$expected")
  if (( cur_idx == -1 || exp_idx == -1 )); then
    return 0
  fi
  if (( cur_idx > exp_idx )); then
    log "ERROR: $STATE_FILE shows stage '$state_stage', which is past '$expected'. Refusing to re-run an earlier stage after a later one already ran. Re-run with --force to override."
    exit 3
  fi
  if (( cur_idx < exp_idx - 1 )); then
    log "ERROR: $STATE_FILE shows stage '$state_stage', which is more than one stage behind '$expected'. Refusing to skip required intermediate stage(s). Re-run with --force to override."
    exit 3
  fi
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

COST_FILE="$CTX/COST_${FEATURE}.md"

# appends one row per agent call immediately after it returns, since a retry's
# `> $PIPELINE/.last_<role>.json` redirection truncates the snapshot the instant
# the next call for that role launches.
log_cost() {
  local role="$1" attempt="$2" src="$3" outcome="${4:-}"
  if [[ ! -f "$COST_FILE" ]]; then
    { echo "# Token Cost & Timing Log: $FEATURE"
      echo
      echo "| Role | Attempt | Model(s) | Duration (ms) | Input Tok | Output Tok | Cache Read Tok | Cost (USD) | Turns | CLI Outcome |"
      echo "|---|---|---|---|---|---|---|---|---|---|"
    } > "$COST_FILE"
  fi
  local models duration in_tok out_tok cache_tok cost turns
  turns=$(jq -r '.num_turns // "?"' "$src" 2>/dev/null)
  models=$(jq -r '(.modelUsage // {}) | keys | join(",")' "$src" 2>/dev/null)
  duration=$(jq -r '.duration_ms // 0' "$src" 2>/dev/null)
  in_tok=$(jq -r '.usage.input_tokens // 0' "$src" 2>/dev/null)
  out_tok=$(jq -r '.usage.output_tokens // 0' "$src" 2>/dev/null)
  cache_tok=$(jq -r '.usage.cache_read_input_tokens // 0' "$src" 2>/dev/null)
  cost=$(jq -r '.total_cost_usd // 0' "$src" 2>/dev/null)
  printf '| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n' \
    "$role" "$attempt" "${models:-?}" "${duration:-0}" "${in_tok:-0}" "${out_tok:-0}" "${cache_tok:-0}" "${cost:-0}" "${turns:-?}" "${outcome:-?}" >> "$COST_FILE"
}

append_cost_total() {
  [[ -f "$COST_FILE" ]] || return 0
  tail -1 "$COST_FILE" | grep -q '^\*\*Total cost' && return 0
  local total
  total=$(awk -F'|' '{v=$9; gsub(/^[ \t]+|[ \t]+$/,"",v); if (v ~ /^[0-9.]+$/) sum+=v} END{printf "%.6f", sum+0}' "$COST_FILE")
  printf '\n**Total cost: $%s USD**\n' "$total" >> "$COST_FILE"
}
trap append_cost_total EXIT

# Classifies a finished claude -p call. Echoes "<class> <reason>" where class is:
#   ok     — CLI completed normally
#   agent  — agent-side problem (turn cap, timeout, execution error)
#   infra  — CLI/API problem (auth, network, rate limit, crash/no JSON); retry later
classify_cli() {
  local rc="$1" out="$2" is_err subtype text
  if (( rc == 124 )); then echo "agent timeout_after_${AGENT_TIMEOUT}"; return; fi
  if ! jq -e . "$out" >/dev/null 2>&1; then echo "infra cli_rc=${rc}_no_json_output"; return; fi
  is_err=$(jq -r '.is_error // false' "$out")
  subtype=$(jq -r '.subtype // "unknown"' "$out")
  text=$(jq -r '.result // ""' "$out" | head -c 300)
  if [[ "$subtype" == "error_max_turns" ]]; then echo "agent error_max_turns"; return; fi
  if [[ "$is_err" == "true" ]]; then
    if grep -qiE 'api error|authentication|unauthori[sz]ed|rate.?limit|overloaded|529|ECONN|ETIMEDOUT|network' <<<"$text"; then
      echo "infra ${subtype}: ${text//$'\n'/ }"
    else
      echo "agent ${subtype}"
    fi
    return
  fi
  if (( rc != 0 )); then echo "infra cli_rc=${rc}"; return; fi
  echo "ok ${subtype}"
}

# role, prompt, attempt -> runs headless, requires the agent to write $RESULT_FILE
run_agent() {
  local role="$1" prompt="$2" attempt="${3:-1}"
  local model="${AGENT_MODEL[$role]:-claude-sonnet-4-6}"
  local turns="${MAX_TURNS:-${AGENT_MAX_TURNS[$role]:-30}}"
  local out="$PIPELINE/.last_${role}.json" rc=0 cls cls_kind cls_reason
  rm -f "$RESULT_FILE"
  timeout "$AGENT_TIMEOUT" claude -p "$prompt" \
    --model "$model" \
    --settings "$SETTINGS/${role}.settings.json" \
    --permission-mode acceptEdits \
    --max-turns "$turns" \
    --output-format json > "$out" || rc=$?
  cls=$(classify_cli "$rc" "$out")
  cls_kind="${cls%% *}"; cls_reason="${cls#* }"
  log_cost "$role" "$attempt" "$out" "$cls_kind: $cls_reason"
  if [[ "$cls_kind" == "infra" ]]; then
    log "ERROR: $role — infrastructure failure ($cls_reason). Nothing wrong with the agent's work; re-run later (use --force to resume this stage)."
    exit 4
  fi
  if [[ ! -f "$RESULT_FILE" ]]; then
    log "ERROR: $role exited without writing RESULT.json ($cls_kind: $cls_reason, turns cap=$turns) — see $out"
    exit 1
  fi
  [[ "$cls_kind" == "agent" ]] && log "WARN: $role reported a result despite CLI outcome '$cls_reason' — trusting RESULT.json"
  return 0
}

git rev-parse --verify "$BRANCH" >/dev/null 2>&1 && git checkout -q "$BRANCH" || git checkout -q -b "$BRANCH"

# --- Spec ---
# Agents write REQUEST files to $PIPELINE (headless writes to .claude/** are
# blocked); the orchestrator archives a copy into $CTX after a successful spec.
REQUEST_FILE="$PIPELINE/REQUEST_${FEATURE}.md"
ARCHIVED_REQUEST_FILE="$CTX/REQUEST_${FEATURE}.md"
if [[ ! -f "$PLAN_FILE" ]]; then
  if [[ ! -f "$REQUEST_FILE" ]]; then
    log "Neither $PLAN_FILE nor $REQUEST_FILE exists."
    log "Write the feature requirements in plain English to $REQUEST_FILE, then re-run."
    exit 1
  fi
  assert_checkpoint "spec"
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
  cp "$REQUEST_FILE" "$ARCHIVED_REQUEST_FILE"
  commit_stage "spec: draft $PLAN_FILE from $REQUEST_FILE"
else
  log "$PLAN_FILE already exists, skipping spec stage"
fi

# --- Unit Test (write failing tests) ---
assert_checkpoint "unit_test"
write_state "unit_test"
run_agent "unit-test-agent" "Follow .claude/rules/testing.md. Read $PLAN_FILE section 4 (Test Scenarios).
Write unit/MockMvc tests for feature $FEATURE, expected to fail (no production code exists yet).
Run mvn test and confirm the new tests fail for the expected reason, not a compile error.
Write $RESULT_FILE as {\"stage\":\"unit_test\",\"result\":\"PASS|FAIL\",\"summary\":\"<one line, note test IDs written>\"}."
[[ "$(result_field result)" == "PASS" ]] || { log "unit_test stage failed: $(result_field summary)"; exit 1; }
commit_stage "test: add failing tests for $FEATURE"

# Runs dev-agent until it reports PASS, up to <max> attempts. Commits on PASS;
# exits 2 (escalate to human) on exhaustion. Shared by the main Dev stage and
# the review-fix loop so the reviewer only ever sees green code.
#   run_dev_until_green <base_prompt> <commit_msg> <max_attempts> <label>
run_dev_until_green() {
  local base_prompt="$1" commit_msg="$2" max="$3" label="$4"
  local attempt=0 feedback=""
  while true; do
    attempt=$((attempt + 1))
    write_state "dev" "$attempt"
    run_agent "dev-agent" "$base_prompt
$feedback
Write $RESULT_FILE as {\"stage\":\"dev\",\"result\":\"PASS|FAIL\",\"summary\":\"<one line>\"}." "${label}${attempt}"
    if [[ "$(result_field result)" == "PASS" ]]; then
      commit_stage "$commit_msg (attempt $attempt)"
      return 0
    fi
    if (( attempt >= max )); then
      log "dev (${label:-main}) failed after $max attempts: $(result_field summary) — escalating to human"
      exit 2
    fi
    feedback="Previous attempt failed: $(result_field summary). Fix the specific failing tests, do not restart from scratch."
    log "dev (${label:-main}) attempt $attempt failed, retrying"
  done
}

# --- Dev (retry loop) ---
assert_checkpoint "dev"
run_dev_until_green "Follow .claude/rules/dev.md. Read $PLAN_FILE sections 1-3.
Implement production code so all tests for feature $FEATURE pass. Run mvn test to verify." \
  "feat: implement $FEATURE" "$MAX_ATTEMPTS" ""

# --- Reviewer (retry loop: REQUEST_CHANGES -> encode as tests -> Dev fixes) ---
assert_checkpoint "reviewer"
round=0
while true; do
  round=$((round + 1))
  write_state "reviewer" "$round"
  run_agent "reviewer-agent" "Follow .claude/rules/reviewer.md. Compare \`git diff main...$BRANCH\` against $PLAN_FILE.
Write your verdict to $DRAFT_REVIEW_FILE.
Write $RESULT_FILE as {\"stage\":\"reviewer\",\"result\":\"PASS|FAIL\",\"summary\":\"APPROVE or REQUEST_CHANGES, one line\"}." "$round"
  cp "$DRAFT_REVIEW_FILE" "$REVIEW_FILE"
  commit_stage "review: round $round verdict for $FEATURE ($(result_field summary))"
  if [[ "$(result_field result)" == "PASS" ]]; then
    log "reviewer approved"
    break
  fi
  if (( round >= MAX_ATTEMPTS )); then
    log "reviewer requested changes $MAX_ATTEMPTS times — escalating to human. See $REVIEW_FILE"
    exit 2
  fi
  write_state "unit_test" "$round"
  run_agent "unit-test-agent" "Follow .claude/rules/testing.md. Read $REVIEW_FILE.
For every blocking [CRITICAL]/[HIGH] issue describing a correctness or behavior gap, add or strengthen tests in src/test/java that encode the correct expected behavior. Do not modify src/main/java. mvn test may legitimately fail against the current (buggy) implementation — that is expected.
Write $RESULT_FILE as {\"stage\":\"unit_test\",\"result\":\"PASS|FAIL\",\"summary\":\"<one line, note test IDs added/changed>\"}." "$round"
  if [[ "$(result_field result)" != "PASS" ]]; then
    log "unit_test (review round $round) failed: $(result_field summary) — likely a plan/harness problem, stopping"
    exit 1
  fi
  commit_stage "test: encode review feedback for $FEATURE (round $round)"
  run_dev_until_green "Follow .claude/rules/dev.md. Reviewer requested changes, see $REVIEW_FILE.
Address every point, then run mvn test." \
    "fix: address review feedback for $FEATURE (round $round)" "$MAX_FIX_ATTEMPTS" "r${round}."
done

# --- E2E ---
assert_checkpoint "e2e"
write_state "e2e"
run_agent "e2e-agent" "Follow .claude/rules/e2e.md. Read $PLAN_FILE section 5.
Start the backend (bin/start.sh, background) if not already running, then write/run Playwright specs for $FEATURE (run \`source bin/env.sh\` first so ADMIN_PASSWORD is set).
Write $RESULT_FILE as {\"stage\":\"e2e\",\"result\":\"PASS|FAIL\",\"summary\":\"<one line, e.g. 8/8 passed>\"}."
if [[ "$(result_field result)" != "PASS" ]]; then
  commit_stage "test(e2e): add e2e specs for $FEATURE (FAILING: $(result_field summary))"
  log "E2E failed: $(result_field summary) — inspect $PIPELINE/.last_e2e-agent.json; route back to dev or unit-test manually"
  exit 2
fi
commit_stage "test(e2e): add e2e specs for $FEATURE"

write_state "done"
log "Feature $FEATURE complete on branch $BRANCH."
log "Orchestrator does not push or merge — review the branch and merge to main yourself."
