# Review: PortfolioView Feature

**Reviewer:** Code Review Agent  
**Branch:** `feature/PortfolioView`  
**Plan:** `.claude/context/PLAN_PortfolioView.md`

---

## Verdict: REQUEST_CHANGES

One **[HIGH]** blocking issue prevents approval. See details below.

---

## Blocking Issues

### [HIGH] Missing Screenshot Capture Function (E2E Rule Violation)

**File:** `tools/screenshots/capture.mjs`  
**Issue:** The portfolio feature implements UI and E2E tests but does NOT include the mandatory screenshot capture function.

Per `.claude/rules/e2e.md` ("Screenshot Capture" section):
> After writing the Playwright tests, add a corresponding capture function to `tools/screenshots/capture.mjs`

**Plan § 6.1** specifies:
- Function name: `capturePortfolioView(page)` in `tools/screenshots/capture.mjs`
- Output directory: `path.join(OUT_DIR, 'E_portfolio_view')`
- 5 screenshots required (01_empty_state.png through 05_schema_error.png)

**Current state:** `capture.mjs` line 222 does NOT call `capturePortfolioView()` in the main block. The function itself is also missing from the file.

**Impact:** Violates mandatory E2E rule for UI-facing features. Screenshot captures are documentation artifacts and CI visibility checks.

---

## Implementation Review (Plan vs. Code)

### ✓ Domain Model — Complete & Correct
- **PortfolioPosition** (`src/main/java/.../domain/model/`): immutable class with factory `of()` and `withResolvedAccountId()` mutator — matches plan §3.2.
- **PortfolioSnapshot**, **PortfolioSortCriteria**, **PortfolioSortField**, **PortfolioFilterCriteria**: all present and correct record/enum definitions.
- **PortfolioImportException**: properly extends RuntimeException with two constructors (message, message+cause).

### ✓ Domain Validators — Complete & Correct
- **PortfolioCsvSchemaValidator**: validates 10 IBKR columns case-sensitively with clear error messages; tolerates extra columns (plan §3.3, test suite S-1 through S-5).
- **PortfolioCsvRowValidator**: implements two-tier validation per plan:
  - Tier 2a: blank checks on 8 required fields (columns 0-7).
  - Tier 2b: decimal parse on Quantity, Price, Market Value; date parse + future-date check on Report Date.
  - Tier 2c: cross-row check that all rows share the same Report Date.
  - Nullable columns (Cost Basis, Unrealized P&L) correctly handled.

### ✓ Infrastructure Adapters — Complete & Correct
- **OpenCsvPortfolioParser** (`infrastructure/adapter/out/persistence/`): two-pass approach (schema, then row validation) before mapping; UTF-8 BOM stripping included; correct exception hierarchy.
- **CsvFilePortfolioRepository**: correctly reads/writes internal CSV format to `~/.finance-app/data/portfolio/<YYYY-MM-DD>.csv`; `findLatest()` scans and sorts lexicographically; handles missing directory gracefully (test suite P-8 through P-12).

### ✓ Use Case Implementation — Complete & Correct
- **PortfolioApplicationService** implements all three ports: ImportPortfolioUseCase, GetLatestPortfolioUseCase, FilterPortfolioUseCase.
- Sorting logic with Comparator switch (SYMBOL, ASSET_CLASS, MARKET_VALUE, UNREALIZED_PNL); nulls-last handling on unrealizedPnl.
- Filter logic correctly applies symbol and assetClass partial matching (case-insensitive).
- Import logic checks for empty positions and delegates to repository.

### ✓ Controller — Complete & Correct
- **PortfolioController** handles GET /portfolio and POST /portfolio/import per plan §3.5.
- Sort field mapping, stale date calculation (> 14 days), Account ID resolution via AccountIdResolver.
- Exception handling for CsvSchemaException, CsvRowValidationException, PortfolioImportException, IOException; flash attribute on successful import.
- `repopulateSnapshot()` helper re-fetches and re-renders latest snapshot on error (UX correctness).

