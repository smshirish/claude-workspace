import { test, expect, Page } from '@playwright/test';
import { login } from './helpers/auth';
import * as path from 'path';
import * as os from 'os';
import * as fs from 'fs';

// Load ADMIN_PASSWORD from secrets file if not already in the environment.
// The orchestrator sources bin/env.sh before running tests; this fallback
// supports running the spec directly (e.g. npx playwright test portfolio.spec.ts).
if (!process.env.ADMIN_PASSWORD) {
  const secretsFile = path.join(__dirname, '..', '..', 'local', 'finance-app-secrets.env');
  if (fs.existsSync(secretsFile)) {
    const match = fs.readFileSync(secretsFile, 'utf-8').match(/^ADMIN_PASSWORD=(.+)$/m);
    if (match) process.env.ADMIN_PASSWORD = match[1].trim();
  }
}

// ---------------------------------------------------------------------------
// Dates — anchored to today 2026-10-17
// ---------------------------------------------------------------------------
const RECENT_DATE = '2026-10-10'; // 7 days ago — not stale (< 14 days)
const STALE_DATE  = '2026-09-27'; // 20 days ago — stale (> 14 days)
const TOMORROW    = '2026-10-18';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function writeTempCsv(content: string): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'finance-e2e-portfolio-'));
  const filePath = path.join(dir, 'portfolio.csv');
  fs.writeFileSync(filePath, content, 'utf-8');
  return filePath;
}

function cleanPortfolioDir(): void {
  const portfolioDir = path.join(os.homedir(), '.finance-app', 'data', 'portfolio');
  if (fs.existsSync(portfolioDir)) {
    fs.rmSync(portfolioDir, { recursive: true, force: true });
  }
}

async function importPortfolioCsv(page: Page, csvContent: string): Promise<void> {
  const filePath = writeTempCsv(csvContent);
  try {
    await page.goto('/portfolio');
    await page.setInputFiles('[data-testid="portfolio-file-input"]', filePath, { timeout: 5000 });
    await page.click('[data-testid="portfolio-import-button"]', { timeout: 5000 });
    await page.waitForLoadState('networkidle');
  } finally {
    if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
    const dir = path.dirname(filePath);
    if (fs.existsSync(dir)) {
      try { fs.rmdirSync(dir); } catch { /* ignore */ }
    }
  }
}

