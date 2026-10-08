# Implementation Plan: D_AsOfDate

---

## 1. Feature Requirements

### FR-1: `asOfDate` field on `BankAccount` — domain and persistence
`BankAccount` gains a nullable `asOfDate: LocalDate` field that flows end-to-end from the import CSV through the domain model, the persisted `accounts.csv`, and back on reads.

- The positional constructor gains `LocalDate asOfDate` as its 8th parameter (no `requireNonNull` — field is nullable)
- The **existing 5-arg `BankAccount.create()` factory is preserved** (sets `asOfDate = null`) so existing test fixtures continue to compile without modification
- A **new 6-arg `BankAccount.create()` overload** is added that accepts `LocalDate asOfDate` as the 6th argument; used by `OpenCsvAccountParser`
- A new `asOfDate()` accessor is added
- The persistence header becomes `accountId,bankName,accountNumber,accountType,balance,currency,importedAt,asOfDate`
- `formatLine()` appends `,<ISO date>` (or `,` if null)
- `parseLine()` split limit changes from `7` to `8`; if fewer than 8 tokens (legacy row), `asOfDate` is `null`; if the 8th token is blank, `asOfDate` is also `null`; otherwise `LocalDate.parse(p[7])`
- No validation is applied when reading the persisted file — `null` is a valid stored value
- The persisted file is silently upgraded to the 8-column header on the next write

### FR-2: Import CSV — schema and row-level validation for `asOfDate`
The import CSV schema is extended; the existing two-tier validation machinery (`AccountCsvSchemaValidator` / `AccountCsvRowValidator` / `RowValidationError`) is reused with no new error pathway.

**Tier 1 — schema (fail-fast):**
- `AccountCsvSchemaValidator.EXPECTED_COLUMNS` extends to 6 elements, appending `"asOfDate"` at index 5
- An import header missing `asOfDate` (≤ 5 columns, or 6 columns with wrong name at position 6) throws `CsvSchemaException` — shown in the existing `schema-error-banner`

**Tier 2 — row-level (accumulative):**
- Blank `asOfDate` → `RowValidationError(column="asOfDate", "Field is required")`
- Non-ISO value (any string `LocalDate.parse()` rejects) → `RowValidationError(column="asOfDate", message names the bad value)`
- Date after today → `RowValidationError(column="asOfDate", message notes the future-date constraint)`. "Today" is evaluated with an injectable `java.time.Clock`. `AccountCsvRowValidator` provides a **no-arg constructor** (defaults to `Clock.systemDefaultZone()`) **and** a single-arg constructor taking `Clock`, so the committed tests (`new AccountCsvRowValidator()`) compile unchanged while new tests can inject a fixed clock
- Today's date and past dates are valid
- Invalid rows do not block valid rows in the same file — existing accumulative behaviour is preserved

**OpenCsvAccountParser — mapping pass:**
- After both tiers pass, reads `row[5]` as the `asOfDate` string, parses it to `LocalDate`, and passes it to the 6-arg `BankAccount.create()`

### FR-3: Display — "As Of Date" column in the accounts table
- A new non-sortable "As Of Date" `<th>` column appears last in the table (after Currency)
- Each `<tr>` renders `account.asOfDate()` as `YYYY-MM-DD` (LocalDate.toString()); null renders as empty string
- `AccountSortField` is **not** extended — no `AS_OF_DATE` enum value
- The `<tfoot>` total row gains one additional empty `<td>` to match the new column count (colspan on the "Total" label cell is unchanged at 3)

### FR-4: CSV converter tool — optional date-transform mapping
The `tools/csv-converter` module is updated so every file it produces satisfies the new 6-column schema.

