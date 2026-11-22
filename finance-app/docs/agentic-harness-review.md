# Agentic Harness Review

**Branch reviewed:** `claude-101` @ `cf95e0b`
**Date:** 2026-11-22
**Scope:** `.claude/` (repo root and `finance-app/`), `orchestrate.sh`, per-role settings, rules, agents, context files.

---

## Summary

The pipeline design is solid. It runs each stage as an isolated headless `claude -p` process, and guardrails come from `settings.deny`, not from wording in the prompt. It is test-first, retries Dev, enforces stage order through checkpoints, and logs the cost of every call. The main problems are drift, gaps in the orchestrator's control flow, and permission/config hygiene.

| # | Issue | Severity | Area |
|---|---|---|---|
| 1 | REQUEST file path mismatch between requirements-agent and orchestrator | HIGH | Orchestrator |
| 2 | Review-loop stage results never checked | HIGH | Orchestrator |
| 3 | E2E commits before checking PASS/FAIL | MEDIUM | Orchestrator |
| 4 | `claude -p ... \|\| true` swallows CLI failures | MEDIUM | Orchestrator |
| 5 | Outdated model IDs hardcoded | MEDIUM | Orchestrator |
| 6 | No resume after escalation (exit 2) | MEDIUM | Orchestrator |
| 7 | `settings.local.json` committed and bloated | HIGH | Permissions |
| 8 | No shared `.claude/settings.json` | MEDIUM | Permissions |
| 9 | `HANDOFF.md` bloated with history | MEDIUM | Context |
| 10 | Duplicate, drifted `project-guide.md` | MEDIUM | Agents |
| 11 | No hooks | MEDIUM | Harness |
| 12 | Rule files are very thin | LOW | Rules |
| 13 | Prompt history mixed into source tree | LOW | Repo hygiene |

---

## Orchestrator (`finance-app/.claude/orchestration/orchestrate.sh`)

### 1. REQUEST file path mismatch — HIGH
- The orchestrator reads `$CTX/REQUEST_<Feature>.md`, which is `.claude/context/REQUEST_*.md`.
- The `requirements-agent` (according to the root `project-guide.md`) writes `pipeline/REQUEST_<Feature>.md`.
- The next feature's request is in fact at `pipeline/REQUEST_AsOfDateVersioning.md`, so `orchestrate.sh AsOfDateVersioning` will exit with "Neither PLAN nor REQUEST exists".
- **Fix:** standardise on one path. Agents can't write to `.claude/**` in headless mode, so use `pipeline/REQUEST_*.md` and have the orchestrator copy it into `$CTX` for the permanent record, the same way it handles PLAN and REVIEW.
- **Status: FIXED (Option A).** The orchestrator reads `pipeline/REQUEST_*.md` and archives a copy to `.claude/context/` after spec PASS. All paths in `requirements-agent.md` now point to `pipeline/`.

### 2. Review-loop stage results never checked — HIGH
- Inside the reviewer `while` loop, the follow-up `unit-test-agent` and `dev-agent` calls write `RESULT.json`, but the script never reads `result_field result`. It commits and goes straight to the next reviewer round.
- If Dev returns FAIL (tests red), the reviewer still reviews broken code and the commit history shows a "fix" that doesn't build.
- **Fix:** check the result after each call. On a Dev FAIL, run the same bounded Dev retry used in the main stage before returning to the reviewer.
- **Status: FIXED (Option A).** Added a shared `run_dev_until_green()` used by the main Dev stage and the review-fix loop. The review-loop unit-test result is now checked (FAIL → `exit 1`). Review-fix attempts are capped by `MAX_FIX_ATTEMPTS=2`. Verified with a stubbed `claude` (dev fails once, reviewer requests changes once → completes correctly).

### 3. E2E commits before checking result — MEDIUM
- `commit_stage "test(e2e): ..."` runs before the PASS/FAIL check, so failing specs get committed.
- This may be deliberate, to keep the failing specs as evidence. If so, use a commit message that says so (e.g. `test(e2e): add specs for X (FAILING)`).
- **Status: FIXED (Option B).** On FAIL the commit is `test(e2e): add e2e specs for X (FAILING: <summary>)`, then exit 2. On PASS the commit message is unchanged.

