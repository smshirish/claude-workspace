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

function rows(page: Page) {
  return page.locator('[data-testid="accounts-table"] tbody tr');
}

async function submitFilter(page: Page, values: { bankName?: string; accountNumber?: string; accountType?: string }) {
  await page.fill('[data-testid="filter-bankName-input"]', values.bankName ?? '');
  await page.fill('[data-testid="filter-accountNumber-input"]', values.accountNumber ?? '');
  await page.fill('[data-testid="filter-accountType-input"]', values.accountType ?? '');
  await page.click('[data-testid="filter-submit-button"]');
  await page.waitForLoadState('networkidle');
}

// ---------------------------------------------------------------------------
// Fixture — 4 accounts: Chase/SAVINGS, Chase/CHECKING, Ally/SAVINGS, BOFA/CHECKING
// ---------------------------------------------------------------------------

const FILTER_FIXTURE_CSV = [
  'bankName,accountNumber,accountType,balance,currency',
  'Chase,000111222,SAVINGS,500.00,USD',
  'Chase,333444555,CHECKING,200.00,USD',
  'Ally,666777888,SAVINGS,800.00,USD',
  'BOFA,999888777,CHECKING,150.00,USD',
].join('\n');

// ---------------------------------------------------------------------------
// Filter Bar
// ---------------------------------------------------------------------------

test.describe('Filter Bar', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await importCsv(page, FILTER_FIXTURE_CSV);
    await page.waitForURL('**/accounts');
  });

  // E2E-1: bankName="chase" → 2 Chase rows; input retains submitted value
  test('E2E-1: Filtering by bankName shows only matching rows and retains input value', async ({ page }) => {
    await submitFilter(page, { bankName: 'chase' });

    await expect(rows(page)).toHaveCount(2);
    await expect(page.locator('[data-testid="filter-bankName-input"]')).toHaveValue('chase');
  });

  // E2E-2: bankName="CHA" (uppercase partial) → same 2 Chase rows (case-insensitive)
  test('E2E-2: Filtering is case-insensitive on partial bankName', async ({ page }) => {
    await submitFilter(page, { bankName: 'CHA' });

    await expect(rows(page)).toHaveCount(2);
  });

  // E2E-3: bankName="chase" AND accountType="CHECKING" → 1 row (Chase/CHECKING)
  test('E2E-3: Combined bankName and accountType filters apply AND logic', async ({ page }) => {
    await submitFilter(page, { bankName: 'chase', accountType: 'CHECKING' });

    await expect(rows(page)).toHaveCount(1);
    await expect(rows(page).first().locator('td').nth(0)).toHaveText('Chase');
    await expect(rows(page).first().locator('td').nth(2)).toHaveText('CHECKING');
  });

  // E2E-4: bankName="NOMATCHING" → empty-state rendered
  test('E2E-4: Filter matching zero accounts renders the empty state', async ({ page }) => {
    await submitFilter(page, { bankName: 'NOMATCHING' });

    await expect(page.locator('[data-testid="accounts-empty-state"]')).toBeVisible();
    await expect(rows(page)).toHaveCount(0);
  });

  // E2E-5: Clear link resets filters and restores all rows
  test('E2E-5: Clear link resets all filter inputs and shows all accounts', async ({ page }) => {
    await submitFilter(page, { bankName: 'chase' });
    await expect(rows(page)).toHaveCount(2);

    await page.click('[data-testid="filter-clear-link"]');
    await page.waitForLoadState('networkidle');

    await expect(rows(page)).toHaveCount(4);
    await expect(page.locator('[data-testid="filter-bankName-input"]')).toHaveValue('');
    await expect(page.locator('[data-testid="filter-accountNumber-input"]')).toHaveValue('');
    await expect(page.locator('[data-testid="filter-accountType-input"]')).toHaveValue('');
  });

  // E2E-6: Sort by balance ASC, then apply bankName filter → filtered rows remain sorted
  test('E2E-6: Filtering after sorting keeps the result set sorted', async ({ page }) => {
    await page.goto('/accounts?sortField=balance&sortDir=asc');
    await page.waitForLoadState('networkidle');

    await submitFilter(page, { bankName: 'chase' });

    await expect(rows(page)).toHaveCount(2);
    // balance ASC: Chase/CHECKING (200.00) before Chase/SAVINGS (500.00)
    await expect(rows(page).nth(0).locator('td').nth(3)).toHaveText('200.00');
    await expect(rows(page).nth(1).locator('td').nth(3)).toHaveText('500.00');
    await expect(page.locator('[data-testid="sort-balance"]')).toContainText('↑');
  });
});
