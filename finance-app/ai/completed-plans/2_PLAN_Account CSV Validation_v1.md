# Implementation Plan: Account CSV Validation

## 1. Feature Requirements

### FR-1: Tier 1 — Schema Validation (Fail-Fast)
Before any row is processed, the incoming CSV header row must be validated against the expected template.

- **Expected columns (exact names, exact order):** `bankName`, `accountNumber`, `accountType`, `balance`, `currency`
- Reject if: any of the first five column names is wrong (case-sensitive), the required columns are out of order, or required columns are missing (fewer than five columns present).
- **Tolerate:** Extra columns appended **beyond** the required five are silently ignored — they do not constitute a schema error and do not stop processing.
- **Behavior:** On any schema failure, stop immediately — do not attempt row parsing. Return a single global schema error.

### FR-2: Tier 2 — Row-Level Data Validation (Accumulative)
If schema passes, validate every data row before mapping to domain objects.

| Column | Mandatory | Type Constraint |
|---|---|---|
| `bankName` | Yes | Non-blank string |
| `accountNumber` | Yes | Non-blank string |
| `accountType` | Yes | Must match `AccountType` enum: `CHECKING`, `SAVINGS`, `CREDIT`, `INVESTMENT`, `OTHER` |
| `balance` | Yes | Valid `BigDecimal`; parseable decimal number |
| `currency` | Yes | Non-blank string |

- **Behavior:** Do NOT stop on first row error. Process all rows, collect every error, and annotate each with its 1-based row number (data rows only; header = row 0).

### FR-3: Structured Error Reporting
Validation failures must be returned as structured data — not flat strings — so the controller can render them as distinct UI elements.

- Schema failure → a single global error string surfaced via a dedicated model attribute.
- Row failure → a list of row-scoped error objects (row number + column name + human-readable message) surfaced via a dedicated model attribute.

---

## 2. Acceptance Criteria

| ID | Criterion |
|---|---|
| AC-1 | A CSV with the correct header and valid data rows is accepted and imported as before. |
| AC-2 | A CSV with a missing column in the header returns a schema error and no rows are processed. |
| AC-3 | A CSV with extra trailing columns in the header is accepted; the extra columns are ignored and all data rows are imported normally. |
| AC-4 | A CSV with columns in the wrong order returns a schema error and no rows are processed. |
| AC-5 | A CSV with correct schema but one invalid row returns row-level errors; all other rows are also checked (errors from all rows are collected). |
| AC-6 | A CSV with multiple invalid rows returns all errors — one entry per violation, each tagged with a row number. |
| AC-7 | A blank mandatory field on any row produces a "Field is required" error for that column on that row. |
| AC-8 | An invalid `accountType` value produces an error naming the bad value and the allowed values. |
| AC-9 | An unparseable `balance` value produces an error identifying the column and the bad value. |
| AC-10 | Schema errors and row errors are surfaced as separate model attributes in the Thymeleaf template; the controller never conflates them into one flat string. |

---

## 3. Component Breakdown

### 3.1 New Domain Exceptions

**`domain/exception/CsvSchemaException`** _(extends `AccountImportException`)_
- Holds a single `String schemaError` describing what is wrong with the header.
- Thrown by the schema validator; caught by the controller.

**`domain/exception/CsvRowValidationException`** _(extends `AccountImportException`)_
- Holds `List<RowValidationError> rowErrors`.
- Thrown by the row validator after collecting all row errors; caught by the controller.

### 3.2 New Domain Value Object

**`domain/model/validation/RowValidationError`** _(Java record)_
```
record RowValidationError(int rowNumber, String column, String message)
```
- `rowNumber`: 1-based data row number (header row is row 0, first data row is row 1).
- `column`: the column name that failed (e.g., `"accountType"`).
- `message`: actionable human-readable description (e.g., `"'FOO' is not a valid account type. Allowed values: CHECKING, SAVINGS, CREDIT, INVESTMENT, OTHER"`).

### 3.3 New Domain Services

**`domain/service/AccountCsvSchemaValidator`**
- Pure domain class (no framework dependencies).
- Declares `static final String[] EXPECTED_COLUMNS = {"bankName","accountNumber","accountType","balance","currency"}`.
- Single method: `void validate(String[] actualHeader)`.
- Throws `CsvSchemaException` immediately if `actualHeader.length < EXPECTED_COLUMNS.length` (too few columns).
- Compares only the first `EXPECTED_COLUMNS.length` positions element-by-element against `EXPECTED_COLUMNS`; any columns beyond that index are **not inspected**.
- Throws `CsvSchemaException` on the first required-position discrepancy with a message identifying the problem (e.g., "Expected column 'accountNumber' at position 2 but found 'acctNum'").
- A header with more than five columns is valid provided the first five match exactly.

