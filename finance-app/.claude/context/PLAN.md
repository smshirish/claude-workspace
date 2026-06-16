# Implementation Plan: Account CSV Validation

## 1. Feature Requirements

### FR-1: Tier 1 — Schema Validation (Fail-Fast)
Before any row is processed, the incoming CSV header row must be validated against the expected template.

- **Expected columns (exact names, exact order):** `bankName`, `accountNumber`, `accountType`, `balance`, `currency`
- Reject if: any column name is wrong (case-sensitive), columns are out of order, columns are missing, or extra columns are present.
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
| AC-3 | A CSV with an extra column in the header returns a schema error and no rows are processed. |
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
- Compares `actualHeader` element-by-element against `EXPECTED_COLUMNS`.
- Throws `CsvSchemaException` on first discrepancy with a message identifying the problem (e.g., "Expected column 'accountNumber' at position 2 but found 'acctNum'").

**`domain/service/AccountCsvRowValidator`**
- Pure domain class.
- Single method: `List<RowValidationError> validate(List<String[]> rawRows)`.
- Iterates all rows; for each row accumulates errors without short-circuiting.
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
4. **Mapping** — only reached when both tiers pass. Map each `String[]` to `BankAccount` directly (positional mapping against the validated column order), removing the need for `CsvToBeanBuilder` in the happy path and eliminating the current fail-fast `toAccount()` method.

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
| S-5 | Extra column appended at the end | `CsvSchemaException` |
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
| P-4 | CSV with extra column | Throws `CsvSchemaException` |
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

## 5. Out of Scope

| Item | Reason |
|---|---|
| File-type / MIME-type validation (non-CSV upload) | Separate concern; existing upload widget already filters by `.csv` extension |
| Duplicate row detection across rows (e.g., same `accountNumber` appearing twice) | Not required in the stated use case; can be a follow-on feature |
| Max row count / file size limit | Infrastructure-level concern (Spring `MultipartFile` config); not a domain validation rule |
| Localisation / i18n of error messages | No internationalisation requirement exists yet |
| Cross-field validation (e.g., `currency` must match `accountType`) | No such rule specified |
| Async / streaming validation for very large files | Import is a manual admin action; file sizes are expected to be small |
| UI changes beyond the two conditional error blocks described above | Template styling and UX polish are a separate ticket |
