# Handoff

## Status
Feature A (Column Sorting) — COMPLETE. Full regression verified 2026-08-30: 96/96 unit/MockMvc/integration tests pass (`mvn test`) + 40/40 E2E tests pass (`npx playwright test`, all specs: account-sort, accounts, csv-validation, nav).

Feature B (Filter Bar) — **Round-4 remediation complete, 2026-08-31.** All 109 unit/MockMvc tests pass (`mvn test`). Ready for reviewer re-pass / E2E. See "Feature B — Round 4 Resolution" below.

## Feature B — Round 4 Resolution (2026-08-31)

Resumed from the parked state below. Fixes applied:

- **`AccountBeanConfig.java`** — replaced the single `accountApplicationService` bean (which caused duplicate-bean errors when paired with per-interface wrapper beans) with three independent `@Bean` methods — `importAccountsUseCase()`, `getAllAccountsUseCase()`, `filterAccountsUseCase()` — each constructing its own `AccountApplicationService` (stateless, so this is safe). This lets `@MockBean` swap one port at a time in older test classes without removing the others from the context.
- **`AccountControllerFilterTest.java`** — `C-1` and `C-4` predated the controller's switch to real `FilterAccountsUseCase` delegation; they only stubbed `getAllAccountsUseCase` and expected the controller's old local `applyFilter()` to do the filtering. Updated both to stub `filterAccountsUseCase.filterAccounts(...)` directly.
- **`AccountApplicationServiceFilterTest.java`** (F-7) — fixture bug, not a production bug: `bofa`'s account number `"999000111"` contained the same `"000111"` substring as `chase`'s `"000111222"`, so the partial-match filter correctly returned 2 accounts against a test asserting 1. Changed `bofa`'s fixture number to `"999888777"`.

**Next:** re-run reviewer-agent pass (or re-invoke `orchestrate.sh B_FilterBar` to resume from current `HEAD`), then run E2E (`account-filter.spec.ts`).

## Feature B — Parked State (historical — resolved above)

`orchestrate.sh B_FilterBar` ran to completion of its reviewer loop and **escalated to human** after round 3 (`MAX_ATTEMPTS=3`, `pipeline/WORKFLOW_STATE.json` shows `stage:reviewer, attempt:3`). Verdict: `.claude/context/REVIEW_B_FilterBar.md` — same 2 blocking `[HIGH]` issues persisted across rounds 1–3:
1. `FilterAccountsUseCase` dead code — controller never calls it, uses a private `applyFilter()` instead.
2. Leftover `src/test/java` stub classes (`AccountFilterCriteria`, `FilterAccountsUseCase`) shadowing the real `src/main/java` versions on the test classpath.

