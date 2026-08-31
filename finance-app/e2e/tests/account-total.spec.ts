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

async function submitFilter(page: Page, values: { bankName?: string; accountNumber?: string; accountType?: string }) {
  await page.fill('[data-testid="filter-bankName-input"]', values.bankName ?? '');
  await page.fill('[data-testid="filter-accountNumber-input"]', values.accountNumber ?? '');
  await page.fill('[data-testid="filter-accountType-input"]', values.accountType ?? '');
  await page.click('[data-testid="filter-submit-button"]');
  await page.waitForLoadState('networkidle');
}

// ---------------------------------------------------------------------------
// Fixture — 3 accounts: Chase/SAVINGS/100, Chase/CHECKING/200, Ally/SAVINGS/300
// Grand total = 600.00; Chase total = 300.00
// ---------------------------------------------------------------------------

const TOTAL_FIXTURE_CSV = [
  'bankName,accountNumber,accountType,balance,currency',
  'Chase,000111222,SAVINGS,100.00,USD',
  'Chase,333444555,CHECKING,200.00,USD',
  'Ally,666777888,SAVINGS,300.00,USD',
].join('\n');

// ---------------------------------------------------------------------------
// Filtered Total
// ---------------------------------------------------------------------------

test.describe('Filtered Total', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await importCsv(page, TOTAL_FIXTURE_CSV);
    await page.waitForURL('**/accounts');
  });

  // E2E-1: No filter → total balance = 600.00 (sum of all 3 accounts)
  test('E2E-1: Total balance shows grand total when no filter is active', async ({ page }) => {
    await expect(page.locator('[data-testid="accounts-total-balance"]')).toBeVisible();
    await expect(page.locator('[data-testid="accounts-total-balance"]')).toHaveText('600.00');
  });

  // E2E-2: bankName="Chase" filter → total = 300.00 (100 + 200)
  test('E2E-2: Total balance reflects only filtered accounts', async ({ page }) => {
    await submitFilter(page, { bankName: 'Chase' });

    await expect(page.locator('[data-testid="accounts-total-balance"]')).toBeVisible();
    await expect(page.locator('[data-testid="accounts-total-balance"]')).toHaveText('300.00');
  });

  // E2E-3: Filter matching nothing → empty state visible; total row absent
  test('E2E-3: Total row is absent when no accounts match the filter', async ({ page }) => {
    await submitFilter(page, { bankName: 'NOMATCHING' });

    await expect(page.locator('[data-testid="accounts-empty-state"]')).toBeVisible();
    await expect(page.locator('[data-testid="accounts-total-balance"]')).not.toBeVisible();
  });

  // E2E-4: Clear filter after E2E-2 → total returns to 600.00
  test('E2E-4: Total balance returns to grand total after clearing the filter', async ({ page }) => {
    await submitFilter(page, { bankName: 'Chase' });
    await expect(page.locator('[data-testid="accounts-total-balance"]')).toHaveText('300.00');

    await page.click('[data-testid="filter-clear-link"]');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('[data-testid="accounts-total-balance"]')).toBeVisible();
    await expect(page.locator('[data-testid="accounts-total-balance"]')).toHaveText('600.00');
  });
});
