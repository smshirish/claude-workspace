# Spec: Per-Feature Agent Cost & Timing Ledger

## 1. Purpose

An agentic pipeline runs a feature through multiple sequential **stages** (e.g. spec → test → implement → review → e2e), where each stage is one or more independent LLM agent invocations, possibly retried. This feature records the token usage, wall-clock duration, model(s), and monetary cost of **every individual agent invocation**, grouped per feature, so that:

- The true $ cost of shipping one feature is auditable after the fact.
- Cost/time outliers (a stage that retried 3x, a stage that burned unusual cache-read tokens) are visible per-row, not just as an opaque total.
- The ledger is durable even if the pipeline crashes mid-run or a later stage overwrites a shared scratch file.

This spec is platform- and LLM-agnostic: it does not assume Claude, a specific CLI, or a specific file format. It defines the **data model**, the **write rules**, and the **aggregation rules**. A conforming implementation may be a bash script, a Python orchestrator, a CI pipeline, etc., against any LLM provider.

---

## 2. Terminology

| Term | Meaning |
|---|---|
| **Feature** | One unit of work tracked end-to-end through the pipeline (maps to one branch/ticket/PR). |
| **Stage / Role** | A named step in the pipeline (e.g. `spec-agent`, `dev-agent`, `reviewer-agent`). A pipeline may run the same role multiple times (retries, review rounds). |
| **Invocation** | One completed call to an LLM agent for a given stage. Each invocation produces exactly one ledger row. |
| **Ledger** | The ordered, append-only collection of invocation records for one feature. |
| **Run Result** | The raw output object/response returned by the agent runner after an invocation completes (provider-specific shape — see §6). |

---

## 3. Data Model

### 3.1 CostRecord (one row = one invocation)

```json
{
  "$id": "CostRecord",
  "type": "object",
  "required": ["role", "attempt", "models", "duration_ms", "input_tokens", "output_tokens", "cache_read_tokens", "cost_usd"],
  "properties": {
    "role":              { "type": "string", "description": "Stage/role name, e.g. 'dev-agent'." },
    "attempt":           { "type": "string", "description": "Attempt number or round label within this role (string, not int — may be '2' or '2 (initial failing tests)')." },
    "models":            { "type": "array", "items": { "type": "string" }, "description": "Distinct model identifiers used during this invocation, in first-seen order. Empty array if unknown." },
    "duration_ms":       { "type": "integer", "minimum": 0, "default": 0 },
    "input_tokens":      { "type": "integer", "minimum": 0, "default": 0 },
    "output_tokens":     { "type": "integer", "minimum": 0, "default": 0 },
    "cache_read_tokens": { "type": "integer", "minimum": 0, "default": 0, "description": "Tokens served from prompt cache, billed at a reduced rate. 0 if the provider has no caching." },
    "cost_usd":          { "type": "number", "minimum": 0, "default": 0, "description": "Fully-loaded cost for this single invocation in USD, as reported (or computed) by the provider." }
  }
}
```

### 3.2 FeatureLedger (one file/collection per feature)

```json
{
  "$id": "FeatureLedger",
  "type": "object",
  "required": ["feature", "records"],
  "properties": {
    "feature": { "type": "string" },
    "records": { "type": "array", "items": { "$ref": "CostRecord" } },
    "total_cost_usd": { "type": "number", "description": "Sum of records[].cost_usd. Derived, not authoritative — recompute rather than trust a stale copy." },
    "partial": { "type": "boolean", "default": false, "description": "True if one or more invocations for this feature are known to be missing from records (see 5.4)." },
    "notes": { "type": "string", "description": "Free-text explanation, required when partial=true." }
  }
}
```

Canonical storage is one ledger per feature, keyed by feature name (e.g. a file named after the feature, or a DB row keyed by feature id). Physical format (Markdown table, CSV, JSONL, DB table) is an implementation detail — §7 gives a reference Markdown rendering, but any format satisfying the schema above is conforming.

---

## 4. Write Rules (non-negotiable — these are what make the ledger trustworthy)

1. **One append per invocation, immediately on completion.** Write the record as soon as the invocation returns — never batch multiple invocations into one deferred write.
   - *Why:* orchestrators commonly reuse one scratch file/variable per role to hold "the last run's raw result," which gets truncated the instant a retry starts. If the ledger write is deferred, a retry can destroy the previous attempt's data before it's recorded. Appending immediately makes each row durable the moment it's known, independent of what happens next.