### 4. CLI failures swallowed — MEDIUM
- `claude -p ... > .last_<role>.json || true` hides auth errors, rate limits and timeouts. The only signal left is "exited without writing RESULT.json".
- If the CLI crashes after a stale `RESULT.json` exists, `rm -f` protects you, but the logs still don't tell a crash apart from an agent mistake.
- **Fix:** capture the exit code and log it, and check `.is_error` / `.subtype` in the JSON output before reading `RESULT.json`.
- Also worth adding: `--max-turns` per role, and a wall-clock `timeout` wrapper, so a stuck agent can't burn tokens indefinitely.
- **Status: FIXED (Option A).** The CLI exit code is captured and classified (`ok` / `agent` / `infra`). Infra failures exit 4 (retry later). Per-role `--max-turns` (spec 20, unit-test 30, dev 40, reviewer 15, e2e 40; `MAX_TURNS` overrides all) and a `timeout` wrapper (`AGENT_TIMEOUT`, default 30m) added. The COST table gains Turns and CLI Outcome columns. **Tune the caps** to about 1.5× the observed turns after the first real run.

### 5. Outdated model IDs — MEDIUM
- `AGENT_MODEL` hardcodes `claude-sonnet-4-6` and `claude-haiku-4-5-20251001`.
- **Fix:** move these to a config file or env vars (`SPEC_MODEL`, `DEV_MODEL`, …) and pick current models. Consider a stronger model for Spec and Reviewer, where the quality of judgement matters most, and the cheaper tier for E2E/test scaffolding.
- **Status: FIXED (B + ii).** Models are pinned in `.claude/orchestration/models.env` with per-role env overrides (`SPEC_MODEL`, `DEV_MODEL`, …): Opus 5.5 for spec and reviewer, Sonnet 5.5 for unit-test, dev and e2e. A missing model now fails loudly instead of falling back silently.

### 6. No resume after escalation — MEDIUM
- After `exit 2` (Dev or Reviewer max attempts), finishing is entirely manual; this is what happened with Feature B, round 4.
- `--force` bypasses every checkpoint, but there is no "resume from stage X" option.
- **Fix:** add `--from <stage>`. It would validate that state shows that stage, or the one just before it, and reset the attempt counter. Record the escalation reason in `WORKFLOW_STATE.json`.
- **Status: FIXED (Option C).**
  - `--resume` restarts at the recorded stage. `--from <stage>` restarts at a stage you choose, which must be at or before the recorded one unless `--force`.
  - Earlier stages are skipped, and attempt counters reset.
  - Review-fix rounds record stage `reviewer`, so resuming restarts the review loop rather than the main Dev stage.
  - On every exit, the state file records `status` (running/escalated/failed/infra_error/done), `exit_code`, `step`, `last_role`, `last_result`, `last_summary` and `updated_at`.
  - The COST table gets a Result column.
  - Verified with 7 stub scenarios.
  - Note: resuming at `reviewer` after a failed review fix reviews the current code. Fix the tests first.

### Minor orchestrator notes
- `cp "$DRAFT_REVIEW_FILE" "$REVIEW_FILE"` aborts the script (`set -e`) with a confusing error if the reviewer didn't write the draft. Guard it with an explicit check and log message.
- The reviewer diffs against `main...$BRANCH`, but day-to-day work happens on `claude-101`. Confirm the base branch, or make it a parameter (`BASE_BRANCH`).
- `git add -A` inside `commit_stage` stages everything in the repo, including unrelated working-tree changes. Scope it to `finance-app/`, or check for a clean tree at startup.

---

## Permissions & settings

### 7. `.claude/settings.local.json` committed and bloated — HIGH
- It's tracked in git (`git ls-files` confirms). By convention this file is personal and should be gitignored.
- It contains about 45 accumulated one-off allows, including:
  - specific `sed -i` edits on source files and `application.yml`
  - hardcoded PIDs (`ps -p 35759 ...`)
  - session-specific scratchpad log paths
  - `Bash(git commit -m ' *)`, `Bash(git checkout *)` and `Bash(git add *)` at the top level