**`domain/service/AccountCsvRowValidator`**
- Pure domain class.
- Single method: `List<RowValidationError> validate(List<String[]> rawRows)`.
- Iterates all rows; for each row accumulates errors without short-circuiting.
- Reads only the first `EXPECTED_COLUMNS.length` values per row; extra trailing values are ignored.
- Rules applied per row:
  - Mandatory blank check for all 5 columns.
  - `accountType` enum membership check against `AccountType.values()`.
  - `balance` `BigDecimal` parseability check via `new BigDecimal(value)` in a try-catch.
- Returns the complete list; an empty list means all rows are valid.

### 3.4 Modified Infrastructure Adapter

**`infrastructure/adapter/out/persistence/OpenCsvAccountParser`** _(refactored)_

Current flow: `InputStream` → `CsvToBeanBuilder` → `AccountCsvRecord` list → `BankAccount` list (fail-fast on first bad row).

New two-pass flow:
1. **Buffer** — read `InputStream` to `byte[]` once so it can be used twice.
2. **Pass 1 (Schema)** — use `CSVReader` to read only the header row from the buffered bytes. Call `AccountCsvSchemaValidator.validate(header)`. Throws `CsvSchemaException` on failure; execution stops here.
3. **Pass 2 (Row parsing)** — use `CSVReader` a second time on the same bytes to read all data rows as `String[]`. Call `AccountCsvRowValidator.validate(dataRows)`. If the returned list is non-empty, throw `CsvRowValidationException(errors)`.
4. **Mapping** — only reached when both tiers pass. Map each `String[]` to `BankAccount` directly using only the first `EXPECTED_COLUMNS.length` positional values; any trailing values are discarded. This removes the need for `CsvToBeanBuilder` in the happy path and eliminates the current fail-fast `toAccount()` method.

> **Note:** `AccountCsvRecord` and the `@CsvBindByName` bean-mapping approach are superseded by this design. The record can be deprecated and eventually deleted.

### 3.5 Modified Web Adapter

**`infrastructure/adapter/in/web/AccountController`** _(refactored `importAccounts` method)_

Current: one `catch (AccountImportException e)` → `model.addAttribute("importError", e.getMessage())`.

New: three explicit catch blocks, in order:
1. `catch (CsvSchemaException e)` → `model.addAttribute("schemaError", e.getSchemaError())`
2. `catch (CsvRowValidationException e)` → `model.addAttribute("rowErrors", e.getRowErrors())`
3. `catch (AccountImportException e)` → `model.addAttribute("importError", e.getMessage())` _(existing fallback, unchanged)_

### 3.6 Template Update (accounts.html)

Add two new conditional blocks:

```html
<!-- Schema error block -->
<div th:if="${schemaError}" data-testid="schema-error-banner">
  <p th:text="${schemaError}"></p>
</div>

<!-- Row errors block -->
<div th:if="${rowErrors}" data-testid="row-errors-banner">
  <ul>
    <li th:each="err : ${rowErrors}"
        th:text="|Row ${err.rowNumber}, '${err.column}': ${err.message}|"
        th:attr="data-testid='row-error-' + ${err.rowNumber}">
    </li>
  </ul>
</div>
```

---

## 4. Test Scenarios

### Unit — `AccountCsvSchemaValidatorTest`
| # | Input | Expected |
|---|---|---|
| S-1 | Correct 5-column header in exact order | No exception |
| S-2 | Column name typo (`acctNumber` instead of `accountNumber`) | `CsvSchemaException` — message names the bad column and position |
| S-3 | Correct columns but wrong order (`accountNumber` first) | `CsvSchemaException` — message names the position mismatch |
| S-4 | Missing one column (only 4 columns present) | `CsvSchemaException` |
| S-5 | Extra column appended at the end | No exception — schema passes; extra column is ignored |
| S-6 | Empty header (zero columns) | `CsvSchemaException` |

