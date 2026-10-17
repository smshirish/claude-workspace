# Project Backlog

Improvements and enhancements deferred from completed or in-progress features.
Items here are **not** in any active pipeline run. When an item is picked up,
move it to a `REQUEST_<FeatureName>.md` and remove it from this list.

---

## Accounts

| # | Item | Context |
|---|---|---|
| B-001 | Account upload should show a success banner after a successful import | Currently the controller redirects silently to `GET /accounts` with no confirmation message. PortfolioView (REQUEST_PortfolioView.md) already requires a success banner — the same pattern should be retrofitted to the Accounts upload for consistency. |

---

## Portfolio

_(populated from REQUEST_PortfolioView.md backlog section)_

| # | Item | Context |
|---|---|---|
| P-001 | As-of date picker UI | Allow the user to select a historical snapshot to view. The stored per-date CSV files are already the history; only the UI and read path need implementing. |
| P-002 | IBKR live connection / thread execution | Listed for awareness; not planned. |

---

## Cross-cutting

_(items that span multiple screens or are architectural)_

| # | Item | Context |
|---|---|---|
| — | — | — |