- It also contains broad allows: `Bash(curl *)` (unrestricted network egress) and `Bash(cd /workspaces/claude-workspace/finance-app *)`, which effectively allows any command prefixed with `cd`.
- **Fix:** `git rm --cached .claude/settings.local.json`, add it to `.gitignore`, and rebuild a minimal list of allows by hand.

### 8. No shared `.claude/settings.json` — MEDIUM
- There is no project-level settings file in either `.claude/` directory, so the baseline rules (deny `git push`, deny reading `local/`, `e2e/data/accounts_v1.csv` and secrets, allow `mvn test`, `npx playwright test`) exist only in the per-role orchestrator files or in the personal local file.
- **Fix:** add a committed `finance-app/.claude/settings.json` with shared allows and denies. Keep the per-role settings as overlays for the pipeline.
- Also consider adding `Read` denies for the sensitive paths already listed in `.gitignore` (`local/`, `RealToAnonymIds.json`, `anonym_to_real_id.json`). `.claudeignore` isn't an enforced control.

---

## Context & agents

### 9. `HANDOFF.md` bloated — MEDIUM
- 207 lines, mostly history of completed features A–D, the Feature B parked/round-4 narrative, and E2E test tables for shipped features. The heading `### Next — Account Listing Enhancements` appears twice, and that section is stale (all three features are done).
- Every agent that reads it pays for that in tokens and is more likely to act on stale instructions.
- **Fix:** keep `HANDOFF.md` to about 30 lines: current feature, stage, blockers, next action. Move the history to `docs/feature-history.md`, or rely on the PLAN, REVIEW and COST files plus git log.

### 10. Duplicate, drifted `project-guide.md` — MEDIUM
- `/.claude/agents/project-guide.md` (102 lines) and `/finance-app/.claude/agents/project-guide.md` (99 lines) have already diverged:
  - The root copy uses `mvn spring-boot:run` and has a "Starting a new feature → requirements-agent" section.
  - The finance-app copy uses `bin/start.sh` / `bin/stop.sh`, `app.env` and `ADMIN_PASSWORD`, and has no requirements-agent section.
- Which copy Claude loads depends on the launch directory, so behaviour is inconsistent.
- **Fix:** keep one copy (in `finance-app/.claude/agents/`), merge the newer content from both, and delete the other. Do the same check for `CLAUDE.md`: keep the root file to a short pointer.

### 11. No hooks — MEDIUM
- Every quality gate depends on the agent running the right command. Candidates:
  - **PostToolUse** (Edit/Write on `*.java`): `mvn -q compile` for fast feedback.
  - **Stop / SubagentStop**: fail if `pipeline/RESULT.json` is missing or invalid JSON during pipeline runs.
  - **PreToolUse** (Bash): block `git push`, `rm -rf`, and reads of the secrets file, as defence in depth on top of deny rules.

### 12. Thin rule files — LOW
- `dev.md` is 3 lines and `testing.md` is 5. They say what not to touch, but not how to work: architecture constraints (hexagonal ports and adapters, the domain layer has no Spring imports), naming conventions, the requirement that tests be named after PLAN scenario IDs, or "don't leave stub classes in `src/test`", which caused the Feature B shadowing bug.
- **Fix:** add lessons from REVIEW files with repeated findings as explicit rules.

### 13. Prompt history in source tree — LOW
- `finance-app/ai/prompts/*` (19 files), plus `src/main/resources/ai/prompt_20_.md`, which sits on the runtime classpath and gets packaged into the JAR.
- **Fix:** move `prompt_20_.md` out of `src/main/resources`. Consider moving `ai/` under `docs/` or archiving it.

---

## Suggested order of work

1. Fix #1 (REQUEST path), which unblocks `AsOfDateVersioning`.
2. Fix #7 and #8 (permissions hygiene).
3. Fix #2, #3 and #4 (orchestrator control flow).
4. Fix #9 and #10 (context and agent deduplication).
5. Fix #5, #6 and #11 (models, resume, hooks).
6. Fix #12 and #13 when convenient.
