import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';

// ---------------------------------------------------------------------------
// Shared Navigation Menu — E2E Tests
// ---------------------------------------------------------------------------

test.describe('Shared Navigation Menu', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  // AC1.1 + AC1.2: Nav on /dashboard shows Dashboard and Accounts links
  test('Authenticated user on /dashboard sees nav with Dashboard and Accounts links', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page.locator('[data-testid="nav-dashboard-link"]')).toBeVisible();
    await expect(page.locator('[data-testid="nav-accounts-link"]')).toBeVisible();
  });

  // AC1.2: Clicking Accounts link navigates to /accounts
  test('Clicking Accounts link navigates to /accounts', async ({ page }) => {
    await page.goto('/dashboard');
    await page.click('[data-testid="nav-accounts-link"]');
    await page.waitForURL('**/accounts');
    expect(page.url()).toContain('/accounts');
  });

  // AC1.2: Clicking Dashboard link navigates to /dashboard
  test('Clicking Dashboard link navigates to /dashboard', async ({ page }) => {
    await page.goto('/accounts');
    await page.click('[data-testid="nav-dashboard-link"]');
    await page.waitForURL('**/dashboard');
    expect(page.url()).toContain('/dashboard');
  });

  // AC1.3: Dashboard link has nav-link--active class on /dashboard
  test('Dashboard link has nav-link--active class when on /dashboard', async ({ page }) => {
    await page.goto('/dashboard');
    const dashboardLink = page.locator('[data-testid="nav-dashboard-link"]');
    await expect(dashboardLink).toHaveClass(/nav-link--active/);
    const accountsLink = page.locator('[data-testid="nav-accounts-link"]');
    await expect(accountsLink).not.toHaveClass(/nav-link--active/);
  });

  // AC1.3: Accounts link has nav-link--active class on /accounts
  test('Accounts link has nav-link--active class when on /accounts', async ({ page }) => {
    await page.goto('/accounts');
    const accountsLink = page.locator('[data-testid="nav-accounts-link"]');
    await expect(accountsLink).toHaveClass(/nav-link--active/);
    const dashboardLink = page.locator('[data-testid="nav-dashboard-link"]');
    await expect(dashboardLink).not.toHaveClass(/nav-link--active/);
  });

  // AC1.4 + AC1.5: Nav on /accounts shows username and Sign Out button
  test('Nav on /accounts shows username and Sign Out button', async ({ page }) => {
    await page.goto('/accounts');
    await expect(page.locator('[data-testid="nav-username"]')).toBeVisible();
    await expect(page.locator('[data-testid="nav-signout-button"]')).toBeVisible();
  });

  // AC1.4: Sign Out button logs out and redirects to /login
  test('Sign Out button logs the user out and redirects to /login', async ({ page }) => {
    await page.goto('/dashboard');
    await page.click('[data-testid="nav-signout-button"]');
    await page.waitForURL('**/login');
    expect(page.url()).toContain('/login');
  });

  // Existing security: Unauthenticated access redirects to /login
  test('Unauthenticated access to /dashboard redirects to /login (nav not rendered)', async ({ browser }) => {
    const freshContext = await browser.newContext();
    const freshPage = await freshContext.newPage();
    await freshPage.goto('/dashboard');
    await freshPage.waitForURL('**/login');
    expect(freshPage.url()).toContain('/login');
    await freshContext.close();
  });
});
