# Plan: Shared Navigation Menu

## Feature Requirements

### F1 — Shared Navigation Fragment
A consistent navigation bar appears on every authenticated page, allowing users to move between sections without using the browser back button.

**Acceptance Criteria:**
- AC1.1: A Thymeleaf fragment `fragments/nav.html` defines the nav bar once and is included by all authenticated pages.
- AC1.2: The nav bar contains a link to **Dashboard** (`/dashboard`) and a link to **Accounts** (`/accounts`).
- AC1.3: The currently active page link is visually distinguished (CSS class `nav-link--active`).
- AC1.4: The nav bar retains the existing **username display** and **Sign Out** button.
- AC1.5: `accounts.html` is updated to use the shared header/nav structure (currently it has none).
- AC1.6: `dashboard.html` is updated to replace its inline nav with the shared fragment.
- AC1.7: All nav links and the Sign Out button carry `data-testid` attributes per project conventions.

### F2 — CSS for Navigation Links
Nav link styles are added to `static/css/style.css` so the menu renders consistently.

**Acceptance Criteria:**
- AC2.1: Nav links use class `nav-link`; the active link additionally uses `nav-link--active`.
- AC2.2: Styles follow the existing CSS patterns already in `style.css` (no new frameworks).

---

## Component Breakdown

### Templates (`src/main/resources/templates/`)

| File | Change |
|---|---|
| `fragments/nav.html` | **New.** Thymeleaf fragment `th:fragment="nav(activePage)"`. Renders header, nav links, username, logout. |
| `dashboard.html` | Replace inline `<header>` block with `th:replace="~{fragments/nav :: nav('dashboard')}"`. |
| `accounts.html` | Add `<header>` via `th:replace="~{fragments/nav :: nav('accounts')}"`. Wrap body in consistent `app-main` layout. |

### Fragment parameters

| Parameter | Values | Used for |
|---|---|---|
| `activePage` | `'dashboard'`, `'accounts'` | Adds `nav-link--active` class to the matching link via `th:classappend`. |

### CSS (`src/main/resources/static/css/style.css`)

Add styles for:
- `.nav-link` — base anchor style inside `.app-nav`
- `.nav-link--active` — highlighted state (e.g. underline or bold)

### `data-testid` map (per thymeleaf-templates.md rule)

| Element | `data-testid` |
|---|---|
| Nav dashboard link | `nav-dashboard-link` |
| Nav accounts link | `nav-accounts-link` |
| Nav username display | `nav-username` |
| Sign Out button | `nav-signout-button` |

---

## Test Scenarios

### E2E Tests (Playwright — `e2e/tests/nav.spec.ts`)

| Test | ACs covered |
|---|---|
| Authenticated user on `/dashboard` sees nav with Dashboard and Accounts links | AC1.1, AC1.2 |
| Clicking Accounts link navigates to `/accounts` | AC1.2 |
| Clicking Dashboard link navigates to `/dashboard` | AC1.2 |
| Dashboard link has `nav-link--active` class when on `/dashboard` | AC1.3 |
| Accounts link has `nav-link--active` class when on `/accounts` | AC1.3 |
| Nav on `/accounts` shows username and Sign Out button | AC1.4, AC1.5 |
| Sign Out button on nav logs the user out and redirects to `/login` | AC1.4 |
| Unauthenticated access to `/dashboard` redirects to `/login` (nav not rendered) | existing security |

### No unit or MockMvc tests required
This feature contains no new backend logic. All behaviour is verifiable via E2E tests.

---

## Implementation Order

1. Create `src/main/resources/templates/fragments/nav.html` with `th:fragment="nav(activePage)"`.
2. Add `.nav-link` and `.nav-link--active` CSS rules to `style.css`.
3. Update `dashboard.html` — replace inline header with fragment include.
4. Update `accounts.html` — add fragment include and wrap content in `app-main`.
5. Write `e2e/tests/nav.spec.ts` E2E tests.
6. Run `npx playwright test` (with backend running) — all tests green.

---

## Out of Scope

| Item | Reason |
|---|---|
| Mobile hamburger / responsive collapse | No mobile breakpoint requirement yet. |
| Role-based menu visibility | Single-user app; all authenticated users see the same nav. |
| Breadcrumbs | Not requested. |
| New pages / routes | Menu links only to existing routes (`/dashboard`, `/accounts`). |
