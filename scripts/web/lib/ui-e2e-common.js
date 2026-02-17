const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const { resolveChromiumArgs } = require('./chromium-args');

function parseBool(value, fallback) {
  if (value == null || value === '') return fallback;
  const normalized = String(value).trim().toLowerCase();
  return normalized === '1' || normalized === 'true' || normalized === 'yes' || normalized === 'on';
}

function parseNumber(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

const evaluateTimeoutMs = parseNumber(process.env.WEB_UI_EVAL_TIMEOUT_MS, 12000);
const runningEvaluateTimeoutMs = parseNumber(process.env.WEB_UI_RUNNING_EVAL_TIMEOUT_MS, 8000);
const defaultDesktopViewport = { width: 1440, height: 900 };
const defaultMobileViewport = { width: 390, height: 844 };

function parseViewportSpec(raw) {
  if (!raw) return null;
  const normalized = String(raw).trim().toLowerCase();
  const match = /^(\d{2,5})\s*[x,]\s*(\d{2,5})$/.exec(normalized);
  if (!match) return null;
  const width = Number(match[1]);
  const height = Number(match[2]);
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) return null;
  return { width, height };
}

function resolveUiDeviceMode(raw) {
  const normalized = String(raw || 'auto').trim().toLowerCase();
  if (normalized === 'mobile') return 'mobile';
  if (normalized === 'desktop') return 'desktop';
  return 'auto';
}

function buildUiDeviceConfig(defaultViewport = defaultDesktopViewport) {
  const mode = resolveUiDeviceMode(process.env.WEB_UI_DEVICE_MODE);
  const viewportOverride = parseViewportSpec(process.env.WEB_UI_VIEWPORT);
  const viewport = viewportOverride
    || (mode === 'mobile' ? defaultMobileViewport : defaultViewport);
  const contextOptions = { viewport };
  if (mode === 'mobile') {
    contextOptions.hasTouch = true;
    contextOptions.isMobile = true;
    contextOptions.deviceScaleFactor = parseNumber(process.env.WEB_UI_DEVICE_SCALE_FACTOR, 2);
  }
  const webMobileOverride = mode === 'mobile' ? true : (mode === 'desktop' ? false : null);
  return { mode, viewport, contextOptions, webMobileOverride };
}

function applyUiDeviceModeToUrl(urlInput, uiDeviceConfig) {
  const url = typeof urlInput === 'string' ? new URL(urlInput) : new URL(urlInput.toString());
  const override = uiDeviceConfig && Object.prototype.hasOwnProperty.call(uiDeviceConfig, 'webMobileOverride')
    ? uiDeviceConfig.webMobileOverride
    : null;
  if (override === true) url.searchParams.set('webMobile', '1');
  else if (override === false) url.searchParams.set('webMobile', '0');
  else url.searchParams.delete('webMobile');
  return url;
}

