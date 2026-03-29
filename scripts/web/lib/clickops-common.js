const {
  attachDiagnostics,
  ensureTmpDir,
  getActionableRequestFailures,
  installBlobDiagnostics,
  launchChromium,
  readBlobDiagnostics,
  writeJson,
} = require('./ui-e2e-common');

function parseJson(raw, fallback) {
  if (!raw || typeof raw !== 'string') return fallback;
  try {
    return JSON.parse(raw);
  } catch (_) {
    return fallback;
  }
}

function hasProbeFlags(url) {
  return /(?:^|[?&])(uiProbe|mpProbe)(?:=|&|$)/i.test(String(url || ''));
}

function ensureNoProbeFlags(url, label) {
  if (!hasProbeFlags(url)) return;
  throw new Error(`[${label}] detected forbidden probe query flags in URL: ${url}`);
}

function hasClickOpsQuery(search) {
  const match = /(?:^|[?&])clickOps(?:=([^&]*))?(?:&|$)/i.exec(String(search || ''));
  if (!match) return false;
  const rawValue = match[1];
  if (typeof rawValue !== 'string') return false;
  return rawValue.trim() === '1';
}

async function gotoClickOpsUrl(page, url, label) {
  const targetUrl = typeof url === 'string' ? url : url.toString();
  const wantedClickOps = hasClickOpsQuery(new URL(targetUrl).search);
  await page.goto(targetUrl, { waitUntil: 'load', timeout: 120000 });
  if (!wantedClickOps) return;
  const landedUrl = page.url();
  if (hasClickOpsQuery(new URL(landedUrl).search)) return;
  await page.goto(targetUrl, { waitUntil: 'load', timeout: 120000 });
  const retryUrl = page.url();
  if (!hasClickOpsQuery(new URL(retryUrl).search)) {
    throw new Error(`[${label}] did not retain clickOps query params after navigation. landed=${retryUrl}`);
  }
}

async function startMainOnce(page, timeoutMs) {
  const startedAt = Date.now();
  while (Date.now() - startedAt <= timeoutMs) {
    const state = await page.evaluate(() => ({
      hasMain: typeof window.main === 'function',
      readyState: document.readyState,
      bootInvoked: window.__uncivBootStarted === true || window.__uncivClickOpsBootInvoked === true,
    }));
    if (state.bootInvoked) return;
    if (state.hasMain && state.readyState === 'complete') {
      await page.evaluate(() => {
        if (window.__uncivBootStarted === true || window.__uncivClickOpsBootInvoked === true) return;
        window.__uncivClickOpsBootInvoked = true;
        try {
          window.__uncivBootStarted = true;
          window.main();
        } catch (_) {
          window.__uncivBootStarted = false;
          window.__uncivClickOpsBootInvoked = false;
        }
      });
    }
    await page.waitForTimeout(100);
  }
  throw new Error(`runtime boot markers not visible within ${timeoutMs}ms`);
}