Per user request, the round-4 test-first remediation flow was run **manually** (not via `orchestrate.sh`, which doesn't auto-resume after an `exit 2` escalation):

- **unit-test-agent — DONE, committed** (`e2f6b74`, branch `feature/B_FilterBar`): deleted both stale stubs; added MockMvc test `C-6` to `AccountControllerFilterTest.java` asserting the controller actually invokes `filterAccountsUseCase.filterAccounts()`. Also surfaced a **new, previously-unflagged pre-existing failure `F-7`** (`filterAccounts_byPartialAccountNumber_returnsMatchingAccounts`) — not yet investigated.
- **dev-agent — INTERRUPTED, stopped by user request, changes UNCOMMITTED** in the working tree:
  - `AccountController.java` — wiring `FilterAccountsUseCase` into the controller, removing `applyFilter()`. This part looks correct and compiles clean.
  - `AccountBeanConfig.java` — **broken.** Added three new `@Bean` methods (`importAccountsUseCase()`, `getAllAccountsUseCase()`, `filterAccountsUseCase()`) that each wrap the same `AccountApplicationService`. Since `AccountApplicationService` already implements all three port interfaces via its single existing `accountApplicationService` bean, this creates duplicate bean definitions. Result: every MockMvc controller test now fails ApplicationContext startup with `IllegalStateException: Unable to register mock bean ... expected a single matching bean to replace but found [accountApplicationService, importAccountsUseCase]` (18 test errors across `AccountControllerTest`, `AccountControllerSortTest`, `AccountControllerFilterTest`).

### Next steps to resume
1. In `AccountBeanConfig.java`, remove the redundant new `@Bean` methods (or the original `accountApplicationService` bean method) so each port interface resolves to exactly one bean.
2. Run `mvn test`, confirm all pass including new `C-6`.
3. Investigate pre-existing `F-7` failure (untouched, unrelated to the filter-bar wiring fix).
4. Commit as `fix: address review feedback for B_FilterBar (round 4)` (mirrors the pipeline's own commit message pattern).
5. Either manually re-run a reviewer-agent pass, or re-invoke `orchestrate.sh B_FilterBar` to resume the automated pipeline (it will start a new reviewer round from current `HEAD`).

### Orchestrator infra changes (already committed, both `claude-101` and `feature/B_FilterBar`)
- `orchestrate.sh`: per-call cost/timing log (`log_cost()` → `.claude/context/COST_<Feature>.md`), reviewer loop restructured to test-first (`unit-test-agent` encodes findings as tests before `dev-agent` fixes them).
- `.claude/rules/reviewer.md`: severity-gate verdict rule — `REQUEST_CHANGES` only for `[CRITICAL]`/`[HIGH]`; `[MEDIUM]`/`[MINOR]` go to non-blocking `### Suggestions`.
- `.claude/context/COST_B_FilterBar.md`: partial backfill for this run (pre-fix calls were overwritten before `log_cost()` existed) — documents the gap; future runs populate it completely per-call.

### Completed
- **Feature A — Implementation done** (2026-06-22)
  - Production domain classes added: `AccountSortField`, `SortDirection`, `AccountSortCriteria`
  - `GetAllAccountsUseCase.getAllAccounts()` updated to accept `AccountSortCriteria`
  - `AccountApplicationService` implements comparator-based sorting
  - `AccountController` accepts `sortField`/`sortDir` params; passes criteria to use case
  - Stub domain classes in test scope deleted
- **Feature A — Tests written** (Test Agent, 2026-06-21)
  - `AccountApplicationServiceSortTest` — 5 unit tests (S-1 to S-5) — all pass
  - `AccountControllerSortTest` — 4 MockMvc tests (C-1 to C-4) — 3 pass, C-3 failing (NullPointerException: `pk` is null at line 99)

### Next — Account Listing Enhancements (3 features, in order)

### Next — Account Listing Enhancements (3 features, in order)

| Order | Feature | Plan file | E2E test file |
|---|---|---|---|
| 1 | Column Sorting | `.claude/context/PLAN_A_ColumnSorting.md` | `e2e/tests/account-sort.spec.ts` |
| 2 | Filter Bar | `.claude/context/PLAN_B_FilterBar.md` | `e2e/tests/account-filter.spec.ts` |
| 3 | Filtered Total | `.claude/context/PLAN_C_FilteredTotal.md` | `e2e/tests/account-total.spec.ts` |

---

## Implementation Agent Notes — Feature A (Column Sorting)

### Test classes (all compile; mostly pass)

| Class | Location | Covers | Status |
|---|---|---|---|
| `AccountApplicationServiceSortTest` | `src/test/java/.../application/service/` | S-1: bankName ASC; S-2: bankName DESC; S-3: balance ASC; S-4: accountType ASC; S-5: DEFAULT criteria | All pass |
| `AccountControllerSortTest` | `src/test/java/.../adapter/in/web/` | C-1: sortField=bankName&sortDir=asc; C-2: sortDir=desc; C-3: no params → default; C-4: unknown sortField → default fallback | All pass |

### Stub classes — DELETED

All three stubs have been removed; production versions are in `src/main/java/com/finance/app/domain/model/`.

### Remaining work for next agent

#### Frontend template (accounts.html) — IMPLEMENTED (2026-06-22)

`src/main/resources/templates/accounts.html` updated:
- Bank Name, Account Type, Balance `<th>` cells replaced with `<a data-testid="sort-{field}">` links
- `href` toggles direction when column is active; defaults to `asc` when switching columns
- `<span th:if="...">` inside each link renders `↑`/`↓` for the active sort column only

#### Regression fix — accounts.spec.ts AC2.2 — DONE (2026-06-22)

`e2e/tests/accounts.spec.ts` lines 175, 177, 178 changed from `toHaveText` to `toContainText` per PLAN_A §5.4.

#### E2E status — ALL PASSING (2026-07-09)

All 8 E2E tests in `account-sort.spec.ts` verified green. Feature A is fully complete.

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
