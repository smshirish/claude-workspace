# E2E Test Agent — Shared Navigation Menu

## Pre-flight checklist (run before launching agent)

### 1. Verify agent file is in place
```bash
ls .claude/agents/e2e-playwright.md
```

### 2. Start the Spring Boot backend (separate terminal)
```bash
mvn spring-boot:run
```

### 3. Verify Node.js >= 18
```bash
node --version
```

### 4. Confirm PLAN.md has E2E scenarios for this feature
```bash
cat .claude/context/PLAN.md
```

---

## Agent invocation

```bash
claude --new-session \
  "Read .claude/context/PLAN.md and .claude/context/HANDOFF.md.
   Your role: E2E Test Agent using the e2e-playwright subagent.
   Feature: Shared Navigation Menu.

   The e2e/ directory already exists with @playwright/test installed.
   Do NOT re-bootstrap or reinstall — go straight to writing tests.

   Write a NEW test file: e2e/tests/nav.spec.ts
   Use the login helper at e2e/tests/helpers/auth.ts for authentication.

   Test scenarios to cover (from PLAN.md):
   1. Authenticated user on /dashboard sees nav links for Dashboard and Accounts.
   2. Clicking the Accounts nav link navigates to /accounts.
   3. Clicking the Dashboard nav link navigates to /dashboard.
   4. Dashboard nav link has CSS class nav-link--active when on /dashboard.
   5. Accounts nav link has CSS class nav-link--active when on /accounts.
   6. Nav on /accounts shows data-testid=nav-username and data-testid=nav-signout-button.
   7. Clicking Sign Out logs the user out and redirects to /login.
   8. Unauthenticated GET /dashboard redirects to /login (nav is not rendered).

   data-testid attributes to target:
   - nav-dashboard-link
   - nav-accounts-link
   - nav-username
   - nav-signout-button

   Run tests from the e2e/ directory:
     cd e2e && npx playwright test tests/nav.spec.ts --reporter=list

   The app is running at http://localhost:8080.

   When done, update .claude/context/HANDOFF.md with:
   - test file path
   - scenarios covered and their pass/fail result
   - any data-testid attributes that were missing from the templates"
```
