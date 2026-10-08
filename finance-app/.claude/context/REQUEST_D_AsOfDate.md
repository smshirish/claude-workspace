# Feature Request: D_AsOfDate

## What it does
Adds an `asOfDate` field to bank accounts, flowing end-to-end: supplied per row in the imported CSV, carried through the domain model and persistence, and shown as a new column on the accounts screen. `asOfDate` is the date the balance was accurate as of — it is business data supplied by the source file, not a system timestamp.

## User trigger
The user uploads a CSV on the accounts screen that now includes an `asOfDate` column. The value is parsed at import, stored with the account, and rendered automatically with the accounts table on every page load — no separate user action.

## Happy path

### CSV format
- Expected header becomes, in exact order: `bankName,accountNumber,accountType,balance,currency,asOfDate` — `asOfDate` is appended as the last column
- `asOfDate` is a required column: the schema (Tier 1) check fails fast if the header is missing it, consistent with the existing mandatory columns
- Accepted value format is ISO `YYYY-MM-DD` (e.g. `2026-08-31`)

### Model + persistence
- `BankAccount` gains an `asOfDate` field of type `java.time.LocalDate` (**not** `LocalDateTime` — this is a calendar date, unlike `importedAt`) alongside its existing fields
- The parsed value is persisted with the account and survives a reload of the accounts screen
- `importedAt` is unchanged and is retained: `asOfDate` does **not** replace it, and `importedAt` remains undisplayed

> **Note — there are two distinct CSV schemas in this codebase. Both change.**
> 1. The **import** schema (user-supplied upload), defined above and bound by `AccountCsvRecord`'s `@CsvBindByName` fields.
> 2. The **persistence** schema, a separate internal format in `CsvFileAccountRepository` (`~/.finance-app/data/`), whose header is currently `accountId,bankName,accountNumber,accountType,balance,currency,importedAt`.
> Do not conflate them — the column orders differ and the persistence file carries `accountId` and `importedAt`, which the import file does not.

- `AccountCsvRecord` gains a sixth `@CsvBindByName(column = "asOfDate")` field so OpenCSV binds the new import column
- `CsvFileAccountRepository.HEADER` gains `asOfDate` as the last column → `accountId,bankName,accountNumber,accountType,balance,currency,importedAt,asOfDate`
- `formatLine()` writes the value as ISO `YYYY-MM-DD`; `parseLine()`'s hardcoded `line.split(",", 7)` limit must become `8`

### Backward compatibility of the persisted file
An `accounts.csv` written before this feature has only 7 columns. Reading it with the new 8-column parser would throw `ArrayIndexOutOfBoundsException`. Required behaviour — **tolerant read**:
- `parseLine()` accepts both 7-column (legacy) and 8-column (current) rows
- A legacy row parses successfully with `asOfDate` as `null`; it is **not** rejected and does not raise a validation error — Tier-2 validation applies to *import* only, never to reads of the already-persisted file
- A `null` `asOfDate` renders as an empty cell on the accounts screen
- The file is silently upgraded to the 8-column header on the next write
- Consequently `asOfDate` is nullable on `BankAccount` (no `Objects.requireNonNull`), even though it is required at import time

### Display
- A new "As Of Date" column appears on the accounts screen, positioned **last** in the table (after Balance/Currency)
- Each row renders its account's `asOfDate` as `YYYY-MM-DD`
- Header and cells follow the existing `data-testid` conventions in `.claude/rules/thymeleaf-templates.md`

### CSV converter tool (`tools/csv-converter`)
`tools/csv-converter` is a standalone Maven module that maps a bank's native export into the app's import format. It is the front door to the import path, so once `asOfDate` is a required Tier-1 column, **every file this tool produces would fail schema validation until it is updated**. It is in scope for this feature.

Two problems, one of them a value-format mismatch rather than a missing column:

- The tool's output header is built from `mapping.json`'s values, so a mapping entry for `asOfDate` is needed for the column to appear at all.
- The real source data already carries the date, but **not in ISO**: `sample/accounts.csv` has an `as_of_date` column formatted `dd-MMM-yy` (e.g. `21-Jun-26`). `CsvConverter.convert()` is a pure rename passthrough — `outputValues.add(inputRow[idx])` copies the cell verbatim with no value transformation — so mapping the column straight through would emit `21-Jun-26` and the app would reject **every row** as a Tier-2 invalid date.

Required behaviour — **add optional value transformation to the converter**:

- `mapping.json` must support an extended object form alongside the existing plain-string form, so existing mapping files keep working unchanged:
  ```json
  {
    "bank": "bankName",
    "as_of_date": { "to": "asOfDate", "type": "date", "from": "dd-MMM-yy" }
  }
  ```
  A plain `"input": "output"` string entry keeps today's behaviour (verbatim copy). An object entry with `"type": "date"` parses the input value using the `from` pattern and writes it as ISO `YYYY-MM-DD`.
