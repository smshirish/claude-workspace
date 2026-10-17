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
| X-001 | Evaluate whether Portfolio needs its own mapping.json or reuses the Account mapping | Currently `AccountIdResolver` resolves anonymous account IDs via a single `MAPPING_FILE` (JSON, env-var-configured). IBKR portfolio CSVs contain the same `AccountId` field, so the same resolver applies out of the box for account-ID anonymisation. A **separate** portfolio mapping.json would only add value if: (a) portfolio-specific fields beyond AccountId need anonymisation (e.g. symbol aliases, custom position names), or (b) portfolio data arrives from a source with different anonymous IDs than the account upload. Current recommendation: reuse the existing resolver; revisit if multi-source anonymisation becomes a requirement. |

---

## Design Ideas

_(design questions and enhancement ideas for future consideration)_

| # | Idea | Context |
|---|---|---|
| D-001 | Integrate CSV column conversion into the upload flow instead of a separate offline tool | Currently `tools/csv-converter/` is a standalone JAR that maps arbitrary column names to the app's expected schema via a `mapping.json`, producing a converted file on disk before the user uploads it. This adds a manual pre-processing step. Integration options: (a) accept a column-mapping config in the upload form and convert server-side during import (parser reads mapping, renames headers on the fly); (b) auto-detect known source formats (IBKR Flex, Schwab, etc.) by header fingerprinting and apply the right mapping automatically; (c) keep the converter but wrap it as a Spring service called by the controller before handing rows to the domain parser. Applies to both Account and Portfolio uploads. Trade-off: simplicity for the user vs. added complexity in the upload adapter. |
