# Implementation Plan: Feature B — Filter Bar

> **Implementation order: 2nd.**
> Feature A must be in place first: the filter form must preserve active `sortField`/`sortDir` params as hidden inputs so sort state survives filter submissions. Feature C (Filtered Total) depends on the filtered list produced here.

---

## 1. Feature Requirements

### FR-1: Filter bar with three inputs
A filter bar above the accounts table provides text inputs for Bank Name, Account Number, and Account Type.

### FR-2: GET-based form submission
Submitting the filter issues `GET /accounts` with filter values as query params — bookmarkable URL. Sort params from Feature A are preserved as hidden form inputs.

### FR-3: Partial, case-insensitive match
Each non-empty filter applies a `contains` match ignoring case. An empty input means "no filter" for that field.

### FR-4: Combined AND logic
An account must satisfy every active (non-empty) filter to appear in results.

### FR-5: Input retention
After filtering, each input displays the value that was submitted (pre-populated from model).

### FR-6: Clear action
A "Clear" link navigates to `GET /accounts` carrying only current sort params, resetting all filter inputs.

---

## 2. Acceptance Criteria

| ID | Criterion |
|---|---|
| AC-1 | `GET /accounts?bankName=chase` returns only accounts where `bankName` contains "chase" (case-insensitive) |
| AC-2 | `GET /accounts?accountType=SAV` returns accounts where `accountType` contains "SAV" |
| AC-3 | `GET /accounts?bankName=chase&accountType=CHECKING` returns accounts matching both criteria |
| AC-4 | `GET /accounts?bankName=` (empty string) treated as no filter; all accounts returned |
| AC-5 | Filter matching zero accounts renders the existing empty-state element |
| AC-6 | After filtering, each input is pre-populated with the submitted value |
| AC-7 | Active sort params are preserved in the filter form's hidden inputs and survive submission |
| AC-8 | "Clear" link navigates to the account list with sort params only; no filter params in URL |

---

## 3. Component Breakdown

### 3.1 New Domain Exceptions
None.

### 3.2 New Domain Value Objects / Records
- `AccountFilterCriteria` record (`String bankName`, `String accountNumber`, `String accountType`) — `domain/model/`
  - Null or blank values mean "no filter" for that field.

