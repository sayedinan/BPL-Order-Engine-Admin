---
name: screenshot-howto
description: v0.3 — how to capture screenshots of the running app for the slide deck. Which pages, which roles, where to save, naming convention. Used by task #28.
---

# Screenshot howto (v0.3)

The team lead's brief asks for screenshots to use in the slide deck
that walks the audience through the build. This skill defines the
list of screenshots, the role + page combinations, the file naming,
and the practical "how" (browser DevTools, capture method,
resolution).

## Output location

`docs/screenshots/` at the repo root. Gitignored? **No** — they're
shipped with the repo so the slide deck can be rebuilt. Each
screenshot is a `*.png` at 1600×1000 (or 1920×1080 for 16:9 slides).

## The list

These are the 7 screenshots from task #28:

| # | Role | Page | File name | Notes |
|---|---|---|---|---|
| 1 | (unauthenticated) | `/login` | `01-login.png` | Empty form; shows the branding |
| 2 | SYS_ADMIN | `/dashboard` | `02-dashboard-sysadmin.png` | 3 engine cards (e.g. A, B, C with different statuses), "+ Add Engine" visible |
| 3 | USER (assigned to A and B) | `/dashboard` | `03-dashboard-user.png` | Only 2 cards (A and B); no "+ Add Engine"; Start/Stop visible on both |
| 4 | SYS_ADMIN | `/logs` (audit view) | `04-logs-audit.png` | Source dropdown = "System Audit Logs"; rows showing recent actions |
| 5 | SYS_ADMIN | `/logs` (engine view, engine A) | `05-logs-engine.png` | Source dropdown = "Engine Execution Logs"; engine A selected; live-streaming lines |
| 6 | SYS_ADMIN | `/admin` (Users tab) | `06-admin-users.png` | Table with at least one row per role; "+ Add User" visible |
| 7 | SYS_ADMIN | `/admin` (Engines tab) | `07-admin-engines.png` | Table of engines with mode column; "+ Add Engine" visible; ideally the Add Engine modal open in a follow-up screenshot if room allows |

A USER visiting `/admin` is captured in screenshot 3 implicitly
(the admin link is absent from the AppShell). A USER visiting
`/logs` (engine view) would only show "Engine Execution Logs" in
the Source dropdown — the audit view is hidden. If time allows, an
8th screenshot of `04-logs-audit.png` from a USER's perspective
shows the Source dropdown with only one option, demonstrating the
RBAC UI gate.

## How to capture

The app runs at `http://localhost:5173` (Vite dev server) backed by
`http://localhost:8080` (Spring Boot). The screenshot tool runs in
the developer's browser; it does not need to be a separate process.

**Method 1 (preferred): Chromium DevTools "Capture full size screenshot"**

1. Open the page in Chromium (or Chrome, or Edge).
2. DevTools → toggle device toolbar (Ctrl+Shift+M) → set to
   "Responsive" → width 1600, height 1000.
3. DevTools → ⋮ menu → "Capture full size screenshot".
4. Save to `docs/screenshots/<file-name>.png`.

**Method 2 (when DevTools capture fails): OS screenshot**

1. Resize the browser window to 1600×1000.
2. Use the OS screenshot tool (Win+Shift+S on Windows, Cmd+Shift+4
   on macOS, gnome-screenshot on Linux).
3. Crop to the browser viewport. Save as PNG.

**Method 3 (Playwright, for repeatable captures):**

```typescript
import { chromium } from 'playwright';

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
const page = await context.newPage();

// Login as the role
await page.goto('http://localhost:5173/login');
await page.fill('input[name="username"]', 'admin');
await page.fill('input[name="secret"]', '<seeded-admin-secret-from-env>');
await page.click('button[type="submit"]');
await page.waitForURL('**/dashboard');

// Capture
await page.screenshot({ path: 'docs/screenshots/02-dashboard-sysadmin.png', fullPage: false });

await browser.close();
```

The Playwright route is good for repeatable captures and for
updating screenshots after UI changes. The seed secret comes from
`application-dev.properties` or the dev `.env.local`, **never hard-
coded in the script**.

## What to verify before capturing

- The seeded admin user has at least one engine in each mode
  (MOCK and REAL) so the dashboard has variety.
- The seeded admin has at least one USER-role user assigned to
  engines A and B (not C) for the USER-role dashboard capture.
- A few audit log rows exist (run a few curl calls as admin before
  capturing the audit view).
- The engine A log buffer has at least 10 lines (the mock can
  produce canned lines; a REAL engine can be left running for a
  few minutes to accumulate).

## What NOT to capture

- The browser DevTools panel itself.
- The OS taskbar / dock.
- The URL bar with anything sensitive (the JWT is in
  `localStorage`, not the URL, so this is normally fine).
- The Change Password page (this captures the mustChangePassword
  flag visibly, which is internal state).
- The 404 / 403 pages (these are demo material, not slide material).

## Anti-patterns

- **Don't capture screenshots with seeded credentials visible in
  the URL bar.** The seed goes in `.env.local` (gitignored), not
  the URL.
- **Don't capture screenshots at non-1600×1000.** The slide deck
  layout is fixed; non-matching sizes crop awkwardly.
- **Don't commit `*.psd` or `*.fig` source files.** PNGs only;
  the source design files belong in the design tool, not the repo.
- **Don't include real user data in the screenshots.** Use seeded
  test users with `test-*` usernames (`test-admin`,
  `test-user-bob`).
- **Don't capture the audit log with real production data.** Use
  dev-profile data only.
