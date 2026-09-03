## Verdict: REQUEST_CHANGES

Reviewed `git diff main...feature/D_AsOfDate` against `PLAN_D_AsOfDate.md`.

FR-1 (BankAccount + persistence), FR-2 (validation), and FR-3 (UI column) are correctly implemented and match the plan.
FR-4 (csv-converter tool) is **not implemented**, and the E2E layer has two blocking gaps.

---

### Blocking Issues

- **[CRITICAL] `ColumnDescriptor` record not created** (`tools/csv-converter/src/main/java/com/finance/tools/csvconverter/ColumnDescriptor.java`)
  `CsvConverter.java` in the diff contains only the old `Map<String, String>` overload. There is no `ColumnDescriptor` class, no `convertInternal` helper, and no `LinkedHashMap<String, ColumnDescriptor>` overload. Tests T-7 and T-8 in `CsvConverterTest.java` use reflection to find `ColumnDescriptor` and both fail with `java.lang.ClassNotFoundException: com.finance.tools.csvconverter.ColumnDescriptor` (confirmed in the committed surefire report under `target/`). Violates FR-4, AC-8, AC-9.

- **[CRITICAL] `Main.java` not updated for date transforms** (`tools/csv-converter/src/main/java/com/finance/tools/csvconverter/Main.java`)
  Still reads `mapping.json` as `Map<String, String>` and calls the old `convert()` overload. Map-typed entries (the `as_of_date` date-transform entry) cannot be deserialized or processed. Violates FR-4 §3.6.

- **[CRITICAL] `sample/mapping.json` not updated** (`tools/csv-converter/sample/mapping.json`)
  Still has 5 columns with plain string values only; the `asOfDate` date-transform entry (`{ "to": "asOfDate", "type": "date", "from": "dd-MMM-yy" }`) specified in the plan is absent. Violates FR-4.

- **[CRITICAL] `e2e/tests/account-asofdate.spec.ts` not written**
  Plan §5.1 defines a new E2E spec file covering E2E-1 through E2E-6 (valid import, schema error, blank date, non-ISO date, future date, tfoot column count). The file does not appear in the diff at all. Violates §5.3.

- **[CRITICAL] Existing E2E test fixtures not updated with `asOfDate` column** — plan §5.4 requires every shared and inline CSV fixture to gain a 6th column. None of the following were updated:
  - `e2e/tests/helpers/csvFixtures.ts` — all 11 constants still use 5-column headers (e.g. `VALID_CSV`, `EXTRA_COLUMN_CSV`, `MISSING_COLUMN_CSV`, etc.)
  - `e2e/tests/account-filter.spec.ts:4894` — `FILTER_FIXTURE_CSV` has 5-column header; importing it will now throw `CsvSchemaException` (schema requires 6 columns), breaking all 6 filter E2E tests.
  - `e2e/tests/account-sort.spec.ts:5017` — `SORT_FIXTURE_CSV` has 5-column header; same breakage for all 8 sort E2E tests.
  - `e2e/tests/accounts.spec.ts:5276` — `VALID_CSV`, `VALID_CSV_2`, `HEADER_ONLY_CSV`, `BAD_ACCOUNT_TYPE_CSV` all use 5-column headers.
  - `e2e/tests/csv-validation.spec.ts` — inline CSVs and `csvFixtures.ts` references likewise unupdated.

---

### Suggestions (non-blocking)

- **[MINOR]** The comment in `AccountCsvRowValidator.java:6819` still reads `"// Mandatory blank checks for all 5 columns"` — should say 6.

- **[MINOR]** `CsvConverterTest` T-7/T-8 use reflection instead of a direct type reference because `ColumnDescriptor` didn't exist when the test was written. Once `ColumnDescriptor` is implemented, these tests should be rewritten to call the API directly for readability.
