# Implementation Plan: PortfolioView

---

## Planning Decisions

**Scope**: Mechanism A (manual file upload) only. Mechanism B (IBKR Flex Query file ingestion) is deferred — the shared validation, storage, and display logic implemented here covers both paths when B is added.

**AccountIdResolver reuse**: The existing `AccountIdResolver` port (`String resolve(String id)`) is fully generic and reused for portfolio Account ID anonymization. No separate `PortfolioIdResolver` port is created.

**Multi-currency total**: Raw arithmetic sum across all currencies; no conversion. Confirmed acceptable per request.

**Storage format**: Internal CSV (`~/.finance-app/data/portfolio/<YYYY-MM-DD>.csv`) uses app-internal column names, not IBKR names. Parser translates on import; repository reads its own format.

**[NEEDS CLARIFICATION before Dev]**: Confirm exact IBKR Flex Query column header strings and their positional order against a real Flex Query export. The ten names used in this plan (`Account ID`, `Symbol`, `Asset Class`, `Quantity`, `Price`, `Market Value`, `Currency`, `Report Date`, `Cost Basis`, `Unrealized P&L`) are the request's stated IBKR defaults and must be verified.

---

## 1. Feature Requirements

### FR-1: Manual CSV File Upload with Two-Tier Validation
The Portfolio screen provides a file upload form (`portfolio-import-form`). When the user submits a CSV:
- **Tier 1 — Schema**: The parser checks that the header contains the 10 required IBKR columns in the expected order (case-sensitive). Any mismatch throws `CsvSchemaException` immediately; nothing is stored.
- **Tier 2 — Row**: All data rows are collected and validated together. Blank checks on the 8 required fields; decimal parse checks on `Quantity`, `Price`, `Market Value`; date parse and future-date check on `Report Date`; cross-row check that all rows share the same `Report Date`. All errors are collected and thrown as `CsvRowValidationException`; nothing is stored.
- **Empty file**: If no data rows follow the header, a `PortfolioImportException` is thrown; nothing is stored.
- **All-or-nothing**: Persistence is only reached when both tiers pass.

### FR-2: Portfolio Snapshot Storage
On successful import the positions are stored as an immutable snapshot CSV at `${user.home}/.finance-app/data/portfolio/<YYYY-MM-DD>.csv` (where `YYYY-MM-DD` is the shared `Report Date` of the imported rows). Re-importing a CSV whose `Report Date` matches an existing file replaces that file. The "current" snapshot displayed is always the file with the lexicographically latest filename (ISO dates sort chronologically).

### FR-3: Portfolio Display — Table, Sort, Filter, Total
The Portfolio screen at `GET /portfolio` shows the latest stored snapshot:
- **Empty state**: If no snapshot exists, show `data-testid="portfolio-empty-state"` and the upload form; no table rendered.
- **As-of date**: Display "Portfolio as of YYYY-MM-DD" above the table. `data-testid="portfolio-asof-date"`.
- **Stale warning**: If the snapshot's as-of date is more than 14 days before today, show `data-testid="portfolio-stale-warning"` alongside the date.
- **Table columns**: Symbol, Asset Class, Quantity, Price, Market Value, Currency, Cost Basis (blank if null), Unrealized P&L (blank if null), Report Date.
- **Sort**: Symbol, Asset Class, Market Value, Unrealized P&L are sortable. Default: Symbol ASC. Toggle ASC/DESC on repeated click; `↑`/`↓` indicator; `data-testid="sort-<camelCase>"` on each sortable header link.
- **Filter**: Symbol (partial, case-insensitive) and Asset Class (partial, case-insensitive). GET params preserved in URL. Clear link resets. `data-testid` names per request spec.
- **Total Market Value footer**: `<tfoot>` sums `Market Value` of displayed rows. `data-testid="portfolio-total-market-value"`. Raw arithmetic sum; currency column blank in footer.
- **Success banner**: On redirect after successful import, flash attribute renders `data-testid="portfolio-import-success"` (e.g. "Portfolio imported — as of YYYY-MM-DD").

### FR-4: Navigation
A "Portfolio" link is added to `nav.html` next to "Accounts". `data-testid="nav-portfolio-link"`. Active-page highlighting applies when `activePage == 'portfolio'`.

### FR-5: Account ID Anonymization
At display time, each position's `Account ID` is resolved through `AccountIdResolver.resolve()`. If no mapping entry exists the raw value is shown unchanged. If the mapping file is absent, account IDs are shown as-is with no error.