### ✓ Templates — Complete & Correct
- **portfolio.html**: all required elements present with correct data-testids:
  - Success/error banners (portfolio-import-success, schema-error-banner, row-errors-banner, portfolio-import-error).
  - Upload form (portfolio-import-form, portfolio-file-input, portfolio-import-button).
  - As-of date + stale warning (portfolio-asof-date, portfolio-stale-warning).
  - Filter form with symbol and assetClass inputs, clear link.
  - Empty state, portfolio-table, sort headers (sort-symbol, sort-assetClass, sort-marketValue, sort-unrealizedPnl).
  - Footer total (portfolio-total-market-value).
  - Table rows with data-testid="portfolio-row-${index}".
- **nav.html**: Portfolio link added with active-page highlighting.
- **application.yml**: `finance.storage.portfolio-dir` configured.

### ~ Minor Template Logic Inconsistency (Not Blocking)
**File:** `src/main/resources/templates/portfolio.html`, lines 71–93  
**Issue:** Sort indicator display logic uses a special case for the symbol field:
```html
<span th:if="${activeSortField == 'symbol' || activeSortField == ''}"></span>
```
vs. other fields (assetClass, marketValue, unrealizedPnl):
```html
<span th:if="${activeSortField == 'assetClass'}"></span>
```

**Explanation:** When no sortField URL param is provided, the controller sets `activeField = ""` (line 82 of PortfolioController). The template special-cases symbol to show the indicator because the DEFAULT sort is Symbol ASC. This works correctly and tests should pass, but the logic is fragile. A cleaner approach would be to set `activeField = "symbol"` in the controller when using DEFAULT, eliminating the need for the `|| activeSortField == ''` clause.

**Why not blocking:** The logic produces correct behavior (tests pass); this is a code-quality/maintainability issue, not a functional defect.

### ✓ Configuration & Wiring — Complete & Correct
- **PortfolioBeanConfig** creates independent instances of PortfolioApplicationService for each port (mirrors AccountBeanConfig pattern per HANDOFF.md); @Lazy annotation on repository prevents circular dependencies.
- All beans properly wired to PortfolioController via constructor injection.

### ✓ Test Coverage — Complete & Correct
- **Unit tests:**
  - PortfolioCsvSchemaValidatorTest: S-1 through S-5 (all test cases from plan §4).
  - PortfolioCsvRowValidatorTest: R-1 through R-8 (per plan §4).
  - PortfolioApplicationServiceTest: A-1 through A-10 (sorting, filtering, import logic).
- **Integration tests:**
  - OpenCsvPortfolioParserTest: P-1 through P-7 (two-tier validation, BOM handling).
  - CsvFilePortfolioRepositoryTest: P-8 through P-12 (file I/O, directory creation, overwrite).
- **MockMvc tests:**
  - PortfolioControllerTest: C-1 through C-8 (all GET/POST scenarios from plan §4).

### ✓ E2E Test Coverage — Comprehensive
**File:** `e2e/tests/portfolio.spec.ts`  
- **Empty state & navigation** (E2E-1, E2E-2): covers AC-8 and AC-14.
- **Import happy path & errors** (E2E-3 through E2E-7): covers AC-1, AC-2, AC-4, AC-5, AC-6, AC-7.
- **Display: sort, filter, total, stale** (E2E-8 through E2E-17): covers AC-9, AC-10, AC-11, AC-12, AC-13.
- Fixtures (VALID_PORTFOLIO_CSV, STALE_PORTFOLIO_CSV, MISSING_COLUMN_CSV, FUTURE_DATE_CSV, EMPTY_ROWS_CSV, FILTER_PORTFOLIO_CSV) are well-defined and reusable.
- Helper functions (writeTempCsv, cleanPortfolioDir, importPortfolioCsv, portfolioRows) are clean and support test isolation.

---

## Suggestions (Non-Blocking)

### [MINOR] Controller Default Sort Field Display

The comment on line 82 of PortfolioController could clarify why `activeField = ""` is used for DEFAULT sort (to trigger the template's special-case logic). Consider refactoring to set `activeField = "symbol"` and remove the `|| activeSortField == ''` clause from the template for consistency.

---

## Summary

The portfolio view implementation is **feature-complete and functionally correct** across all layers: domain, infrastructure, controllers, templates, and tests. All acceptance criteria and test scenarios from the plan are implemented. The one blocking issue is a rule violation (missing screenshot capture function), not a functional defect. Once that is addressed, the feature is ready for approval.
