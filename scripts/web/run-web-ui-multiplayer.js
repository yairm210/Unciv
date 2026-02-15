const path = require('path');
const { randomUUID } = require('crypto');
const {
  attachDiagnostics,
  ensureTmpDir,
  launchChromium,
  startMainOnce,
  waitForUiProbeResult,
  writeJson,
} = require('./lib/ui-e2e-common');

async function main() {
  const tmpDir = ensureTmpDir();
  const outputPath = path.join(tmpDir, 'web-ui-multiplayer-result.json');
  const hostScreenshotPath = path.join(tmpDir, 'web-ui-multiplayer-host-latest.png');
  const guestScreenshotPath = path.join(tmpDir, 'web-ui-multiplayer-guest-latest.png');
  const baseUrl = String(process.env.WEB_BASE_URL || 'http://127.0.0.1:18080').trim().replace(/\/+$/, '');
  const mpServer = String(process.env.WEB_MP_SERVER || 'http://127.0.0.1:19090').trim();
  const webProfile = String(process.env.WEB_PROFILE || 'phase4-full').trim() || 'phase4-full';
  const timeoutMs = 30000;
  const runId = randomUUID();

  const report = {
    status: 'UNKNOWN',
    generatedAt: new Date().toISOString(),
    runId,
    browser: 'chromium',
    webProfile,
    timeoutMs,
    baseUrl,
    mpServer,
    host: null,
    guest: null,
    hostStepLog: null,
    guestStepLog: null,
    pageErrors: [],
    consoleErrors: [],
    requestFailures: [],
    hostScreenshotPath,
    guestScreenshotPath,
  };

  let browser;
  let hostContext;
  let guestContext;
  let hostPage;
  let guestPage;

  try {
    browser = await launchChromium();
    hostContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    guestContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    hostPage = await hostContext.newPage();
    guestPage = await guestContext.newPage();

    attachDiagnostics(hostPage, report, 'host');
    attachDiagnostics(guestPage, report, 'guest');

    const hostUrl = new URL('/index.html', `${baseUrl}/`);
    hostUrl.searchParams.set('webtest', '1');
    hostUrl.searchParams.set('webProfile', webProfile);
    hostUrl.searchParams.set('uiProbe', '1');
    hostUrl.searchParams.set('uiProbeRole', 'host');
    hostUrl.searchParams.set('uiProbeRunId', runId);
    hostUrl.searchParams.set('uiProbeTimeoutMs', String(timeoutMs));
    hostUrl.searchParams.set('mpServer', mpServer);

    const guestUrl = new URL(hostUrl.toString());
    guestUrl.searchParams.set('uiProbeRole', 'guest');

    await guestPage.goto(guestUrl.toString(), { waitUntil: 'load', timeout: 30000 });
    await startMainOnce(guestPage, 15000);
    await hostPage.goto(hostUrl.toString(), { waitUntil: 'load', timeout: 30000 });
    await startMainOnce(hostPage, 15000);

    const probeOutcomes = await Promise.allSettled([
      waitForUiProbeResult(
        hostPage,
        'host',
        timeoutMs,
        () => {
          if (report.pageErrors.length > 0) return `fatal page error before host probe completion: ${report.pageErrors[0]}`;
          if (report.consoleErrors.length > 0) return `fatal console error before host probe completion: ${report.consoleErrors[0]}`;
          return null;
        },
      ),
      waitForUiProbeResult(
        guestPage,
        'guest',
        timeoutMs,
        () => {
          if (report.pageErrors.length > 0) return `fatal page error before guest probe completion: ${report.pageErrors[0]}`;
          if (report.consoleErrors.length > 0) return `fatal console error before guest probe completion: ${report.consoleErrors[0]}`;
          return null;
        },
      ),
    ]);

    const failures = [];
    const hostOutcome = probeOutcomes[0];
    const guestOutcome = probeOutcomes[1];
    if (hostOutcome.status === 'fulfilled') {
      report.host = hostOutcome.value.result;
      report.hostStepLog = hostOutcome.value.stepLog;
      if (hostOutcome.value.result.passed !== true) failures.push(`host failed: ${hostOutcome.value.result.notes || ''}`);
    } else {
      failures.push(`host probe failed: ${String(hostOutcome.reason)}`);
      const hostState = await hostPage.evaluate(() => ({
        state: window.__uncivUiProbeState || null,
        error: window.__uncivUiProbeError || null,
        resultJson: window.__uncivUiProbeResultJson || null,
        stepLogJson: window.__uncivUiProbeStepLogJson || null,
      }));
      report.hostProbeState = hostState;
      if (hostState.stepLogJson) {
        try { report.hostStepLog = JSON.parse(hostState.stepLogJson); } catch (_) {}
      }
      if (hostState.resultJson) {
        try { report.host = JSON.parse(hostState.resultJson); } catch (_) {}
      }
    }

    if (guestOutcome.status === 'fulfilled') {
      report.guest = guestOutcome.value.result;
      report.guestStepLog = guestOutcome.value.stepLog;
      if (guestOutcome.value.result.passed !== true) failures.push(`guest failed: ${guestOutcome.value.result.notes || ''}`);
    } else {
      failures.push(`guest probe failed: ${String(guestOutcome.reason)}`);
      const guestState = await guestPage.evaluate(() => ({
        state: window.__uncivUiProbeState || null,
        error: window.__uncivUiProbeError || null,
        resultJson: window.__uncivUiProbeResultJson || null,
        stepLogJson: window.__uncivUiProbeStepLogJson || null,
      }));
      report.guestProbeState = guestState;
      if (guestState.stepLogJson) {
        try { report.guestStepLog = JSON.parse(guestState.stepLogJson); } catch (_) {}
      }
      if (guestState.resultJson) {
        try { report.guest = JSON.parse(guestState.resultJson); } catch (_) {}
      }
    }

    if (report.pageErrors.length > 0) failures.push(`page errors detected: ${report.pageErrors.length}`);
    if (report.consoleErrors.length > 0) failures.push(`console errors detected: ${report.consoleErrors.length}`);

    report.status = failures.length === 0 ? 'PASSED' : 'FAILED';
    if (failures.length > 0) report.failures = failures;
  } catch (err) {
    report.status = 'FAILED';
    report.failures = [String(err && err.stack ? err.stack : err)];
  } finally {
    if (hostPage) await hostPage.screenshot({ path: hostScreenshotPath, fullPage: true }).catch(() => {});
    if (guestPage) await guestPage.screenshot({ path: guestScreenshotPath, fullPage: true }).catch(() => {});
    if (hostContext) await hostContext.close().catch(() => {});
    if (guestContext) await guestContext.close().catch(() => {});
    if (browser) await browser.close().catch(() => {});
  }

  writeJson(outputPath, report);
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
  if (report.status !== 'PASSED') process.exit(2);
}

main().catch((err) => {
  process.stderr.write(`${err && err.stack ? err.stack : String(err)}\n`);
  process.exit(1);
});