async function waitForClickOpsStart(page, label, timeoutMs) {
  const startedAt = Date.now();
  while (Date.now() - startedAt <= timeoutMs) {
    const state = await page.evaluate(() => ({
      search: (window.location && window.location.search) || '',
      runnerSelected: window.__uncivRunnerSelected || null,
      runnerReason: window.__uncivRunnerReason || null,
      clickOpsState: window.__uncivClickOpsStateJson || null,
      clickOpsTargets: window.__uncivClickOpsTargetsJson || null,
      clickOpsError: window.__uncivClickOpsError || null,
      hasMain: typeof window.main === 'function',
      bootInvoked: window.__uncivBootStarted === true || window.__uncivClickOpsBootInvoked === true,
    }));
    if (hasClickOpsQuery(state.search) && state.runnerSelected && state.runnerSelected !== 'clickOps') {
      throw new Error(
        `[${label}] clickOps runner mismatch during startup: got ${state.runnerSelected}`
        + ` (reason=${state.runnerReason || 'n/a'})`,
      );
    }
    if (state.clickOpsError || state.clickOpsState || state.clickOpsTargets) return;
    if (state.runnerSelected === 'clickOps') return;
    await page.waitForTimeout(120);
  }
  const finalState = await page.evaluate(() => ({
    search: (window.location && window.location.search) || '',
    runnerSelected: window.__uncivRunnerSelected || null,
    runnerReason: window.__uncivRunnerReason || null,
    clickOpsState: window.__uncivClickOpsStateJson || null,
    clickOpsTargets: window.__uncivClickOpsTargetsJson || null,
    clickOpsError: window.__uncivClickOpsError || null,
    hasMain: typeof window.main === 'function',
    bootInvoked: window.__uncivBootStarted === true || window.__uncivClickOpsBootInvoked === true,
  }));
  throw new Error(
    `[${label}] clickOps startup did not become observable within ${timeoutMs}ms `
    + `(state=${finalState.clickOpsState ? 'yes' : 'no'} targets=${finalState.clickOpsTargets ? 'yes' : 'no'} `
    + `error=${finalState.clickOpsError || 'null'} runner=${finalState.runnerSelected || 'null'} `
    + `reason=${finalState.runnerReason || 'null'} hasMain=${finalState.hasMain} bootInvoked=${finalState.bootInvoked})`,
  );
}

async function clearClickOpsRuntimeMarkers(page) {
  await page.evaluate(() => {
    window.__uncivBootStarted = false;
    window.__uncivBootInProgress = false;
    window.__uncivClickOpsBootInvoked = false;
    window.__uncivRunnerSelected = null;
    window.__uncivRunnerReason = null;
    window.__uncivClickOpsStateJson = null;
    window.__uncivClickOpsTargetsJson = null;
    window.__uncivClickOpsError = null;
  }).catch(() => {});
}

function shouldRetryClickOpsStartup(errText) {
  if (!errText) return false;
  if (/clickOps startup did not become observable within/i.test(errText)) return true;
  if (/runtime boot markers not visible within/i.test(errText)) return true;
  return false;
}

async function ensureClickOpsBoot(page, options) {
  const {
    url,
    label,
    startupTimeoutMs,
    startupAttempts,
    onRetry,
  } = options;
  const startupBootTimeoutMs = Math.min(30000, Math.max(3000, Math.floor(startupTimeoutMs / 2)));
  let lastError = null;

  for (let attempt = 1; attempt <= startupAttempts; attempt += 1) {
    try {
      await gotoClickOpsUrl(page, url, label);
      await startMainOnce(page, startupBootTimeoutMs);
      await waitForClickOpsStart(page, label, startupTimeoutMs);
      return;
    } catch (err) {
      lastError = err;
      const errText = String(err && err.stack ? err.stack : err);
      if (attempt >= startupAttempts || !shouldRetryClickOpsStartup(errText)) break;
      if (typeof onRetry === 'function') onRetry(attempt, errText);
      await clearClickOpsRuntimeMarkers(page);
      await page.goto('about:blank', { waitUntil: 'domcontentloaded', timeout: 15000 }).catch(() => {});
      await page.waitForTimeout(200);
    }
  }

  throw lastError || new Error(`[${label}] clickOps boot failed`);
}

async function readClickOpsSnapshot(page) {
  return page.evaluate(() => ({
    url: window.location ? window.location.href : '',
    targetsJson: window.__uncivClickOpsTargetsJson || null,
    stateJson: window.__uncivClickOpsStateJson || null,
    error: window.__uncivClickOpsError || null,
    runnerSelected: window.__uncivRunnerSelected || null,
    runnerReason: window.__uncivRunnerReason || null,
  }));
}

