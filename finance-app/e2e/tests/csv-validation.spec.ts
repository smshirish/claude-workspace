import { test, expect, Page } from '@playwright/test';
import * as path from 'path';
import * as os from 'os';
import * as fs from 'fs';
import { login } from './helpers/auth';
import {
  VALID_CSV,
  MISSING_COLUMN_CSV,
  EXTRA_COLUMN_CSV,
  WRONG_ORDER_CSV,
  TYPO_COLUMN_CSV,
  BAD_ACCOUNT_TYPE_ROW1_CSV,
  BLANK_BANK_NAME_ROW2_CSV,
  BAD_BALANCE_ROW1_CSV,
  MULTI_ROW_ERRORS_CSV,
  MIXED_VALID_INVALID_CSV,
} from './helpers/csvFixtures';

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

/** Writes content to a temp file and returns its absolute path. */
function writeTempCsv(content: string): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'finance-e2e-'));
  const filePath = path.join(dir, 'accounts.csv');
  fs.writeFileSync(filePath, content, 'utf-8');
  return filePath;
}

/** Uploads a CSV via the accounts import form and waits for the page to settle. */
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
// F3 — CSV Schema Validation
// ---------------------------------------------------------------------------

test.describe('F3 — CSV Schema Validation', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  // E2E-S1: Missing column shows schema-error-banner
  test('E2E-S1: Missing column shows schema-error-banner', async ({ page }) => {
    await importCsv(page, MISSING_COLUMN_CSV);

    // Schema error banner must be visible
    await expect(
      page.locator('[data-testid="schema-error-banner"]'),
    ).toBeVisible({ timeout: 8_000 });

    // Row errors banner must NOT be present in DOM
    await expect(
      page.locator('[data-testid="row-errors-banner"]'),
    ).toHaveCount(0);

    // Must stay on the accounts page (no redirect away)
    expect(page.url()).toContain('/accounts');
  });

  // E2E-S2: Extra trailing column is accepted — import succeeds
  test('E2E-S2: Extra trailing column is accepted and import succeeds', async ({ page }) => {
    // Seed a known baseline before the test upload
    await importCsv(page, VALID_CSV);
    await page.waitForURL('**/accounts');

    // Now upload with an extra trailing column (2 data rows)
    await importCsv(page, EXTRA_COLUMN_CSV);
    await page.waitForURL('**/accounts');

    // No error banners
    await expect(page.locator('[data-testid="schema-error-banner"]')).toHaveCount(0);
    await expect(page.locator('[data-testid="row-errors-banner"]')).toHaveCount(0);

    // Table is visible and contains the expected 2 imported rows
    const table = page.locator('[data-testid="accounts-table"]');
    await expect(table).toBeVisible();
    await expect(table.locator('tbody tr')).toHaveCount(2);
  });

  // E2E-S3: Wrong column order shows schema-error-banner with position detail
  test('E2E-S3: Wrong column order shows schema-error-banner naming the mismatched position', async ({ page }) => {
    await importCsv(page, WRONG_ORDER_CSV);

    const banner = page.locator('[data-testid="schema-error-banner"]');
    await expect(banner).toBeVisible({ timeout: 8_000 });

    // Error text must reference the positional mismatch (position 1 or column name)
    const bannerText = await banner.textContent();
    expect(bannerText).toBeTruthy();
    // The message should reference a position number or a column name from the mismatch
    expect(bannerText).toMatch(/position|column|bankName|accountNumber/i);
  });

  // E2E-S4: Column name typo shows schema-error-banner referencing the bad column
  test('E2E-S4: Column name typo shows schema-error-banner referencing the bad column name', async ({ page }) => {
    await importCsv(page, TYPO_COLUMN_CSV);

    const banner = page.locator('[data-testid="schema-error-banner"]');
    await expect(banner).toBeVisible({ timeout: 8_000 });

    // Error text must mention the bad column value
    const bannerText = await banner.textContent();
    expect(bannerText).toBeTruthy();
    expect(bannerText).toMatch(/acctNumber|accountNumber/i);
  });

  // E2E-S5: Schema error does not import any rows (table count unchanged)
  test('E2E-S5: Schema error does not import any rows — table count unchanged', async ({ page }) => {
    // Establish a known pre-upload state with exactly 1 row
    await importCsv(page, VALID_CSV.split('\n').slice(0, 2).join('\n')); // header + 1 data row
    await page.waitForURL('**/accounts');
    const preCount = await page
      .locator('[data-testid="accounts-table"] tbody tr')
      .count();
    expect(preCount).toBe(1);

    // Attempt a schema-invalid upload
    await importCsv(page, MISSING_COLUMN_CSV);

    // Count must remain unchanged
    const postCount = await page
      .locator('[data-testid="accounts-table"] tbody tr')
      .count();
    expect(postCount).toBe(1);
  });

  // E2E-S6: Schema error never co-renders row-errors-banner
  test('E2E-S6: Schema error never co-renders row-errors-banner', async ({ page }) => {
    await importCsv(page, MISSING_COLUMN_CSV);

    await expect(
      page.locator('[data-testid="schema-error-banner"]'),
    ).toBeVisible({ timeout: 8_000 });

    // row-errors-banner must have zero occurrences in the DOM
    await expect(
      page.locator('[data-testid="row-errors-banner"]'),
    ).toHaveCount(0);
  });
});

