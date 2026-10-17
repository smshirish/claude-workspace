# Feature Request: PortfolioView

## What It Does

A top-level Portfolio screen showing the user's latest IBKR portfolio snapshot.
Snapshots are stored as CSV files (one per as-of date) and are populated by one of
two independent ingestion mechanisms: manual file upload or an IBKR Flex Query
report. Displaying the portfolio is always showing the most recent snapshot on file.

---

## User Trigger

- The user clicks the **Portfolio** menu item, which sits next to **Accounts** in the
  top navigation.
- The Portfolio screen opens directly to the latest snapshot (if any), together with
  an upload form.
- The user manually triggers a snapshot by uploading a CSV file or by running an
  IBKR Flex Query and importing the result. There is **no automatic or scheduled
  ingestion**.

---

## Ingestion Mechanisms (two independent features — plan agent decides the split)

### Mechanism A — Manual File Upload
- A dedicated upload form lives on the Portfolio screen (not shared with the
  Accounts import).
- The form follows the Accounts import conventions:
  - `<input type="file" accept=".csv">` with a matching Import button and error
    banners displayed inline next to the form.
  - `data-testid` naming mirrors the Accounts pattern:
    `portfolio-import-form`, `portfolio-file-input`, `portfolio-import-button`.
- Existing Accounts import for reference: `POST /accounts/import` in
  `AccountController`, `ImportAccountsUseCase`, `CsvSchemaException` and
  `CsvRowValidationException` banners.

### Mechanism B — IBKR Flex Query
- The user runs the Flex Query on IBKR manually (outside the app), then provides
  the resulting file to the app.
- The Flex Query report and the manual upload deliver the **same set of attributes**
  (see Fields below).
- The ingestion pipeline for the Flex Query file is a separate feature from the
  manual upload, but the validation, storage, and display logic is shared.

---

## Fields

All ten fields must be present as columns in the uploaded file.
**Column headers use IBKR native naming** (title-case with spaces), matching the
output of an IBKR Flex Query report exactly. The schema validator must compare
headers case-sensitively against these names.

[NEEDS CLARIFICATION: confirm exact IBKR Flex Query CSV header strings before the
Dev stage — names below are the expected IBKR defaults but may vary by report
configuration]

| IBKR Column Header | Required? | Notes |
|---|---|---|
| `Account ID` | Required, non-blank | IBKR account identifier; subject to anonymisation (see below) |
| `Symbol` | Required, non-blank | Ticker symbol |
| `Asset Class` | Required, non-blank | e.g. STK, OPT, CASH |
| `Quantity` | Required, non-blank | Number of units held; must be a valid decimal |
| `Price` | Required, non-blank | Price per unit; must be a valid decimal |
| `Market Value` | Required, non-blank | quantity × price; must be a valid decimal |
| `Currency` | Required, non-blank | ISO 4217 code |
| `Report Date` | Required, non-blank | As-of date of the snapshot; must be YYYY-MM-DD and **not in the future** |
| `Cost Basis` | Column must exist; value may be blank | IBKR omits for some positions (e.g. transfers in) |
| `Unrealized P&L` | Column must exist; value may be blank | IBKR omits for some positions |

---

## Row Validation Rules (mirrors Accounts pattern)

- **Blank check:** every Required field must be non-blank; error per row per column.
- **Decimal parse:** `Quantity`, `Price`, `Market Value` must parse as `BigDecimal`.
- **Date parse:** `Report Date` must parse as `LocalDate` (YYYY-MM-DD).
- **Future date:** `Report Date` must not be after today (same rule as `asOfDate` in
  `AccountCsvRowValidator`).
- **Nullable columns:** `Cost Basis` and `Unrealized P&L` columns must be present in
  the header but their row values may be blank — blank is stored as-is.
- All errors are collected across all rows and returned together as
  `CsvRowValidationException` (same pattern as accounts).

---

## Storage

- **Format:** CSV.
- **Granularity:** one immutable file per upload, keyed by as-of date (the
  `Report Date` field — all rows in a valid upload must share the same date).
- **Re-upload of same date:** replaces the existing file for that date.
- **"Current" snapshot:** the file with the latest as-of date.
- **Storage path:** `${user.home}/.finance-app/data/portfolio/<YYYY-MM-DD>.csv`
  — [NEEDS CLARIFICATION: confirm path and filename pattern before implementation]
- **History:** each stored file is the raw material for the future as-of date picker
  (see Backlog). No separate history table is needed now.

---

## Display — Portfolio Table

### Columns shown
| Column | Sortable? | Notes |
|---|---|---|
| Symbol | ✅ | Default sort: Symbol ASC |
| Asset Class | ✅ | |
| Quantity | — | |
| Price | — | |
| Market Value | ✅ | |
| Currency | — | |
| Cost Basis | — | Blank if not provided |
| Unrealized P&L | ✅ | Blank if not provided |
| Report Date | — | Per-row date |

### Sorting
- Sortable columns: **Symbol**, **Asset Class**, **Market Value**, **Unrealized P&L**.
- Behaviour mirrors the Accounts table: toggle ASC/DESC on repeated click, `↑ ↓`
  indicator in the column header, `data-testid="sort-<columnName>"` on each
  sortable header link (camelCase column name, e.g. `sort-symbol`, `sort-assetClass`,
  `sort-marketValue`, `sort-unrealizedPnl`).

