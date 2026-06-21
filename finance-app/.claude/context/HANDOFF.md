# Handoff: Import Accounts — CSV Row-Level Validation

## Status
Backend complete — all unit tests green; E2E tests written; E2E run pending (requires live app).

### Completed
- `domain/exception/CsvSchemaException` — holds single `schemaError` string.
- `domain/exception/CsvRowValidationException` — holds `List<RowValidationError>`.
- `domain/model/validation/RowValidationError` — record `(int rowNumber, String column, String message)`.
- `domain/service/AccountCsvSchemaValidator` — validates header; throws `CsvSchemaException` on any mismatch; tolerates extra trailing columns.
- `domain/service/AccountCsvRowValidator` — accumulates all row errors; validates mandatory fields, `accountType` enum, and `balance` parseability.
- `infrastructure/adapter/out/persistence/OpenCsvAccountParser` — refactored to two-pass flow (schema check → row validation → mapping).
- `infrastructure/adapter/in/web/AccountController` — three explicit catch blocks for `CsvSchemaException`, `CsvRowValidationException`, `AccountImportException`.
- `src/main/resources/templates/accounts.html` — `schema-error-banner` and `row-errors-banner` blocks added.
- `pom.xml` — Surefire plugin configured to exclude E2E tests from `mvn test`.
- `e2e/tests/csv-validation.spec.ts` — 13 tests: F3 schema validation (E2E-S1–S6) + F4 row-level validation (E2E-R1–R7).
- `e2e/tests/accounts.spec.ts` — AC1.6 selector updated from `accounts-import-error` to `row-errors-banner`.
- All unit tests: `AccountCsvSchemaValidatorTest` (S-1–S-6), `AccountCsvRowValidatorTest` (R-1–R-6), `OpenCsvAccountParserTest` (P-1–P-7), `AccountControllerTest` (C-1–C-3) — all green.

### Next
- Run E2E suite: start app (`mvn spring-boot:run`), then `cd e2e && npx playwright test csv-validation.spec.ts`.
- Fix any E2E failures (selector mismatches, error message wording, fixture edge cases).
- Verify AC1.6 regression in `accounts.spec.ts` passes with updated selector.

---

## Feature
Add two-tier CSV validation to the Import Accounts use case:
- Tier 1: Schema validation (header columns, order, count)
- Tier 2: Row-level data validation (mandatory fields, enum check, BigDecimal parse)
- Structured error model attributes (`schemaError`, `rowErrors`) surfaced in `accounts.html`

## Previous cycle
Shared Navigation Menu — fully implemented and E2E verified. See git history for details.