### Unit — `AccountCsvRowValidatorTest`
| # | Input | Expected |
|---|---|---|
| R-1 | All rows valid | Empty list returned |
| R-2 | Row 2 has blank `bankName` | One `RowValidationError(rowNumber=2, column="bankName", ...)` |
| R-3 | Row 3 has invalid `accountType` value | One `RowValidationError(rowNumber=3, column="accountType", ...)` with allowed values in message |
| R-4 | Row 1 has non-numeric `balance` | One `RowValidationError(rowNumber=1, column="balance", ...)` |
| R-5 | Multiple rows have multiple errors | All errors collected; list size equals total violation count across all rows |
| R-6 | Row has blank `accountNumber` | `RowValidationError(column="accountNumber", ...)` |

### Integration — `OpenCsvAccountParserTest` _(augments existing T4.x tests)_
| # | Input | Expected |
|---|---|---|
| P-1 | Valid CSV — 3 data rows | Returns 3 `BankAccount` objects (existing T4.1, now re-validated) |
| P-2 | CSV with wrong column order | Throws `CsvSchemaException` (replaces T4.4 which expected generic `AccountImportException`) |
| P-3 | CSV with missing column | Throws `CsvSchemaException` |
| P-4 | CSV with extra trailing column | Returns `BankAccount` list (extra column silently ignored; no exception thrown) |
| P-5 | Valid schema, invalid `accountType` on row 2 | Throws `CsvRowValidationException` with `rowErrors` containing row 2 entry |
| P-6 | Valid schema, 3 rows each with one error | Throws `CsvRowValidationException` with 3 entries |
| P-7 | Header-only file | Returns empty list (AC-1 baseline, no change) |

### MockMvc — `AccountControllerTest` _(augments existing T6.x tests)_
| # | Scenario | Expected model attributes |
|---|---|---|
| C-1 | Use case throws `CsvSchemaException` | `schemaError` present; `rowErrors` absent; no redirect |
| C-2 | Use case throws `CsvRowValidationException` with 2 errors | `rowErrors` list present with size 2; `schemaError` absent |
| C-3 | Use case throws generic `AccountImportException` | `importError` present (existing T6.4 behaviour unchanged) |

---

## 5. E2E Test Plan

### 5.1 Framework & Configuration

**Framework:** Playwright + TypeScript — already configured in `e2e/` (no new dependencies).

**Test file:** New `e2e/tests/csv-validation.spec.ts` — keeps validation scenarios separate from the existing `accounts.spec.ts` import/view tests.

**Shared helpers:** Reuse `writeTempCsv()` and `login()` from the existing spec and auth helper. Centralize all CSV fixture strings in a new `e2e/tests/helpers/csvFixtures.ts` file so each test imports named constants rather than defining inline strings.

**No new packages required.** The existing Playwright + Chromium setup (`playwright.config.ts`) covers all scenarios.

**Breaking change in existing tests:** `AC1.6` in `accounts.spec.ts` currently asserts `data-testid="accounts-import-error"` for a bad `accountType` upload. After this feature lands, that error will surface as `data-testid="row-errors-banner"` instead. `AC1.6` must be updated to assert the new selector.

---

### 5.2 CSV Fixture Inventory

| Constant | Header valid? | Data issues | Purpose |
|---|---|---|---|
| `VALID_CSV` | Yes | None | Happy path (already in `accounts.spec.ts`) |
| `HEADER_ONLY_CSV` | Yes | No data rows | Already in `accounts.spec.ts` |
| `MISSING_COLUMN_CSV` | No — missing `currency` | — | Schema error: missing column |
| `EXTRA_COLUMN_CSV` | Yes — extra `notes` at end (trailing) | None | Lenient parsing: extra trailing column is accepted and import succeeds |
| `WRONG_ORDER_CSV` | No — `accountNumber` before `bankName` | — | Schema error: wrong order |
| `TYPO_COLUMN_CSV` | No — `acctNumber` instead of `accountNumber` | — | Schema error: column name typo |
| `BAD_ACCOUNT_TYPE_ROW1_CSV` | Yes | Row 1: `accountType=MORTGAGE` | Row error: invalid enum value |
| `BLANK_BANK_NAME_ROW2_CSV` | Yes | Row 2: `bankName` is blank | Row error: mandatory field blank |
| `BAD_BALANCE_ROW1_CSV` | Yes | Row 1: `balance=not-a-number` | Row error: unparseable BigDecimal |
| `MULTI_ROW_ERRORS_CSV` | Yes | Rows 1–3 each have one error | Row errors: accumulation across rows |
| `MIXED_VALID_INVALID_CSV` | Yes | Row 1 valid, Row 2 bad `accountType` | Row errors: all rows checked even after first bad one |

---

### 5.3 E2E Test Cases — F3: CSV Schema Validation

