import { Page } from '@playwright/test';

/**
 * Logs in to the finance app using the default admin credentials.
 * Navigates to /login, fills credentials, and waits for redirect to complete.
 */
export async function login(page: Page, username = 'admin', password = 'admin123'): Promise<void> {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  // Wait until we leave the login page (successful redirect)
  await page.waitForURL((url) => !url.pathname.includes('/login'));
}
