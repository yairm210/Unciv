const fs = require('fs');
const path = require('path');
const { randomUUID } = require('crypto');
const { chromium } = require('playwright');

function parseBool(value, fallback) {
  if (value == null || value === '') return fallback;
  const normalized = String(value).trim().toLowerCase();
  return normalized === '1' || normalized === 'true' || normalized === 'yes' || normalized === 'on';
}

function ensureTmpDir() {
  const dir = path.resolve('tmp');
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

function shouldIgnoreConsoleError(text) {
  return /\/favicon\.ico\b/i.test(text) || /Failed to load resource/i.test(text);
}

async function waitForProbeResult(page, label, timeoutMs) {
  const startedAt = Date.now();
  let lastState = null;
  while (Date.now() - startedAt <= timeoutMs) {
    const state = await page.evaluate(() => ({
      state: window.__uncivMpProbeState || null,
      error: window.__uncivMpProbeError || null,
      json: window.__uncivMpProbeResultJson || null,
    }));
    if (state.state && state.state !== lastState) {
      process.stdout.write(`[${label}] state=${state.state}\n`);
      lastState = state.state;
    }
    if (state.error) {
      throw new Error(`[${label}] probe error: ${state.error}`);
    }
    if (state.json) {
      const parsed = JSON.parse(state.json);
      if (!parsed || parsed.passed !== true) {
        throw new Error(`[${label}] invalid probe result payload`);
      }
      return parsed;
    }
    await page.waitForTimeout(400);
  }
  throw new Error(`[${label}] timed out after ${timeoutMs}ms waiting for probe completion`);
}

async function main() {
  const tmpDir = ensureTmpDir();
  const outputPath = path.join(tmpDir, 'web-multiplayer-multi-instance-result.json');
  const baseUrl = String(process.env.WEB_BASE_URL || 'http://127.0.0.1:18080').trim().replace(/\/+$/, '');
  const mpServer = String(process.env.WEB_MP_SERVER || 'http://127.0.0.1:19090').trim();
  const webProfile = String(process.env.WEB_PROFILE || 'phase4-full').trim() || 'phase4-full';
  const timeoutMs = Number(process.env.WEB_MP_PROBE_TIMEOUT_MS || '300000');
  const headless = parseBool(process.env.HEADLESS, true);
  const gameId = randomUUID();

  const report = {
    status: 'UNKNOWN',
    timestamp: new Date().toISOString(),
    browser: 'chromium',
    webProfile,
    baseUrl,
    mpServer,
    gameId,
    timeoutMs,
    host: null,
    guest: null,
    hostPageErrors: [],
    guestPageErrors: [],
    hostConsoleErrors: [],
    guestConsoleErrors: [],
    screenshots: {
      host: path.join(tmpDir, 'web-mp-host-latest.png'),
      guest: path.join(tmpDir, 'web-mp-guest-latest.png'),
    },
  };

  let browser;
  let hostContext;
  let guestContext;
  let hostPage;
  let guestPage;

  try {
    browser = await chromium.launch({
      headless,
      args: [
        '--use-gl=swiftshader',
        '--disable-background-timer-throttling',
        '--disable-backgrounding-occluded-windows',
        '--disable-renderer-backgrounding',
      ],
    });

    hostContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    guestContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    hostPage = await hostContext.newPage();
    guestPage = await guestContext.newPage();

    hostPage.on('pageerror', (err) => {
      report.hostPageErrors.push(String(err && err.stack ? err.stack : err));
    });
    guestPage.on('pageerror', (err) => {
      report.guestPageErrors.push(String(err && err.stack ? err.stack : err));
    });
    hostPage.on('console', (msg) => {
      if (msg.type() !== 'error') return;
      const text = msg.text();
      if (shouldIgnoreConsoleError(text)) return;
      report.hostConsoleErrors.push(text);
    });
    guestPage.on('console', (msg) => {
      if (msg.type() !== 'error') return;
      const text = msg.text();
      if (shouldIgnoreConsoleError(text)) return;
      report.guestConsoleErrors.push(text);
    });

    const hostUrl = new URL('/index.html', `${baseUrl}/`);
    hostUrl.searchParams.set('webProfile', webProfile);
    hostUrl.searchParams.set('mpProbe', '1');
    hostUrl.searchParams.set('mpRole', 'host');
    hostUrl.searchParams.set('mpGameId', gameId);
    hostUrl.searchParams.set('mpTimeoutMs', String(Math.max(60000, timeoutMs - 15000)));
    hostUrl.searchParams.set('mpServer', mpServer);

    const guestUrl = new URL(hostUrl.toString());
    guestUrl.searchParams.set('mpRole', 'guest');

    await Promise.all([
      hostPage.goto(hostUrl.toString(), { waitUntil: 'domcontentloaded', timeout: 120000 }),
      guestPage.goto(guestUrl.toString(), { waitUntil: 'domcontentloaded', timeout: 120000 }),
    ]);

    const [hostResult, guestResult] = await Promise.all([
      waitForProbeResult(hostPage, 'host', timeoutMs),
      waitForProbeResult(guestPage, 'guest', timeoutMs),
    ]);
    report.host = hostResult;
    report.guest = guestResult;

    const failures = [];
    if (hostResult.role !== 'host') failures.push(`Host role mismatch: ${hostResult.role}`);
    if (guestResult.role !== 'guest') failures.push(`Guest role mismatch: ${guestResult.role}`);
    if (!hostResult.turnSyncObserved) failures.push('Host did not observe guest turn synchronization.');
    if (!guestResult.turnSyncObserved) failures.push('Guest did not produce turn synchronization update.');
    if (!hostResult.peerChatObserved) failures.push('Host did not observe guest chat acknowledgement.');
    if (!guestResult.peerChatObserved) failures.push('Guest did not observe host chat ping.');
    if (!hostResult.ownChatEchoObserved) failures.push('Host did not observe its own chat echo.');
    if (!guestResult.ownChatEchoObserved) failures.push('Guest did not observe its own chat echo.');
    if (report.hostPageErrors.length > 0) failures.push(`Host page errors detected: ${report.hostPageErrors.length}`);
    if (report.guestPageErrors.length > 0) failures.push(`Guest page errors detected: ${report.guestPageErrors.length}`);
    if (report.hostConsoleErrors.length > 0) failures.push(`Host console errors detected: ${report.hostConsoleErrors.length}`);
    if (report.guestConsoleErrors.length > 0) failures.push(`Guest console errors detected: ${report.guestConsoleErrors.length}`);

    report.status = failures.length === 0 ? 'PASSED' : 'FAILED';
    if (failures.length > 0) report.failures = failures;
  } catch (err) {
    report.status = 'FAILED';
    report.failures = [String(err && err.stack ? err.stack : err)];
  } finally {
    if (hostPage) {
      await hostPage.screenshot({ path: report.screenshots.host, fullPage: true }).catch(() => {});
    }
    if (guestPage) {
      await guestPage.screenshot({ path: report.screenshots.guest, fullPage: true }).catch(() => {});
    }
    if (hostContext) await hostContext.close().catch(() => {});
    if (guestContext) await guestContext.close().catch(() => {});
    if (browser) await browser.close().catch(() => {});
  }

  fs.writeFileSync(outputPath, JSON.stringify(report, null, 2));
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
  if (report.status !== 'PASSED') process.exit(2);
}

main().catch((err) => {
  process.stderr.write(`${err && err.stack ? err.stack : String(err)}\n`);
  process.exit(1);
});