> Maps to FR-1, AC-2, AC-3, AC-4. All tests in `describe('F3 — CSV Schema Validation')`.

| ID | Scenario | CSV Fixture | Assertions |
|---|---|---|---|
| E2E-S1 | Missing column shows schema-error-banner | `MISSING_COLUMN_CSV` | `[data-testid="schema-error-banner"]` visible; `[data-testid="row-errors-banner"]` absent; no redirect to `/accounts` |
| E2E-S2 | Extra trailing column is accepted — import succeeds | `EXTRA_COLUMN_CSV` | No `schema-error-banner`; no `row-errors-banner`; accounts table count increases by the expected number of data rows |
| E2E-S3 | Wrong column order shows schema-error-banner | `WRONG_ORDER_CSV` | `[data-testid="schema-error-banner"]` visible; error text names the mismatched position |
| E2E-S4 | Column name typo shows schema-error-banner | `TYPO_COLUMN_CSV` | `[data-testid="schema-error-banner"]` visible; error text references the bad column name |
| E2E-S5 | Schema error does not import any rows | `MISSING_COLUMN_CSV` | After upload: accounts table count unchanged from before upload (pre-seed 1 row, post upload still 1 row) |
| E2E-S6 | Schema error never co-renders row-errors-banner | `MISSING_COLUMN_CSV` | `[data-testid="row-errors-banner"]` has count 0 in DOM |

---

### 5.4 E2E Test Cases — F4: CSV Row-Level Validation

> Maps to FR-2, AC-5, AC-6, AC-7, AC-8, AC-9. All tests in `describe('F4 — CSV Row-Level Validation')`.

| ID | Scenario | CSV Fixture | Assertions |
|---|---|---|---|
| E2E-R1 | Invalid accountType shows row-errors-banner | `BAD_ACCOUNT_TYPE_ROW1_CSV` | `[data-testid="row-errors-banner"]` visible; `[data-testid="row-error-1"]` visible; item text contains the bad value (`MORTGAGE`) and lists allowed values |
| E2E-R2 | Blank mandatory field shows row error | `BLANK_BANK_NAME_ROW2_CSV` | `[data-testid="row-error-2"]` visible; text references column `bankName`; text contains "required" or "blank" |
| E2E-R3 | Unparseable balance shows row error | `BAD_BALANCE_ROW1_CSV` | `[data-testid="row-error-1"]` visible; text references column `balance` |
| E2E-R4 | Multiple rows with errors — all collected | `MULTI_ROW_ERRORS_CSV` | `[data-testid="row-errors-banner"] li` count equals total violation count; `data-testid="row-error-1"`, `row-error-2`, `row-error-3` all present |
| E2E-R5 | Mixed valid/invalid rows — all rows still checked | `MIXED_VALID_INVALID_CSV` | `[data-testid="row-errors-banner"]` visible; `[data-testid="row-error-2"]` present; `[data-testid="row-error-1"]` absent (row 1 was valid) |
| E2E-R6 | Row errors do not import any rows | `BAD_ACCOUNT_TYPE_ROW1_CSV` | After upload: accounts table count unchanged (pre-seed 1 row, post upload still 1 row) |
| E2E-R7 | Row error never co-renders schema-error-banner | `BAD_ACCOUNT_TYPE_ROW1_CSV` | `[data-testid="schema-error-banner"]` has count 0 in DOM |

---

### 5.5 E2E Regression — Existing Tests That Must Be Updated

| Existing test | Change required |
|---|---|
| `AC1.6` in `accounts.spec.ts` — "CSV with unrecognised accountType shows importError" | Change assertion from `[data-testid="accounts-import-error"]` to `[data-testid="row-errors-banner"]`; rename test description to match new behaviour |

No other existing E2E tests are affected by this feature.

---

## 6. Out of Scope

| Item | Reason |
|---|---|
| File-type / MIME-type validation (non-CSV upload) | Separate concern; existing upload widget already filters by `.csv` extension |
| Duplicate row detection across rows (e.g., same `accountNumber` appearing twice) | Not required in the stated use case; can be a follow-on feature |
| Max row count / file size limit | Infrastructure-level concern (Spring `MultipartFile` config); not a domain validation rule |
| Localisation / i18n of error messages | No internationalisation requirement exists yet |
| Cross-field validation (e.g., `currency` must match `accountType`) | No such rule specified |
| Async / streaming validation for very large files | Import is a manual admin action; file sizes are expected to be small |
| UI changes beyond the two conditional error blocks described above | Template styling and UX polish are a separate ticket |