---

## 2. Acceptance Criteria

| ID | Criterion |
|---|---|
| AC-1 | A valid 10-column IBKR CSV with uniform `Report Date` uploads successfully; page redirects to `GET /portfolio` showing the new snapshot with `data-testid="portfolio-import-success"` visible |
| AC-2 | A CSV whose header is missing a required column is rejected with `data-testid="schema-error-banner"` visible; no file is stored |
| AC-3 | A CSV row with an unparseable decimal in `Quantity` is rejected with `data-testid="row-errors-banner"` visible; no file is stored |
| AC-4 | A CSV row with `Report Date` after today is rejected with `data-testid="row-errors-banner"` visible; no file is stored |
| AC-5 | A CSV with no data rows (header only) is rejected with `data-testid="portfolio-import-error"` visible; no file is stored |
| AC-6 | A CSV containing two rows with different `Report Date` values is rejected with `data-testid="row-errors-banner"` visible; no file is stored |
| AC-7 | Re-uploading a CSV whose `Report Date` matches an existing snapshot replaces the stored file; the new snapshot is shown after redirect |
| AC-8 | On first visit with no snapshot on file, `data-testid="portfolio-empty-state"` is visible and `data-testid="accounts-table"` is absent |
| AC-9 | Clicking `data-testid="sort-symbol"` twice renders rows in descending symbol order and shows `↓` indicator; `sort-assetClass` and `sort-marketValue` show no indicator |
| AC-10 | Entering a partial symbol in `data-testid="portfolio-filter-symbol-input"` and submitting shows only matching rows |
| AC-11 | `data-testid="portfolio-total-market-value"` shows the arithmetic sum of `Market Value` for displayed (possibly filtered) rows |
| AC-12 | When the snapshot's `Report Date` is 15 days before today, `data-testid="portfolio-stale-warning"` is visible |
| AC-13 | When the snapshot's `Report Date` is 10 days before today, `data-testid="portfolio-stale-warning"` is absent |
| AC-14 | `data-testid="nav-portfolio-link"` is present on every authenticated page and navigates to `/portfolio` |
| AC-15 | An `Account ID` present in the anonymisation mapping file is shown as the resolved value in the table |

---

## 3. Component Breakdown

### 3.1 New Domain Exceptions

- **`PortfolioImportException`** (`domain/exception/`) — NEW  
  Extends `RuntimeException`. Signals empty file, IO read errors, or irrecoverable parse failures. Mirrors `AccountImportException`. Constructors: `(String message)` and `(String message, Throwable cause)`.

### 3.2 New Domain Value Objects / Records

- **`PortfolioPosition`** (`domain/model/`) — NEW  
  Mutable-free class (following `BankAccount` pattern). Fields: `String accountId`, `String symbol`, `String assetClass`, `BigDecimal quantity`, `BigDecimal price`, `BigDecimal marketValue`, `String currency`, `LocalDate reportDate`, `BigDecimal costBasis` (nullable), `BigDecimal unrealizedPnl` (nullable).  
  Factory: `static PortfolioPosition of(String accountId, String symbol, ...)`.  
  Mutator: `PortfolioPosition withResolvedAccountId(String resolved)` (mirrors `BankAccount.withResolvedAccountNumber`).

- **`PortfolioSnapshot`** (`domain/model/`) — NEW  
  `record PortfolioSnapshot(LocalDate asOfDate, List<PortfolioPosition> positions)`.

- **`PortfolioSortField`** (`domain/model/`) — NEW  
  `enum PortfolioSortField { SYMBOL, ASSET_CLASS, MARKET_VALUE, UNREALIZED_PNL }`.

- **`PortfolioSortCriteria`** (`domain/model/`) — NEW  
  `record PortfolioSortCriteria(PortfolioSortField field, SortDirection direction)`.  
  `static final DEFAULT = new PortfolioSortCriteria(PortfolioSortField.SYMBOL, SortDirection.ASC)`.

- **`PortfolioFilterCriteria`** (`domain/model/`) — NEW  
  `record PortfolioFilterCriteria(String symbol, String assetClass)`.

### 3.3 New Domain Services and Ports

**Domain Services** (`domain/service/`):

