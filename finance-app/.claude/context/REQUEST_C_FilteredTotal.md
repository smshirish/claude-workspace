# Feature Request: C_FilteredTotal

## What it does
Show the sum of balances for all accounts currently displayed on the accounts screen. If a filter is applied, the sum considers only the filtered accounts.

## User trigger
Automatic — the total is rendered with the accounts table on every page load and every filter submission. No separate user action required.

## Happy path
- A total row appears at the bottom of the accounts table
- The total sums only the accounts currently displayed (filtered or unfiltered)
- The total value is formatted identically to the individual balance cells

## Failure / edge cases
- No accounts match the active filter: the empty-state message is shown instead of the table, and the total row is absent along with it

## Out of scope
- Currency conversion
- Per-currency subtotals
- Visual styling of the total row — no bold, no distinct color, no icon. The row uses the table's existing cell styling.

Keep the implementation simple — a single summed value.

## Dependencies
- Feature B (Filter Bar) — COMPLETE. The total must reflect the post-filter account list already resolved by the controller.
- Spec agent should review and confirm any further dependencies.