- A new `ColumnDescriptor` record (java record) is introduced with two static factories: `verbatim(String outputColumn)` and `dateTransform(String outputColumn, String fromPattern)`
- `CsvConverter` gains a **new overloaded `convert()` method** accepting `LinkedHashMap<String, ColumnDescriptor>` that handles both verbatim and date-transform entries; the **existing `Map<String, String>` overload is preserved** so all current `CsvConverterTest` tests (T-1 through T-6) continue to pass unchanged
- For verbatim descriptors, the cell value is copied unchanged (existing behaviour)
- For `"type": "date"` descriptors, the input value is parsed with the declared `from` pattern (`DateTimeFormatter.ofPattern(...)`) and emitted as ISO `YYYY-MM-DD`; two-digit years resolve via the standard java.time base year (so `21-Jun-26` → `2026-06-21`)
- If parsing fails, `CsvConversionException` is thrown naming the column, the offending value, and the 1-based row number (counting from the first data row)
- `Main.java` reads `mapping.json` as `LinkedHashMap<String, Object>` (Jackson); a String value becomes `ColumnDescriptor.verbatim(...)`; a Map value with `"type": "date"` becomes `ColumnDescriptor.dateTransform(to, from)`; the new `convert(LinkedHashMap<String, ColumnDescriptor>)` overload is called
- `sample/mapping.json` and `README.md` are updated to include `asOfDate`

---

## 2. Acceptance Criteria

| ID | Criterion |
|---|---|
| AC-1 | A valid 6-column import CSV (header ends with `asOfDate`; data rows carry ISO dates) imports without error; each `BankAccount.asOfDate()` equals the parsed `LocalDate` |
| AC-2 | An import CSV with only 5 columns (missing `asOfDate`) triggers a Tier-1 `CsvSchemaException`; the `schema-error-banner` is shown; no accounts are saved |
| AC-3 | An import CSV row with a blank `asOfDate` cell triggers a Tier-2 `RowValidationError` for `asOfDate`; other valid rows in the same file are not rejected |
| AC-4 | An import CSV row with a non-ISO `asOfDate` (e.g., `31/08/2026`) triggers a Tier-2 `RowValidationError` for `asOfDate` |
| AC-5 | An import CSV row whose `asOfDate` is strictly after today (per the injected `Clock`) triggers a Tier-2 `RowValidationError`; a row whose `asOfDate` equals today is accepted |
| AC-6 | After a successful import the accounts table shows "As Of Date" as the rightmost column; each row renders the ISO date; the column is a plain `<th>` (no sort link) |
| AC-7 | A persisted `accounts.csv` written before this feature (7-column format, no `asOfDate`) is read without error; accounts load with `asOfDate == null`; the UI renders empty cells for those accounts |
| AC-8 | The converter correctly transforms `21-Jun-26` using `dd-MMM-yy` → `2026-06-21`; a plain-string entry in the same `mapping.json` still copies its column verbatim |
| AC-9 | A converter input cell that cannot be parsed with the declared `from` pattern causes `CsvConversionException` whose message names the input column, the offending value, and the row number |

---

## 3. Component Breakdown

### 3.1 New Domain Exceptions
None.

### 3.2 New Domain Value Objects / Records
**`ColumnDescriptor`** (NEW — `tools/csv-converter/src/main/java/com/finance/tools/csvconverter/`)
```java
record ColumnDescriptor(String outputColumn, String dateFromPattern) {
    static ColumnDescriptor verbatim(String outputColumn) {
        return new ColumnDescriptor(outputColumn, null);
    }
    static ColumnDescriptor dateTransform(String outputColumn, String fromPattern) {
        return new ColumnDescriptor(outputColumn, fromPattern);
    }
    boolean isDateTransform() { return dateFromPattern != null; }
}
```
Carries the target column name and an optional `from` date pattern. Used only in the converter tool.

### 3.3 New / Modified Domain Services

**`AccountCsvSchemaValidator`** (MODIFIED — `domain/service/`)
- `EXPECTED_COLUMNS` grows from 5 to 6 elements: `{"bankName","accountNumber","accountType","balance","currency","asOfDate"}`
- No logic change — same prefix-match algorithm now enforces the 6th required column
- Side-effect: `AccountCsvRowValidator.COLUMN_NAMES` (a static alias to `EXPECTED_COLUMNS`) automatically picks up `asOfDate` in its existing blank-field loop

**`AccountCsvRowValidator`** (MODIFIED — `domain/service/`)
- Add `private final Clock clock` field
- **No-arg constructor:** `this.clock = Clock.systemDefaultZone()` — required so existing test instantiations `new AccountCsvRowValidator()` compile without change
- **Single-arg constructor:** `this.clock = Objects.requireNonNull(clock)` — used by `OpenCsvAccountParser` and injectable-clock tests
- After the existing balance check, add `asOfDate` validation at position 5:
  1. Blank check is already handled by the `COLUMN_NAMES` loop (inherited from schema change)
  2. If non-blank: attempt `LocalDate.parse(value)` — on `DateTimeParseException` add `RowValidationError(rowNumber, "asOfDate", "'" + value + "' is not a valid date (expected YYYY-MM-DD)")`
  3. If parse succeeds and `parsed.isAfter(LocalDate.now(clock))`: add `RowValidationError(rowNumber, "asOfDate", "Date must not be in the future")`

