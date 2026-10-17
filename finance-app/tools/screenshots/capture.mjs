/**
 * Finance App — per-feature screenshot capture tool.
 *
 * Usage:
 *   npm install          (first time only)
 *   node capture.mjs     (app must be running on the APP_PORT in app.env)
 *
 * Output: output/<feature>/<nn>_<description>.png
 *
 * Credentials: admin / $ADMIN_PASSWORD (run via: source bin/env.sh)
 */

import { chromium } from 'playwright';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';
import { fileURLToPath } from 'url';

// Port comes from finance-app/app.env (single source of truth)
const APP_PORT = fs.readFileSync(
  path.join(fileURLToPath(import.meta.url), '..', '..', '..', 'app.env'), 'utf8',
).match(/^APP_PORT=(\d+)/m)[1];
const BASE_URL = `http://localhost:${APP_PORT}`;
const OUT_DIR = path.join(fileURLToPath(import.meta.url), '..', 'output');

// ── CSV fixture ─────────────────────────────────────────────────────────────
// Four accounts across three banks — enough to demonstrate every feature.
const FIXTURE_CSV = [
  'bankName,accountNumber,accountType,balance,currency,asOfDate',
  'Chase,000111222,SAVINGS,100.00,USD,2026-08-31',
  'Chase,333444555,CHECKING,200.00,USD,2026-08-31',
  'Ally,666777888,SAVINGS,300.00,USD,2026-08-31',
  'BOFA,999888777,CHECKING,150.00,USD,2026-08-31',
].join('\n');

// ── Helpers ──────────────────────────────────────────────────────────────────

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function writeTempCsv(content) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'finance-screenshots-'));
  const file = path.join(dir, 'accounts.csv');
  fs.writeFileSync(file, content, 'utf-8');
  return { file, cleanup: () => { fs.unlinkSync(file); fs.rmdirSync(dir); } };
}

async function login(page) {
  await page.goto(`${BASE_URL}/login`);
  await page.fill('input[name="username"]', 'admin');
  await page.fill('input[name="password"]', process.env.ADMIN_PASSWORD);
  await page.click('button[type="submit"]');
  await page.waitForLoadState('networkidle');
}

async function importCsv(page, content) {
  const { file, cleanup } = writeTempCsv(content);
  await page.goto(`${BASE_URL}/accounts`);
  await page.setInputFiles('[data-testid="accounts-file-input"]', file);
  await page.click('[data-testid="accounts-import-button"]');
  await page.waitForLoadState('networkidle');
  cleanup();
}

async function shot(page, dir, name) {
  await page.waitForTimeout(300);
  await page.screenshot({ path: path.join(dir, name), fullPage: true });
  console.log(`  saved ${name}`);
}

// ── Feature capture functions ────────────────────────────────────────────────

async function captureColumnSorting(page) {
  const dir = path.join(OUT_DIR, 'A_column_sorting');
  ensureDir(dir);
  console.log('\n[A] Column Sorting');

  await page.goto(`${BASE_URL}/accounts`);
  await shot(page, dir, '01_default_balance_asc.png');

  await page.click('[data-testid="sort-bankName"]');
  await page.waitForLoadState('networkidle');
  await shot(page, dir, '02_bank_name_asc.png');

  await page.click('[data-testid="sort-bankName"]');
  await page.waitForLoadState('networkidle');
  await shot(page, dir, '03_bank_name_desc.png');

  await page.click('[data-testid="sort-accountType"]');
  await page.waitForLoadState('networkidle');
  await shot(page, dir, '04_account_type_asc.png');
}

async function captureFilterBar(page) {
  const dir = path.join(OUT_DIR, 'B_filter_bar');
  ensureDir(dir);
  console.log('\n[B] Filter Bar');

  await page.goto(`${BASE_URL}/accounts`);
  await shot(page, dir, '01_unfiltered.png');

  await page.fill('[data-testid="filter-bankName-input"]', 'chase');
  await page.click('[data-testid="filter-submit-button"]');
  await page.waitForLoadState('networkidle');
  await shot(page, dir, '02_filtered_chase.png');

  await page.fill('[data-testid="filter-bankName-input"]', 'NOMATCHING');
  await page.click('[data-testid="filter-submit-button"]');
  await page.waitForLoadState('networkidle');
  await shot(page, dir, '03_no_results.png');

  await page.click('[data-testid="filter-clear-link"]');
  await page.waitForLoadState('networkidle');
  await shot(page, dir, '04_cleared.png');

  // Filter + sort combination
  await page.fill('[data-testid="filter-bankName-input"]', 'chase');
  await page.click('[data-testid="filter-submit-button"]');
  await page.waitForLoadState('networkidle');
  await page.click('[data-testid="sort-accountType"]');
  await page.waitForLoadState('networkidle');
  await shot(page, dir, '05_filter_and_sort.png');
}