function portfolioRows(page: Page) {
  return page.locator('[data-testid="portfolio-table"] tbody tr');
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

// IBKR Flex Query header (10 required columns in order)
const IBKR_HEADER = 'Account ID,Symbol,Asset Class,Quantity,Price,Market Value,Currency,Report Date,Cost Basis,Unrealized P&L';

// 3 positions — distinct market values enable unambiguous sort assertions
// Symbol ASC order     : AAPL, MSFT, TSLA
// Market Value ASC order: TSLA (300.00), MSFT (750.00), AAPL (1500.00)
// Grand total market value: 2550.00
const VALID_PORTFOLIO_CSV = [
  IBKR_HEADER,
  `U1234567,AAPL,STK,10,150.00,1500.00,USD,${RECENT_DATE},1200.00,300.00`,
  `U1234567,MSFT,STK,5,150.00,750.00,USD,${RECENT_DATE},700.00,50.00`,
  `U1234567,TSLA,STK,3,100.00,300.00,USD,${RECENT_DATE},350.00,-50.00`,
].join('\n');

// 1 position with a date 20 days ago — triggers portfolio-stale-warning
const STALE_PORTFOLIO_CSV = [
  IBKR_HEADER,
  `U1234567,AAPL,STK,10,150.00,1500.00,USD,${STALE_DATE},1200.00,300.00`,
].join('\n');

// Header missing "Market Value" (9 columns) — triggers schema-error-banner
const MISSING_COLUMN_CSV = [
  'Account ID,Symbol,Asset Class,Quantity,Price,Currency,Report Date,Cost Basis,Unrealized P&L',
  `U1234567,AAPL,STK,10,150.00,USD,${RECENT_DATE},1200.00,300.00`,
].join('\n');

// Row with future Report Date — triggers row-errors-banner
const FUTURE_DATE_CSV = [
  IBKR_HEADER,
  `U1234567,AAPL,STK,10,150.00,1500.00,USD,${TOMORROW},1200.00,300.00`,
].join('\n');

// Header only, no data rows — triggers portfolio-import-error
const EMPTY_ROWS_CSV = `${IBKR_HEADER}\n`;

// 4 positions: 2 AAPL (STK + OPT), 1 MSFT (STK), 1 USD (CASH)
// symbol "aapl" filter → 2 rows (AAPL/STK + AAPL/OPT); total = 1600.00
// assetClass "STK" filter → 2 rows (AAPL/STK + MSFT/STK)
const FILTER_PORTFOLIO_CSV = [
  IBKR_HEADER,
  `U1234567,AAPL,STK,10,150.00,1500.00,USD,${RECENT_DATE},1200.00,300.00`,
  `U1234567,AAPL,OPT,5,20.00,100.00,USD,${RECENT_DATE},80.00,20.00`,
  `U1234567,MSFT,STK,5,150.00,750.00,USD,${RECENT_DATE},700.00,50.00`,
  `U1234567,USD,CASH,10000,1.00,10000.00,USD,${RECENT_DATE},,`,
].join('\n');

// ---------------------------------------------------------------------------
// Empty state and navigation
// ---------------------------------------------------------------------------

test.describe('Portfolio — empty state and navigation', () => {
  test.beforeAll(() => {
    cleanPortfolioDir();
  });

  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  // E2E-1 (AC-8): /portfolio with no snapshot on disk → empty-state visible, table absent
  test('E2E-1: no snapshot shows empty state and hides table', async ({ page }) => {
    await page.goto('/portfolio');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('[data-testid="portfolio-empty-state"]')).toBeVisible();
    await expect(page.locator('[data-testid="portfolio-table"]')).not.toBeVisible();
  });

  // E2E-2 (AC-14): nav-portfolio-link present on authenticated page; navigates and is active
  test('E2E-2: Portfolio nav link navigates to /portfolio and shows active class', async ({ page }) => {
    await page.goto('/accounts');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('[data-testid="nav-portfolio-link"]')).toBeVisible();
    await page.click('[data-testid="nav-portfolio-link"]');
    await page.waitForURL('**/portfolio');

    await expect(page.locator('[data-testid="nav-portfolio-link"]')).toHaveClass(/nav-link--active/);
  });
});

// ---------------------------------------------------------------------------
// Upload — happy path and error paths
// ---------------------------------------------------------------------------

test.describe('Portfolio — import', () => {
  test.beforeEach(async ({ page }) => {
    cleanPortfolioDir();
    await login(page);
  });

  // E2E-3 (AC-1): valid CSV → redirects to /portfolio, success banner, table visible
  test('E2E-3: valid CSV upload shows success banner and portfolio table', async ({ page }) => {
    await importPortfolioCsv(page, VALID_PORTFOLIO_CSV);

    await expect(page.locator('[data-testid="portfolio-import-success"]')).toBeVisible();
    await expect(page.locator('[data-testid="portfolio-empty-state"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="portfolio-table"]')).toBeVisible();
  });

  // E2E-4 (AC-2): CSV missing required column → schema-error-banner visible
  test('E2E-4: CSV missing Market Value column shows schema error banner', async ({ page }) => {
    await importPortfolioCsv(page, MISSING_COLUMN_CSV);

    await expect(page.locator('[data-testid="schema-error-banner"]')).toBeVisible();
    await expect(page.locator('[data-testid="row-errors-banner"]')).not.toBeVisible();
  });

  // E2E-5 (AC-4): CSV with future Report Date → row-errors-banner visible
  test('E2E-5: CSV with future Report Date shows row errors banner', async ({ page }) => {
    await importPortfolioCsv(page, FUTURE_DATE_CSV);

    await expect(page.locator('[data-testid="row-errors-banner"]')).toBeVisible();
  });

  // E2E-6 (AC-5): header-only CSV → portfolio-import-error visible
  test('E2E-6: header-only CSV shows import error banner', async ({ page }) => {
    await importPortfolioCsv(page, EMPTY_ROWS_CSV);

    await expect(page.locator('[data-testid="portfolio-import-error"]')).toBeVisible();
  });

  // E2E-7 (AC-7): re-uploading with the same Report Date replaces the snapshot
  test('E2E-7: re-uploading CSV with same Report Date replaces snapshot', async ({ page }) => {
    await importPortfolioCsv(page, VALID_PORTFOLIO_CSV);
    await expect(page.locator('[data-testid="portfolio-import-success"]')).toBeVisible();

    await importPortfolioCsv(page, VALID_PORTFOLIO_CSV);
    await expect(page.locator('[data-testid="portfolio-import-success"]')).toBeVisible();
    await expect(page.locator('[data-testid="portfolio-asof-date"]')).toContainText(RECENT_DATE);
    await expect(portfolioRows(page)).toHaveCount(3);
  });
});

