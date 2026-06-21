# Implementation Plan: Feature C — Filtered Total

> **Implementation order: 3rd.**
> Depends on Feature B: the total must reflect the post-filter account list already resolved by the controller. No new use case required — the total is a derived value computed directly from the list already in the model.

---

## 1. Feature Requirements

### FR-1: Total Balance row in table footer
A `<tfoot>` row labelled "Total" appears at the bottom of the accounts table, showing the sum of `balance` across all currently rendered rows.

### FR-2: Reflects active filter
When a filter is active the total sums only filtered accounts. When no filter is active it sums all accounts.

### FR-3: Zero total on empty result
When no accounts match the filter, the total shows `0.00`.

### FR-4: Consistent formatting
Total balance is formatted identically to the individual balance cells.

---

## 2. Acceptance Criteria

| ID | Criterion |
|---|---|
| AC-1 | No filter active: total = sum of all account balances |
| AC-2 | `bankName=chase` filter active: total = sum of Chase account balances only |
| AC-3 | Filter returns zero matches: total row displays `0.00` |
| AC-4 | Total row is always visible when the table is rendered (regardless of filter/sort state) |
| AC-5 | Total label cell spans the non-balance columns; value cell aligns with the Balance column |

---

## 3. Component Breakdown

### 3.1 New Domain Exceptions
None.

### 3.2 New Domain Value Objects / Records
None — `BigDecimal` sum is computed inline; no new domain concept.

### 3.3 New / Modified Domain Services
None — total is a derived presentation value, not a domain concept.

### 3.4 Modified Infrastructure Adapters (Out-bound / Persistence)
None.

### 3.5 Modified Web Adapters (In-bound / Controllers)
**Modified — `AccountController.showAccounts()`**:
- After resolving the final display list (filter applied, then sort applied), compute:
  `BigDecimal total = accounts.stream().map(BankAccount::balance).reduce(BigDecimal.ZERO, BigDecimal::add);`
- Add `totalBalance` to model (value is `BigDecimal.ZERO` when the list is empty).

### 3.6 Template / UI Changes
**Modified — `accounts.html`**:
- Add `<tfoot>` inside the accounts table, inside the same `th:unless` wrapper as the `<tbody>`
- Single `<tr>`: `<td colspan="3">Total</td>`, `<td class="balance-cell" th:text="${totalBalance}" data-testid="accounts-total-balance"></td>`, `<td></td>` (Currency column placeholder)
- When the empty-state is shown instead of the table, total row is also absent — consistent with the existing `th:unless="${#lists.isEmpty(accounts)}"` guard

---

## 4. Test Scenarios

### MockMvc — `AccountControllerTotalTest`
| # | Scenario | Expected model attributes |
|---|---|---|
| T-1 | `GET /accounts` — 3 accounts with balances 100, 200, 300 | `totalBalance` = 600 |
| T-2 | `GET /accounts?bankName=chase` — 2 Chase accounts (100 + 200) | `totalBalance` = 300 |
| T-3 | `GET /accounts?bankName=NOMATCHING` — empty result | `totalBalance` = 0 |
| T-4 | `GET /accounts` — no accounts imported (empty repository) | `totalBalance` = 0 |
| T-5 | `GET /accounts?sortField=balance&sortDir=asc` — sort only, no filter | `totalBalance` = grand total (sort does not affect sum) |

---

## 5. E2E Test Plan

### 5.1 Framework & Configuration
**Framework:** Playwright + TypeScript — existing setup in `e2e/`.
**Test file:** `e2e/tests/account-total.spec.ts`
**Shared helpers:** `login` from `./helpers/auth`; `writeTempCsv` defined inline
**New helpers:** None
**No new packages required.**
**Breaking changes in existing tests:** None — `<tfoot>` is additive; no existing row assertions conflict.

---

### 5.2 Test Fixtures / Data Inventory

| Constant / Fixture | Valid? | Issues | Purpose |
|---|---|---|---|
| `TOTAL_FIXTURE_CSV` | Yes | None | 3 accounts: Chase/100.00, Chase/200.00, Ally/300.00; known grand total = 600.00 |

---

### 5.3 E2E Test Cases — Filtered Total

> Maps to FR-1–FR-4, AC-1–AC-5. All tests in `describe('Filtered Total')`.

| ID | Scenario | Fixture / State | Assertions |
|---|---|---|---|
| E2E-1 | Page load, no filter | `TOTAL_FIXTURE_CSV` imported | `[data-testid="accounts-total-balance"]` text = "600.00" |
| E2E-2 | Filter by bankName = "Chase" | same | Total = "300.00" (100 + 200) |
| E2E-3 | Filter by bankName = "NOMATCHING" | same | `[data-testid="accounts-empty-state"]` visible; total row absent |
| E2E-4 | Click Clear after E2E-2 | filtered state | Total returns to "600.00" |

---

### 5.4 E2E Regression — Existing Tests That Must Be Updated

| Existing test | Change required |
|---|---|
| None identified | `<tfoot>` is additive; no existing assertions target table footer |

---

## 6. Out of Scope

| Item | Reason |
|---|---|
| Per-currency sub-totals | All accounts assumed same currency per import; multi-currency aggregation not in requirements |
| Total affected by sort-only (no filter) | Sort does not change which accounts are visible, only their order — total is the same as grand total |
| Persistent total display toggle | Not in requirements |