async function captureFilteredTotal(page) {
  const dir = path.join(OUT_DIR, 'C_filtered_total');
  ensureDir(dir);
  console.log('\n[C] Filtered Total');

  await page.goto(`${BASE_URL}/accounts`);
  await shot(page, dir, '01_total_all_accounts.png');

  await page.fill('[data-testid="filter-bankName-input"]', 'chase');
  await page.click('[data-testid="filter-submit-button"]');
  await page.waitForLoadState('networkidle');
  await shot(page, dir, '02_total_filtered_chase.png');

  await page.fill('[data-testid="filter-bankName-input"]', 'NOMATCHING');
  await page.click('[data-testid="filter-submit-button"]');
  await page.waitForLoadState('networkidle');
  await shot(page, dir, '03_total_hidden_on_empty.png');
}

async function captureAsOfDate(page) {
  const dir = path.join(OUT_DIR, 'D_as_of_date');
  ensureDir(dir);
  console.log('\n[D] As Of Date');

  await page.goto(`${BASE_URL}/accounts`);
  await shot(page, dir, '01_as_of_date_column.png');
}

async function captureCsvImport(page) {
  const dir = path.join(OUT_DIR, 'csv_import');
  ensureDir(dir);
  console.log('\n[CSV Import]');

  await page.goto(`${BASE_URL}/accounts`);
  await shot(page, dir, '01_import_form.png');

  const { file, cleanup } = writeTempCsv(FIXTURE_CSV);
  await page.setInputFiles('[data-testid="accounts-file-input"]', file);
  await shot(page, dir, '02_file_selected.png');
  await page.click('[data-testid="accounts-import-button"]');
  await page.waitForLoadState('networkidle');
  cleanup();
  await shot(page, dir, '03_after_import.png');
}

async function captureCsvValidation(page) {
  const dir = path.join(OUT_DIR, 'csv_validation');
  ensureDir(dir);
  console.log('\n[CSV Validation]');

  // Schema error: missing asOfDate column
  const schemaErrorCsv = [
    'bankName,accountNumber,accountType,balance,currency',
    'Chase,000111222,SAVINGS,100.00,USD',
  ].join('\n');

  await importCsv(page, schemaErrorCsv);
  await shot(page, dir, '01_schema_error_missing_column.png');

  // Row error: invalid balance
  const rowErrorCsv = [
    'bankName,accountNumber,accountType,balance,currency,asOfDate',
    'Chase,000111222,SAVINGS,NOT_A_NUMBER,USD,2026-08-31',
  ].join('\n');

  await importCsv(page, rowErrorCsv);
  await shot(page, dir, '02_row_error_invalid_balance.png');
}