// ---------------------------------------------------------------------------
// Display — sort, filter, total, stale warning
// ---------------------------------------------------------------------------

test.describe('Portfolio — display', () => {
  test.beforeEach(async ({ page }) => {
    cleanPortfolioDir();
    await login(page);
  });

  // E2E-8 (AC-9 partial): default page load shows Symbol ASC; sort-symbol shows ↑
  test('E2E-8: default page load shows Symbol ASC sort with ↑ indicator on sort-symbol', async ({ page }) => {
    await importPortfolioCsv(page, VALID_PORTFOLIO_CSV);
    await page.goto('/portfolio');
    await page.waitForLoadState('networkidle');

    // Symbol ASC → first row symbol is AAPL (alphabetically lowest)
    await expect(page.locator('[data-testid="sort-symbol"]')).toContainText('↑');
    await expect(portfolioRows(page).first().locator('td').nth(0)).toHaveText('AAPL');
    await expect(page.locator('[data-testid="sort-marketValue"]')).not.toContainText('↑');
    await expect(page.locator('[data-testid="sort-marketValue"]')).not.toContainText('↓');
    await expect(page.locator('[data-testid="sort-assetClass"]')).not.toContainText('↑');
    await expect(page.locator('[data-testid="sort-assetClass"]')).not.toContainText('↓');
  });

  // E2E-9 (AC-9): clicking sort-symbol twice toggles to DESC; ↓ indicator shown
  test('E2E-9: clicking sort-symbol twice renders Symbol DESC with ↓ indicator', async ({ page }) => {
    await importPortfolioCsv(page, VALID_PORTFOLIO_CSV);
    await page.goto('/portfolio');
    await page.waitForLoadState('networkidle');

    await page.click('[data-testid="sort-symbol"]');
    await page.waitForLoadState('networkidle');
    await page.click('[data-testid="sort-symbol"]');
    await page.waitForLoadState('networkidle');

    // Symbol DESC → first row is TSLA (alphabetically highest)
    await expect(page.locator('[data-testid="sort-symbol"]')).toContainText('↓');
    await expect(portfolioRows(page).first().locator('td').nth(0)).toHaveText('TSLA');
    await expect(page.locator('[data-testid="sort-assetClass"]')).not.toContainText('↑');
    await expect(page.locator('[data-testid="sort-assetClass"]')).not.toContainText('↓');
    await expect(page.locator('[data-testid="sort-marketValue"]')).not.toContainText('↑');
    await expect(page.locator('[data-testid="sort-marketValue"]')).not.toContainText('↓');
  });

  // E2E-10: clicking sort-marketValue from default → Market Value ASC; ↑ indicator
  test('E2E-10: clicking sort-marketValue shows Market Value ASC order with ↑ indicator', async ({ page }) => {
    await importPortfolioCsv(page, VALID_PORTFOLIO_CSV);
    await page.goto('/portfolio');
    await page.waitForLoadState('networkidle');

    await page.click('[data-testid="sort-marketValue"]');
    await page.waitForLoadState('networkidle');

    // Market Value ASC → first row is TSLA (300.00, lowest)
    await expect(page.locator('[data-testid="sort-marketValue"]')).toContainText('↑');
    await expect(portfolioRows(page).first().locator('td').nth(4)).toHaveText('300.00');
    await expect(page.locator('[data-testid="sort-symbol"]')).not.toContainText('↑');
    await expect(page.locator('[data-testid="sort-symbol"]')).not.toContainText('↓');
  });

  // E2E-11 (AC-10): partial symbol filter (lowercase, case-insensitive) shows matching rows
  test('E2E-11: partial symbol filter shows only matching rows', async ({ page }) => {
    await importPortfolioCsv(page, FILTER_PORTFOLIO_CSV);
    await page.goto('/portfolio');
    await page.waitForLoadState('networkidle');

    await page.fill('[data-testid="portfolio-filter-symbol-input"]', 'aapl');
    await page.click('[data-testid="portfolio-filter-submit-button"]');
    await page.waitForLoadState('networkidle');

    // "aapl" matches AAPL/STK and AAPL/OPT
    await expect(portfolioRows(page)).toHaveCount(2);
    await expect(page.locator('[data-testid="portfolio-filter-clear-link"]')).toBeVisible();
  });

  // E2E-12: asset class filter shows only STK rows
  test('E2E-12: asset class filter shows only STK rows', async ({ page }) => {
    await importPortfolioCsv(page, FILTER_PORTFOLIO_CSV);
    await page.goto('/portfolio');
    await page.waitForLoadState('networkidle');

    await page.fill('[data-testid="portfolio-filter-assetClass-input"]', 'STK');
    await page.click('[data-testid="portfolio-filter-submit-button"]');
    await page.waitForLoadState('networkidle');

    // STK matches AAPL/STK and MSFT/STK
    await expect(portfolioRows(page)).toHaveCount(2);
  });

  // E2E-13: filter matching no rows shows empty state; total market value absent
  test('E2E-13: filter with no matches shows empty state and hides total', async ({ page }) => {
    await importPortfolioCsv(page, FILTER_PORTFOLIO_CSV);
    await page.goto('/portfolio');
    await page.waitForLoadState('networkidle');

    await page.fill('[data-testid="portfolio-filter-symbol-input"]', 'NOMATCHING');
    await page.click('[data-testid="portfolio-filter-submit-button"]');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('[data-testid="portfolio-empty-state"]')).toBeVisible();
    await expect(page.locator('[data-testid="portfolio-total-market-value"]')).not.toBeVisible();
  });

  // E2E-14: clearing filter restores all rows and empties inputs
  test('E2E-14: clearing filter restores all rows and empties inputs', async ({ page }) => {
    await importPortfolioCsv(page, FILTER_PORTFOLIO_CSV);
    await page.goto('/portfolio');
    await page.waitForLoadState('networkidle');

    await page.fill('[data-testid="portfolio-filter-symbol-input"]', 'aapl');
    await page.click('[data-testid="portfolio-filter-submit-button"]');
    await page.waitForLoadState('networkidle');
    await expect(portfolioRows(page)).toHaveCount(2);

    await page.click('[data-testid="portfolio-filter-clear-link"]');
    await page.waitForLoadState('networkidle');

    await expect(portfolioRows(page)).toHaveCount(4);
    await expect(page.locator('[data-testid="portfolio-filter-symbol-input"]')).toHaveValue('');
  });

  // E2E-15 (AC-11): total market value reflects only filtered rows
  test('E2E-15: total market value shows sum of filtered rows', async ({ page }) => {
    await importPortfolioCsv(page, FILTER_PORTFOLIO_CSV);
    await page.goto('/portfolio');
    await page.waitForLoadState('networkidle');

    await page.fill('[data-testid="portfolio-filter-symbol-input"]', 'aapl');
    await page.click('[data-testid="portfolio-filter-submit-button"]');
    await page.waitForLoadState('networkidle');

    // AAPL/STK (1500.00) + AAPL/OPT (100.00) = 1600.00
    await expect(page.locator('[data-testid="portfolio-total-market-value"]')).toHaveText('1600.00');
  });

  // E2E-16 (AC-12): snapshot 20 days old → stale warning visible with as-of date
  test('E2E-16: snapshot 20 days old shows stale warning', async ({ page }) => {
    await importPortfolioCsv(page, STALE_PORTFOLIO_CSV);
    await page.goto('/portfolio');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('[data-testid="portfolio-stale-warning"]')).toBeVisible();
    await expect(page.locator('[data-testid="portfolio-asof-date"]')).toContainText(STALE_DATE);
  });

  // E2E-17 (AC-13): snapshot 7 days old → no stale warning
  test('E2E-17: snapshot 7 days old does not show stale warning', async ({ page }) => {
    await importPortfolioCsv(page, VALID_PORTFOLIO_CSV);
    await page.goto('/portfolio');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('[data-testid="portfolio-stale-warning"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="portfolio-asof-date"]')).toContainText(RECENT_DATE);
  });
});
