// capture-screenshots.mjs
// Renders the v0.3 frontend in 1600x1000 headless Chromium and saves
// the screenshots described in .claude/skills/screenshot-howto.
//
// Usage:
//   node tools/capture-screenshots.mjs
//
// Assumes the Vite dev server is running on http://127.0.0.1:5173.
// Mock API is on by default (VITE_USE_MOCK is not 'false').

import { chromium } from '../frontend/node_modules/playwright/index.mjs';
import { mkdir } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT = join(__dirname, '..', 'docs', 'screenshots');
const BASE = 'http://127.0.0.1:5173';
const VIEWPORT = { width: 1600, height: 1000 };

async function loginAs(page, username) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.fill('#login-username', username);
  await page.fill('#login-password', `${username}123`);
  await Promise.all([
    page.waitForURL((u) => !u.toString().endsWith('/login'), { timeout: 10000 }),
    page.click('button[type="submit"]'),
  ]);
  // sysadmin and user1 hit the change-password gate on first login
  if (page.url().includes('/change-password')) {
    await page.fill('#cp-current', `${username}123`);
    await page.fill('#cp-next', 'NewPassword123!');
    const confirm = page.locator('#cp-confirm');
    if (await confirm.count()) await confirm.fill('NewPassword123!');
    await Promise.all([
      page.waitForURL((u) => !u.toString().includes('/change-password'), { timeout: 10000 }),
      page.click('button[type="submit"]'),
    ]);
  }
  await page.waitForLoadState('networkidle');
}

async function logout(page) {
  const btn = page.locator('button:has-text("Logout"), button:has-text("Sign out"), a:has-text("Logout")').first();
  if (await btn.count()) {
    await btn.click();
    await page.waitForURL(/\/login/, { timeout: 10000 }).catch(() => {});
  }
}

async function shot(page, name) {
  const path = join(OUT, name);
  await page.screenshot({ path, fullPage: false });
  console.log('  wrote', name);
}

async function main() {
  await mkdir(OUT, { recursive: true });
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: VIEWPORT });
  const page = await ctx.newPage();

  // 1. Login (unauthenticated)
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await shot(page, '01-login.png');

  // 2. SYS_ADMIN dashboard
  await loginAs(page, 'sysadmin');
  await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle' });
  // Trigger a state change so the dashboard has variety
  const stopBtn = page.locator('button:has-text("Stop")').first();
  if (await stopBtn.count()) {
    await stopBtn.click();
    await page.waitForTimeout(400);
    const startBtn = page.locator('button:has-text("Start")').first();
    if (await startBtn.count()) {
      await startBtn.click();
      await page.waitForTimeout(400);
    }
  }
  await page.waitForTimeout(500);
  await shot(page, '02-dashboard-sysadmin.png');
  await logout(page);

  // 3. USER dashboard
  await loginAs(page, 'user1');
  await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(500);
  await shot(page, '03-dashboard-user.png');
  await logout(page);

  // 4. SYS_ADMIN audit log
  await loginAs(page, 'sysadmin');
  await page.goto(`${BASE}/logs`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(500);
  await shot(page, '04-logs-audit.png');

  // 5. SYS_ADMIN engine log
  // Switch the source filter to engine execution, then pick BPL
  const sourceSel = page.locator('select').first();
  if (await sourceSel.count()) {
    const options = await sourceSel.locator('option').allTextContents();
    const idx = options.findIndex((t) => /engine/i.test(t));
    if (idx >= 0) {
      await sourceSel.selectOption({ index: idx });
      await page.waitForTimeout(400);
    }
  }
  const engineSel = page.locator('select').nth(1);
  if (await engineSel.count()) {
    await engineSel.selectOption({ label: /BPL/ }).catch(() => engineSel.selectOption({ index: 0 }));
    await page.waitForTimeout(1500); // let some heartbeat lines stream
  }
  await shot(page, '05-logs-engine.png');

  // 6. SYS_ADMIN admin/users tab
  await page.goto(`${BASE}/admin`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(500);
  // Click the Users tab if there's a tab nav
  const usersTab = page.locator('button:has-text("Users"), a:has-text("Users"), [role="tab"]:has-text("Users")').first();
  if (await usersTab.count()) await usersTab.click();
  await page.waitForTimeout(400);
  await shot(page, '06-admin-users.png');

  // 7. SYS_ADMIN admin/engines tab
  const enginesTab = page.locator('button:has-text("Engines"), a:has-text("Engines"), [role="tab"]:has-text("Engines")').first();
  if (await enginesTab.count()) await enginesTab.click();
  await page.waitForTimeout(400);
  await shot(page, '07-admin-engines.png');

  // 8. USER's reduced logs view (bonus — proves the RBAC UI gate)
  await logout(page);
  await loginAs(page, 'user1');
  await page.goto(`${BASE}/logs`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(500);
  await shot(page, '08-logs-user-restricted.png');

  await browser.close();
  console.log('\nDone. 8 screenshots in docs/screenshots/');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