async function getSnapshot(page, label) {
  const raw = await readClickOpsSnapshot(page);
  ensureNoProbeFlags(raw.url, label);
  if (String(raw.url || '').includes('clickOps=1') && raw.runnerSelected && raw.runnerSelected !== 'clickOps') {
    throw new Error(
      `[${label}] clickOps runner mismatch: expected clickOps got ${raw.runnerSelected}`
      + `${raw.runnerReason ? ` (reason=${raw.runnerReason})` : ''}`,
    );
  }
  const targetsPayload = parseJson(raw.targetsJson, { targets: [] }) || { targets: [] };
  const statePayload = parseJson(raw.stateJson, {}) || {};
  return {
    url: raw.url,
    error: raw.error || null,
    targets: Array.isArray(targetsPayload.targets) ? targetsPayload.targets : [],
    state: statePayload,
  };
}

async function waitForState(page, options) {
  const {
    label,
    timeoutMs,
    description,
    predicate,
  } = options;
  const startedAt = Date.now();
  while (Date.now() - startedAt <= timeoutMs) {
    const snapshot = await getSnapshot(page, label);
    if (snapshot.error) {
      throw new Error(`[${label}] clickOps error while waiting for state (${description}): ${snapshot.error}`);
    }
    if (typeof predicate === 'function' && predicate(snapshot.state, snapshot)) {
      return snapshot;
    }
    await page.waitForTimeout(120);
  }
  const snapshot = await getSnapshot(page, label);
  throw new Error(
    `[${label}] timed out waiting for state (${description}) after ${timeoutMs}ms `
    + `screen=${snapshot.state && snapshot.state.screen ? snapshot.state.screen : 'unknown'}`,
  );
}

async function waitForTarget(page, options) {
  const {
    label,
    targetId,
    timeoutMs,
    requireEnabled = true,
    allowScroll = false,
  } = options;
  const startedAt = Date.now();
  let lastScrollAt = 0;
  let scrollAttempts = 0;
  while (Date.now() - startedAt <= timeoutMs) {
    const snapshot = await getSnapshot(page, label);
    if (snapshot.error) {
      throw new Error(`[${label}] clickOps error while waiting for target [${targetId}]: ${snapshot.error}`);
    }
    const target = snapshot.targets.find((item) => item && item.id === targetId);
    if (target && target.visible === true && (!requireEnabled || target.enabled === true)) {
      const viewport = page.viewportSize() || { width: 0, height: 0 };
      const left = Number(target.x || 0);
      const top = Number(target.y || 0);
      const right = left + Number(target.width || 0);
      const bottom = top + Number(target.height || 0);
      const onScreen = right > 0 && bottom > 0
        && left < viewport.width && top < viewport.height;
      if (!allowScroll || onScreen || viewport.width <= 0 || viewport.height <= 0) {
        return { snapshot, target };
      }
      const wheelDelta = top >= viewport.height ? 480 : -480;
      await page.mouse.wheel(0, wheelDelta).catch(() => {});
      await page.waitForTimeout(140);
      continue;
    }
    if (allowScroll && (Date.now() - lastScrollAt) >= 260) {
      lastScrollAt = Date.now();
      const wheelDelta = scrollAttempts % 2 === 0 ? 520 : -520;
      scrollAttempts += 1;
      await page.mouse.wheel(0, wheelDelta).catch(() => {});
      await page.waitForTimeout(100);
      continue;
    }
    await page.waitForTimeout(120);
  }
  const snapshot = await getSnapshot(page, label);
  const available = snapshot.targets.map((item) => item.id).slice(0, 40).join(', ');
  throw new Error(
    `[${label}] timed out waiting for target [${targetId}] after ${timeoutMs}ms `
    + `(availableTargets=${available || 'none'})`,
  );
}

async function clickPoint(page, x, y, options = {}) {
  const {
    moveSteps = 6,
    delayMs = 40,
  } = options;
  await page.mouse.move(x, y, { steps: moveSteps }).catch(() => {});
  await page.mouse.click(x, y, { delay: delayMs }).catch(async () => {
    await page.mouse.down().catch(() => {});
    if (delayMs > 0) await page.waitForTimeout(delayMs).catch(() => {});
    await page.mouse.up().catch(() => {});
  });
}

