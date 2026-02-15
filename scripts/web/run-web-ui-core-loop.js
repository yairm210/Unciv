const path = require('path');
const { randomUUID } = require('crypto');
const {
  attachDiagnostics,
  ensureTmpDir,
  launchChromium,
  ensureUiProbeBoot,
  waitForUiProbeResult,
  writeJson,
} = require('./lib/ui-e2e-common');

async function main() {
  const tmpDir = ensureTmpDir();
  const outputPath = path.join(tmpDir, 'web-ui-core-loop-result.json');
  const screenshotPath = path.join(tmpDir, 'web-ui-core-loop-latest.png');
  const baseUrl = String(process.env.WEB_BASE_URL || 'http://127.0.0.1:18080').trim().replace(/\/+$/, '');
  const webProfile = String(process.env.WEB_PROFILE || 'phase1').trim() || 'phase1';
  const timeoutMs = Number(process.env.WEB_UI_CORE_LOOP_TIMEOUT_MS || process.env.WEB_UI_TIMEOUT_MS || '45000');
  const startupTimeoutMs = Number(process.env.WEB_UI_STARTUP_TIMEOUT_MS || '20000');
  const startupAttempts = Number(process.env.WEB_UI_STARTUP_ATTEMPTS || '2');
  const runId = randomUUID();

  const report = {
    status: 'UNKNOWN',
    generatedAt: new Date().toISOString(),
    runId,
    role: 'solo',
    browser: 'chromium',
    webProfile,
    timeoutMs,
    baseUrl,
    result: null,
    stepLog: null,
    pageErrors: [],
    consoleErrors: [],
    requestFailures: [],
    screenshotPath,
  };

  let browser;
  let context;
  let page;
  try {
    browser = await launchChromium();
    context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    page = await context.newPage();
    attachDiagnostics(page, report, 'solo');

    const url = new URL('/index.html', `${baseUrl}/`);
    url.searchParams.set('webtest', '1');
    url.searchParams.set('webProfile', webProfile);
    url.searchParams.set('uiProbe', '1');
    url.searchParams.set('uiProbeRole', 'solo');
    url.searchParams.set('uiProbeRunId', runId);
    url.searchParams.set('uiProbeTimeoutMs', String(timeoutMs));

    await ensureUiProbeBoot(page, {
      url,
      label: 'solo',
      timeoutMs,
      startupTimeoutMs,
      startupAttempts,
      onRetry: (attempt, reason) => {
        process.stdout.write(`[solo] startup retry ${attempt + 1}/${startupAttempts}: ${reason}\n`);
        report.pageErrors.length = 0;
        report.consoleErrors.length = 0;
        report.requestFailures.length = 0;
      },
    });
    const probe = await waitForUiProbeResult(
      page,
      'solo',
      timeoutMs,
      () => {
        if (report.pageErrors.length > 0) return `fatal page error before probe completion: ${report.pageErrors[0]}`;
        if (report.consoleErrors.length > 0) return `fatal console error before probe completion: ${report.consoleErrors[0]}`;
        return null;
      },
    );
    report.result = probe.result;
    report.stepLog = probe.stepLog;

    const failures = [];
    if (probe.result.passed !== true) failures.push(`solo result passed=false notes=${probe.result.notes || ''}`);
    if (!/found city/i.test(String(probe.result.notes || ''))) failures.push('solo notes missing "found city"');
    if (!/construction/i.test(String(probe.result.notes || ''))) failures.push('solo notes missing "construction"');
    if (!/tech/i.test(String(probe.result.notes || ''))) failures.push('solo notes missing "tech"');
    if (!/10 turns/i.test(String(probe.result.notes || ''))) failures.push('solo notes missing "10 turns"');
    if (report.pageErrors.length > 0) failures.push(`page errors detected: ${report.pageErrors.length}`);
    if (report.consoleErrors.length > 0) failures.push(`console errors detected: ${report.consoleErrors.length}`);

    report.status = failures.length === 0 ? 'PASSED' : 'FAILED';
    if (failures.length > 0) report.failures = failures;
  } catch (err) {
    report.status = 'FAILED';
    report.failures = [String(err && err.stack ? err.stack : err)];
  } finally {
    if (page) await page.screenshot({ path: screenshotPath, fullPage: true }).catch(() => {});
    if (context) await context.close().catch(() => {});
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
