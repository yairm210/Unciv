const path = require('path');
const { randomUUID } = require('crypto');
const {
  attachDiagnostics,
  ensureTmpDir,
  launchChromium,
  ensureUiProbeBoot,
  waitForUiProbeResult,
  writeJson,
} = require('./ui-e2e-common');

function splitForces(raw) {
  return String(raw || '')
    .split(',')
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0);
}

async function runWarProbe(options) {
  const {
    role,
    outputFilename,
    screenshotFilename,
    timeoutMs,
    assertions,
  } = options;

  const tmpDir = ensureTmpDir();
  const outputPath = path.join(tmpDir, outputFilename);
  const screenshotPath = path.join(tmpDir, screenshotFilename);
  const baseUrl = String(process.env.WEB_BASE_URL || 'http://127.0.0.1:18080').trim().replace(/\/+$/, '');
  const webProfile = String(process.env.WEB_PROFILE || 'phase4-full').trim() || 'phase4-full';
  const startupTimeoutMs = Number(process.env.WEB_UI_STARTUP_TIMEOUT_MS || '20000');
  const startupAttempts = Number(process.env.WEB_UI_STARTUP_ATTEMPTS || '2');
  const runId = randomUUID();

  const report = {
    status: 'UNKNOWN',
    generatedAt: new Date().toISOString(),
    runId,
    role,
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
    attachDiagnostics(page, report, role);

    const url = new URL('/index.html', `${baseUrl}/`);
    url.searchParams.set('webtest', '1');
    url.searchParams.set('webProfile', webProfile);
    url.searchParams.set('uiProbe', '1');
    url.searchParams.set('uiProbeRole', role);
    url.searchParams.set('uiProbeRunId', runId);
    url.searchParams.set('uiProbeTimeoutMs', String(timeoutMs));

    await ensureUiProbeBoot(page, {
      url,
      label: role,
      timeoutMs,
      startupTimeoutMs,
      startupAttempts,
      onRetry: (attempt, reason) => {
        process.stdout.write(`[${role}] startup retry ${attempt + 1}/${startupAttempts}: ${reason}\n`);
        report.pageErrors.length = 0;
        report.consoleErrors.length = 0;
        report.requestFailures.length = 0;
      },
    });

    const probe = await waitForUiProbeResult(
      page,
      role,
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
    if (Array.isArray(assertions)) {
      for (const check of assertions) {
        const failure = check(probe.result, report, splitForces);
        if (failure) failures.push(failure);
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
    if (page) await page.screenshot({ path: screenshotPath, fullPage: true }).catch(() => {});
    if (context) await context.close().catch(() => {});
    if (browser) await browser.close().catch(() => {});
  }

  writeJson(outputPath, report);
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
  if (report.status !== 'PASSED') process.exit(2);
}

module.exports = {
  runWarProbe,
};