- Output column order continues to follow the order of keys in `mapping.json`, unchanged.
- A value that fails to parse against the declared `from` pattern raises a `CsvConversionException` naming the column, the offending value, and the row number — the converter fails loudly rather than emitting a bad date that would only surface later as an opaque row-rejection at upload.
- Two-digit years in `dd-MMM-yy` resolve via the standard `java.time` base year, so `21-Jun-26` → `2026-06-21`.
- Update `sample/mapping.json` and the sample CSVs to include the new column, and update `README.md`: the "All five output columns required" table becomes six rows (adding `asOfDate`, constraint: ISO `YYYY-MM-DD`, not in the future), plus a short section documenting the object mapping form.
- Add unit coverage to `CsvConverterTest` for: the plain-string form still copying verbatim, the date-transform form producing ISO, and the unparseable-value failure.

> **Release note (not a code task):** users with their own `mapping.json` must add an `asOfDate` entry before their next import, or the upload fails Tier-1 schema validation.

## Failure / edge cases
- Blank or missing `asOfDate` in a row: Tier-2 row-level validation error — the row is rejected and listed in the existing row-errors banner, exactly like an invalid `balance` or `accountType`
- Unparseable date (wrong format, e.g. `31/08/2026`, `2026-13-01`, free text): Tier-2 row-level validation error, same handling as above
- A date **after today** is invalid: rejected as a Tier-2 row-level error and listed in the row-errors banner. Today's date itself is valid; past dates are valid. "Today" is evaluated against the system default timezone; the comparison must be made through an injectable `java.time.Clock` (defaulted to `Clock.systemDefaultZone()` via a `@Bean`) so the boundary cases are deterministically testable rather than depending on the wall clock at test time
- Header missing the `asOfDate` column: Tier-1 schema error, fail-fast, shown in the existing schema-error banner
- Rows failing `asOfDate` validation do not block valid rows in the same file — the existing accumulative Tier-2 behaviour is preserved

## Out of scope
These are **deliberate exclusions confirmed by the user**, not oversights. Do not implement them:
- **Sorting by As Of Date — explicitly NOT included.** The "As Of Date" column must not be a sort link, and `AS_OF_DATE` must **not** be added to `AccountSortField`. Existing sort behaviour (Bank Name, Account Type, Balance) is unchanged.
- **Filtering by As Of Date — explicitly NOT included.** The filter bar continues to cover only Bank Name, Account Number, and Account Type. No date filter, no date-range filter.
- Replacing, removing, or displaying `importedAt` — it stays as-is and stays hidden
- Alternative date formats, locale-specific formatting, or timezone handling **in the app** — the importer accepts ISO `YYYY-MM-DD` only, and the UI renders ISO only. Non-ISO source dates are the converter's problem to solve, not the importer's; do not add lenient date parsing to `AccountCsvRowValidator`
- Value transformations in the converter other than `"type": "date"` — no number, currency, or string transforms in this feature
- Any editing of `asOfDate` from the UI

## Dependencies
- Feature 2.2 (CSV validation, two-tier schema + row-level) — COMPLETE. `asOfDate` validation must reuse the existing `AccountCsvSchemaValidator` / `AccountCsvRowValidator` / `RowValidationError` machinery rather than introducing a parallel error path.
- Features 2.3–2.5 (Column sorting, Filter bar, Filtered total) — COMPLETE. Adding the column must not regress the sort links, filter bar, or the `<tfoot>` total row; the total row's colspan must be adjusted for the extra column.
- Existing E2E fixtures embed the 5-column CSV header and will need the new column appended — treat this as required regression work. Affected: `e2e/tests/helpers/csvFixtures.ts` (shared), `accounts.spec.ts`, `account-sort.spec.ts`, `account-filter.spec.ts`, `account-total.spec.ts`, `csv-validation.spec.ts`.
- **Constructor and factory signature change — required regression work.** `asOfDate` is a new field on `BankAccount`, so both the positional constructor and the static `BankAccount.create(...)` factory gain a parameter. Every call site breaks, including existing unit/MockMvc tests that build fixtures. Affected test files embedding the CSV header or constructing accounts directly: `OpenCsvAccountParserTest`, `CsvFileAccountRepositoryTest`, `AccountControllerTest`, plus the sort/filter service and controller test classes.
- Adding the column must not regress the two-tier validation banners, the empty-state row, or the existing sort/filter query-parameter round-trip.
- **`tools/csv-converter` is a separate Maven module with its own `pom.xml` and its own test suite** — it is not built by the app's root `mvn test`. Build and test it explicitly (`cd tools/csv-converter && mvn package -q`). Its sample CSVs under `sample/` are gitignored; `mapping.json` and `README.md` are tracked.