### 3.4 Modified Infrastructure Adapters (Out-bound / Persistence)

**`BankAccount`** (MODIFIED — `domain/model/`)
- Add `private final LocalDate asOfDate` (nullable; no `requireNonNull`)
- Constructor: add `LocalDate asOfDate` as 8th parameter; store with no null-check
- **Keep existing 5-arg `create()` factory** (`asOfDate = null`) — existing tests that call it without a date continue to compile and pass
- **Add 6-arg `create()` overload** accepting `LocalDate asOfDate`; used by `OpenCsvAccountParser`
- Add `public LocalDate asOfDate()` accessor

**`AccountCsvRecord`** (MODIFIED — `infrastructure/adapter/out/persistence/`)
- Add field: `@CsvBindByName(column = "asOfDate") private String asOfDate;`
- Add getter: `public String getAsOfDate()`

**`OpenCsvAccountParser`** (MODIFIED — `infrastructure/adapter/out/persistence/`)
- Constructor gains `Clock clock` parameter; passes it to `new AccountCsvRowValidator(clock)` (replacing the no-arg field initialiser)
- Mapping pass: reads `row[5]` as the `asOfDate` string (already validated); calls `LocalDate.parse(row[5])` to obtain a `LocalDate`; passes it to the 6-arg `BankAccount.create(row[0], row[1], accountType, balance, row[4], asOfDate)`

**`CsvFileAccountRepository`** (MODIFIED — `infrastructure/adapter/out/persistence/`)
- `HEADER` → `"accountId,bankName,accountNumber,accountType,balance,currency,importedAt,asOfDate"`
- `parseLine()` — split limit changes from `7` to `8`:
  - If `p.length < 8` or `p[7].isBlank()`: `asOfDate = null`
  - Else: `asOfDate = LocalDate.parse(p[7])`
  - Pass `asOfDate` as the 8th argument to the positional constructor
- `formatLine()` — append `,` + (`a.asOfDate() != null ? a.asOfDate().toString() : ""`)

### 3.5 Modified Web Adapters (In-bound / Controllers)

**`AccountBeanConfig`** (MODIFIED — `infrastructure/config/`)
- Add `@Bean public Clock clock() { return Clock.systemDefaultZone(); }`
- Change `accountFileParser()` to accept a `Clock` parameter and return `new OpenCsvAccountParser(clock)`:
  ```java
  @Bean
  public AccountFileParser accountFileParser(Clock clock) {
      return new OpenCsvAccountParser(clock);
  }
  ```

`AccountController` requires no changes — it passes `BankAccount` objects to the model unchanged; the new `asOfDate()` getter is accessible in Thymeleaf automatically.

### 3.6 Template / UI Changes

**`accounts.html`** (MODIFIED — `src/main/resources/templates/`)

`<thead>` — add after the Currency `<th>`:
```html
<th data-testid="column-asOfDate">As Of Date</th>
```

`<tbody>` row — add after the Currency `<td>`:
```html
<td th:text="${account.asOfDate()}"
    th:attr="data-testid='account-asOfDate-' + ${account.accountId()}"></td>
```
(Thymeleaf renders a null `LocalDate` as an empty string automatically.)

`<tfoot>` — add one empty `<td>` after the existing Currency empty cell (colspan on the "Total" label `<td>` stays at `3`):
```html
<td></td>
```

**CSV Converter — `CsvConverter`** (MODIFIED — `tools/csv-converter/src/main/java/`)
- Keep existing `public String convert(String inputCsv, Map<String, String> columnMapping)` overload unchanged — existing tests T-1 through T-6 call this and must remain green
- Extract private `convertInternal(String inputCsv, LinkedHashMap<String, ColumnDescriptor> mapping)` with the shared implementation
- The `Map<String, String>` overload wraps each string value as `ColumnDescriptor.verbatim(value)` and delegates to `convertInternal`
- Add new `public String convert(String inputCsv, LinkedHashMap<String, ColumnDescriptor> mapping)` overload that delegates directly to `convertInternal`
- In `convertInternal`, for each entry: if `descriptor.isDateTransform()`, parse the cell value with `DateTimeFormatter.ofPattern(descriptor.dateFromPattern())` and emit as ISO; on `DateTimeParseException` throw `CsvConversionException("Column '" + inputCol + "': unparseable date '" + rawValue + "' on data row " + rowIndex + " (expected: " + descriptor.dateFromPattern() + ")")`