- **`PortfolioCsvSchemaValidator`** — NEW  
  `void validate(String[] actualHeader)` — checks that the first 10 positions match the expected IBKR column names (case-sensitive). Throws `CsvSchemaException` on first mismatch or if fewer than 10 columns. Extra columns beyond position 10 are tolerated.  
  `static final String[] EXPECTED_COLUMNS = {"Account ID", "Symbol", "Asset Class", "Quantity", "Price", "Market Value", "Currency", "Report Date", "Cost Basis", "Unrealized P&L"}`.  
  [NEEDS CLARIFICATION: verify these are the exact IBKR Flex Query headers before Dev]

- **`PortfolioCsvRowValidator`** — NEW  
  `List<RowValidationError> validate(List<String[]> rawRows)`. Uses `Clock` for future-date check (same pattern as `AccountCsvRowValidator`).  
  Per-row: blank check on columns 0–7 (required fields); decimal parse on columns 3 (`Quantity`), 4 (`Price`), 5 (`Market Value`); date parse + future-date check on column 7 (`Report Date`).  
  Nullable columns: columns 8 (`Cost Basis`) and 9 (`Unrealized P&L`) — blank is valid.  
  Cross-row: after per-row loop, if there are two or more distinct successfully-parsed `Report Date` values, a `RowValidationError` is added for each row whose date differs from the first valid date, with message `"All rows must share the same Report Date"`.

**Ports (In)** (`domain/port/in/`):

- **`ImportPortfolioUseCase`** — NEW  
  `record ImportPortfolioCommand(InputStream fileContent, String fileName) {}`  
  `LocalDate importPortfolio(ImportPortfolioCommand command)` — returns the as-of date of the stored snapshot (used by controller to build the success-banner flash message).

- **`GetLatestPortfolioUseCase`** — NEW  
  `Optional<PortfolioSnapshot> getLatestPositions(PortfolioSortCriteria criteria)` — returns sorted positions wrapped in a snapshot, or `Optional.empty()` if no snapshot exists.

- **`FilterPortfolioUseCase`** — NEW  
  `Optional<PortfolioSnapshot> filterPositions(PortfolioFilterCriteria filterCriteria, PortfolioSortCriteria sortCriteria)` — filters then sorts; returns snapshot with as-of date preserved, or `Optional.empty()` if no snapshot exists.

**Ports (Out)** (`domain/port/out/`):

- **`PortfolioFileParser`** — NEW  
  `List<PortfolioPosition> parse(InputStream inputStream, String fileName)` — mirrors `AccountFileParser`. Performs both tiers of validation; throws `CsvSchemaException`, `CsvRowValidationException`, or `PortfolioImportException`.

- **`PortfolioRepository`** — NEW  
  `void save(LocalDate asOfDate, List<PortfolioPosition> positions)` — writes internal-format CSV to `<portfolio-dir>/<asOfDate>.csv`.  
  `Optional<PortfolioSnapshot> findLatest()` — scans portfolio directory, returns snapshot from the latest-dated file, or `Optional.empty()` if directory is empty or absent.

### 3.4 New Infrastructure Adapters (Out-bound / Persistence)

**`OpenCsvPortfolioParser`** (`infrastructure/adapter/out/persistence/`) — NEW  
Implements `PortfolioFileParser`. Mirrors `OpenCsvAccountParser` two-pass approach:
1. Strip UTF-8 BOM.
2. Pass 1: read header only → `PortfolioCsvSchemaValidator.validate(header)`.
3. Collect data rows (skip header).
4. Pass 2: `PortfolioCsvRowValidator.validate(dataRows)` → throw `CsvRowValidationException` if errors.
5. Map each `String[]` row to `PortfolioPosition` (decimal and date parsing using already-validated values).  
   - `costBasis`: blank → null; else `new BigDecimal(row[8])`.  
   - `unrealizedPnl`: blank → null; else `new BigDecimal(row[9])`.

**`CsvFilePortfolioRepository`** (`infrastructure/adapter/out/persistence/`) — NEW  
Implements `PortfolioRepository`.  
- Constructor: `@Value("${finance.storage.portfolio-dir}") String portfolioDir` → `Path portfolioDir`.  
- Internal CSV header: `accountId,symbol,assetClass,quantity,price,marketValue,currency,reportDate,costBasis,unrealizedPnl`.  
- `save()`: `Files.createDirectories(portfolioDir)`, then write header + formatted rows to `portfolioDir/<asOfDate>.csv`.  
- `findLatest()`: if directory absent or contains no `*.csv` files → `Optional.empty()`. Otherwise: list files matching pattern `\d{4}-\d{2}-\d{2}\.csv`, sort lexicographically, take last. Read with `Files.readAllLines`, skip header, parse each line (split on `,` with limit 10; blank `costBasis`/`unrealizedPnl` → null). Wrap in `PortfolioSnapshot`.
- `PortfolioImportException` is thrown on IO failure.

