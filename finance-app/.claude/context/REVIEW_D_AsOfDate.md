## Verdict: REQUEST_CHANGES

Reviewed `git diff main...feature/D_AsOfDate` against `PLAN_D_AsOfDate.md` (round 3).

FR-1 (BankAccount + persistence), FR-2 (schema/row validation + parser), FR-3 (UI column), and FR-4 (csv-converter tool — `ColumnDescriptor`, `CsvConverter` overload, `Main.java`, `sample/mapping.json`) are all correctly implemented and match the plan. The two blocking E2E gaps from round 2 remain entirely unresolved.

---

### Blocking Issues

- **[HIGH] `e2e/tests/account-asofdate.spec.ts` is missing entirely.**
  Plan §5.1 requires this new file; §5.3 specifies E2E-1 through E2E-6 covering FR-2 import validation (blank date, non-ISO date, future date, schema error) and FR-3 display (As Of Date column visible, tfoot column count). The file does not exist — `e2e/tests/` still contains only `account-filter.spec.ts`, `account-sort.spec.ts`, `account-total.spec.ts`, `accounts.spec.ts`, `csv-validation.spec.ts`, `helpers/`, and `nav.spec.ts`. AC-1 through AC-6 have no E2E coverage at all.

- **[HIGH] All existing E2E fixture strings still use 5-column headers — every import-based E2E test will fail against the new schema validator.**
  Plan §5.4 explicitly lists every fixture that must gain `,asOfDate` in its header and a valid past ISO date on every data row. None of those updates were made:
  - `e2e/tests/helpers/csvFixtures.ts` — all exported constants (`VALID_CSV`, `HEADER_ONLY_CSV`, `EXTRA_COLUMN_CSV`, `BAD_ACCOUNT_TYPE_ROW1_CSV`, `BLANK_BANK_NAME_ROW2_CSV`, `BAD_BALANCE_ROW1_CSV`, `MULTI_ROW_ERRORS_CSV`, `MIXED_VALID_INVALID_CSV`) still have `bankName,accountNumber,accountType,balance,currency` (5 columns).
  - `e2e/tests/account-sort.spec.ts:40` — `SORT_FIXTURE_CSV` still 5-column.
  - `e2e/tests/account-filter.spec.ts:45` — `FILTER_FIXTURE_CSV` still 5-column.
  - `e2e/tests/account-total.spec.ts:42` — `TOTAL_FIXTURE_CSV` still 5-column.
  - `e2e/tests/accounts.spec.ts:20,27,31,34` — all inline fixtures still 5-column.

  `AccountCsvSchemaValidator.EXPECTED_COLUMNS` now requires 6 columns including `asOfDate`. Any 5-column import CSV throws `CsvSchemaException`. Every previously-green E2E test that imports CSV data (`account-sort`, `account-filter`, `account-total`, happy-path and row-error tests in `accounts` and `csv-validation`) will fail with an unexpected schema error banner instead of proceeding to the tested behaviour.

  Note: `csv-validation.spec.ts` uses fixtures from `csvFixtures.ts` and its own inline strings — none contain `asOfDate`.

**Root cause of the cycle:** The dev agent is rule-prohibited from writing to `e2e/`. Both fixes belong to the E2E agent. The orchestrator must run the E2E agent before the reviewer can approve — or a human must apply the E2E changes manually and re-run the reviewer.

---

### Suggestions (non-blocking)

- **[MINOR] `CsvFileAccountRepositoryTest` T5.6 and T5.7 use reflection** (`BankAccount.class.getDeclaredMethod("asOfDate")`) even though `asOfDate()` is now a public method. Direct calls (`loaded.get(0).asOfDate()`) would be cleaner and less fragile.

- **[MINOR] `CsvConverterTest` T-7 and T-8 use reflection** to invoke `ColumnDescriptor` and the `convert(LinkedHashMap<ColumnDescriptor>)` overload for the same reason. Both are now directly callable; reflection can be removed.

- **[MINOR] R-10 comment says `"2026-08-31"` is "today per system clock on 2026-08-31"** but the project date is 2026-09-03 — the test validates a past date, not today's boundary. A fixed `Clock` injected via the single-arg constructor would make the today-boundary AC-5 test meaningful.
