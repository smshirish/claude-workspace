# Handoff: Import Accounts — CSV Row-Level Validation

## Status
In progress. See PLAN.md for full spec (schema validation + row-level validation + structured error reporting).

---

## Feature
Add two-tier CSV validation to the Import Accounts use case:
- Tier 1: Schema validation (header columns, order, count)
- Tier 2: Row-level data validation (mandatory fields, enum check, BigDecimal parse)
- Structured error model attributes (`schemaError`, `rowErrors`) surfaced in `accounts.html`

## Previous cycle
Shared Navigation Menu — fully implemented and E2E verified. See git history for details.