### 3.5 New Web Adapters (In-bound / Controllers)

**`PortfolioController`** (`infrastructure/adapter/in/web/`) — NEW

```
GET /portfolio
  params: sortField, sortDir, symbol (default ""), assetClass (default "")
  1. Resolve PortfolioSortCriteria from params (default: SYMBOL ASC).
  2. If symbol or assetClass non-blank → filterPortfolioUseCase.filterPositions(criteria, sort)
     else → getLatestPortfolioUseCase.getLatestPositions(sort)
  3. If Optional.empty(): model ← empty-state flag; return "portfolio".
  4. Else: resolveIds(positions); compute totalMarketValue; compute isStale
     (asOfDate.isBefore(LocalDate.now(clock).minusDays(14))); populate model.
  Model attributes: positions, asOfDate, totalMarketValue, isStale, activeSortField,
    activeSortDir, filterSymbol, filterAssetClass, clearFilterUrl, importSuccess (from flash)

POST /portfolio/import
  Try importPortfolioUseCase.importPortfolio(command) → LocalDate asOfDate
    → redirectAttributes.addFlashAttribute("importSuccess", "Portfolio imported — as of " + asOfDate)
    → redirect:/portfolio
  Catch CsvSchemaException → model ← schemaError; repopulate snapshot; return "portfolio"
  Catch CsvRowValidationException → model ← rowErrors; repopulate snapshot; return "portfolio"
  Catch PortfolioImportException → model ← importError; repopulate snapshot; return "portfolio"
  Catch IOException → model ← importError "Could not read uploaded file."; return "portfolio"
```

Sort field map: `"symbol" → SYMBOL`, `"assetClass" → ASSET_CLASS`, `"marketValue" → MARKET_VALUE`, `"unrealizedPnl" → UNREALIZED_PNL`.

Private helpers:
- `resolveIds(List<PortfolioPosition>)` — maps `p.withResolvedAccountId(accountIdResolver.resolve(p.accountId()))`.
- `sortPositions(List, PortfolioSortCriteria)` — comparator switch on `PortfolioSortField` (unrealizedPnl: null sorts last; use `Comparator.nullsLast`).

### 3.6 Template / UI Changes

**`portfolio.html`** (`src/main/resources/templates/`) — NEW

Key blocks:

