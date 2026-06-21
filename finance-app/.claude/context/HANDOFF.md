# Handoff: Import Accounts — CSV Row-Level Validation

## Status
In progress — E2E tests written; backend implementation pending.

### Completed
- `e2e/tests/csv-validation.spec.ts` — 13 tests: F3 schema validation (E2E-S1–S6) + F4 row-level validation (E2E-R1–R7).
- `e2e/tests/accounts.spec.ts` — AC1.6 selector updated from `accounts-import-error` to `row-errors-banner`.

---

## Feature
Add two-tier CSV validation to the Import Accounts use case:
- Tier 1: Schema validation (header columns, order, count)
- Tier 2: Row-level data validation (mandatory fields, enum check, BigDecimal parse)
- Structured error model attributes (`schemaError`, `rowErrors`) surfaced in `accounts.html`

## Previous cycle
Shared Navigation Menu — fully implemented and E2E verified. See git history for details.