### 3.3 New / Modified Domain Services
**New port — `FilterAccountsUseCase`** in `domain/port/in/`:
```
List<BankAccount> filterAccounts(AccountFilterCriteria criteria);
```
**Modified — `AccountApplicationService`** implements the new port:
- Fetch full list from repository
- Stream-filter: for each non-blank criterion, apply `String.contains` (lowercased) on the matching field
- Return filtered list (sort is applied after filtering by the controller chaining with Feature A's sort)

### 3.4 Modified Infrastructure Adapters (Out-bound / Persistence)
None.

### 3.5 Modified Web Adapters (In-bound / Controllers)
**Modified — `AccountController.showAccounts()`**:
- Add `@RequestParam(required=false) String bankName`, `accountNumber`, `accountType`
- Build `AccountFilterCriteria`; call `filterAccountsUseCase` when any criterion is non-blank; else call `getAllAccountsUseCase`
- Apply sort (Feature A) on the result of filter (filter first → sort after)
- Add `filterBankName`, `filterAccountNumber`, `filterAccountType` to model for input retention
- Add `clearFilterUrl` to model (sort params only, constructed from active sort state)

### 3.6 Template / UI Changes
**Modified — `accounts.html`**:
- Add `<form method="get" th:action="@{/accounts}" data-testid="filter-form">` above the table
- Three `<input type="text">` with `th:value` bound to model filter values
  - `data-testid`: `filter-bankName-input`, `filter-accountNumber-input`, `filter-accountType-input`
- Hidden `<input>` for `sortField` and `sortDir` to preserve sort state across filter submissions
- Submit button `data-testid="filter-submit-button"`
- Clear anchor `data-testid="filter-clear-link"` with `th:href` pointing to `clearFilterUrl`

---

## 4. Test Scenarios

### Unit — `AccountApplicationServiceFilterTest`
| # | Input | Expected |
|---|---|---|
| F-1 | `filterAccounts(bankName="chase")` with [Chase, Ally, BOFA] | returns [Chase] |
| F-2 | `filterAccounts(bankName="CHA")` uppercase partial | returns [Chase] (case-insensitive) |
| F-3 | `filterAccounts(accountType="SAV")` | returns all SAVINGS accounts |
| F-4 | `filterAccounts(bankName="chase", accountType="CHECKING")` | returns Chase/CHECKING only |
| F-5 | `filterAccounts(bankName="")` blank | returns all accounts (no filter applied) |
| F-6 | `filterAccounts(bankName="NOMATCHING")` | returns empty list |
| F-7 | `filterAccounts(accountNumber="000111")` partial account number | returns matching accounts |

### MockMvc — `AccountControllerFilterTest`
| # | Scenario | Expected model attributes |
|---|---|---|
| C-1 | `GET /accounts?bankName=chase` | `accounts` = Chase rows only; `filterBankName=chase` |
| C-2 | `GET /accounts?bankName=&accountType=SAVINGS` | filtered by type only; `filterBankName` empty string |
| C-3 | `GET /accounts?bankName=NOMATCHING` | `accounts` empty; empty-state rendered |
| C-4 | `GET /accounts?bankName=chase&sortField=balance&sortDir=asc` | filtered Chase rows sorted by balance asc |
| C-5 | `GET /accounts` no params | all accounts; all filter model attrs are empty/null |

---

## 5. E2E Test Plan

### 5.1 Framework & Configuration
**Framework:** Playwright + TypeScript — existing setup in `e2e/`.
**Test file:** `e2e/tests/account-filter.spec.ts`
**Shared helpers:** `login` from `./helpers/auth`; `writeTempCsv` defined inline
**New helpers:** None
**No new packages required.**
**Breaking changes in existing tests:** None — filter bar is additive; no existing selectors conflict.

---

### 5.2 Test Fixtures / Data Inventory

| Constant / Fixture | Valid? | Issues | Purpose |
|---|---|---|---|
| `FILTER_FIXTURE_CSV` | Yes | None | 4 accounts: Chase/SAVINGS, Chase/CHECKING, Ally/SAVINGS, BOFA/CHECKING |

---

### 5.3 E2E Test Cases — Filter Bar

> Maps to FR-1–FR-6, AC-1–AC-8. All tests in `describe('Filter Bar')`.

| ID | Scenario | Fixture / State | Assertions |
|---|---|---|---|
| E2E-1 | Type "chase" in `[data-testid="filter-bankName-input"]` and submit | `FILTER_FIXTURE_CSV` imported | 2 rows visible; input value = "chase" |
| E2E-2 | Type "CHA" (uppercase partial) and submit | same | Same 2 Chase rows shown (case-insensitive match) |
| E2E-3 | Type "chase" in bankName + "CHECKING" in accountType and submit | same | 1 row visible (Chase/CHECKING) |
| E2E-4 | Type "NOMATCHING" in bankName and submit | same | `[data-testid="accounts-empty-state"]` visible |
| E2E-5 | Click `[data-testid="filter-clear-link"]` after E2E-1 | filtered state | All 4 rows visible; inputs empty |
| E2E-6 | Sort by Bank Name (Feature A), then apply bankName filter | same | Only filtered rows shown, sorted |

---

### 5.4 E2E Regression — Existing Tests That Must Be Updated

| Existing test | Change required |
|---|---|
| None identified | Filter form is additive above the table; no existing selectors overlap |

---

## 6. Out of Scope

| Item | Reason |
|---|---|
| Filter on Currency or Import Date | Not in requirements |
| Exact-match mode | Not in requirements (partial match only) |
| OR logic across fields | Not in requirements (AND logic only) |
| Live / debounced filter without form submit | No JavaScript frameworks in use |