**`Main`** (MODIFIED):
- Change Jackson deserialization from `Map<String, String>` to `Map<String, Object>` (handles mixed String / Map values)
- Build `LinkedHashMap<String, ColumnDescriptor>` from the parsed object:
  - String value → `ColumnDescriptor.verbatim(value)`
  - Map value → `ColumnDescriptor.dateTransform(map.get("to"), map.get("from"))`
- Call the new `convert(inputCsv, LinkedHashMap<String, ColumnDescriptor>)` overload

**`sample/mapping.json`** (UPDATED):
```json
{
  "bank":             "bankName",
  "acc_no":           "accountNumber",
  "account_purpose":  "accountType",
  "amount_chf":       "balance",
  "account_currency": "currency",
  "as_of_date":       { "to": "asOfDate", "type": "date", "from": "dd-MMM-yy" }
}
```

**`README.md`** (`tools/csv-converter/README.md`) (UPDATED):
- Update the required output columns table to 6 rows, adding `asOfDate` (constraint: ISO `YYYY-MM-DD`, not in the future)
- Add a short "Object mapping entries" section documenting the `{ "to", "type", "from" }` form and the `"type": "date"` transform

---

## 4. Test Scenarios

> Tests marked `(committed)` were written by the test agent in commit `1533a7a` and are already failing. They drive the dev agent's implementation. Tests marked `(new)` need to be added by the test agent or dev agent as appropriate.

### Unit — `AccountCsvSchemaValidatorTest`

| # | Input | Expected |
|---|---|---|
| S-1 (committed) | 6-column header: `{"bankName","accountNumber","accountType","balance","currency","asOfDate"}` | No exception |
| S-2 | `{"bankName","acctNumber","accountType","balance","currency"}` | `CsvSchemaException` mentioning `accountNumber` / position 2 |
| S-3 | `{"accountNumber","bankName","accountType","balance","currency"}` | `CsvSchemaException` |
| S-4 | `{"bankName","accountNumber","accountType","balance"}` | `CsvSchemaException` |
| S-5 (committed) | 7-column header — required 6 + extra trailing `"notes"` | No exception |
| S-6 | `{}` (empty) | `CsvSchemaException` |
| S-7 (committed) | 5-column header (first 5 correct, `"asOfDate"` absent) | `CsvSchemaException` |
| S-8 (committed) | 6-column header with wrong name at position 6 | `CsvSchemaException` whose message references position 6 or `"asOfDate"` |

### Unit — `AccountCsvRowValidatorTest`

All tests instantiate via the **no-arg constructor** `new AccountCsvRowValidator()` (system clock). Tests R-7 through R-11 are committed.

| # | Input | Expected |
|---|---|---|
| R-1 (committed) | Two valid 6-element rows with `asOfDate = "2026-08-31"` | Empty error list |
| R-2 (committed) | Row 2 blank bankName, valid asOfDate | 1 error: row 2, column `bankName` |
| R-3 (committed) | Row 3 accountType = `"MORTGAGE"`, valid asOfDate | 1 error: row 3, column `accountType`, message lists allowed values |
| R-4 (committed) | Row 1 balance = `"not-a-number"`, valid asOfDate | 1 error: row 1, column `balance` |
| R-5 (committed) | 3 rows: bad balance, blank bankName, bad accountType (each valid asOfDate) | 3 errors, one per row |
| R-6 (committed) | Row 1 accountNumber = `"  "` (whitespace), valid asOfDate | 1 error: row 1, column `accountNumber` |
| R-7 (committed) | Row 1 asOfDate = `""` (blank) | Error with rowNumber=1, column=`asOfDate` |
| R-8 (committed) | Row 1 asOfDate = `"31/08/2026"` (non-ISO format) | Error with rowNumber=1, column=`asOfDate`, message contains `"31/08/2026"` |
| R-9 (committed) | Row 1 asOfDate = `"2099-01-01"` (always-future date) | Error with rowNumber=1, column=`asOfDate` |
| R-10 (committed) | Row 1 asOfDate = `"2026-08-31"` (today per system clock on 2026-08-31) | No error for `asOfDate` |
| R-11 (committed) | Row 1 asOfDate = `"2020-01-01"` (past date) | No error for `asOfDate` |

