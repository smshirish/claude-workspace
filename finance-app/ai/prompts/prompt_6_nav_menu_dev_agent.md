# Dev Agent — Shared Navigation Menu

## Pre-flight checklist

### 1. Confirm PLAN.md and HANDOFF.md are current
```bash
cat .claude/context/PLAN.md
cat .claude/context/HANDOFF.md
```

### 2. Start the Spring Boot backend (separate terminal — needed for E2E verification)
```bash
mvn spring-boot:run
```

---

## Agent invocation

```bash
claude --new-session \
  "Read .claude/context/PLAN.md and .claude/context/HANDOFF.md.
   Your role: Dev Agent. Implement the Shared Navigation Menu feature.

   IMPORTANT CONSTRAINTS:
   - This feature has NO backend changes. Do NOT touch src/main/java/.
   - Do NOT touch any existing test files.
   - Changes are limited to: src/main/resources/templates/ and src/main/resources/static/css/style.css

   Implement in this exact order — confirm each step before moving to the next:

   Step 1: Create src/main/resources/templates/fragments/nav.html
   - Thymeleaf fragment: th:fragment='nav(activePage)'
   - Contains the full <header> block with app-title, nav links, username, Sign Out
   - Nav links: Dashboard (/dashboard) and Accounts (/accounts)
   - Active link gets CSS class nav-link--active via th:classappend=\"\${activePage == 'dashboard'} ? 'nav-link--active'\"
   - data-testid attributes: nav-dashboard-link, nav-accounts-link, nav-username, nav-signout-button
   - Sign Out uses th:action and includes CSRF hidden input (match existing dashboard.html pattern)

   Step 2: Add CSS to src/main/resources/static/css/style.css
   - .nav-link base style for anchor tags inside .app-nav
   - .nav-link--active highlighted state (bold or underline)
   - Follow existing CSS patterns already in the file

   Step 3: Update src/main/resources/templates/dashboard.html
   - Replace the existing inline <header> block with:
     <header th:replace=\"~{fragments/nav :: nav('dashboard')}\"></header>
   - Keep all other content unchanged

   Step 4: Update src/main/resources/templates/accounts.html
   - Add the same header fragment include above the existing <body> content:
     <header th:replace=\"~{fragments/nav :: nav('accounts')}\"></header>
   - Wrap existing page content in <main class='app-main'> to match dashboard layout

   After all steps, verify using the E2E tests (backend must be running):
     cd e2e && npx playwright test tests/nav.spec.ts --reporter=list

   Fix any failures before finishing.

   Update .claude/context/HANDOFF.md with:
   - Files created/modified
   - E2E test results (pass/fail per scenario)
   - Any deviations from the plan"
```