async function clickTarget(page, options) {
  const {
    label,
    targetId,
    timeoutMs,
    allowScroll = false,
  } = options;
  const { target } = await waitForTarget(page, {
    label,
    targetId,
    timeoutMs,
    requireEnabled: true,
    allowScroll,
  });
  const clickX = Math.floor(Number(target.x || 0) + Number(target.width || 0) / 2);
  const clickY = Math.floor(Number(target.y || 0) + Number(target.height || 0) / 2);
  await clickPoint(page, clickX, clickY);
  return target;
}

async function fillTargetWithText(page, options) {
  const {
    label,
    targetId,
    text,
    timeoutMs,
    allowScroll = false,
  } = options;
  const expectedText = String(text || '');
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    await clickTarget(page, { label, targetId, timeoutMs, allowScroll });
    await page.keyboard.press('Control+A').catch(() => {});
    await page.keyboard.press('Meta+A').catch(() => {});
    await page.keyboard.press('Backspace').catch(() => {});
    await page.keyboard.type(expectedText, { delay: 20 }).catch(async () => {
      await page.keyboard.type(expectedText).catch(() => {});
    });
    await page.keyboard.press('Tab').catch(() => {});
    const matched = await waitForState(page, {
      label,
      timeoutMs: Math.min(timeoutMs, 5000),
      description: `text field [${targetId}] matched expected text`,
      predicate: (_, snapshot) => {
        const target = snapshot.targets.find((item) => item && item.id === targetId);
        return !!target && String(target.text || '').trim() === expectedText.trim();
      },
    }).catch(() => null);
    if (matched) return;
    await page.waitForTimeout(120).catch(() => {});
  }
  throw new Error(`[${label}] failed to fill target [${targetId}] with expected text "${expectedText}"`);
}

async function dismissPopups(page, options) {
  const {
    label,
    timeoutMs = 10000,
  } = options;
  const startedAt = Date.now();
  let lastSnapshot = null;
  while (Date.now() - startedAt <= timeoutMs) {
    const snapshot = await getSnapshot(page, label);
    lastSnapshot = snapshot;
    if (!snapshot.state || snapshot.state.hasPopup !== true) return;
    const dismiss = snapshot.targets.find((item) => item && item.id === 'popup.dismiss' && item.visible === true && item.enabled === true);
    if (!dismiss) {
      if (snapshot.state && snapshot.state.screen === 'WorldScreen') {
        await page.waitForTimeout(150);
        continue;
      }
      await page.keyboard.press('Escape').catch(() => {});
      await page.keyboard.press('Enter').catch(() => {});
      await page.waitForTimeout(150);
      continue;
    }
    const x = Math.floor(Number(dismiss.x || 0) + Number(dismiss.width || 0) / 2);
    const y = Math.floor(Number(dismiss.y || 0) + Number(dismiss.height || 0) / 2);
    await clickPoint(page, x, y);
    await page.waitForTimeout(180);
  }
  const available = (lastSnapshot && Array.isArray(lastSnapshot.targets))
    ? lastSnapshot.targets.map((item) => item.id).slice(0, 40).join(', ')
    : 'none';
  throw new Error(`[${label}] popup remained open after ${timeoutMs}ms (availableTargets=${available})`);
}

module.exports = {
  attachDiagnostics,
  ensureTmpDir,
  getActionableRequestFailures,
  installBlobDiagnostics,
  launchChromium,
  readBlobDiagnostics,
  writeJson,
  ensureClickOpsBoot,
  ensureNoProbeFlags,
  getSnapshot,
  waitForState,
  waitForTarget,
  clickPoint,
  clickTarget,
  fillTargetWithText,
  dismissPopups,
};
