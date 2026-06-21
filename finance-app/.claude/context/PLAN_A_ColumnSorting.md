# Implementation Plan: Feature A — Column Sorting

> **Implementation order: 1st.**
> No dependencies on Filter or Total. Establishes the `sortField`/`sortDir` query-param pattern that Feature B preserves in its filter form.

---

## 1. Feature Requirements

### FR-1: Clickable column headers
The Bank Name, Balance, and Account Type `<th>` cells render as anchor links. Clicking one issues `GET /accounts?sortField=<field>&sortDir=<dir>`.

### FR-2: Backend-applied sort
`AccountApplicationService` sorts the list before returning it. No client-side reordering.

### FR-3: Direction toggle
If the clicked header matches the active `sortField`, its link href reverses `sortDir` (asc → desc, desc → asc). Clicking a different column always starts at `asc`.

### FR-4: Sort indicator in active header
The active sort column shows ↑ (asc) or ↓ (desc). Inactive columns show no indicator.

### FR-5: Default sort by Amount
`GET /accounts` with no sort params returns the list sorted by balance ascending. This is the default sort applied whenever no valid `sortField`/`sortDir` params are present.

---

## 2. Acceptance Criteria

| ID | Criterion |
|---|---|
| AC-1 | `GET /accounts?sortField=bankName&sortDir=asc` returns accounts ordered by `bankName` A→Z |
| AC-2 | `GET /accounts?sortField=bankName&sortDir=desc` returns accounts ordered by `bankName` Z→A |
| AC-3 | `GET /accounts?sortField=balance&sortDir=asc` returns accounts ordered by `balance` low→high |
| AC-4 | `GET /accounts?sortField=accountType&sortDir=asc` returns accounts ordered by `accountType` A→Z |
| AC-5 | `GET /accounts` (no sort params) returns accounts sorted by balance ascending (default sort) |
| AC-6 | Unknown `sortField` value is silently ignored; falls back to default sort (balance ASC), no error |
| AC-7 | Clicking the active sort column header reverses the direction in the rendered href |
| AC-8 | Active header shows ↑ or ↓ indicator; inactive headers show none |

---

## 3. Component Breakdown

### 3.1 New Domain Exceptions
None.

### 3.2 New Domain Value Objects / Records
- `AccountSortField` enum (`BANK_NAME`, `BALANCE`, `ACCOUNT_TYPE`) — `domain/model/`
- `SortDirection` enum (`ASC`, `DESC`) — `domain/model/`
- `AccountSortCriteria` record (`AccountSortField field`, `SortDirection direction`) — `domain/model/`

### 3.3 New / Modified Domain Services
**Modified — `GetAllAccountsUseCase`** (existing port in `domain/port/in/`):
- Change method signature from `getAllAccounts()` to `getAllAccounts(AccountSortCriteria criteria)`
- `AccountSortCriteria` exposes a `DEFAULT` constant: `new AccountSortCriteria(BALANCE, ASC)`

**Modified — `AccountApplicationService`** implements the updated port:
- Fetch full list from repository
- Build a `Comparator<BankAccount>` from `criteria.field()` switched to the matching getter
- Reverse comparator when `criteria.direction() == DESC`
- Return sorted copy (repository untouched)
- All existing callers updated to pass `AccountSortCriteria.DEFAULT` when no user criteria available

### 3.4 Modified Infrastructure Adapters (Out-bound / Persistence)
None.

### 3.5 Modified Web Adapters (In-bound / Controllers)
**Modified — `AccountController.showAccounts()`**:
- Accept `@RequestParam(required=false) String sortField` and `String sortDir`
- Parse to `AccountSortField` / `SortDirection` via enum lookup; on `IllegalArgumentException` or missing params use `AccountSortCriteria.DEFAULT`
- Always call `getAllAccountsUseCase.getAllAccounts(criteria)` — single call path, no fallback branching
- Add `activeSortField` and `activeSortDir` to model for template toggle logic
- **Also update `importAccounts()` catch blocks** — each call to `getAllAccountsUseCase.getAllAccounts()` must pass `AccountSortCriteria.DEFAULT`