```html
<!-- Nav fragment (activePage = 'portfolio') -->
<header th:replace="~{fragments/nav :: nav('portfolio')}"></header>

<!-- Success banner (flash attribute) -->
<div th:if="${importSuccess}" class="alert-banner alert-banner--success"
     data-testid="portfolio-import-success">
  <p th:text="${importSuccess}"></p>
</div>

<!-- Schema / row / import error banners (mirrors accounts.html) -->
<div th:if="${schemaError}" class="alert-banner alert-banner--error"
     data-testid="schema-error-banner">...</div>
<div th:if="${rowErrors}" class="alert-banner alert-banner--error"
     data-testid="row-errors-banner">...</div>
<div th:if="${importError}" class="alert-banner alert-banner--error"
     data-testid="portfolio-import-error">...</div>

<!-- Upload form -->
<form method="post" th:action="@{/portfolio/import}" enctype="multipart/form-data"
      data-testid="portfolio-import-form">
  <input type="file" name="file" accept=".csv" data-testid="portfolio-file-input"/>
  <button type="submit" data-testid="portfolio-import-button">Import CSV</button>
</form>

<!-- As-of date + stale warning -->
<div th:if="${asOfDate}" class="portfolio-asof">
  <span th:text="|Portfolio as of ${asOfDate}|" data-testid="portfolio-asof-date"></span>
  <span th:if="${isStale}" class="stale-warning" data-testid="portfolio-stale-warning">
    Stale data</span>
</div>

<!-- Filter form -->
<form method="get" th:action="@{/portfolio}" data-testid="portfolio-filter-form"
      class="filter-form">
  <input type="hidden" name="sortField" th:value="${activeSortField}"/>
  <input type="hidden" name="sortDir" th:value="${activeSortDir}"/>
  <input type="text" name="symbol" th:value="${filterSymbol}"
         placeholder="Symbol" data-testid="portfolio-filter-symbol-input"/>
  <input type="text" name="assetClass" th:value="${filterAssetClass}"
         placeholder="Asset Class" data-testid="portfolio-filter-assetClass-input"/>
  <button type="submit" data-testid="portfolio-filter-submit-button">Filter</button>
  <a th:href="${clearFilterUrl}" data-testid="portfolio-filter-clear-link">Clear</a>
</form>

<!-- Empty state -->
<div th:if="${emptyState}" class="empty-state" data-testid="portfolio-empty-state">
  <p>No portfolio uploaded yet. Upload a CSV file to get started.</p>
</div>

<!-- Table (th:unless emptyState) -->
<table class="accounts-table" data-testid="portfolio-table">
  <thead><tr>
    <th><a data-testid="sort-symbol" th:href="...">Symbol<span th:if="...">↑/↓</span></a></th>
    <th><a data-testid="sort-assetClass" th:href="...">Asset Class...</a></th>
    <th>Quantity</th>
    <th>Price</th>
    <th><a data-testid="sort-marketValue" th:href="...">Market Value...</a></th>
    <th>Currency</th>
    <th>Cost Basis</th>
    <th><a data-testid="sort-unrealizedPnl" th:href="...">Unrealized P&amp;L...</a></th>
    <th>Report Date</th>
    <th>Account ID</th>
  </tr></thead>
  <tbody>
    <tr th:each="p : ${positions}" th:attr="data-testid='portfolio-row-' + ${pStat.index}">
      <td th:text="${p.symbol}"></td>
      <td th:text="${p.assetClass}"></td>
      <td th:text="${p.quantity}"></td>
      <td th:text="${p.price}"></td>
      <td th:text="${p.marketValue}"></td>
      <td th:text="${p.currency}"></td>
      <td th:text="${p.costBasis != null ? p.costBasis : ''}"></td>
      <td th:text="${p.unrealizedPnl != null ? p.unrealizedPnl : ''}"></td>
      <td th:text="${p.reportDate}"></td>
      <td th:text="${p.accountId}"></td>
    </tr>
  </tbody>
  <tfoot>
    <tr>
      <td colspan="4"></td>
      <td th:text="${totalMarketValue}" data-testid="portfolio-total-market-value"></td>
      <td colspan="5"></td>
    </tr>
  </tfoot>
</table>
```

**`nav.html`** (`src/main/resources/templates/fragments/`) — MODIFIED  
Add Portfolio link between Accounts and the username span:

```html
<a th:href="@{/portfolio}"
   class="nav-link"
   th:classappend="${activePage == 'portfolio'} ? 'nav-link--active'"
   data-testid="nav-portfolio-link">Portfolio</a>
```

**`application.yml`** (`src/main/resources/`) — MODIFIED  
Add under `finance.storage`:
```yaml
portfolio-dir: ${user.home}/.finance-app/data/portfolio
```

**`PortfolioBeanConfig`** (`infrastructure/config/`) — NEW  
Follows `AccountBeanConfig` pattern: separate `@Bean` per use-case port to avoid `@MockBean` conflicts.

```java
@Bean PortfolioFileParser portfolioFileParser(Clock clock) { ... }
@Bean ImportPortfolioUseCase importPortfolioUseCase(PortfolioFileParser, PortfolioRepository) { ... }
@Bean GetLatestPortfolioUseCase getLatestPortfolioUseCase(PortfolioFileParser, PortfolioRepository) { ... }
@Bean FilterPortfolioUseCase filterPortfolioUseCase(PortfolioFileParser, PortfolioRepository) { ... }
@Bean PortfolioRepository portfolioRepository(@Value("${finance.storage.portfolio-dir}") String dir) { ... }
```

---

## 4. Test Scenarios

### Unit — `PortfolioCsvSchemaValidatorTest`
| # | Input | Expected |
|---|---|---|
| S-1 | Header with all 10 IBKR columns in correct order | No exception |
| S-2 | Header missing `Market Value` (9 columns) | `CsvSchemaException` mentioning missing column count |
| S-3 | Header with `symbol` (lowercase) at position 1 instead of `Symbol` | `CsvSchemaException` mentioning expected `Symbol` found `symbol` |
| S-4 | Header with all 10 columns plus 2 extra columns | No exception (extra columns tolerated) |
| S-5 | Empty header array | `CsvSchemaException` mentioning 0 column(s) |

