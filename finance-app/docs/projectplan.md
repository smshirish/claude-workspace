# Project Plan

## Implemented Features

| # | Feature | Notes |
|---|---|---|
| 1.0 | User login | Spring Security, BCrypt, form-login |
| 1.1 | Shared navigation menu | Dashboard, Accounts, Sign Out; Thymeleaf fragment |
| 2.0 | Bank account listing | Display imported accounts in a table |
| 2.1 | CSV import | Upload a file to populate the account list |
| 2.2 | CSV validation | Schema (Tier 1 fail-fast) + row-level (Tier 2 accumulative) with structured error display |
| 2.3 | Column sorting | Sort by Bank Name, Account Type, or Balance; toggle direction |

---

## In Progress / Next Up

### Phase 2 — Account Listing Enhancements

| Order | Feature | Plan file | E2E spec | Status |
|---|---|---|---|---|
| B | Filter bar | `.claude/context/PLAN_B_FilterBar.md` | `e2e/tests/account-filter.spec.ts` | Not started |
| C | Filtered total (balance sum in `<tfoot>`) | `.claude/context/PLAN_C_FilteredTotal.md` | `e2e/tests/account-total.spec.ts` | Not started |

### Phase 3 — Investment Portfolio Analytics

> No implementation started. Requirements are captured in `docs/ai-workflow.md` under the investment analytics section.

| Feature | Description |
|---|---|
| PnL analysis | Realised + unrealised PnL; YTD/MTD/custom periods; absolute + %; FIFO cost basis |
| Asset allocation | By asset class, geography, sector; vs. target; concentration-risk flag (>10%) |
| Performance metrics | Total Return, Annualised Return, Volatility, Sharpe Ratio, Max Drawdown, benchmark comparison |
| Charts | Pie (allocation), bar (PnL), line (portfolio over time), waterfall (per-position contribution) |

---

## Domain Model

### Entities

| Entity | Key Fields |
|---|---|
| `User` | `UserId`, `Username`, `HashedPassword`, `Role` |
| `BankAccount` | `AccountId`, `bankName`, `accountNumber`, `AccountType`, `balance (BigDecimal)`, `currency`, `importedAt` |

### Enumerations

- `AccountType`: `CHECKING`, `SAVINGS`, `CREDIT`, `INVESTMENT`, `OTHER`
- `SortDirection`: `ASC`, `DESC`
- `AccountSortField`: `BANK_NAME`, `ACCOUNT_TYPE`, `BALANCE`

### CSV Import Format

Expected header (exact names, exact order; extra trailing columns are tolerated):

```
bankName,accountNumber,accountType,balance,currency
```

Row validation rules: all columns mandatory and non-blank; `accountType` must match the enum; `balance` must be a parseable decimal.

---

## Known Technical Debt

| Item | Notes |
|---|---|
| Dev container setup | Playwright / Chromium installation steps are documented as comments in `devcontainer.json` rather than automated |
| `AuthenticationApplicationService` | Exists as a domain use case but is not wired into the Spring Security filter chain (DaoAuthenticationProvider drives actual login) |
