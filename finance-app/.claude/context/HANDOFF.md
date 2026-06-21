# Handoff: Import Accounts — CSV Row-Level Validation

## Status
In progress — E2E tests written; all unit tests written; backend implementation pending.

### Completed
- `e2e/tests/csv-validation.spec.ts` — 13 tests: F3 schema validation (E2E-S1–S6) + F4 row-level validation (E2E-R1–R7).
- `e2e/tests/accounts.spec.ts` — AC1.6 selector updated from `accounts-import-error` to `row-errors-banner`.
- `domain/service/AccountCsvSchemaValidatorTest` — S-1 through S-6 (all 6 scenarios covered).
- `domain/service/AccountCsvRowValidatorTest` — R-1 through R-6 (all 6 scenarios covered).
- `infrastructure/adapter/out/persistence/OpenCsvAccountParserTest` — P-1 through P-7 (all 7 integration scenarios covered, plus legacy T4.x tests retained).
- `infrastructure/adapter/in/web/AccountControllerTest` — C-1 through C-3 (all 3 MockMvc scenarios covered, plus existing T6.x tests retained).

### Next
- Implement backend: `CsvSchemaException`, `CsvRowValidationException`, `RowValidationError`, `AccountCsvSchemaValidator`, `AccountCsvRowValidator`, refactor `OpenCsvAccountParser` (two-pass flow), update `AccountController` catch blocks, update `accounts.html` template.

---

## Feature
Add two-tier CSV validation to the Import Accounts use case:
- Tier 1: Schema validation (header columns, order, count)
- Tier 2: Row-level data validation (mandatory fields, enum check, BigDecimal parse)
- Structured error model attributes (`schemaError`, `rowErrors`) surfaced in `accounts.html`

## Previous cycle
Shared Navigation Menu — fully implemented and E2E verified. See git history for details.
