/**
 * CSV fixture strings for F3/F4 E2E validation tests.
 * Each constant corresponds to a fixture in PLAN.md §5.2.
 */

/** Happy path: correct header, all rows valid. */
export const VALID_CSV = [
  'bankName,accountNumber,accountType,balance,currency',
  'Chase,000111222,CHECKING,1500.00,USD',
  'Wells Fargo,333444555,SAVINGS,8200.50,USD',
  'Citi,666777888,CREDIT,-350.00,USD',
].join('\n');

/** Correct header, no data rows. */
export const HEADER_ONLY_CSV = 'bankName,accountNumber,accountType,balance,currency\n';

/** Schema error: required column `currency` is missing (only 4 columns). */
export const MISSING_COLUMN_CSV = [
  'bankName,accountNumber,accountType,balance',
  'Chase,000111222,CHECKING,1500.00',
].join('\n');

/**
 * Lenient parsing: extra trailing column `notes` appended after the required five.
 * The schema validator must accept this and import proceeds normally.
 */
export const EXTRA_COLUMN_CSV = [
  'bankName,accountNumber,accountType,balance,currency,notes',
  'Chase,000111222,CHECKING,1500.00,USD,some note',
  'Wells Fargo,333444555,SAVINGS,8200.50,USD,another note',
].join('\n');

/** Schema error: columns are in wrong order (`accountNumber` appears before `bankName`). */
export const WRONG_ORDER_CSV = [
  'accountNumber,bankName,accountType,balance,currency',
  '000111222,Chase,CHECKING,1500.00,USD',
].join('\n');

/** Schema error: `accountNumber` is misspelled as `acctNumber`. */
export const TYPO_COLUMN_CSV = [
  'bankName,acctNumber,accountType,balance,currency',
  'Chase,000111222,CHECKING,1500.00,USD',
].join('\n');

/** Row error: row 1 has an invalid `accountType` value (`MORTGAGE`). */
export const BAD_ACCOUNT_TYPE_ROW1_CSV = [
  'bankName,accountNumber,accountType,balance,currency',
  'Chase,000111222,MORTGAGE,1500.00,USD',
].join('\n');

/** Row error: row 2 has a blank `bankName`. Row 1 is valid. */
export const BLANK_BANK_NAME_ROW2_CSV = [
  'bankName,accountNumber,accountType,balance,currency',
  'Chase,000111222,CHECKING,1500.00,USD',
  ',333444555,SAVINGS,8200.50,USD',
].join('\n');

/** Row error: row 1 has an unparseable `balance` value (`not-a-number`). */
export const BAD_BALANCE_ROW1_CSV = [
  'bankName,accountNumber,accountType,balance,currency',
  'Chase,000111222,CHECKING,not-a-number,USD',
].join('\n');

/**
 * Row errors across multiple rows: all errors must be collected (accumulative).
 * Row 1: invalid accountType; Row 2: blank bankName; Row 3: bad balance.
 */
export const MULTI_ROW_ERRORS_CSV = [
  'bankName,accountNumber,accountType,balance,currency',
  'Chase,000111222,MORTGAGE,1500.00,USD',
  ',333444555,SAVINGS,8200.50,USD',
  'Citi,666777888,CHECKING,not-a-number,USD',
].join('\n');

/**
 * Mixed: row 1 is fully valid, row 2 has an invalid `accountType`.
 * Verifies that validation continues past the first valid row.
 */
export const MIXED_VALID_INVALID_CSV = [
  'bankName,accountNumber,accountType,balance,currency',
  'Chase,000111222,CHECKING,1500.00,USD',
  'Wells Fargo,333444555,MORTGAGE,8200.50,USD',
].join('\n');
