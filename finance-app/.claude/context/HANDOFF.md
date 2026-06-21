# Handoff

## Status
Plans complete. Implementation pending. E2E tests to be written and validated once backend is implemented.

### Completed
_Nothing in progress._

### Next — Account Listing Enhancements (3 features, in order)

| Order | Feature | Plan file | E2E test file |
|---|---|---|---|
| 1 | Column Sorting | `.claude/context/PLAN_A_ColumnSorting.md` | `e2e/tests/account-sort.spec.ts` |
| 2 | Filter Bar | `.claude/context/PLAN_B_FilterBar.md` | `e2e/tests/account-filter.spec.ts` |
| 3 | Filtered Total | `.claude/context/PLAN_C_FilteredTotal.md` | `e2e/tests/account-total.spec.ts` |

---

## E2E Agent Instructions

### Pre-requisites
- Backend must be running on `http://localhost:8080` before executing tests: `mvn spring-boot:run`
- Run tests from the `e2e/` directory: `npx playwright test`
- Shared helper: `login` imported from `./helpers/auth`
- `writeTempCsv` is defined inline per spec file (see pattern in `e2e/tests/accounts.spec.ts`)

### Feature A — Column Sorting (`account-sort.spec.ts`)

**Fixture:**
```
bankName,accountNumber,accountType,balance,currency
Chase,000111222,SAVINGS,500.00,USD
Ally,333444555,CHECKING,100.00,USD
BOFA,666777888,SAVINGS,300.00,USD
```

**Test cases:**
| ID | Action | Assertion |
|---|---|---|
| E2E-1 | Click `[data-testid="sort-bankName"]` | First row bankName = "Ally"; `sort-bankName` shows ↑; `sort-balance` and `sort-accountType` show no indicator |
| E2E-2 | Click `[data-testid="sort-bankName"]` again | First row bankName = "Chase"; `sort-bankName` shows ↓; `sort-balance` and `sort-accountType` show no indicator |
| E2E-3 | Click `[data-testid="sort-balance"]` | First row balance = "100.00"; `sort-balance` shows ↑; `sort-bankName` shows no indicator |
| E2E-4 | Click `[data-testid="sort-accountType"]` | First row type = "CHECKING"; `sort-accountType` shows ↑ |
| E2E-5 | Fresh page load (no params) | `sort-balance` shows ↑ (default); `sort-bankName` and `sort-accountType` show no indicator; first row balance = "100.00" |
| E2E-6 | Navigate to `/accounts?sortField=invalid&sortDir=asc` | Page renders without error; `sort-balance` shows ↑ (default fallback); first row balance = "100.00" |
| E2E-7 | Click `sort-bankName` (asc), then click `sort-accountType` | `sort-accountType` shows ↑ (starts at asc); `sort-bankName` shows no indicator; first row type = "CHECKING" |
| E2E-8 | Click `sort-balance` twice | Second click: first row balance = "500.00"; `sort-balance` shows ↓ |

**Regression:** Update `accounts.spec.ts` AC2.2 test — lines checking `toHaveText('Bank Name')`, `toHaveText('Account Type')`, `toHaveText('Balance')` on `<th>` elements must change to `toContainText(...)` because those `<th>` cells now wrap `<a>` anchor links.

---

### Feature B — Filter Bar (`account-filter.spec.ts`)

**Fixture:**
```
bankName,accountNumber,accountType,balance,currency
Chase,000111222,SAVINGS,500.00,USD
Chase,333444555,CHECKING,200.00,USD
Ally,666777888,SAVINGS,800.00,USD
BOFA,999000111,CHECKING,150.00,USD
```

**Test cases:**
| ID | Action | Assertion |
|---|---|---|
| E2E-1 | Type "chase" in `[data-testid="filter-bankName-input"]`, submit | 2 rows visible; input value = "chase" |
| E2E-2 | Type "CHA" (uppercase), submit | Same 2 Chase rows (case-insensitive) |
| E2E-3 | Type "chase" + "CHECKING" in accountType, submit | 1 row visible (Chase/CHECKING) |
| E2E-4 | Type "NOMATCHING", submit | `[data-testid="accounts-empty-state"]` visible |
| E2E-5 | Click `[data-testid="filter-clear-link"]` | All 4 rows visible; inputs empty |
| E2E-6 | Sort by bankName, then apply bankName filter | Only filtered rows shown, in sorted order |

---

### Feature C — Filtered Total (`account-total.spec.ts`)

**Fixture:**
```
bankName,accountNumber,accountType,balance,currency
Chase,000111222,SAVINGS,100.00,USD
Chase,333444555,CHECKING,200.00,USD
Ally,666777888,SAVINGS,300.00,USD
```
Grand total = 600.00

**Test cases:**
| ID | Action | Assertion |
|---|---|---|
| E2E-1 | Page load, no filter | `[data-testid="accounts-total-balance"]` = "600.00" |
| E2E-2 | Filter bankName = "Chase" | Total = "300.00" |
| E2E-3 | Filter bankName = "NOMATCHING" | `[data-testid="accounts-empty-state"]` visible; total row absent |
| E2E-4 | Clear filter after E2E-2 | Total returns to "600.00" |

---

## Previous cycle
Account CSV Validation — fully implemented and verified.
- Two-tier CSV validation: schema (Tier 1) + row-level (Tier 2)
- Domain: `CsvSchemaException`, `CsvRowValidationException`, `RowValidationError`, `AccountCsvSchemaValidator`, `AccountCsvRowValidator`
- Infrastructure: `OpenCsvAccountParser` refactored to two-pass flow; `AccountController` updated with three catch blocks; `accounts.html` updated with `schema-error-banner` and `row-errors-banner` blocks
- Tests: unit (S-1–S-6, R-1–R-6, P-1–P-7, C-1–C-3) all green; E2E (E2E-S1–S6, E2E-R1–R7) written, pending live run
