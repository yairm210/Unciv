const fs = require('fs');
const path = require('path');
const { randomUUID } = require('crypto');
const { chromium } = require('playwright');
const { resolveChromiumArgs } = require('./lib/chromium-args');

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
  return /\/favicon\.ico\b/i.test(text)
    || /Failed to load resource/i.test(text)
    || /uncivserver\.xyz\/chat/i.test(text)
    || /Chat websocket error/i.test(text)
    || /Exception while deserializing GameInfo JSON/i.test(text);
}

function shouldIgnorePageError(text) {
  return /Cannot read properties of null \(reading '\$dispose'\)/.test(text)
    || /Cannot read properties of null \(reading '\$pause'\)/.test(text);
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
    await page.waitForTimeout(150);
  }
  throw new Error(`[${label}] timed out after ${timeoutMs}ms waiting for probe completion`);
}

async function waitForProbeStart(page, label, timeoutMs) {
  const startedAt = Date.now();
  while (Date.now() - startedAt <= timeoutMs) {
    const state = await page.evaluate(() => ({
      state: window.__uncivMpProbeState || null,
      error: window.__uncivMpProbeError || null,
      json: window.__uncivMpProbeResultJson || null,
    }));
    if (state.state || state.error || state.json) return;
    await page.waitForTimeout(150);
  }
  const bootDebug = await page.evaluate(() => ({
    hasMain: typeof window.main === 'function',
    bootStarted: window.__uncivBootStarted === true,
    readyState: document.readyState,
    href: (window.location && window.location.href) || '',
  }));
  throw new Error(`[${label}] probe did not start within ${timeoutMs}ms. boot=${JSON.stringify(bootDebug)}`);
}

async function startMainOnce(page, label, timeoutMs) {
  const startedAt = Date.now();
  while (Date.now() - startedAt <= timeoutMs) {
    const started = await page.evaluate(() => {
      if (typeof window.main !== 'function') return false;
      if (window.__uncivBootStarted === true || window.__uncivProbeBootInvoked === true) return true;
      window.__uncivBootStarted = true;
      window.__uncivProbeBootInvoked = true;
      window.main();
      return true;
    });
    if (started) return;
    await page.waitForTimeout(100);
  }
  throw new Error(`[${label}] window.main not available for boot within ${timeoutMs}ms`);
}

async function gotoProbeUrl(page, url, label) {
  await page.goto(url.toString(), { waitUntil: 'domcontentloaded', timeout: 120000 });
  const landedUrl = page.url();
  if (!landedUrl.includes('mpProbe=1')) {
    await page.goto(url.toString(), { waitUntil: 'domcontentloaded', timeout: 120000 });
    const retryUrl = page.url();
    if (!retryUrl.includes('mpProbe=1')) {
      throw new Error(`[${label}] did not retain probe query params after navigation. landed=${retryUrl}`);
    }
  }
}

async function main() {
  const tmpDir = ensureTmpDir();
  const outputPath = path.join(tmpDir, 'web-multiplayer-multi-instance-result.json');
  const baseUrl = String(process.env.WEB_BASE_URL || 'http://127.0.0.1:18080').trim().replace(/\/+$/, '');
  const mpServer = String(process.env.WEB_MP_SERVER || 'http://127.0.0.1:19090').trim();
  const webProfile = String(process.env.WEB_PROFILE || 'phase4-full').trim() || 'phase4-full';
  const timeoutMs = Number(process.env.WEB_MP_PROBE_TIMEOUT_MS || '300000');
  const startupTimeoutMs = Number(process.env.WEB_MP_PROBE_STARTUP_TIMEOUT_MS || '60000');
  const startupAttempts = Number(process.env.WEB_MP_PROBE_STARTUP_ATTEMPTS || '2');
  const headless = parseBool(process.env.HEADLESS, true);
  const chromiumArgs = resolveChromiumArgs(process.env.WEB_CHROMIUM_ARGS);
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

  let hostBrowser;
  let guestBrowser;
  let hostContext;
  let guestContext;
  let hostPage;
  let guestPage;

  try {
    hostBrowser = await chromium.launch({
      headless,
      args: chromiumArgs,
    });
    guestBrowser = await chromium.launch({
      headless,
      args: chromiumArgs,
    });

    hostContext = await hostBrowser.newContext({ viewport: { width: 1440, height: 900 } });
    guestContext = await guestBrowser.newContext({ viewport: { width: 1440, height: 900 } });
    hostPage = await hostContext.newPage();
    guestPage = await guestContext.newPage();

    hostPage.on('pageerror', (err) => {
      const text = String(err && err.stack ? err.stack : err);
      if (shouldIgnorePageError(text)) return;
      report.hostPageErrors.push(text);
    });
    guestPage.on('pageerror', (err) => {
      const text = String(err && err.stack ? err.stack : err);
      if (shouldIgnorePageError(text)) return;
      report.guestPageErrors.push(text);
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
    hostUrl.searchParams.set('webtest', '1');
    hostUrl.searchParams.set('mpProbe', '1');
    hostUrl.searchParams.set('mpRole', 'host');
    hostUrl.searchParams.set('mpGameId', gameId);
    hostUrl.searchParams.set('mpTimeoutMs', String(Math.max(60000, timeoutMs - 15000)));
    hostUrl.searchParams.set('mpServer', mpServer);

    const guestUrl = new URL(hostUrl.toString());
    guestUrl.searchParams.set('mpRole', 'guest');

    let startupCompleted = false;
    let startupFailure = null;
    for (let attempt = 1; attempt <= startupAttempts; attempt += 1) {
      try {
        await gotoProbeUrl(hostPage, hostUrl, 'host');
        await startMainOnce(hostPage, 'host', Math.min(20000, timeoutMs));
        await gotoProbeUrl(guestPage, guestUrl, 'guest');
        await startMainOnce(guestPage, 'guest', Math.min(20000, timeoutMs));
        await Promise.all([
          waitForProbeStart(hostPage, 'host', startupTimeoutMs),
          waitForProbeStart(guestPage, 'guest', startupTimeoutMs),
        ]);
        startupCompleted = true;
        break;
      } catch (err) {
        startupFailure = err;
        if (attempt >= startupAttempts) break;
        report.hostPageErrors.length = 0;
        report.guestPageErrors.length = 0;
        report.hostConsoleErrors.length = 0;
        report.guestConsoleErrors.length = 0;
      }
    }
    if (!startupCompleted) {
      throw startupFailure || new Error('Multiplayer probe startup failed before state became available');
    }

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
    if (hostBrowser) await hostBrowser.close().catch(() => {});
    if (guestBrowser) await guestBrowser.close().catch(() => {});
  }

  fs.writeFileSync(outputPath, JSON.stringify(report, null, 2));
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
  if (report.status !== 'PASSED') process.exit(2);
}

main().catch((err) => {
  process.stderr.write(`${err && err.stack ? err.stack : String(err)}\n`);
  process.exit(1);
});
