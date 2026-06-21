import { test, expect, Page } from '@playwright/test';
import { login } from './helpers/auth';
import * as path from 'path';
import * as os from 'os';
import * as fs from 'fs';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function writeTempCsv(content: string): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'finance-e2e-'));
  const filePath = path.join(dir, 'accounts.csv');
  fs.writeFileSync(filePath, content, 'utf-8');
  return filePath;
}

async function importCsv(page: Page, csvContent: string): Promise<void> {
  const filePath = writeTempCsv(csvContent);
  await page.goto('/accounts');
  await page.setInputFiles('[data-testid="accounts-file-input"]', filePath);
  await page.click('[data-testid="accounts-import-button"]');
  await page.waitForLoadState('networkidle');
  fs.unlinkSync(filePath);
  fs.rmdirSync(path.dirname(filePath));
}

function firstRow(page: Page) {
  return page.locator('[data-testid="accounts-table"] tbody tr').first();
}

// ---------------------------------------------------------------------------
// Fixture — 3 accounts with distinct bankName, accountType, and balance values
// Default sort (balance ASC): Ally (100.00), BOFA (300.00), Chase (500.00)
// bankName ASC:               Ally, BOFA, Chase
// accountType ASC:            Ally (CHECKING), Chase (SAVINGS), BOFA (SAVINGS)
// ---------------------------------------------------------------------------

const SORT_FIXTURE_CSV = [
  'bankName,accountNumber,accountType,balance,currency',
  'Chase,000111222,SAVINGS,500.00,USD',
  'Ally,333444555,CHECKING,100.00,USD',
  'BOFA,666777888,SAVINGS,300.00,USD',
].join('\n');

// ---------------------------------------------------------------------------
// Column Sorting
// ---------------------------------------------------------------------------