async function evaluateWithTimeout(page, fn, arg, label, timeoutMs = evaluateTimeoutMs) {
  let timer = null;
  const evaluatePromise = arg === undefined ? page.evaluate(fn) : page.evaluate(fn, arg);
  const timeoutPromise = new Promise((_, reject) => {
    timer = setTimeout(() => {
      reject(new Error(`[${label}] page evaluate timed out after ${timeoutMs}ms`));
    }, timeoutMs);
  });
  try {
    return await Promise.race([evaluatePromise, timeoutPromise]);
  } finally {
    if (timer !== null) clearTimeout(timer);
  }
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
    || /Chat websocket error/i.test(text)
    || /WebFetch text error .* Failed to fetch/i.test(text);
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

function shouldIgnoreRequestFailure(entry) {
  const url = String(entry && entry.url ? entry.url : '');
  const error = String(entry && entry.error ? entry.error : '');
  if (/favicon\.ico(?:$|\?)/i.test(url)) return true;
  if (/net::ERR_ABORTED/i.test(error) && /\/assets\//i.test(url)) return true;
  return false;
}

function getActionableRequestFailures(entries) {
  if (!Array.isArray(entries)) return [];
  return entries.filter((entry) => !shouldIgnoreRequestFailure(entry));
}

async function launchChromium() {
  const headless = parseBool(process.env.HEADLESS, true);
  const args = resolveChromiumArgs(process.env.WEB_CHROMIUM_ARGS);
  return chromium.launch({
    headless,
    args,
  });
}

function hasUiProbeQuery(search) {
  return /(?:^|[?&])uiProbe(?:=|&|$)/i.test(String(search || ''));
}

function extractErrorText(err) {
  return String(err && err.stack ? err.stack : err);
}

function shouldRetryUiProbeStartup(errText) {
  if (!errText) return false;
  if (/uiProbe startup did not become observable within/i.test(errText)) return true;
  if (/timed out waiting for uiProbe result/i.test(errText) && /state=null/.test(errText) && /runner=null/.test(errText)) return true;
  return false;
}

async function gotoUiProbeUrl(page, url, label) {
  const targetUrl = typeof url === 'string' ? url : url.toString();
  const wantedUiProbe = hasUiProbeQuery(new URL(targetUrl).search);
  await page.goto(targetUrl, { waitUntil: 'load', timeout: 120000 });
  if (!wantedUiProbe) return;
  const landedUrl = page.url();
  if (hasUiProbeQuery(new URL(landedUrl).search)) return;
  await page.goto(targetUrl, { waitUntil: 'load', timeout: 120000 });
  const retryUrl = page.url();
  if (!hasUiProbeQuery(new URL(retryUrl).search)) {
    throw new Error(`[${label}] did not retain uiProbe query params after navigation. landed=${retryUrl}`);
  }
}

async function waitForUiProbeStart(page, label, timeoutMs) {
  const startedAt = Date.now();
  while (Date.now() - startedAt <= timeoutMs) {
    const state = await evaluateWithTimeout(page, () => ({
      state: window.__uncivUiProbeState || null,
      error: window.__uncivUiProbeError || null,
      json: window.__uncivUiProbeResultJson || null,
      runnerSelected: window.__uncivRunnerSelected || null,
      runnerReason: window.__uncivRunnerReason || null,
      search: (window.location && window.location.search) || '',
      hasMain: typeof window.main === 'function',
      bootInvoked: window.__uncivBootStarted === true || window.__uncivUiProbeBootInvoked === true,
    }), undefined, `${label} startup-state`);
    if (hasUiProbeQuery(state.search) && state.runnerSelected && state.runnerSelected !== 'uiProbe') {
      throw new Error(`[${label}] uiProbe runner mismatch during startup: got ${state.runnerSelected} (reason=${state.runnerReason || 'n/a'})`);
    }
    if (state.error || state.json || state.state) return;
    if (state.runnerSelected === 'uiProbe') return;
    await page.waitForTimeout(120);
  }
  const finalState = await evaluateWithTimeout(page, () => ({
    state: window.__uncivUiProbeState || null,
    error: window.__uncivUiProbeError || null,
    hasResult: !!window.__uncivUiProbeResultJson,
    runnerSelected: window.__uncivRunnerSelected || null,
    runnerReason: window.__uncivRunnerReason || null,
    search: (window.location && window.location.search) || '',
    hasMain: typeof window.main === 'function',
    bootInvoked: window.__uncivBootStarted === true || window.__uncivUiProbeBootInvoked === true,
  }), undefined, `${label} startup-final-state`);
  throw new Error(
    `[${label}] uiProbe startup did not become observable within ${timeoutMs}ms `
    + `(state=${finalState.state || 'null'} error=${finalState.error || 'null'} hasResult=${finalState.hasResult} `
    + `runner=${finalState.runnerSelected || 'null'} reason=${finalState.runnerReason || 'null'} `
    + `hasMain=${finalState.hasMain} bootInvoked=${finalState.bootInvoked})`,
  );
}

async function clearUiProbeRuntimeMarkers(page) {
  await evaluateWithTimeout(page, () => {
    window.__uncivBootStarted = false;
    window.__uncivBootInProgress = false;
    window.__uncivUiProbeBootInvoked = false;
    window.__uncivRunnerSelected = null;
    window.__uncivRunnerReason = null;
    window.__uncivUiProbeState = null;
    window.__uncivUiProbeError = null;
    window.__uncivUiProbeResultJson = null;
    window.__uncivUiProbeStepLogJson = null;
  }, undefined, 'clear-ui-probe-runtime-markers').catch(() => {});
}

async function ensureUiProbeBoot(page, options) {
  const {
    url,
    label,
    timeoutMs,
    startupTimeoutMs,
    startupAttempts,
    onRetry,
  } = options;

  const startupBootTimeoutMs = Math.min(30000, Math.max(3000, Math.floor(startupTimeoutMs / 2)));
  let lastError = null;

  for (let attempt = 1; attempt <= startupAttempts; attempt += 1) {
    try {
      await gotoUiProbeUrl(page, url, label);
      await startMainOnce(page, startupBootTimeoutMs);
      await waitForUiProbeStart(page, label, startupTimeoutMs);
      return;
    } catch (err) {
      lastError = err;
      const errText = extractErrorText(err);
      if (attempt >= startupAttempts || !shouldRetryUiProbeStartup(errText)) break;
      if (typeof onRetry === 'function') {
        onRetry(attempt, errText);
      }
      await clearUiProbeRuntimeMarkers(page);
      await page.goto('about:blank', { waitUntil: 'domcontentloaded', timeout: 15000 }).catch(() => {});
      await page.waitForTimeout(200);
    }
  }

  throw lastError || new Error(`[${label}] uiProbe boot failed`);
}

async function startMainOnce(page, timeoutMs) {
  const startedAt = Date.now();
  while (Date.now() - startedAt <= timeoutMs) {
    const state = await evaluateWithTimeout(page, () => ({
      hasMain: typeof window.main === 'function',
      bootInvoked: window.__uncivBootStarted === true || window.__uncivUiProbeBootInvoked === true,
      uiProbeState: window.__uncivUiProbeState || null,
      readyState: document.readyState,
    }), undefined, 'start-main-state');
    if (state.bootInvoked || state.uiProbeState) return;
    if (state.hasMain && state.readyState === 'complete') {
      await evaluateWithTimeout(page, () => {
        if (window.__uncivBootStarted === true || window.__uncivUiProbeBootInvoked === true) return;
        window.__uncivUiProbeBootInvoked = true;
        try {
          window.__uncivBootStarted = true;
          window.main();
        } catch (_) {
          window.__uncivBootStarted = false;
          window.__uncivUiProbeBootInvoked = false;
        }
      }, undefined, 'start-main-invoke');
    }

    await page.waitForTimeout(100);
  }
  throw new Error(`runtime boot markers not visible within ${timeoutMs}ms`);
}

async function waitForUiProbeResult(page, label, timeoutMs, shouldAbort) {
  const startupGraceMs = Number(process.env.WEB_UI_RUNNER_STARTUP_GRACE_MS || '3500');
  const startedAt = Date.now();
  let lastState = null;
  let pollTimeoutMs = evaluateTimeoutMs;
  while (Date.now() - startedAt <= timeoutMs) {
    if (typeof shouldAbort === 'function') {
      const abortReason = shouldAbort();
      if (abortReason) throw new Error(`[${label}] ${abortReason}`);
    }
    const state = await evaluateWithTimeout(page, () => ({
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
    }), undefined, `${label} poll-state`, pollTimeoutMs);
    const elapsedMs = Date.now() - startedAt;
    if (
      elapsedMs >= startupGraceMs
      && hasUiProbeQuery(state.search)
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
    if (state.state && /^running:/i.test(state.state)) {
      pollTimeoutMs = runningEvaluateTimeoutMs;
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
  const finalState = await evaluateWithTimeout(page, () => ({
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
  }), undefined, `${label} timeout-final-state`).catch(() => ({
    state: null,
    error: null,
    hasResult: false,
    hasStepLog: false,
    runnerSelected: null,
    runnerReason: null,
    bootstrapTraceJson: null,
    search: '',
    hasMain: false,
    bootInvoked: false,
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
  applyUiDeviceModeToUrl,
  attachDiagnostics,
  buildUiDeviceConfig,
  ensureTmpDir,
  getActionableRequestFailures,
  launchChromium,
  resolveUiDeviceMode,
  ensureUiProbeBoot,
  startMainOnce,
  waitForUiProbeResult,
  writeJson,
};