async function capturePortfolioView(page) {
  const dir = path.join(OUT_DIR, 'E_portfolio_view');
  ensureDir(dir);
  console.log('\n[E] Portfolio View');

  const IBKR_HEADER = 'Account ID,Symbol,Asset Class,Quantity,Price,Market Value,Currency,Report Date,Cost Basis,Unrealized P&L';
  const portfolioDir = path.join(os.homedir(), '.finance-app', 'data', 'portfolio');

  function cleanPortfolioDir() {
    if (fs.existsSync(portfolioDir)) {
      fs.rmSync(portfolioDir, { recursive: true, force: true });
    }
  }

  async function importPortfolioFixture(csvContent) {
    const { file, cleanup } = writeTempCsv(csvContent);
    try {
      await page.goto(`${BASE_URL}/portfolio`);
      await page.setInputFiles('[data-testid="portfolio-file-input"]', file);
      await page.click('[data-testid="portfolio-import-button"]');
      await page.waitForLoadState('networkidle');
    } finally {
      cleanup();
    }
  }

  const today = new Date();
  const recentDate = new Date(today);
  recentDate.setDate(today.getDate() - 7);
  const staleDate = new Date(today);
  staleDate.setDate(today.getDate() - 20);
  const fmtDate = (d) => d.toISOString().slice(0, 10);

  const VALID_CSV = [
    IBKR_HEADER,
    `U1234567,AAPL,STK,10,150.00,1500.00,USD,${fmtDate(recentDate)},1200.00,300.00`,
    `U1234567,MSFT,STK,5,150.00,750.00,USD,${fmtDate(recentDate)},700.00,50.00`,
    `U1234567,TSLA,STK,3,100.00,300.00,USD,${fmtDate(recentDate)},350.00,-50.00`,
  ].join('\n');

  const STALE_CSV = [
    IBKR_HEADER,
    `U1234567,AAPL,STK,10,150.00,1500.00,USD,${fmtDate(staleDate)},1200.00,300.00`,
  ].join('\n');

  const FILTER_CSV = [
    IBKR_HEADER,
    `U1234567,AAPL,STK,10,150.00,1500.00,USD,${fmtDate(recentDate)},1200.00,300.00`,
    `U1234567,AAPL,OPT,5,20.00,100.00,USD,${fmtDate(recentDate)},80.00,20.00`,
    `U1234567,MSFT,STK,5,150.00,750.00,USD,${fmtDate(recentDate)},700.00,50.00`,
    `U1234567,USD,CASH,10000,1.00,10000.00,USD,${fmtDate(recentDate)},,`,
  ].join('\n');

  const MISSING_COLUMN_CSV = [
    'Account ID,Symbol,Asset Class,Quantity,Price,Currency,Report Date,Cost Basis,Unrealized P&L',
    `U1234567,AAPL,STK,10,150.00,USD,${fmtDate(recentDate)},1200.00,300.00`,
  ].join('\n');

  // 01: empty state — no snapshot on disk
  cleanPortfolioDir();
  await page.goto(`${BASE_URL}/portfolio`);
  await page.waitForLoadState('networkidle');
  await shot(page, dir, '01_empty_state.png');

  // 02: table default — Symbol ASC, as-of date, total in footer
  await importPortfolioFixture(VALID_CSV);
  await page.goto(`${BASE_URL}/portfolio`);
  await page.waitForLoadState('networkidle');
  await shot(page, dir, '02_table_default.png');

  // 03: stale warning — snapshot older than 14 days
  cleanPortfolioDir();
  await importPortfolioFixture(STALE_CSV);
  await page.goto(`${BASE_URL}/portfolio`);
  await page.waitForLoadState('networkidle');
  await shot(page, dir, '03_stale_warning.png');

  // 04: filtered — symbol=aapl shows 2 rows with filtered total
  cleanPortfolioDir();
  await importPortfolioFixture(FILTER_CSV);
  await page.fill('[data-testid="portfolio-filter-symbol-input"]', 'aapl');
  await page.click('[data-testid="portfolio-filter-submit-button"]');
  await page.waitForLoadState('networkidle');
  await shot(page, dir, '04_filtered.png');

  // 05: schema error — missing Market Value column
  await page.goto(`${BASE_URL}/portfolio`);
  const { file: badFile, cleanup: badCleanup } = writeTempCsv(MISSING_COLUMN_CSV);
  try {
    await page.setInputFiles('[data-testid="portfolio-file-input"]', badFile);
    await page.click('[data-testid="portfolio-import-button"]');
    await page.waitForLoadState('networkidle');
  } finally {
    badCleanup();
  }
  await shot(page, dir, '05_schema_error.png');
}

async function captureDashboard(page) {
  const dir = path.join(OUT_DIR, 'dashboard');
  ensureDir(dir);
  console.log('\n[Dashboard]');

  await page.goto(`${BASE_URL}/dashboard`);
  await shot(page, dir, '01_dashboard.png');
}

// ── Main ─────────────────────────────────────────────────────────────────────

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage();
await page.setViewportSize({ width: 1280, height: 900 });

console.log('Logging in…');
await login(page);

console.log('Importing fixture data…');
await importCsv(page, FIXTURE_CSV);

await captureDashboard(page);
await captureColumnSorting(page);
await captureFilterBar(page);
await captureFilteredTotal(page);
await captureAsOfDate(page);
await captureCsvImport(page);
await captureCsvValidation(page);
await capturePortfolioView(page);

await browser.close();
console.log(`\nDone. Screenshots saved to: ${OUT_DIR}`);
