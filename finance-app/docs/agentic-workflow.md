# Agentic Pipeline (Spec → Test → Dev → Review → E2E)

This document describes the **automated** multi-agent pipeline that implements a feature end-to-end with no human in the loop until the final merge decision. It complements [`ai-workflow.md`](./ai-workflow.md), which describes the manual/interactive style of working with Claude Code one class at a time. Use this doc when reviewing, debugging, or changing the automated setup itself.

---

## 1. Why separate OS processes, not sub-agents

The pipeline's safety property is: **the Dev role cannot touch tests, the Test role cannot touch production code, and the Reviewer role cannot touch anything.** This only holds if each role's write scope is enforced by something outside the model's judgment.

In-process sub-agents (the `Agent` tool, `.claude/agents/*.md`) share the parent session's permission grant — a prompt telling a sub-agent "don't touch `src/test`" is a convention, not a control. A misfiring or adversarially-crafted plan could still route a `Write` through the parent's already-approved scope.

Each pipeline role is instead a **fully separate `claude` CLI process**, launched headless (`claude -p`) with its own `--settings <role>.settings.json`. Claude Code's permission system evaluates `permissions.deny` *before* any tool call executes, independent of what the model intended — that check is what makes the scope real. This is also why git's destructive verbs (`commit`, `push`, `merge`) are denied to every role: only the orchestrator process commits, so there is exactly one place in the whole pipeline that touches git history.

---

## 2. Roles

| Role | Settings file | Rule file (prompt/contract) | Can write | Cannot write | Retry behavior |
|---|---|---|---|---|---|
| **Spec Agent** | `orchestration/settings/spec-agent.settings.json` | `rules/spec.md` | `.claude/context/PLAN_<X>.md` | `src/**`, `e2e/**` | Runs once; failure halts the pipeline |
| **Unit Test Agent** | `orchestration/settings/unit-test-agent.settings.json` | `rules/testing.md` | `src/test/**` | `src/main/**`, `e2e/**` | Runs once; must produce *compiling but failing* tests |
| **Dev Agent** | `orchestration/settings/dev-agent.settings.json` | `rules/dev.md` | `src/main/**` | `src/test/**`, `e2e/**` | Up to 3 attempts against failing tests, then 3 more rounds if the Reviewer requests changes |
| **Reviewer Agent** | `orchestration/settings/reviewer-agent.settings.json` | `rules/reviewer.md` | `.claude/context/REVIEW_<X>.md` only | `src/**`, `e2e/**` | Up to 3 approve/request-changes rounds |
| **E2E Agent** | `orchestration/settings/e2e-agent.settings.json` | `rules/e2e.md` | `e2e/**` | `src/main/**`, `src/test/**` | Runs once; failure halts the pipeline (routed back manually) |

(All paths above are relative to `finance-app/`.)

### What each settings file actually does

Every `*.settings.json` follows the same shape:

```json
{
  "permissions": {
    "deny": [ "Edit(<forbidden-path>/**)", "Write(<forbidden-path>/**)", "Bash(git commit*)", "Bash(git push*)", "Bash(git merge*)" ],
    "allow": [ "Bash(mvn test*)", "..." ],
    "defaultMode": "acceptEdits"
  }
}
```