### Filter form
- Filter fields: **Symbol** (text, partial match), **Asset Class** (text, partial
  match).
- Behaviour mirrors the Accounts filter form: GET request, filters preserved in URL
  params, Clear link resets to unfiltered view.
- `data-testid` names: `portfolio-filter-form`, `portfolio-filter-symbol-input`,
  `portfolio-filter-assetClass-input`, `portfolio-filter-submit-button`,
  `portfolio-filter-clear-link`.

### Table footer — Total Market Value
- A `<tfoot>` row shows the **sum of `Market Value`** across all displayed rows.
- `data-testid="portfolio-total-market-value"`.
- **Multi-currency note:** the sum is a raw arithmetic total across all currencies.
  No currency conversion is applied. The footer shows the numeric total only; the
  currency column is left blank in the footer.
  [NEEDS CLARIFICATION: confirm that a raw cross-currency total is acceptable, or
  whether a per-currency breakdown is needed]

### As-of date display
- The as-of date of the displayed snapshot is shown prominently above the table
  (e.g. "Portfolio as of YYYY-MM-DD").
- If the as-of date is more than 14 days before today, a **"Stale data"** warning
  banner is shown alongside it. `data-testid="portfolio-stale-warning"`.

---

## Happy Path

1. User opens Portfolio screen.
2. Latest snapshot is displayed as a table with its as-of date shown.
3. User uploads a valid CSV.
4. On success: a **success banner** is shown (e.g. "Portfolio imported — as of
   YYYY-MM-DD") and the page **redirects to `GET /portfolio`**, which shows the
   newly imported snapshot. `data-testid="portfolio-import-success"`.
5. If the displayed snapshot's as-of date is more than 2 weeks in the past, the
   stale-data warning is shown.

---

## Failure / Edge Cases

| Scenario | Expected behaviour |
|---|---|
| File has missing required columns | Reject with schema error banner; nothing stored |
| File has malformed / unparseable rows | Reject with row-error banners (row + column); nothing stored |
| File is empty (no rows after header) | Reject with error banner; nothing stored |
| `Report Date` is in the future | Reject with row-error banner; nothing stored |
| Re-upload of an existing as-of date | Replace the stored file; show success banner on redirect |
| No snapshot on file (first visit / empty state) | Show "No portfolio uploaded yet" message + upload form. `data-testid="portfolio-empty-state"` |
| Snapshot is older than 14 days | Show snapshot with stale-data warning; upload form still accessible |

All uploads are **all-or-nothing**. On any parse or validation error, no data is
persisted.

---

## Anonymisation

The `Account ID` field in a portfolio snapshot may contain a real IBKR account ID
(e.g. `U1234567`). The same anonymisation pattern used for Accounts applies:

- A JSON mapping file at `${finance.anonymisation.mapping-file}` (the same
  `MAPPING_FILE` env var as Accounts) maps anonymous IDs to real ones.
- At display time, each row's `Account ID` is resolved through this mapping before
  rendering. If no mapping entry exists, the raw value is shown unchanged.
- This reuses `AccountIdResolver` / `JsonFileAccountIdResolver`. The planning agent
  decides whether to create a separate `PortfolioIdResolver` port or reuse the
  existing one — both are valid.
- If the mapping file is absent, account IDs are shown as-is (no error).

---

## Navigation

- Portfolio is a **top-level menu item** next to Accounts.
- It is a standalone screen, not a sub-tab of Accounts.
- The `nav.html` fragment must be updated to add the Portfolio link with
  `data-testid="nav-portfolio-link"` and active-page highlighting when
  `activePage == 'portfolio'`.

---

## Out of Scope

| Item | Reason |
|---|---|
| Automatic or scheduled snapshots | Out of scope; on-demand only |
| Live IBKR connection or trade execution | Out of scope; handled entirely outside the app |
| Editing positions in the app | Out of scope |

---

## Backlog (deferred — not implemented in this iteration)

| Item | Notes |
|---|---|
| As-of date picker UI | User can select a historical snapshot to view; the stored files are already the history. Deferred pending the as-of date picker feature. |
| IBKR live connection / thread execution | Listed for completeness; not planned |

---

## Dependencies

| Dependency | Status |
|---|---|
| AsOfDateVersioning | Related parallel pattern (immutable snapshots keyed by date, replace-on-re-import). Raw request at `pipeline/REQUEST_AsOfDateVersioning.md`. Not yet spec'd. **Not a blocker.** |

---

## Notes for the Planning Agent

- The two ingestion mechanisms (manual upload and Flex Query) must be broken into
  independent sub-features. The planning agent decides the split.
- All shared logic (validation, storage write, display) should be factored once and
  reused by both ingestion paths.
- The `[NEEDS CLARIFICATION: ...]` annotations (IBKR column names, storage path,
  multi-currency total) must be resolved before the Dev stage.
- The `AccountIdResolver` reuse vs. new `PortfolioIdResolver` port decision is left
  to the planning agent.