### 3.6 Template / UI Changes
**Modified — `accounts.html`**:
- Bank Name, Balance, Account Type `<th>` text replaced by `<a th:href>` links
- `href` uses Thymeleaf to encode toggled direction when the column is active, else defaults to `asc`
- Indicator `<span>` rendered via `th:if="${activeSortField == 'bankName'}"` etc.
- New `data-testid` attributes: `sort-bankName`, `sort-balance`, `sort-accountType`

---

## 4. Test Scenarios

### Unit — `AccountApplicationServiceSortTest`
| # | Input | Expected |
|---|---|---|
| S-1 | `getSortedAccounts(BANK_NAME, ASC)` with [Chase, Ally, BOFA] | returns [Ally, BOFA, Chase] |
| S-2 | `getSortedAccounts(BANK_NAME, DESC)` same data | returns [Chase, BOFA, Ally] |
| S-3 | `getSortedAccounts(BALANCE, ASC)` with balances [500, 100, 300] | returns order [100, 300, 500] |
| S-4 | `getSortedAccounts(ACCOUNT_TYPE, ASC)` with [SAVINGS, CHECKING, SAVINGS] | CHECKING first |
| S-5 | `getAllAccounts(AccountSortCriteria.DEFAULT)` with balances [500, 100, 300] | returns [100, 300, 500] (balance ASC) |

### MockMvc — `AccountControllerSortTest`
| # | Scenario | Expected model attributes |
|---|---|---|
| C-1 | `GET /accounts?sortField=bankName&sortDir=asc` | `accounts` sorted A→Z; `activeSortField=bankName`; `activeSortDir=asc` |
| C-2 | `GET /accounts?sortField=balance&sortDir=desc` | `accounts` sorted by balance desc; `activeSortDir=desc` |
| C-3 | `GET /accounts` (no params) | `accounts` sorted by balance ASC; `activeSortField=balance`; `activeSortDir=asc` |
| C-4 | `GET /accounts?sortField=unknown&sortDir=asc` | falls back to default (balance ASC); no error thrown; view renders normally |

---

## 5. E2E Test Plan

### 5.1 Framework & Configuration
**Framework:** Playwright + TypeScript — existing setup in `e2e/`.
**Test file:** `e2e/tests/account-sort.spec.ts`
**Shared helpers:** `login` from `./helpers/auth`; `writeTempCsv` defined inline (pattern from `accounts.spec.ts`)
**New helpers:** None
**No new packages required.**
**Breaking changes in existing tests:** Any test asserting `<th>Bank Name</th>` as plain text must update to target `[data-testid="sort-bankName"]`.

---

### 5.2 Test Fixtures / Data Inventory

| Constant / Fixture | Valid? | Issues | Purpose |
|---|---|---|---|
| `SORT_FIXTURE_CSV` | Yes | None | 3 accounts: Chase/SAVINGS/500, Ally/CHECKING/100, BOFA/SAVINGS/300 |

---

### 5.3 E2E Test Cases — Column Sorting

> Maps to FR-1–FR-5, AC-1–AC-8. All tests in `describe('Column Sorting')`.

| ID | Scenario | Fixture / State | Assertions |
|---|---|---|---|
| E2E-1 | Click `[data-testid="sort-bankName"]` | `SORT_FIXTURE_CSV` imported | First account row contains "Ally"; indicator shows ↑ |
| E2E-2 | Click `[data-testid="sort-bankName"]` again | same | First account row contains "Chase"; indicator shows ↓ |
| E2E-3 | Click `[data-testid="sort-balance"]` | same | First row balance cell = "100" |
| E2E-4 | Click `[data-testid="sort-accountType"]` | same | First row type cell = "CHECKING" |
| E2E-5 | Fresh page load, no sort params | same | `[data-testid="sort-balance"]` shows ↑ (default sort active); other headers show no indicator |

---

### 5.4 E2E Regression — Existing Tests That Must Be Updated

| Existing test | Change required |
|---|---|
| `accounts.spec.ts` — any assertion on plain-text `<th>` content for sortable columns | Retarget to `[data-testid="sort-bankName"]` etc. |

---

## 6. Out of Scope

| Item | Reason |
|---|---|
| Multi-column / secondary sort key | Not in requirements |
| Persistent sort preference across sessions | Not in requirements |
| Sort on Account Number or Currency columns | Not in requirements |
| Client-side / JavaScript sort | Explicitly excluded by requirements |
