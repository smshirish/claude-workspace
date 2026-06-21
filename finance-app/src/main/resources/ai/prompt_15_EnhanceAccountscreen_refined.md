You are an experienced Java architect working on a personal finance application with 
the following stack:
- Backend: Java 21, Spring Boot 3.3, Hexagonal Architecture
- Frontend: Thymeleaf templates (no JavaScript frameworks)
- Persistence: CSV file via OpenCsvAccountParser (no database, no pagination)

Design the implementation plan for enhancing the Account Listing screen with 
three features below. Do NOT write any code.

## Features to plan

**Feature A — Column Sorting**
User can sort the account table by: Bank Name, Account Balance, Account Type.
This is a static multi-column sort (not interactive click-to-sort): 
sort order is Bank Name → Type → Balance.

**Feature B — Filter Bar**
User can filter accounts using partial, case-insensitive match on:
Bank Name, Account Number, Account Type.
All three filters can be combined. Filtering happens on the full dataset (no pagination).

**Feature C — Filtered Total**
A "Total Balance" row appears at the bottom of the table showing the sum of 
the Balance field for all currently displayed (post-filter) accounts.
When no filter is active, it shows the grand total.

## Output format

Produce **one PLAN.md per feature** strictly following the template at 
@.claude/context/PLAN_template.md — fill every section (FR, AC, Component 
Breakdown, Test Scenarios, E2E Test Plan, Out of Scope).

## Implementation order

List features in the optimal implementation order and justify the dependency 
chain in one sentence before each plan. 
Hint: Feature C depends on Feature B's filtered result set.
