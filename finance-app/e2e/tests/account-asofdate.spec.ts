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

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

// 3 rows with valid past ISO asOfDate values; balance ASC: Chase(100) first
const VALID_ASOFDATE_CSV = [
  'bankName,accountNumber,accountType,balance,currency,asOfDate',
  'Chase,000111222,CHECKING,100.00,USD,2026-08-31',
  'Wells Fargo,333444555,SAVINGS,200.00,USD,2026-01-15',
  'Citi,666777888,CREDIT,300.00,USD,2020-06-01',
].join('\n');

// Header has only 5 columns — asOfDate absent (Tier-1 schema error)
const MISSING_ASOFDATE_COLUMN_CSV = [
  'bankName,accountNumber,accountType,balance,currency',
  'Chase,000111222,CHECKING,100.00,USD',
].join('\n');

// Row 1 asOfDate blank (Tier-2 row error)
const BLANK_ASOFDATE_ROW1_CSV = [
  'bankName,accountNumber,accountType,balance,currency,asOfDate',
  'Chase,000111222,CHECKING,100.00,USD,',
].join('\n');

// Row 1 asOfDate in non-ISO format (Tier-2 row error)
const INVALID_DATE_FORMAT_ROW1_CSV = [
  'bankName,accountNumber,accountType,balance,currency,asOfDate',
  'Chase,000111222,CHECKING,100.00,USD,31/08/2026',
].join('\n');

// Row 1 asOfDate strictly in the future (Tier-2 row error)
const FUTURE_DATE_ROW1_CSV = [
  'bankName,accountNumber,accountType,balance,currency,asOfDate',
  'Chase,000111222,CHECKING,100.00,USD,2099-01-01',
].join('\n');

// ---------------------------------------------------------------------------
// As Of Date column
// ---------------------------------------------------------------------------

test.describe('As Of Date column', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  // E2E-1: Valid 6-col import → As Of Date header visible; first row shows correct date
  test('E2E-1: Valid import shows As Of Date column header and correct date in first row', async ({ page }) => {
    await importCsv(page, VALID_ASOFDATE_CSV);
    await page.waitForURL('**/accounts');

    await expect(page.locator('[data-testid="column-asOfDate"]')).toBeVisible();

    // Balance ASC default: Chase (100.00) is first; its asOfDate = 2026-08-31
    const asOfDateCell = page.locator('[data-testid^="account-asOfDate-"]').first();
    await expect(asOfDateCell).toHaveText('2026-08-31');
  });

  // E2E-2: Missing asOfDate column → schema-error-banner; no row-errors-banner; no new rows imported
  test('E2E-2: Missing asOfDate column triggers schema-error-banner and no row-errors-banner', async ({ page }) => {
    // Establish a known baseline: exactly 1 account
    await importCsv(page, [
      'bankName,accountNumber,accountType,balance,currency,asOfDate',
      'Chase,111000222,CHECKING,50.00,USD,2026-08-31',
    ].join('\n'));
    await page.waitForURL('**/accounts');
    const preCount = await page.locator('[data-testid="accounts-table"] tbody tr').count();

    await importCsv(page, MISSING_ASOFDATE_COLUMN_CSV);

    await expect(page.locator('[data-testid="schema-error-banner"]')).toBeVisible({ timeout: 8_000 });
    await expect(page.locator('[data-testid="row-errors-banner"]')).toHaveCount(0);
    // Table still shows the pre-import rows — schema error does not wipe existing accounts
    const postCount = await page.locator('[data-testid="accounts-table"] tbody tr').count();
    expect(postCount).toBe(preCount);
  });

  // E2E-3: Blank asOfDate on row 1 → row-errors-banner; row-error-1 mentions asOfDate
  test('E2E-3: Blank asOfDate triggers row-errors-banner with asOfDate in row-error-1', async ({ page }) => {
    await importCsv(page, BLANK_ASOFDATE_ROW1_CSV);

    await expect(page.locator('[data-testid="row-errors-banner"]')).toBeVisible({ timeout: 8_000 });
    const rowError1 = page.locator('[data-testid="row-error-1"]');
    await expect(rowError1).toBeVisible();
    await expect(rowError1).toContainText('asOfDate');
  });

  // E2E-4: Non-ISO date on row 1 → row-errors-banner; row-error-1 mentions asOfDate
  test('E2E-4: Non-ISO asOfDate triggers row-errors-banner with asOfDate in row-error-1', async ({ page }) => {
    await importCsv(page, INVALID_DATE_FORMAT_ROW1_CSV);

    await expect(page.locator('[data-testid="row-errors-banner"]')).toBeVisible({ timeout: 8_000 });
    const rowError1 = page.locator('[data-testid="row-error-1"]');
    await expect(rowError1).toBeVisible();
    await expect(rowError1).toContainText('asOfDate');
  });

  // E2E-5: Future date on row 1 → row-errors-banner; row-error-1 mentions asOfDate
  test('E2E-5: Future asOfDate triggers row-errors-banner with asOfDate in row-error-1', async ({ page }) => {
    await importCsv(page, FUTURE_DATE_ROW1_CSV);

    await expect(page.locator('[data-testid="row-errors-banner"]')).toBeVisible({ timeout: 8_000 });
    const rowError1 = page.locator('[data-testid="row-error-1"]');
    await expect(rowError1).toBeVisible();
    await expect(rowError1).toContainText('asOfDate');
  });

  // E2E-6: After valid import, tfoot total is correct and footer row has 4 physical <td>s
  test('E2E-6: Total balance shows correct sum and tfoot has correct cell count', async ({ page }) => {
    await importCsv(page, VALID_ASOFDATE_CSV);
    await page.waitForURL('**/accounts');

    // Total = 100 + 200 + 300 = 600.00
    await expect(page.locator('[data-testid="accounts-total-balance"]')).toHaveText('600.00');

    // tfoot row: colspan-3 Total cell + balance cell + empty currency cell + empty asOfDate cell = 4 physical tds
    const tfootTds = page.locator('[data-testid="accounts-table"] tfoot tr td');
    await expect(tfootTds).toHaveCount(4);
  });
});
