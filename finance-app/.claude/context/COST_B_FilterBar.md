# Token Cost & Timing Log: B_FilterBar

**Note:** this log predates the `log_cost()` fix in `orchestrate.sh` (commit `d4080f9`),
which appends a row immediately after each `claude -p` call returns. Before that fix,
`pipeline/.last_<role>.json` was overwritten on every retry, so only the **most recent**
call per role survived. Missing from this backfill: the original dev-agent attempt 1
run, and the dev-agent/reviewer-agent calls for review round 1. Every future pipeline
run (via `orchestrate.sh`) will populate this file completely, per-call, going forward.

| Role | Attempt/Round | Model(s) | Duration (ms) | Input Tok | Output Tok | Cache Read Tok | Cost (USD) |
|---|---|---|---|---|---|---|---|
| unit-test-agent | 1 (initial failing tests) | claude-haiku-4-5-20251001, claude-sonnet-4-6 | 58466 | 9 | 1509 | 208482 | 0.18539085 |
| dev-agent | round 2 fix (last surviving snapshot) | claude-haiku-4-5-20251001, claude-sonnet-4-6 | 723901 | 26 | 33649 | 1247817 | 1.1317801 |
| reviewer-agent | round 3 (last surviving snapshot) | claude-haiku-4-5-20251001, claude-sonnet-4-6 | 269967 | 20 | 12730 | 718718 | 0.57270465 |

**Partial total (recovered calls only): $1.88987560 USD**

Not recovered (overwritten before this fix existed): dev-agent attempt 1, dev-agent round 1 fix,
reviewer-agent round 1, reviewer-agent round 2.