### Unit — `PortfolioCsvRowValidatorTest`
| # | Input | Expected |
|---|---|---|
| R-1 | 1 valid row, all 10 fields present, `Report Date` = today | Empty error list |
| R-2 | Row with blank `Symbol` (position 1) | `RowValidationError` for `Symbol` with "Field is required" |
| R-3 | Row with `Quantity` = `"not-a-number"` | `RowValidationError` for `Quantity` with "not a valid decimal" |
| R-4 | Row with `Report Date` = tomorrow | `RowValidationError` for `Report Date` with "must not be in the future" |
| R-5 | Row with `Report Date` = `"2024-13-01"` (invalid format) | `RowValidationError` for `Report Date` with "not a valid date" |
| R-6 | Row with blank `Cost Basis` and blank `Unrealized P&L` | No errors (nullable columns) |
| R-7 | Two rows with different `Report Date` values (both valid, both in past) | `RowValidationError` for row 2 with "All rows must share the same Report Date" |
| R-8 | Two rows sharing the same `Report Date` | Empty error list |

### Unit — `PortfolioApplicationServiceTest`
| # | Input | Expected |
|---|---|---|
| A-1 | `getLatestPositions(DEFAULT)` — repository returns snapshot with 3 positions | Returns `Optional<PortfolioSnapshot>` with positions sorted by symbol ASC |
| A-2 | `getLatestPositions(MARKET_VALUE DESC)` — repository returns snapshot | Positions returned in descending market value order |
| A-3 | `getLatestPositions(UNREALIZED_PNL ASC)` — one position has null `unrealizedPnl` | Null sorts last |
| A-4 | `getLatestPositions(DEFAULT)` — repository returns `Optional.empty()` | Returns `Optional.empty()` |
| A-5 | `filterPositions(symbol="AAPL", "")` — snapshot has AAPL and MSFT | Returns snapshot with only AAPL position |
| A-6 | `filterPositions("", assetClass="STK")` — snapshot has STK and OPT | Returns snapshot with only STK positions |
| A-7 | `filterPositions(symbol="aapl", "")` — AAPL present (case-insensitive) | Returns matching row |
| A-8 | `importPortfolio(command)` — parser returns 2 positions, `reportDate=2024-11-01` | `repository.save(2024-11-01, positions)` called; returns `LocalDate.of(2024, 11, 1)` |
| A-9 | `importPortfolio(command)` — parser throws `PortfolioImportException` | Exception propagated unchanged |
| A-10 | `importPortfolio(command)` — parser returns empty list | Throws `PortfolioImportException` "No positions found" |

### Integration — `OpenCsvPortfolioParserTest`
| # | Input | Expected |
|---|---|---|
| P-1 | Valid 10-column IBKR CSV, 2 data rows, both with `reportDate=2024-11-01` | Returns `List<PortfolioPosition>` size 2; fields mapped correctly; `costBasis` and `unrealizedPnl` non-null |
| P-2 | CSV with UTF-8 BOM prefix | Parsed correctly (BOM stripped) |
| P-3 | Header row only (no data rows) | Throws `PortfolioImportException` "No positions found" |
| P-4 | CSV where `Asset Class` header is lowercased `asset class` | Throws `CsvSchemaException` |
| P-5 | CSV with `Quantity` = `"abc"` on row 2 | Throws `CsvRowValidationException` containing 1 error for `Quantity` |
| P-6 | CSV with blank `Cost Basis` on row 1 | `positions.get(0).costBasis()` is null |
| P-7 | CSV with two rows having different `Report Date` values | Throws `CsvRowValidationException` |

### Integration — `CsvFilePortfolioRepositoryTest`
| # | Input | Expected |
|---|---|---|
| P-8 | `save(2024-11-01, positions)` — directory absent | Directory and file created; file contains header + formatted rows |
| P-9 | `findLatest()` — directory contains `2024-11-01.csv` and `2024-11-15.csv` | Returns snapshot with `asOfDate = 2024-11-15` |
| P-10 | `findLatest()` — portfolio directory is absent | Returns `Optional.empty()` |
| P-11 | `findLatest()` — directory exists but contains no `.csv` files | Returns `Optional.empty()` |
| P-12 | `save(2024-11-01, positions)` when `2024-11-01.csv` already exists | Overwrites; file contains only the new rows |