test.describe('Column Sorting', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await importCsv(page, SORT_FIXTURE_CSV);
    await page.waitForURL('**/accounts');
    // State after beforeEach: /accounts with no sort params → balance ASC is the default active sort
  });

  // E2E-1: bankName ASC — active indicator on bankName; balance and accountType show none
  test('E2E-1: Click sort-bankName sorts A→Z and shows ↑ only on bankName header', async ({ page }) => {
    // Default sort is balance ASC; clicking bankName starts at ASC (switching column)
    await page.click('[data-testid="sort-bankName"]');
    await page.waitForLoadState('networkidle');

    await expect(firstRow(page).locator('td').nth(0)).toHaveText('Ally');
    await expect(page.locator('[data-testid="sort-bankName"]')).toContainText('↑');
    await expect(page.locator('[data-testid="sort-balance"]')).not.toContainText('↑');
    await expect(page.locator('[data-testid="sort-balance"]')).not.toContainText('↓');
    await expect(page.locator('[data-testid="sort-accountType"]')).not.toContainText('↑');
    await expect(page.locator('[data-testid="sort-accountType"]')).not.toContainText('↓');
  });

  // E2E-2: bankName DESC (toggle) — indicator reverses; balance and accountType still inactive
  test('E2E-2: Click sort-bankName twice toggles to Z→A and shows ↓', async ({ page }) => {
    await page.click('[data-testid="sort-bankName"]');
    await page.waitForLoadState('networkidle');
    await page.click('[data-testid="sort-bankName"]');
    await page.waitForLoadState('networkidle');

    await expect(firstRow(page).locator('td').nth(0)).toHaveText('Chase');
    await expect(page.locator('[data-testid="sort-bankName"]')).toContainText('↓');
    await expect(page.locator('[data-testid="sort-balance"]')).not.toContainText('↑');
    await expect(page.locator('[data-testid="sort-balance"]')).not.toContainText('↓');
    await expect(page.locator('[data-testid="sort-accountType"]')).not.toContainText('↑');
    await expect(page.locator('[data-testid="sort-accountType"]')).not.toContainText('↓');
  });

  // E2E-3: balance ASC when switching from another column — new column always starts at ASC
  test('E2E-3: Click sort-balance from a non-balance sort shows lowest balance first with ↑', async ({ page }) => {
    // Activate bankName sort first so balance is not the active column
    await page.goto('/accounts?sortField=bankName&sortDir=asc');
    await page.waitForLoadState('networkidle');

    await page.click('[data-testid="sort-balance"]');
    await page.waitForLoadState('networkidle');

    // Ally has the lowest balance (100.00)
    await expect(firstRow(page).locator('td').nth(3)).toHaveText('100.00');
    await expect(page.locator('[data-testid="sort-balance"]')).toContainText('↑');
    await expect(page.locator('[data-testid="sort-bankName"]')).not.toContainText('↑');
    await expect(page.locator('[data-testid="sort-bankName"]')).not.toContainText('↓');
  });

  // E2E-4: accountType ASC — CHECKING sorts before SAVINGS
  test('E2E-4: Click sort-accountType shows CHECKING first and ↑ on accountType header', async ({ page }) => {
    await page.click('[data-testid="sort-accountType"]');
    await page.waitForLoadState('networkidle');

    // CHECKING < SAVINGS alphabetically; Ally is the only CHECKING account
    await expect(firstRow(page).locator('td').nth(2)).toHaveText('CHECKING');
    await expect(page.locator('[data-testid="sort-accountType"]')).toContainText('↑');
  });

  // E2E-5: Default sort — balance ASC on page load with no sort params
  test('E2E-5: Default page load shows balance ASC with ↑ on balance and no indicator elsewhere', async ({ page }) => {
    await page.goto('/accounts');
    await page.waitForLoadState('networkidle');

    // Ally (100.00) is first under balance ASC
    await expect(firstRow(page).locator('td').nth(3)).toHaveText('100.00');
    await expect(page.locator('[data-testid="sort-balance"]')).toContainText('↑');
    await expect(page.locator('[data-testid="sort-bankName"]')).not.toContainText('↑');
    await expect(page.locator('[data-testid="sort-bankName"]')).not.toContainText('↓');
    await expect(page.locator('[data-testid="sort-accountType"]')).not.toContainText('↑');
    await expect(page.locator('[data-testid="sort-accountType"]')).not.toContainText('↓');
  });

  // E2E-6: Unknown sortField silently falls back to default — no error banners, balance ASC active
  test('E2E-6: Unknown sortField param falls back to default balance ASC with no error banners', async ({ page }) => {
    await page.goto('/accounts?sortField=invalid&sortDir=asc');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('[data-testid="schema-error-banner"]')).toHaveCount(0);
    await expect(page.locator('[data-testid="row-errors-banner"]')).toHaveCount(0);

    // Ally (100.00) is first under balance ASC default
    await expect(firstRow(page).locator('td').nth(3)).toHaveText('100.00');
    await expect(page.locator('[data-testid="sort-balance"]')).toContainText('↑');
  });

  // E2E-7: Switching columns always starts at ASC; prior active column loses its indicator
  test('E2E-7: Switching from bankName to accountType starts at ASC and bankName loses indicator', async ({ page }) => {
    await page.click('[data-testid="sort-bankName"]');
    await page.waitForLoadState('networkidle');

    await page.click('[data-testid="sort-accountType"]');
    await page.waitForLoadState('networkidle');

    // accountType must start at ASC (↑), not inherit bankName's direction
    await expect(page.locator('[data-testid="sort-accountType"]')).toContainText('↑');
    await expect(firstRow(page).locator('td').nth(2)).toHaveText('CHECKING');

    // bankName must lose its indicator
    await expect(page.locator('[data-testid="sort-bankName"]')).not.toContainText('↑');
    await expect(page.locator('[data-testid="sort-bankName"]')).not.toContainText('↓');
  });

  // E2E-8: balance direction toggle — ASC then DESC shows highest balance first
  test('E2E-8: Click sort-balance twice (asc then desc) shows highest balance first with ↓', async ({ page }) => {
    // Activate a different column first so the first click on sort-balance starts at ASC
    await page.click('[data-testid="sort-bankName"]');
    await page.waitForLoadState('networkidle');

    // First click: balance ASC
    await page.click('[data-testid="sort-balance"]');
    await page.waitForLoadState('networkidle');
    await expect(firstRow(page).locator('td').nth(3)).toHaveText('100.00');

    // Second click: balance DESC — Chase (500.00) is now first
    await page.click('[data-testid="sort-balance"]');
    await page.waitForLoadState('networkidle');

    await expect(firstRow(page).locator('td').nth(3)).toHaveText('500.00');
    await expect(page.locator('[data-testid="sort-balance"]')).toContainText('↓');
  });
});