### Integration — `OpenCsvAccountParserTest`

All tests are committed in `1533a7a`. `OpenCsvAccountParser` receives a `Clock` from its constructor; `OpenCsvAccountParserTest` instantiates it as `new OpenCsvAccountParser()` — the parser should expose a no-arg constructor that defaults to `Clock.systemDefaultZone()` (or construct the validator with the no-arg constructor).

> **Dev agent guidance:** `OpenCsvAccountParser` may provide a no-arg constructor `OpenCsvAccountParser() { this(Clock.systemDefaultZone()); }` so the test's `new OpenCsvAccountParser()` compiles. Alternatively, the test agent should update the instantiation; check the committed test before deciding.

| # | Input | Expected |
|---|---|---|
| T4.1 (committed) | 6-col CSV, 3 valid rows | Returns 3 `BankAccount` objects with correct fields |
| T4.2 (committed) | 6-col CSV, unknown `accountType` | `AccountImportException` mentioning the unknown type |
| T4.3 (committed) | Header-only 6-col CSV | Empty list |
| T4.4 (committed) | Missing `accountNumber` (5-col header) | `AccountImportException` (schema) |
| T4.5 (committed) | `balance = "12345.67"` | `balance` maps to `BigDecimal("12345.67")` exactly |
| P-2 (committed) | Wrong column order | `CsvSchemaException` |
| P-3 (committed) | 4-column CSV | `CsvSchemaException` |
| P-4 (committed) | 7-col: correct 6 + trailing `"notes"` | Returns 2 accounts (extra column ignored) |
| P-5 (committed) | Row 2 accountType = `"MORTGAGE"` | `CsvRowValidationException`, 1 error on row 2, column `accountType` |
| P-6 (committed) | 3 rows each with one error | `CsvRowValidationException`, 3 errors |
| P-7 (committed) | Header-only 6-col CSV | Empty list |
| P-9 (committed) | Row 1 blank asOfDate | `CsvRowValidationException`, error on row 1, column `asOfDate` |
| P-10 (committed) | Row 1 asOfDate = `"2099-01-01"` | `CsvRowValidationException`, error on row 1, column `asOfDate` |
| P-11 (committed) | Row 1 asOfDate = `"31/08/2026"` | `CsvRowValidationException`, error on row 1, column `asOfDate` |

### Integration — `CsvFileAccountRepositoryTest`

Existing T5.1–T5.5 use the **5-arg `BankAccount.create()`** (kept for backward compat) and compile unchanged. T5.1's expected header string is already updated in the committed code to `"accountId,...,importedAt,asOfDate"`. T5.6 and T5.7 need to be added.

| # | Input | Expected |
|---|---|---|
| T5.1 (committed) | `saveAll()` 2 accounts via 5-arg create | Line 0 = `"accountId,bankName,accountNumber,accountType,balance,currency,importedAt,asOfDate"`; 3 lines total |
| T5.2 | `saveAll()` then `findAll()` (5-arg create, asOfDate=null) | Round-trips; `asOfDate()` returns null |
| T5.3 | `findAll()` on non-existent file | Empty list |
| T5.4 | `saveAll()` then `deleteAll()` | File contains only the 8-col header |
| T5.5 | `saveAll()` to nested path | File created with parent directories |
| T5.6 (new) | `saveAll()` account with `asOfDate = LocalDate.of(2026, 8, 31)` via 6-arg create; `findAll()` | Loaded `asOfDate()` equals `LocalDate.of(2026, 8, 31)` |
| T5.7 (new) | Directly write a 7-column raw CSV line to the temp file; `findAll()` | 1 account returned; `asOfDate()` is `null`; no exception |

### MockMvc — `AccountControllerTest` / `AccountControllerSortTest` / `AccountControllerFilterTest` / `AccountControllerTotalTest`