### MockMvc — `PortfolioControllerTest`
| # | Scenario | Expected model attributes / response |
|---|---|---|
| C-1 | `getLatestPositions` returns `Optional.empty()` | `emptyState` = true; `positions` absent; HTTP 200 |
| C-2 | `getLatestPositions` returns snapshot | `positions` present; `emptyState` absent; `asOfDate` present |
| C-3 | `isStale` = true (snapshot date 15 days ago) | `isStale` = true |
| C-4 | `isStale` = false (snapshot date 10 days ago) | `isStale` = false |
| C-5 | POST import throws `CsvSchemaException("bad header")` | `schemaError` = "bad header"; `rowErrors` absent |
| C-6 | POST import throws `CsvRowValidationException([1 error])` | `rowErrors` present (size 1); `schemaError` absent |
| C-7 | POST import throws `PortfolioImportException("No positions")` | `importError` = "No positions"; `rowErrors` absent |
| C-8 | POST import succeeds (returns `2024-11-01`) | Redirect to `/portfolio`; flash attribute `importSuccess` set |

---

## 5. E2E Test Plan

### 5.1 Framework & Configuration

**Framework:** Playwright + TypeScript — existing setup in `e2e/` (no new dependencies).

**Test file:** `e2e/tests/portfolio.spec.ts`

**Shared helpers:** `login` from `./helpers/auth`; `writeTempCsv` (defined inline per spec file, same pattern as `accounts.spec.ts`)

**New helpers:** None

**No new packages required.**

**Breaking changes in existing tests:** `accounts.spec.ts`, `account-sort.spec.ts`, `account-filter.spec.ts`, `account-total.spec.ts` — all use `data-testid="nav-..."` assertions. Adding the Portfolio nav link does not break existing nav assertions; no changes required.

---

### 5.2 Test Fixtures / Data Inventory

| Constant / Fixture | Valid? | Issues | Purpose |
|---|---|---|---|
| `VALID_PORTFOLIO_CSV` | Yes | None | Happy path — 3 positions, single past `Report Date`, all 10 columns |
| `STALE_PORTFOLIO_CSV` | Yes | Report Date 20 days in past | Stale-warning display test |
| `MISSING_COLUMN_CSV` | No | Header missing `Market Value` column | Schema error banner test |
| `FUTURE_DATE_CSV` | No | `Report Date` = tomorrow | Row error — future date test |
| `MIXED_DATES_CSV` | No | Two rows with different `Report Date` values | Row error — date inconsistency test |
| `EMPTY_ROWS_CSV` | No | Header only, no data rows | Import error — empty file test |
| `FILTER_PORTFOLIO_CSV` | Yes | None | 4 positions: 2 STK, 1 OPT, 1 CASH; 2 AAPL, 1 MSFT, 1 CASH; same `Report Date` |

---

### 5.3 E2E Test Cases — Portfolio Empty State & Navigation

> Maps to FR-4, AC-8, AC-14. All tests in `describe('Portfolio — empty state and navigation')`.

| ID | Scenario | Fixture / State | Assertions |
|---|---|---|---|
| E2E-1 | Visit `/portfolio` with no snapshot on file | No CSV stored | `[data-testid="portfolio-empty-state"]` visible; `[data-testid="portfolio-table"]` absent |
| E2E-2 | Click Portfolio nav link from Accounts page | Logged in, on `/accounts` | Navigates to `/portfolio`; `[data-testid="nav-portfolio-link"]` has active class |

---

### 5.4 E2E Test Cases — Upload (Happy Path & Errors)

> Maps to FR-1, FR-2, AC-1–AC-7. All tests in `describe('Portfolio — import')`.

| ID | Scenario | Fixture / State | Assertions |
|---|---|---|---|
| E2E-3 | Upload valid CSV | `VALID_PORTFOLIO_CSV` | Redirects to `/portfolio`; `[data-testid="portfolio-import-success"]` visible; `portfolio-empty-state` absent; `portfolio-table` visible |
| E2E-4 | Upload CSV with missing column | `MISSING_COLUMN_CSV` | `[data-testid="schema-error-banner"]` visible; `portfolio-table` either absent or showing previous snapshot |
| E2E-5 | Upload CSV with future `Report Date` | `FUTURE_DATE_CSV` | `[data-testid="row-errors-banner"]` visible; at least one row error item visible |
| E2E-6 | Upload CSV with header only (no data rows) | `EMPTY_ROWS_CSV` | `[data-testid="portfolio-import-error"]` visible |
| E2E-7 | Re-upload different CSV for same `Report Date` | `VALID_PORTFOLIO_CSV` uploaded twice | Second import succeeds; success banner shows same date; table shows positions from second upload |