- `defaultMode: acceptEdits` means any `Edit`/`Write` call that **isn't** matched by a `deny` rule is auto-accepted — no interactive prompt, which is required for a headless run. **It does not cover `Bash`.** A headless `claude -p` process has no human to approve a Bash command, so any shell command not matched by an explicit `allow` rule is silently denied — this bit us in the first real pipeline run (Unit Test Agent could write files but every `mvn test` invocation was denied, so it never produced `RESULT.json`). Each role's `allow` list is therefore scoped to exactly the commands its `rules/<role>.md` says it needs (e.g. unit-test-agent: `mvn test*`; e2e-agent: `mvn spring-boot:run*`, `npx playwright*`) — narrowest prefix that covers the role's actual job, not a blanket `Bash(*)`.
- **`acceptEdits` itself isn't reliable across a monorepo, either.** `finance-app/` is a subdirectory of a larger git repo, not its own repo, so the enclosing repo's root `.claude/settings.local.json` is also in the settings stack Claude Code resolves for a headless process launched with cwd = `finance-app/`. That root file has no `defaultMode` set, and whatever merge precedence applies to that scalar field can leave the *effective* default back at interactive "default" — which auto-denies in headless mode — even though the role's own `--settings` file says `acceptEdits`. `allow` arrays don't have this problem: they get unioned across every settings source, so an explicit `allow` entry always applies regardless of how `defaultMode` resolves. This surfaced as a second, distinct failure after the Bash fix: `mvn test` ran fine, but the plain `Write` call to `.claude/context/RESULT.json` was still denied. Fix is the same shape as the Bash one — every role's `allow` list also carries an explicit entry for its own coordination-file output (`Write(.claude/context/RESULT.json)` on all five; `Write(.claude/context/PLAN_*.md)` on spec-agent; `Write(.claude/context/REVIEW_*.md)` on reviewer-agent) — do not rely on `defaultMode` alone to cover writes to `.claude/context/`.
- The `deny` array is the actual guardrail and always wins over `allow`/`defaultMode`. A rule like `Edit(src/test/**)` blocks the Dev Agent from ever modifying test files, regardless of what its prompt says, what the model decides mid-task, or what's in its `allow` list.
- `Bash(git commit*)` / `push*` / `merge*` are denied identically across every role — git history is the orchestrator's exclusive responsibility (see §4).
- Reviewer additionally has no path carved out for its own output: `.claude/context/REVIEW_<X>.md` is *not* matched by any `deny(src/**|e2e/**)` rule, so it remains writable under `acceptEdits` — this is intentional, not an oversight. "Read-only" means read-only over the codebase, not literally zero bytes written anywhere. Reviewer's `allow` list (`git diff*`, `git log*`, `git show*`) is read-only at the git level too — nothing in it can mutate history.

---

## 3. Coordination contract (how stateless processes hand off work)

Since each role is a fresh process with no memory of the others, everything they need to communicate is a file:

| File | Written by | Read by | Committed to git? |
|---|---|---|---|
| `.claude/context/PLAN_<Feature>.md` | Spec Agent | every downstream role | Yes — permanent record, same as existing `PLAN_A/B/C_*.md` |
| `.claude/context/RESULT.json` | whichever role just ran | orchestrator only | No (gitignored, ephemeral, overwritten every stage) |
| `.claude/context/REVIEW_<Feature>.md` | Reviewer Agent | orchestrator, Dev Agent (on request-changes) | Yes — audit trail of what the reviewer flagged |
| `.claude/context/WORKFLOW_STATE.json` | orchestrator | orchestrator (crash/resume visibility) | No (gitignored, ephemeral) |
| `.claude/context/.last_<role>.json` | `claude -p --output-format json` | orchestrator, for debugging a failed stage | No (gitignored) |

**`RESULT.json` is the hard contract every role prompt ends with**, e.g.:

```json
{ "stage": "dev", "result": "PASS", "summary": "S-1..S-5, C-1..C-4 all green" }
```

The orchestrator only reads this file to decide pass/fail — never the role's free-text stdout. If a role exits without writing it, the orchestrator treats that as a hard failure (`orchestrate.sh` line: `[[ ! -f "$RESULT_FILE" ]] → exit 1`), because a role that can't confirm its own output can't be trusted to have done it correctly.

---

## 4. Sequence

```
checkout feature/<Feature>
  │
  ▼
[Spec Agent]  — skipped if PLAN_<Feature>.md already exists
  │  commit: "spec: draft PLAN_<Feature>.md"
  ▼
[Unit Test Agent] — writes tests, confirms they fail for the right reason
  │  commit: "test: add failing tests for <Feature>"
  ▼
[Dev Agent] ──(FAIL, attempt < 3)──┐
  │  PASS                          │  feed back failure summary, retry
  │  commit: "feat: implement..."  │
  ▼                                │
[Reviewer Agent] ──(REQUEST_CHANGES, round < 3)──> [Dev Agent] ──┘
  │  APPROVE                            commit: "fix: address review feedback (round N)"
  ▼
[E2E Agent]
  │  commit: "test(e2e): add e2e specs for <Feature>"
  ▼
STOP — report "ready to merge", orchestrator does not push or merge
```