No new test scenarios. These tests use the **5-arg `BankAccount.create()`** which is kept; they compile and pass without modification. No assertion changes needed — the new `asOfDate` field is only rendered in the template.

### Unit — `CsvConverterTest` (new tests only; T-1 through T-6 unchanged)

| # | Input | Expected |
|---|---|---|
| T-7 (new) | 6-descriptor mapping: 5 verbatim + `"as_of_date" → ColumnDescriptor.dateTransform("asOfDate", "dd-MMM-yy")`; row has `as_of_date = "21-Jun-26"` | Output cell for `asOfDate` = `"2026-06-21"`; verbatim cells unchanged |
| T-8 (new) | Same date-transform mapping; row 1 `as_of_date = "not-a-date"` | `CsvConversionException` message contains `"as_of_date"`, `"not-a-date"`, and `"1"` |

---

## 5. E2E Test Plan

### 5.1 Framework & Configuration
**Framework:** Playwright + TypeScript — existing setup in `e2e/` (no new dependencies).

**Test file:** `e2e/tests/account-asofdate.spec.ts` (new file)

**Shared helpers:** `login()` from `./helpers/auth`; `writeTempCsv()` / `importCsv()` pattern (inline, per existing spec convention)

**New helpers:** None

**No new packages required.**

**Breaking changes in existing tests:** All existing spec files embed or import 5-column CSV fixtures. Every fixture must gain the `asOfDate` column. See §5.4.

---

### 5.2 Test Fixtures / Data Inventory

| Constant / Fixture | Valid? | Issues | Purpose |
|---|---|---|---|
| `VALID_ASOFDATE_CSV` (inline) | Yes | None | Happy path: 3 accounts with valid past ISO `asOfDate` values |
| `MISSING_ASOFDATE_COLUMN_CSV` (inline) | No | Header has only 5 columns — `asOfDate` absent | Tier-1 schema error (AC-2) |
| `BLANK_ASOFDATE_ROW1_CSV` (inline) | No | Row 1 `asOfDate` blank | Tier-2 blank-date error (AC-3) |
| `INVALID_DATE_FORMAT_ROW1_CSV` (inline) | No | Row 1 `asOfDate = "31/08/2026"` | Tier-2 non-ISO error (AC-4) |
| `FUTURE_DATE_ROW1_CSV` (inline) | No | Row 1 `asOfDate = "2099-01-01"` | Tier-2 future-date error (AC-5) |

All five constants are defined inline in `account-asofdate.spec.ts` (not added to `csvFixtures.ts` — the shared helper is updated for the six column-specific validation tests already there).

---

### 5.3 E2E Test Cases — As Of Date Column

> Maps to FR-1, FR-2, FR-3, AC-1 through AC-9. All tests in `describe('As Of Date column')`.

| ID | Scenario | Fixture / State | Assertions |
|---|---|---|---|
| E2E-1 | Import valid 6-col CSV; view accounts table | `VALID_ASOFDATE_CSV` (3 rows with dates `2026-08-31`, `2026-01-15`, `2020-06-01`) | `[data-testid="column-asOfDate"]` visible; first row `[data-testid^="account-asOfDate-"]` text = `"2026-08-31"` |
| E2E-2 | Import CSV missing `asOfDate` column | `MISSING_ASOFDATE_COLUMN_CSV` | `[data-testid="schema-error-banner"]` visible; `[data-testid="accounts-table"]` absent |
| E2E-3 | Import CSV with blank `asOfDate` on row 1 | `BLANK_ASOFDATE_ROW1_CSV` | `[data-testid="row-errors-banner"]` visible; `[data-testid="row-error-1"]` text contains `"asOfDate"` |
| E2E-4 | Import CSV with non-ISO date on row 1 | `INVALID_DATE_FORMAT_ROW1_CSV` | `[data-testid="row-errors-banner"]` visible; `[data-testid="row-error-1"]` text contains `"asOfDate"` |
| E2E-5 | Import CSV with future date on row 1 | `FUTURE_DATE_ROW1_CSV` | `[data-testid="row-errors-banner"]` visible; `[data-testid="row-error-1"]` text contains `"asOfDate"` |
| E2E-6 | After valid import, verify tfoot structure | `VALID_ASOFDATE_CSV` (total = sum of 3 balances) | `[data-testid="accounts-total-balance"]` shows correct sum; row has 6 `<td>` cells |