// ---------------------------------------------------------------------------
// F4 — CSV Row-Level Validation
// ---------------------------------------------------------------------------

test.describe('F4 — CSV Row-Level Validation', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  // E2E-R1: Invalid accountType shows row-errors-banner and row-error-1
  test('E2E-R1: Invalid accountType shows row-errors-banner with row-error-1 naming MORTGAGE and allowed values', async ({ page }) => {
    await importCsv(page, BAD_ACCOUNT_TYPE_ROW1_CSV);

    const banner = page.locator('[data-testid="row-errors-banner"]');
    await expect(banner).toBeVisible({ timeout: 8_000 });

    const rowError1 = page.locator('[data-testid="row-error-1"]');
    await expect(rowError1).toBeVisible();

    const text = await rowError1.textContent();
    expect(text).toBeTruthy();
    // Must name the bad value
    expect(text).toContain('MORTGAGE');
    // Must list at least one allowed value
    expect(text).toMatch(/CHECKING|SAVINGS|CREDIT|INVESTMENT|OTHER/);
  });

  // E2E-R2: Blank mandatory field shows row error for row 2 referencing bankName
  test('E2E-R2: Blank bankName on row 2 shows row-error-2 referencing bankName as required', async ({ page }) => {
    await importCsv(page, BLANK_BANK_NAME_ROW2_CSV);

    const rowError2 = page.locator('[data-testid="row-error-2"]');
    await expect(rowError2).toBeVisible({ timeout: 8_000 });

    const text = await rowError2.textContent();
    expect(text).toBeTruthy();
    expect(text).toMatch(/bankName/i);
    expect(text).toMatch(/required|blank/i);
  });

  // E2E-R3: Unparseable balance shows row-error-1 referencing balance column
  test('E2E-R3: Unparseable balance on row 1 shows row-error-1 referencing balance column', async ({ page }) => {
    await importCsv(page, BAD_BALANCE_ROW1_CSV);

    const rowError1 = page.locator('[data-testid="row-error-1"]');
    await expect(rowError1).toBeVisible({ timeout: 8_000 });

    const text = await rowError1.textContent();
    expect(text).toBeTruthy();
    expect(text).toMatch(/balance/i);
  });

  // E2E-R4: Multiple rows with errors — all collected
  test('E2E-R4: Multiple rows with errors — all error entries collected in row-errors-banner', async ({ page }) => {
    await importCsv(page, MULTI_ROW_ERRORS_CSV);

    const banner = page.locator('[data-testid="row-errors-banner"]');
    await expect(banner).toBeVisible({ timeout: 8_000 });

    // Each of the 3 data rows has exactly 1 error → 3 <li> items
    const items = banner.locator('li');
    await expect(items).toHaveCount(3);

    // Each per-row element must be present
    await expect(page.locator('[data-testid="row-error-1"]')).toBeVisible();
    await expect(page.locator('[data-testid="row-error-2"]')).toBeVisible();
    await expect(page.locator('[data-testid="row-error-3"]')).toBeVisible();
  });

  // E2E-R5: Mixed valid/invalid — validation continues past the first valid row
  test('E2E-R5: Mixed valid/invalid rows — row-error-2 present, row-error-1 absent', async ({ page }) => {
    await importCsv(page, MIXED_VALID_INVALID_CSV);

    const banner = page.locator('[data-testid="row-errors-banner"]');
    await expect(banner).toBeVisible({ timeout: 8_000 });

    // Row 2 (bad accountType) must have an error entry
    await expect(page.locator('[data-testid="row-error-2"]')).toBeVisible();

    // Row 1 was valid — its error entry must NOT exist
    await expect(page.locator('[data-testid="row-error-1"]')).toHaveCount(0);
  });

  // E2E-R6: Row errors do not import any rows — table count unchanged
  test('E2E-R6: Row errors do not import any rows — table count unchanged', async ({ page }) => {
    // Establish a baseline of exactly 1 row
    await importCsv(page, VALID_CSV.split('\n').slice(0, 2).join('\n')); // header + 1 row
    await page.waitForURL('**/accounts');
    const preCount = await page
      .locator('[data-testid="accounts-table"] tbody tr')
      .count();
    expect(preCount).toBe(1);

    // Upload CSV that will fail row validation
    await importCsv(page, BAD_ACCOUNT_TYPE_ROW1_CSV);

    const postCount = await page
      .locator('[data-testid="accounts-table"] tbody tr')
      .count();
    expect(postCount).toBe(1);
  });

  // E2E-R7: Row error never co-renders schema-error-banner
  test('E2E-R7: Row error never co-renders schema-error-banner', async ({ page }) => {
    await importCsv(page, BAD_ACCOUNT_TYPE_ROW1_CSV);

    await expect(
      page.locator('[data-testid="row-errors-banner"]'),
    ).toBeVisible({ timeout: 8_000 });

    // schema-error-banner must have zero occurrences in the DOM
    await expect(
      page.locator('[data-testid="schema-error-banner"]'),
    ).toHaveCount(0);
  });
});
