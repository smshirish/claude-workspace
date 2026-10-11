# Feature Request: AsOfDateVersioning

## What it does
The import pipeline treats the `asOfDate` value (sourced from the last column of the uploaded CSV) as a snapshot version key. Each unique `asOfDate` is stored as a separate, immutable snapshot. Importing a CSV with an already-seen `asOfDate` replaces only that date's accounts; all other snapshots are preserved. The accounts screen gains a dropdown so the user can view any stored snapshot.

## User trigger
1. Import — the user uploads a CSV whose last column contains an `asOfDate` value (e.g. `2026-09-30`). The import button / existing upload flow is unchanged; the new column is parsed automatically.
2. Snapshot selection — the user opens or navigates to the Accounts screen and selects a date from the asOfDate dropdown.

## Happy path
- After a successful import the page displays a confirmation message: "Imported N accounts for YYYY-MM-DD" (where N is the row count and YYYY-MM-DD is the asOfDate from the CSV).
- On first visit to the Accounts screen the latest asOfDate snapshot is shown by default.
- The asOfDate dropdown lists all available snapshots in descending date order, with the latest pre-selected.
- Selecting a different snapshot from the dropdown immediately switches the displayed accounts table, active filter, sort, and filtered balance total to reflect that snapshot's data.
- The selected asOfDate persists across page navigation (via session or URL parameter — whichever is simpler to implement).
- All previously imported snapshots remain accessible; no data is deleted.

## Failure / edge cases
- If the user imports a CSV whose `asOfDate` already exists in storage, the accounts for that date are replaced by the new import (latest import wins); all other snapshots are unaffected.
- If the `asOfDate` column is missing or blank on a row, treat it as a Tier-1 schema validation failure consistent with the existing CSV validation rules — surface an error and reject the file.
- If no snapshots exist yet (first-time use), the accounts screen shows the existing empty state with no dropdown rendered.

## Out of scope
- Snapshot deletion — users cannot remove a stored snapshot through the UI.

## Dependencies
- Feature D (AsOfDate field on BankAccount) — `PLAN_D_AsOfDate.md` exists in the pipeline directory and covers adding `asOfDate` to the domain model and CSV persistence. That plan must be fully implemented and merged before this feature's versioning/dropdown work begins.