---

### 5.4 E2E Regression — Existing Tests That Must Be Updated

**`e2e/tests/helpers/csvFixtures.ts`** — every exported constant must gain `,asOfDate` at the end of its header and a valid past ISO date (e.g. `2026-08-31`) appended to every data row:

| Fixture constant | Change |
|---|---|
| `VALID_CSV` | Append `,asOfDate` to header; append `,2026-08-31` to each of 3 data rows |
| `HEADER_ONLY_CSV` | Append `,asOfDate` to header string |
| `EXTRA_COLUMN_CSV` | Header becomes `"...,currency,asOfDate,notes"` (asOfDate before notes); data rows gain a date before the notes value |
| `WRONG_ORDER_CSV` | Append `,asOfDate` to header; append date to data row (schema error still fires at position 1 before reaching asOfDate) |
| `TYPO_COLUMN_CSV` | Append `,asOfDate` to header; append date to data row (schema error fires at position 2) |
| `BAD_ACCOUNT_TYPE_ROW1_CSV` | Append `,asOfDate` to header; append valid date to data row |
| `BLANK_BANK_NAME_ROW2_CSV` | Append `,asOfDate` to header; append valid date to both rows |
| `BAD_BALANCE_ROW1_CSV` | Append `,asOfDate` to header; append valid date to data row |
| `MULTI_ROW_ERRORS_CSV` | Append `,asOfDate` to header; append valid date to all 3 rows |
| `MIXED_VALID_INVALID_CSV` | Append `,asOfDate` to header; append valid date to both rows |
| `MISSING_COLUMN_CSV` | 4-col header unchanged (schema error); append a date to the data row for correctness |

**`e2e/tests/accounts.spec.ts`** — inline CSV strings within the file need `,asOfDate` column; any assertion that counts `<th>` elements or checks table column text must accommodate the 6th column.

**`e2e/tests/account-sort.spec.ts`** — inline 3-row fixture:
```
bankName,accountNumber,accountType,balance,currency,asOfDate
Chase,000111222,SAVINGS,500.00,USD,2026-08-31
Ally,333444555,CHECKING,100.00,USD,2026-08-31
BOFA,666777888,SAVINGS,300.00,USD,2026-08-31
```

**`e2e/tests/account-filter.spec.ts`** — inline 4-row fixture (note: `BOFA` account number is `999888777` per HANDOFF.md fix):
```
bankName,accountNumber,accountType,balance,currency,asOfDate
Chase,000111222,SAVINGS,500.00,USD,2026-08-31
Chase,333444555,CHECKING,200.00,USD,2026-08-31
Ally,666777888,SAVINGS,800.00,USD,2026-08-31
BOFA,999888777,CHECKING,150.00,USD,2026-08-31
```

**`e2e/tests/account-total.spec.ts`** — inline 3-row fixture:
```
bankName,accountNumber,accountType,balance,currency,asOfDate
Chase,000111222,SAVINGS,100.00,USD,2026-08-31
Chase,333444555,CHECKING,200.00,USD,2026-08-31
Ally,666777888,SAVINGS,300.00,USD,2026-08-31
```

**`e2e/tests/csv-validation.spec.ts`** — any inline CSV strings within the file need the 6th column; the shared fixtures from `csvFixtures.ts` are covered above.

---

## 6. Out of Scope

| Item | Reason |
|---|---|
| Sorting by As Of Date (`AS_OF_DATE` in `AccountSortField`) | Explicitly excluded by user — "As Of Date" column must not be a sort link |
| Filtering by As Of Date | Explicitly excluded — the filter bar (Bank Name, Account Number, Account Type) is unchanged |
| Displaying or changing `importedAt` | `importedAt` remains stored and undisplayed; this feature does not alter it |
| Lenient date parsing in the importer | The app accepts ISO `YYYY-MM-DD` only; non-ISO source dates are the converter's responsibility |
| Value transforms other than `"type": "date"` in the converter | Only date transformation is in scope |
| UI editing of `asOfDate` | Import-only; no edit flow |
| Locale-specific or timezone-aware date formatting | ISO-only output; system default timezone via Clock for "today" comparison |
| Validation of `asOfDate` when reading the persisted file | The persisted file is internal; null-on-missing is the required backward-compat behaviour |
