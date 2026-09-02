/**
 * capture-screenshots.mjs
 *
 * Captures a fixed set of demo screenshots against the frontend in mock
 * mode. Used to generate the slide deck for the agentic-coding session.
 *
 * Usage:
 *   node capture-screenshots.mjs                # auto-detect dev server
 *   BASE_URL=http://127.0.0.1:5174 node capture-screenshots.mjs
 *
 * Output: ./screenshots/*.png
 */
import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';

const BASE = process.env.BASE_URL || 'http://127.0.0.1:5174';
const OUT  = path.resolve('screenshots');
if (!existsSync(OUT)) await mkdir(OUT, { recursive: true });

const VIEWPORT = { width: 1440, height: 900 };
const FULL_VIEWPORT = { width: 1600, height: 1000 };

async function shot(page, name) {
  const file = path.join(OUT, `${name}.png`);
  await page.screenshot({ path: file, fullPage: false });
  console.log(`✓ ${name}.png`);
}

async function shotFull(page, name) {
  const file = path.join(OUT, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  console.log(`✓ ${name}.png (full)`);
}

async function login(page, username, password) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.fill('input[type="text"]', username);
  await page.fill('input[type="password"]', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(/\/(dashboard|change-password)/, { timeout: 10000 });
  // wait for any post-login layout shift
  await page.waitForLoadState('networkidle');
}

const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: 1 });
const page = await ctx.newPage();

// 1) Login screen
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
await shot(page, '01-login');

// 2) Login with bad credentials -> error state
await page.fill('input[type="text"]', 'admin');
await page.fill('input[type="password"]', 'wrong-password');
await page.click('button[type="submit"]');
try {
  await page.waitForSelector('.login-card__error', { timeout: 5000 });
} catch (e) {
  // fall back: just give it a beat
  await page.waitForTimeout(1500);
}
await shot(page, '02-login-error');
await page.reload({ waitUntil: 'networkidle' });

// 3) Force-change-password screen (use sysadmin)
await login(page, 'sysadmin', 'sysadmin123');
await page.waitForURL(/change-password/);
await shot(page, '03-change-password');

// 4) Sign out -> back to login, then log in as admin (clean path, no force-change)
await page.evaluate(() => localStorage.clear());
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
await login(page, 'admin', 'admin123');
await page.waitForURL(/dashboard/);
await page.waitForTimeout(800);
await shot(page, '04-dashboard');

// 5) Dashboard with one engine toggled to STOPPED to show both states
// (the seed has BPL=RUNNING, PCL=STOPPED, so the default view is good)

// 6) Logs page (System Audit Logs)
await page.goto(`${BASE}/logs`, { waitUntil: 'networkidle' });
await page.waitForTimeout(800);
await shot(page, '05-logs-audit');

// 7) Logs page (Engine Execution Logs) - click the source dropdown
// Try to find and pick the "Engine Execution Logs" option
try {
  const sourceSelect = page.locator('select').first();
  if (await sourceSelect.count() > 0) {
    const options = await sourceSelect.locator('option').allTextContents();
    const target = options.find(o => /engine.*execution/i.test(o));
    if (target) {
      await sourceSelect.selectOption({ label: target });
      await page.waitForTimeout(500);
    }
  }
} catch (e) { /* fall through */ }
await shot(page, '06-logs-engine');

// 8) Admin Panel (Users tab)
await page.goto(`${BASE}/admin`, { waitUntil: 'networkidle' });
await page.waitForTimeout(800);
await shot(page, '07-admin-users');

// 9) Admin Panel (Engines tab)
try {
  // try to click the Engines tab if it exists
  const tabs = page.locator('button, [role="tab"]');
  const count = await tabs.count();
  for (let i = 0; i < count; i++) {
    const text = (await tabs.nth(i).textContent()) || '';
    if (/engines/i.test(text)) {
      await tabs.nth(i).click();
      break;
    }
  }
  await page.waitForTimeout(500);
} catch (e) {}
await shot(page, '08-admin-engines');

// 10) Full-page dashboard for a clean reference
await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle' });
await page.waitForTimeout(600);
await shotFull(page, '09-dashboard-full');

// 11) Wide viewport for slide use
const wideCtx = await browser.newContext({ viewport: FULL_VIEWPORT, deviceScaleFactor: 2 });
const wide = await wideCtx.newPage();
await login(wide, 'admin', 'admin123');
await wide.waitForURL(/dashboard/);
await wide.waitForTimeout(800);
await wide.screenshot({ path: path.join(OUT, '10-dashboard-wide.png') });
console.log('✓ 10-dashboard-wide.png');

await browser.close();
console.log('\nDone. Output in', OUT);