- **Dev retry cap: 3 attempts** against failing unit/MockMvc tests. Exceeding it halts the whole run (`exit 2`) rather than looping — a human should look at *why* Dev is stuck rather than let it keep guessing.
- **Reviewer retry cap: 3 rounds** of request-changes ↔ fix. Same reasoning — three rounds of unresolved review comments means the plan or the diff needs a human, not a fourth pass.
- **E2E failure is not auto-retried.** Whether the fix belongs in Dev (prod bug) or the Unit Test Agent (bad fixture/assertion) is a judgment call the orchestrator doesn't make for you; it halts and points at the log.
- **Every stage transition is a commit** on `feature/<Feature>` (never on `main`), so a bad Dev attempt or a rejected review round is independently bisectable later — nothing is squashed away.
- **No stage ever pushes or merges.** The pipeline's terminal state is "branch complete, HANDOFF-worthy" — merging to `main` is always a manual decision.

---

## 5. Running it

```bash
cd finance-app
.claude/orchestration/orchestrate.sh FilterBar
```

- If `.claude/context/PLAN_FilterBar.md` already exists (as it does for the three planned Account Listing features), the Spec stage is skipped and the pipeline starts at Unit Test.
- Prerequisites for the E2E stage are the same as manual E2E runs: nothing else needs to be running beforehand — the E2E Agent starts the backend itself (`mvn spring-boot:run`, per `rules/e2e.md`).
- On any halt (`exit 1` = hard failure, `exit 2` = retry cap hit), `WORKFLOW_STATE.json` still reflects the last attempted stage, and `.claude/context/.last_<role>.json` holds that role's full transcript for debugging.

---

## 6. Reviewing or changing the setup

This section exists so future changes are made in the right file, not by re-deriving the design.

| You want to... | Edit this |
|---|---|
| Change what a role is/isn't allowed to touch | `orchestration/settings/<role>.settings.json` — add/remove `deny` entries for scope, or `allow` entries for new Bash commands the role needs to run. Prefer the narrowest glob that expresses the boundary (`Edit(src/test/**)`, not a bare `Edit`; `Bash(mvn test*)`, not `Bash(mvn *)`). Remember: `deny` always wins over `allow`. |
| Change a role's instructions/behavior | `rules/<role>.md` — this is the actual prompt content each headless invocation is told to follow. |
| Change the retry caps | `MAX_ATTEMPTS` in `orchestration/orchestrate.sh` (currently 3 for both the Dev↔Test loop and the Dev↔Reviewer loop). |
| Change the git strategy (e.g. squash instead of per-stage commits) | `commit_stage()` in `orchestration/orchestrate.sh`. |
| Add a human approval gate mid-pipeline (not just at the end) | Insert an `exit` (or a wait/prompt) between the relevant stages in `orchestrate.sh` — today the only built-in human gate is after E2E passes. |
| Add a new role (e.g. a security-review stage) | 1) new `orchestration/settings/<role>.settings.json` with its own `deny` list, 2) new `rules/<role>.md` contract ending in a `RESULT.json` write instruction, 3) a new stage block in `orchestrate.sh` wired into the sequence. |
| Change the plan format all roles read | `context/PLAN_template.md` — every role's prompt points at this; changing its section numbers means updating whichever `rules/*.md` cites a section number (e.g. "Read PLAN section 4"). |

**Sanity checks after any change:**
```bash
jq empty finance-app/.claude/orchestration/settings/*.json   # settings JSON still valid
bash -n finance-app/.claude/orchestration/orchestrate.sh     # script syntax still valid
```

---

## 7. Known limitations

- **Cost/latency.** Every stage is a cold process that reloads `CLAUDE.md`, the relevant `rules/*.md`, and the plan file from scratch. A 5-stage feature pays that fixed overhead 5 times (more with retries) — slower wall-clock than one continuous interactive session, traded for the enforcement guarantee in §1.
- **Spec Kit is process-only.** `rules/spec.md` borrows Spec Kit's specify → clarify → plan discipline, but does not use the actual Spec Kit CLI (it requires installing `uv` and produces `spec.md`/`plan.md` in a different shape than `PLAN_template.md`). The Spec Agent's deliverable is still a `PLAN_<X>.md` conforming to the existing template, so no downstream role needed to change.
- **E2E failure routing is manual.** The orchestrator halts rather than guessing whether an E2E failure is a production bug (→ Dev) or a bad fixture/assertion (→ Unit Test Agent).
- **No automatic merge.** By design (see §4) — confirmed as the preferred default when this pipeline was designed, so a fully green pipeline is a recommendation, not an autonomous merge decision.
