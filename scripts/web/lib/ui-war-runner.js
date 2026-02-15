const path = require('path');
const fs = require('fs');
const zlib = require('zlib');
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

const preloadFilesByRole = {
  war_from_start: {
    payload: 'war-from-start.save.txt',
    metadata: 'war-from-start.meta.json',
  },
  war_preworld: {
    payload: 'war-preworld.save.txt',
    metadata: 'war-preworld.meta.json',
  },
  war_deep: {
    payload: 'war-deep.save.txt',
    metadata: 'war-deep.meta.json',
  },
};

async function ensureIndexReachable(baseUrl) {
  const target = new URL('/index.html?preflight=1', `${baseUrl}/`).toString();
  const response = await fetch(target, { method: 'GET' });
  if (!response.ok) {
    throw new Error(`static server preflight failed for ${target} (status=${response.status})`);
  }
}

async function withTimeout(promise, timeoutMs) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(`operation timed out after ${timeoutMs}ms`)), timeoutMs);
  });
  try {
    return await Promise.race([promise, timeout]);
  } finally {
    clearTimeout(timer);
  }
}

function loadWarPreload(role) {
  const fileMap = preloadFilesByRole[role];
  if (!fileMap) {
    throw new Error(`unsupported war preload role: ${role}`);
  }
  const preloadRoot = path.resolve('web', 'src', 'main', 'resources', 'webtest', 'preloads');
  const payloadPath = path.join(preloadRoot, fileMap.payload);
  const metadataPath = path.join(preloadRoot, fileMap.metadata);
  if (!fs.existsSync(payloadPath)) {
    throw new Error(`missing war preload payload file: ${payloadPath}`);
  }
  if (!fs.existsSync(metadataPath)) {
    throw new Error(`missing war preload metadata file: ${metadataPath}`);
  }
  const payloadRaw = String(fs.readFileSync(payloadPath, 'utf8')).trim();
  if (!payloadRaw) {
    throw new Error(`empty war preload payload file: ${payloadPath}`);
  }
  const { runtimePayload, payloadEncoding } = decodeWarPreloadPayload(payloadRaw, payloadPath);
  const metadataRaw = String(fs.readFileSync(metadataPath, 'utf8')).trim();
  if (!metadataRaw) {
    throw new Error(`empty war preload metadata file: ${metadataPath}`);
  }
  let metadata;
  try {
    metadata = JSON.parse(metadataRaw);
  } catch (err) {
    throw new Error(`invalid war preload metadata JSON (${metadataPath}): ${String(err && err.message ? err.message : err)}`);
  }
  return {
    payload: runtimePayload,
    payloadEncoding,
    metadataRaw,
    metadataPath,
    payloadPath,
    metadata,
  };
}

function decodeWarPreloadPayload(payloadRaw, payloadPath) {
  const trimmed = String(payloadRaw || '').trim();
  if (!trimmed) {
    throw new Error(`empty war preload payload: ${payloadPath}`);
  }
  if (trimmed.startsWith('{')) {
    return { runtimePayload: trimmed, payloadEncoding: 'json' };
  }
  try {
    const decoded = Buffer.from(trimmed, 'base64');
    if (!decoded.length) throw new Error('base64 decode produced empty payload');
    const unzipped = zlib.gunzipSync(decoded).toString('utf8').trim();
    if (!unzipped.startsWith('{')) {
      throw new Error('gunzip payload does not look like JSON');
    }
    return { runtimePayload: unzipped, payloadEncoding: 'gzip+base64' };
  } catch (err) {
    const reason = err && err.message ? err.message : String(err);
    throw new Error(`failed to decode war preload payload ${payloadPath}: ${reason}`);
  }
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
  const warPreload = loadWarPreload(role);

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
    warPreloadMetadata: warPreload.metadata,
    warPreloadPayloadEncoding: warPreload.payloadEncoding,
    warPreloadPayloadPath: warPreload.payloadPath,
    warPreloadMetadataPath: warPreload.metadataPath,
  };

  let browser;
  let context;
  let page;
  try {
    browser = await launchChromium();
    context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    await context.addInitScript(({ payload, metadataJson }) => {
      window.__uncivWarPreloadPayload = payload;
      window.__uncivWarPreloadMetaJson = metadataJson;
    }, {
      payload: warPreload.payload,
      metadataJson: warPreload.metadataRaw,
    });
    page = await context.newPage();
    attachDiagnostics(page, report, role);
    await ensureIndexReachable(baseUrl);

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
    if (page) await withTimeout(page.screenshot({ path: screenshotPath, fullPage: true }).catch(() => {}), 5000).catch(() => {});
    if (context) await withTimeout(context.close().catch(() => {}), 5000).catch(() => {});
    if (browser) await withTimeout(browser.close().catch(() => {}), 5000).catch(() => {});
  }

  writeJson(outputPath, report);
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
  if (report.status !== 'PASSED') process.exit(2);
}

module.exports = {
  runWarProbe,
};
