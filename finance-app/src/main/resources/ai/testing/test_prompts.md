Role: You are an expert QA Automation Engineer specializing in Playwright with TypeScript.

Task: Write a comprehensive, production-ready End-to-End (E2E) automation test script using Playwright for our application's Login page. 

This prompt serves as the functional Test Specification. Please implement the code strictly according to the details below.

---

### 1. Test Environment & Configuration
- Language: TypeScript
- Test Runner: Playwright Test (`@playwright/test`)
- Base URL: [Insert URL, e.g., https://staging.example.com/login or use process.env.BASE_URL]

### 2. Page Elements & Locators
Please use the following locators (preferring data-testid or semantic roles where possible):
- Username/Email Input: [e.g., page.getByLabel('Email') or data-testid="username"]
- Password Input: [e.g., page.getByLabel('Password') or data-testid="password"]
- Login Button: [e.g., page.getByRole('button', { name: 'Log in' })]
- Error Message Container: [e.g., data-testid="login-error"]

### 3. Test Scenarios to Implement

#### Scenario 1: Successful Login (Happy Path)
- Pre-conditions: Navigate to the login page.
- Actions: 
  1. Input valid username: "[Insert valid user/placeholder]"
  2. Input valid password: "[Insert valid pass/placeholder]"
  3. Click the Login button.
- Expected Assertions:
  - The URL should change to "[Insert expected post-login URL, e.g., /dashboard]".
  - The page should display the element "[Insert element, e.g., page.getByRole('heading', { name: 'Dashboard' })]".
  - State check: Ensure auth cookies or local storage tokens are set (if applicable).

#### Scenario 2: Failed Login (Invalid Credentials)
- Pre-conditions: Navigate to the login page.
- Actions:
  1. Input an invalid username or password.
  2. Click the Login button.
- Expected Assertions:
  - The URL should remain on the login page.
  - An error message should be visible containing the text: "[Insert expected error text, e.g., 'Invalid credentials']".

#### Scenario 3: Validation (Empty Fields)
- Pre-conditions: Navigate to the login page.
- Actions:
  1. Leave fields empty and click the Login button.
- Expected Assertions:
  - Inline validation errors should appear for both Username and Password fields.
  - The form should not submit.

---

### Code Quality & Pattern Requirements
- Best Practices: Use the Page Object Model (POM) pattern. Separate the page actions/locators into a `LoginPage` class file, and the tests into a `login.spec.ts` file.
- Clean Code: Use proper async/await syntax, descriptive `test.describe` and `test` blocks, and include brief comments explaining the steps.
- Robustness: Ensure implicit/explicit web-first assertions (like `expect(locator).toBeVisible()`) are used to prevent flakiness.

Please generate the `LoginPage.ts` class first, followed by the `login.spec.ts` test file.