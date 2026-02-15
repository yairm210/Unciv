const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

function parseBool(value, fallback) {
  if (value == null || value === '') return fallback;
  const normalized = String(value).trim().toLowerCase();
  return normalized === '1' || normalized === 'true' || normalized === 'yes' || normalized === 'on';
}

function ensureTmpDir() {
  const tmpDir = path.resolve('tmp');
  fs.mkdirSync(tmpDir, { recursive: true });
  return tmpDir;
}

const bootstrapDebugPath = path.resolve('tmp', 'web-runner-bootstrap-debug.json');

function shouldIgnoreConsoleError(text) {
  return /\/favicon\.ico\b/i.test(text)
    || /Failed to load resource/i.test(text)
    || /uncivserver\.xyz\/chat/i.test(text)
    || /Chat websocket error/i.test(text);
}

function shouldIgnorePageError(text) {
  return /Cannot read properties of null \(reading '\$dispose'\)/.test(text)
    || /Cannot read properties of null \(reading '\$pause'\)/.test(text)
    || /Cannot read properties of null \(reading 'pixelStorei'\)/.test(text);
}

function attachDiagnostics(page, report, label) {
  page.on('pageerror', (err) => {
    const text = String(err && err.stack ? err.stack : err);
    if (shouldIgnorePageError(text)) return;
    report.pageErrors.push(`[${label}] ${text}`);
  });
  page.on('console', (msg) => {
    if (msg.type() !== 'error') return;
    const text = msg.text();
    if (shouldIgnoreConsoleError(text)) return;
    report.consoleErrors.push(`[${label}] ${text}`);
  });
  page.on('requestfailed', (req) => {
    report.requestFailures.push({
      label,
      url: req.url(),
      error: req.failure() ? req.failure().errorText : 'unknown',
    });
  });
}

async function launchChromium() {
  const headless = parseBool(process.env.HEADLESS, true);
  const args = [
    '--disable-background-timer-throttling',
    '--disable-backgrounding-occluded-windows',
    '--disable-renderer-backgrounding',
  ];
  if (process.platform === 'linux') {
    args.push('--use-gl=swiftshader');
  }
  return chromium.launch({
    headless,
    args,
  });
}

async function startMainOnce(page, timeoutMs) {
  const startedAt = Date.now();
  while (Date.now() - startedAt <= timeoutMs) {
    const state = await page.evaluate(() => ({
      hasMain: typeof window.main === 'function',
      bootInvoked: window.__uncivBootStarted === true || window.__uncivUiProbeBootInvoked === true,
      uiProbeState: window.__uncivUiProbeState || null,
      readyState: document.readyState,
    }));
    if (state.bootInvoked || state.uiProbeState) return;
    if (state.hasMain && state.readyState === 'complete') {
      await page.evaluate(() => {
        if (window.__uncivBootStarted === true || window.__uncivUiProbeBootInvoked === true) return;
        window.__uncivUiProbeBootInvoked = true;
        try {
          window.__uncivBootStarted = true;
          window.main();
        } catch (_) {
          window.__uncivBootStarted = false;
          window.__uncivUiProbeBootInvoked = false;
        }
      });
    }

    await page.waitForTimeout(100);
  }
  throw new Error(`runtime boot markers not visible within ${timeoutMs}ms`);
}

async function waitForUiProbeResult(page, label, timeoutMs, shouldAbort) {
  const startupGraceMs = Number(process.env.WEB_UI_RUNNER_STARTUP_GRACE_MS || '3500');
  const startedAt = Date.now();
  let lastState = null;
  while (Date.now() - startedAt <= timeoutMs) {
    if (typeof shouldAbort === 'function') {
      const abortReason = shouldAbort();
      if (abortReason) throw new Error(`[${label}] ${abortReason}`);
    }
    const state = await page.evaluate(() => ({
      state: window.__uncivUiProbeState || null,
      error: window.__uncivUiProbeError || null,
      json: window.__uncivUiProbeResultJson || null,
      steps: window.__uncivUiProbeStepLogJson || null,
      runnerSelected: window.__uncivRunnerSelected || null,
      runnerReason: window.__uncivRunnerReason || null,
      bootstrapTraceJson: window.__uncivBootstrapTraceJson || null,
      search: (window.location && window.location.search) || '',
      hasMain: typeof window.main === 'function',
      bootInvoked: window.__uncivBootStarted === true || window.__uncivUiProbeBootInvoked === true,
    }));
    const elapsedMs = Date.now() - startedAt;
    if (
      elapsedMs >= startupGraceMs
      && state.search.includes('uiProbe=1')
      && state.runnerSelected
      && state.runnerSelected !== 'uiProbe'
    ) {
      const bootstrapDebug = {
        generatedAt: new Date().toISOString(),
        label,
        reason: `runner mismatch: expected uiProbe got ${state.runnerSelected}`,
        elapsedMs,
        state,
      };
      writeJson(bootstrapDebugPath, bootstrapDebug);
      throw new Error(`[${label}] uiProbe runner mismatch: expected uiProbe got ${state.runnerSelected} (reason=${state.runnerReason || 'n/a'})`);
    }
    if (state.state && state.state !== lastState) {
      process.stdout.write(`[${label}] state=${state.state}\n`);
      lastState = state.state;
    }
    if (state.error && !state.json) {
      const bootstrapDebug = {
        generatedAt: new Date().toISOString(),
        label,
        reason: 'uiProbe error before result json',
        elapsedMs,
        state,
      };
      writeJson(bootstrapDebugPath, bootstrapDebug);
      throw new Error(`[${label}] uiProbe error: ${state.error}`);
    }
    if (state.json) {
      const parsed = JSON.parse(state.json);
      let stepLog = null;
      if (state.steps) {
        stepLog = JSON.parse(state.steps);
      }
      return { result: parsed, stepLog };
    }
    await page.waitForTimeout(120);
  }
  const finalState = await page.evaluate(() => ({
    state: window.__uncivUiProbeState || null,
    error: window.__uncivUiProbeError || null,
    hasResult: !!window.__uncivUiProbeResultJson,
    hasStepLog: !!window.__uncivUiProbeStepLogJson,
    runnerSelected: window.__uncivRunnerSelected || null,
    runnerReason: window.__uncivRunnerReason || null,
    bootstrapTraceJson: window.__uncivBootstrapTraceJson || null,
    search: (window.location && window.location.search) || '',
    hasMain: typeof window.main === 'function',
    bootInvoked: window.__uncivBootStarted === true || window.__uncivUiProbeBootInvoked === true,
  }));
  const bootstrapDebug = {
    generatedAt: new Date().toISOString(),
    label,
    reason: `uiProbe timeout after ${timeoutMs}ms`,
    timeoutMs,
    state: finalState,
  };
  writeJson(bootstrapDebugPath, bootstrapDebug);
  throw new Error(`[${label}] timed out waiting for uiProbe result after ${timeoutMs}ms (state=${finalState.state || 'null'} error=${finalState.error || 'null'} hasResult=${finalState.hasResult} hasStepLog=${finalState.hasStepLog} runner=${finalState.runnerSelected || 'null'} reason=${finalState.runnerReason || 'null'} hasMain=${finalState.hasMain} bootInvoked=${finalState.bootInvoked})`);
}

function writeJson(filePath, payload) {
  fs.writeFileSync(filePath, JSON.stringify(payload, null, 2));
}

module.exports = {
  attachDiagnostics,
  ensureTmpDir,
  launchChromium,
  startMainOnce,
  waitForUiProbeResult,
  writeJson,
};