---

### 5.5 E2E Test Cases — Display: Sort, Filter, Total, Stale

> Maps to FR-3, AC-9–AC-13. All tests in `describe('Portfolio — display')`.

| ID | Scenario | Fixture / State | Assertions |
|---|---|---|---|
| E2E-8 | Default sort on page load | `VALID_PORTFOLIO_CSV` uploaded | `[data-testid="sort-symbol"]` shows `↑`; first row is alphabetically lowest symbol |
| E2E-9 | Click `sort-symbol` twice (toggle to DESC) | After E2E-8 | `sort-symbol` shows `↓`; first row is alphabetically highest symbol; `sort-marketValue` shows no indicator |
| E2E-10 | Click `sort-marketValue` once | After E2E-8 | `sort-marketValue` shows `↑`; rows ordered by market value ASC |
| E2E-11 | Filter by partial symbol (lowercase) | `FILTER_PORTFOLIO_CSV` uploaded | Submit "aapl" in `portfolio-filter-symbol-input`; 2 rows visible; `portfolio-filter-clear-link` present |
| E2E-12 | Filter by asset class | `FILTER_PORTFOLIO_CSV` uploaded | Submit "STK" in `portfolio-filter-assetClass-input`; 2 rows visible |
| E2E-13 | No-match filter shows empty state | `FILTER_PORTFOLIO_CSV` uploaded | Submit "NOMATCHING"; `portfolio-empty-state` visible; `portfolio-total-market-value` absent |
| E2E-14 | Clear filter restores all rows | After E2E-11 | Click `portfolio-filter-clear-link`; all 4 rows visible; inputs empty |
| E2E-15 | Total market value after filter | `FILTER_PORTFOLIO_CSV` uploaded | Filter to AAPL (2 rows, known balances); `portfolio-total-market-value` equals sum of those 2 rows' market values |
| E2E-16 | Stale warning visible for old snapshot | `STALE_PORTFOLIO_CSV` uploaded | `portfolio-stale-warning` visible; `portfolio-asof-date` shows the stale date |
| E2E-17 | No stale warning for recent snapshot | `VALID_PORTFOLIO_CSV` with yesterday's date | `portfolio-stale-warning` absent |

---

### 5.6 E2E Regression — Existing Tests That Must Be Updated

No existing E2E tests are affected by this feature. The new Portfolio nav link is additive; existing `nav-accounts-link` and `nav-dashboard-link` assertions remain valid.

---

## 6. Screenshot Capture

### 6.1 Capture Function

Function name: `capturePortfolioView(page)` in `tools/screenshots/capture.mjs`  
Output directory: `path.join(OUT_DIR, 'E_portfolio_view')`

| File | UI state to capture |
|---|---|
| `01_empty_state.png` | `/portfolio` with no snapshot — shows `portfolio-empty-state` and upload form |
| `02_table_default.png` | `/portfolio` after importing `VALID_PORTFOLIO_CSV` — table with default Symbol ASC sort, total in footer, as-of date displayed |
| `03_stale_warning.png` | `/portfolio` after importing `STALE_PORTFOLIO_CSV` — `portfolio-stale-warning` banner visible |
| `04_filtered.png` | `/portfolio` with symbol filter applied — 2 rows shown, filtered total in footer |
| `05_schema_error.png` | After uploading `MISSING_COLUMN_CSV` — `schema-error-banner` visible |

---

## 7. Out of Scope

| Item | Reason |
|---|---|
| Mechanism B — IBKR Flex Query file ingestion | Deferred to a separate feature; shared validation and storage is implemented here and reusable |
| As-of date picker (view historical snapshots) | Explicitly deferred to Backlog per request |
| Live IBKR connection / trade execution | Out of scope per request |
| Editing positions in the app | Out of scope per request |
| Automatic / scheduled snapshot ingestion | Out of scope per request; on-demand only |
| Per-currency market value breakdown in footer | Raw arithmetic total confirmed acceptable per request |
| File size limit on upload | Not specified; out of scope for this iteration |
| SecurityConfig changes | `.anyRequest().authenticated()` already covers `/portfolio/**`; no change required |