2. **Append-only, never rewrite existing rows.** A record, once written, is not edited or deleted by the pipeline. Corrections happen by adding an explanatory record/note, not by mutating history.
3. **Header/initialization is idempotent.** If the ledger doesn't exist yet, initialize it (header row / empty records array) before the first append; if it exists, append without touching prior content.
4. **Degrade to zero/empty on missing fields, never fail the pipeline.** If the run result is missing a field (duration, token counts, cost — e.g. provider didn't report it, or the call errored before producing usage stats), write `0` (or `[]` for models) for that field rather than aborting the stage or leaving the row out. Ledger completeness must never gate pipeline success.
5. **Total is computed, not carried.** Do not persist a running total that later code trusts blindly; recompute the sum from `records[].cost_usd` whenever a total is needed (see §5.1). This avoids drift if a record is added after a total was last rendered.
6. **Total-append is idempotent.** If a rendering step appends a human-readable "Total: $X" line to the ledger, it must first check whether that line already exists (e.g. "is the last line already a total line?") and skip re-appending if so. This matters because the write-total step commonly runs on process exit (success or failure) and must be safe to invoke more than once for the same ledger.
7. **Retries and repeated rounds are new rows, not overwrites.** Every invocation of a role — whether it's attempt 1, a retry after test failure, or review round 3 — gets its own row with an `attempt` label distinguishing it. Never collapse retries into a single "latest" row.
8. **Ledger writes are best-effort and non-blocking w.r.t. the pipeline's git/state transitions.** A failure to write a cost record must not prevent the stage's actual output (code, tests, review verdict) from being committed/persisted.

---

## 5. Aggregation Rules

### 5.1 Per-feature total
```
total_cost_usd(feature) = sum(record.cost_usd for record in ledger[feature].records)
```
Non-numeric or missing `cost_usd` values are treated as `0` in the sum (never as an error that aborts aggregation).

### 5.2 Model list per record
`models` is the **distinct set** of model identifiers actually used to service that one invocation (an invocation may internally route across more than one model — e.g. a fast router model plus a completion model), listed in first-seen order, comma-joined if rendered as a flat string. Not the union across the whole ledger.

### 5.3 Cross-feature rollup (optional, derived)
A rollup across features is a pure derived view — `sum(total_cost_usd(f) for f in features)` — and must never be the write target itself. Always compute it from per-feature ledgers, never maintain it as an independently-updated running counter.

### 5.4 Partial ledgers
If the implementation is aware that some invocations are missing from a feature's ledger (e.g. a bug in the write path existed for part of the pipeline's history and was later fixed), mark `partial: true` and record which rows are known-missing in `notes`. A partial ledger's total is labeled as a **partial total**, not presented as the true total.

---

## 6. Extraction / Mapping Guide (provider-agnostic)

Every LLM provider's run/response object exposes usage data under different field names. Implementations must map their provider's Run Result onto the `CostRecord` fields via a thin adapter. Reference mapping (adjust names per provider):

| CostRecord field | Typical source field(s) |
|---|---|
| `models` | List/keys of a per-model usage breakdown on the run result; falls back to a single top-level `model` field if the provider doesn't support multi-model routing |
| `duration_ms` | Wall-clock duration of the call, as reported by the runner or measured by the caller if the provider doesn't report it |
| `input_tokens` | `usage.input_tokens` / `usage.prompt_tokens` (naming varies by provider) |
| `output_tokens` | `usage.output_tokens` / `usage.completion_tokens` |
| `cache_read_tokens` | `usage.cache_read_input_tokens` / `usage.cached_tokens` / `0` if provider has no prompt caching |
| `cost_usd` | `total_cost_usd` if the provider computes it; otherwise derive as `input_tokens * input_price + output_tokens * output_price + cache_read_tokens * cache_price` from a static price table keyed by model |

If the provider does not report cost directly, the implementation **must** maintain its own price table (per-model, per-token-type $ rates) and compute `cost_usd` locally rather than leaving it blank — a ledger with silently-zero costs is worse than one with a documented estimate.

---

## 7. Reference Rendering (Markdown — one conforming presentation, not the only one)

```
# Token Cost & Timing Log: <Feature>

| Role | Attempt | Model(s) | Duration (ms) | Input Tok | Output Tok | Cache Read Tok | Cost (USD) |
|---|---|---|---|---|---|---|---|
| spec-agent | 1 | modelA,modelB | 685892 | 22 | 37087 | 1195387 | 1.26256185 |
| dev-agent | 1 | modelA,modelB | 454150 | 27 | 12128 | 1346159 | 0.81848470 |
...

**Total cost: $8.773220 USD**
```

Ordering: rows appear in the order invocations completed (append order), not grouped/sorted by role — this preserves the pipeline's actual execution timeline for debugging.

---

## 8. Trigger Points

Log exactly one `CostRecord` at each of these points, and no others:

- Immediately after every stage-agent invocation returns (success, failure, or partial output all still produce a record — a failed call still has duration and often partial token usage).
- Do **not** log at pipeline start, at git-commit time, or at review-verdict time — those are separate events, not invocations.

---

## 9. Non-Goals

- This spec does not define budget alerts, cost caps, or real-time cost dashboards — it defines the ledger those could be built on top of.
- This spec does not mandate a specific storage backend (flat file vs. database vs. object store) — only the record shape and write/aggregation semantics.
- This spec does not define how `attempt`/round numbering is decided by the orchestrator — only that each invocation's row must carry whatever label the orchestrator already uses to distinguish it from sibling invocations of the same role.
