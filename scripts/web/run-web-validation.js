const fs = require('fs');
const path = require('path');
const { chromium, firefox, webkit } = require('playwright');

function parseBoolEnv(value, defaultValue) {
  if (value === undefined || value === null) return defaultValue;
  const normalized = String(value).trim().toLowerCase();
  if (normalized === '') return defaultValue;
  if (normalized === '0' || normalized === 'false' || normalized === 'no' || normalized === 'off') return false;
  if (normalized === '1' || normalized === 'true' || normalized === 'yes' || normalized === 'on') return true;
  return defaultValue;
}

function isSlf4jProviderWarning(text) {
  return /SLF4J\(W\):\s*No SLF4J providers were found\./.test(text)
    || /SLF4J\(W\):\s*Defaulting to no-operation \(NOP\) logger implementation/.test(text)
    || /SLF4J\(W\):\s*Ignoring binding found at/.test(text);
}

async function main() {
  const baseUrl = process.env.WEB_BASE_URL || 'http://127.0.0.1:8000';
  const webProfile = String(process.env.WEB_PROFILE || '').trim();
  const effectiveProfile = webProfile.length > 0 ? webProfile : 'phase4-full';
  const browserName = String(process.env.WEB_BROWSER || 'chromium').toLowerCase();
  const timeoutMs = Number(process.env.WEB_VALIDATION_TIMEOUT_MS || '430000');
  const startupTimeoutMs = Number(process.env.WEB_VALIDATION_STARTUP_TIMEOUT_MS || '45000');
  const startupAttempts = Number(process.env.WEB_VALIDATION_STARTUP_ATTEMPTS || '1');
  const headless = parseBoolEnv(process.env.HEADLESS, true);
  const debugState = String(process.env.WEB_VALIDATION_DEBUG || '') === '1';
  const mpServer = String(process.env.WEB_MP_SERVER || '').trim();
  const modZipUrl = String(process.env.WEB_MOD_ZIP_URL || '').trim();
  const chromiumArgs = String(process.env.WEB_CHROMIUM_ARGS || '').trim();
  const tmpDir = process.env.WEB_TMP_DIR || path.join(process.cwd(), 'tmp');
  fs.mkdirSync(tmpDir, { recursive: true });
  const screenshotPath = path.join(tmpDir, 'web-validation-latest.png');

  const pageErrors = [];
  const consoleErrors = [];
  const requestFailures = [];
  let mainMenuReadyAt = null;
  let worldEntryReadyAt = null;

  if (debugState) {
    console.log(`validation_debug_start profile=${effectiveProfile} baseUrl=${baseUrl} browser=${browserName} headless=${headless}`);
  }
  const browserTypes = { chromium, firefox, webkit };
  const browserType = browserTypes[browserName];
  if (!browserType) {
    throw new Error(`Unsupported WEB_BROWSER='${browserName}'. Expected one of: ${Object.keys(browserTypes).join(', ')}`);
  }

  const launchOptions = { headless };
  if (browserName === 'chromium') {
    launchOptions.args = chromiumArgs.length > 0
      ? chromiumArgs.split(/\s+/).filter(Boolean)
      : [
        '--use-gl=swiftshader',
        '--disable-background-timer-throttling',
        '--disable-backgrounding-occluded-windows',
        '--disable-renderer-backgrounding',
      ];
  }
  const browser = await browserType.launch(launchOptions);
  const contextOptions = {
    viewport: { width: 1280, height: 800 },
  };
  if (browserName === 'chromium') {
    contextOptions.permissions = ['clipboard-read', 'clipboard-write'];
  }
  const context = await browser.newContext(contextOptions);
  await context.addInitScript(({ profile, baseUrl, mpServerUrl, modZip }) => {
    window.__uncivEnableWebValidation = true;
    if (typeof baseUrl === 'string' && baseUrl.length > 0) {
      window.__uncivBaseUrl = baseUrl;
    }
    if (typeof mpServerUrl === 'string' && mpServerUrl.length > 0) {
      window.__uncivTestMultiplayerServer = mpServerUrl;
    }
    if (typeof modZip === 'string' && modZip.length > 0) {
      window.__uncivTestModZipUrl = modZip;
    }
    if (typeof profile === 'string' && profile.length > 0) {
      window.__uncivWebProfile = profile;
    }
  }, { profile: webProfile, baseUrl, mpServerUrl: mpServer, modZip: modZipUrl });
  const page = await context.newPage();

  page.on('pageerror', (err) => {
    const text = err && err.stack ? err.stack : String(err);
    if (/Cannot read properties of null \(reading '\$dispose'\)/.test(text)) return;
    if (/Cannot read properties of null \(reading '\$pause'\)/.test(text)) return;
    pageErrors.push(text);
  });
  page.on('crash', () => {
    pageErrors.push('Page crash detected');
  });
  page.on('requestfailed', (req) => {
    requestFailures.push({
      url: req.url(),
      error: req.failure() ? req.failure().errorText : 'unknown',
    });
  });
  page.on('console', (msg) => {
    const text = msg.text();
    if (isSlf4jProviderWarning(text)) {
      consoleErrors.push(`[slf4j-provider-warning] ${text}`);
      return;
    }
    if (/Failed to load resource/i.test(text)) return;
    if (msg.type() === 'error' || /Fatal Error|Uncaught throwable|JavaError|\[ERROR\]/i.test(text)) {
      consoleErrors.push(`[${msg.type()}] ${text}`);
    }
  });

  let state = null;
  let completed = false;
  let lastState = null;
  const validationStartAt = Date.now();
  try {
    const targetUrl = new URL('/index.html', baseUrl);
    targetUrl.searchParams.set('webtest', '1');
    if (webProfile.length > 0) targetUrl.searchParams.set('webProfile', webProfile);
    let startupRetryReason = null;
    for (let attempt = 1; attempt <= startupAttempts; attempt += 1) {
      startupRetryReason = null;
      await page.goto(targetUrl.toString(), { waitUntil: 'domcontentloaded', timeout: 120000 });
      const landedUrl = page.url();
      if (!landedUrl.includes('webtest=1')) {
        await page.goto(targetUrl.toString(), { waitUntil: 'domcontentloaded', timeout: 120000 });
      }
      const attemptStartedAt = Date.now();
      const deadline = Date.now() + timeoutMs;
      let sawValidationState = false;
      while (Date.now() < deadline) {
        state = await page.evaluate(() => ({
          validationState: window.__uncivWebValidationState || null,
          validationError: window.__uncivWebValidationError || null,
          validationResultJson: window.__uncivWebValidationResultJson || null,
        }));
        if (state.validationState) {
          sawValidationState = true;
        }
        const phaseState = String(state.validationState || '');
        if (phaseState.startsWith('running:') && mainMenuReadyAt === null) {
          mainMenuReadyAt = Date.now();
        }
        if (
          worldEntryReadyAt === null
          && (
            phaseState === 'running:End turn loop'
            || phaseState === 'running:Local save/load'
            || phaseState === 'running:Clipboard import/export'
            || phaseState === 'running:Audio'
            || phaseState === 'running:Multiplayer'
            || phaseState === 'running:Mod download/update'
            || phaseState === 'running:Custom file picker save/load'
            || phaseState === 'running:Translation/font selection'
            || phaseState === 'running:External links'
            || phaseState === 'done'
          )
        ) {
          worldEntryReadyAt = Date.now();
        }
        if (debugState && state.validationState !== lastState) {
          lastState = state.validationState;
          console.log(`validation_state=${String(lastState)}`);
        }
        if (state.validationError || state.validationResultJson || state.validationState === 'done') {
          completed = true;
          break;
        }
        if (!state.validationState && (Date.now() - attemptStartedAt) > startupTimeoutMs) {
          break;
        }
        if (pageErrors.length > 0) {
          if (!sawValidationState && attempt < startupAttempts) {
            startupRetryReason = `startup page error: ${pageErrors.join('\n')}`;
            pageErrors.length = 0;
            consoleErrors.length = 0;
            requestFailures.length = 0;
            state = null;
            break;
          }
          throw new Error(`Page errors detected before validation completed: ${pageErrors.join('\n')}`);
        }
        if (consoleErrors.length > 0) {
          if (!sawValidationState && attempt < startupAttempts) {
            startupRetryReason = `startup console error: ${consoleErrors.join('\n')}`;
            pageErrors.length = 0;
            consoleErrors.length = 0;
            requestFailures.length = 0;
            state = null;
            break;
          }
          throw new Error(`Console errors detected before validation completed: ${consoleErrors.join('\n')}`);
        }
        await page.waitForTimeout(1000);
      }
      if (completed) break;
      if (attempt < startupAttempts) {
        if (startupRetryReason) {
          console.warn(`Retrying validation startup after attempt ${attempt}/${startupAttempts}: ${startupRetryReason}`);
        }
        await page.waitForTimeout(1000);
      }
    }

    if (!completed) {
      const bootDebug = await page.evaluate(() => ({
        hasMain: typeof window.main === 'function',
        bootStarted: window.__uncivBootStarted === true,
        readyState: document.readyState,
      }));
      throw new Error(`Timed out waiting for web validation state. state=${JSON.stringify(state)} boot=${JSON.stringify(bootDebug)} pageErrors=${JSON.stringify(pageErrors)} consoleErrors=${JSON.stringify(consoleErrors)}`);
    }
    await page.waitForTimeout(5000);
  } finally {
    await page.screenshot({ path: screenshotPath, fullPage: true });
    await browser.close();
  }

  if (!state.validationResultJson) {
    throw new Error(`Missing validation result JSON. state=${JSON.stringify(state)}`);
  }

  const result = JSON.parse(state.validationResultJson);
  const failingFeatures = (result.features || []).filter((f) => f.status === 'FAIL' || f.status === 'BLOCKED');
  const startGameFeature = (result.features || []).find((f) => f.feature === 'Start new game');
  const uiClickFeature = (result.features || []).find((f) => f.feature === 'UI click core loop');
  const endTurnFeature = (result.features || []).find((f) => f.feature === 'End turn loop');
  const startNotes = startGameFeature ? String(startGameFeature.notes || '') : '';
  const uiClickNotes = uiClickFeature ? String(uiClickFeature.notes || '') : '';
  const hasSettlerValidation = startNotes.includes('Settler founding validated');
  const hasWarriorValidation = startNotes.includes('Warrior melee combat validated');
  const hasUiFoundCity = /found city/i.test(uiClickNotes);
  const hasUiConstruction = /construction/i.test(uiClickNotes);
  const hasUiTech = /tech/i.test(uiClickNotes);
  const hasUiTenTurns = /\b10 turns\b/i.test(uiClickNotes);

  const summary = {
    generatedAt: result.generatedAt,
    browser: browserName,
    webProfile: effectiveProfile,
    counts: result.summary,
    startNewGame: startGameFeature,
    uiClickCoreLoop: uiClickFeature,
    endTurnLoop: endTurnFeature,
    hasSettlerValidation,
    hasWarriorValidation,
    hasUiFoundCity,
    hasUiConstruction,
    hasUiTech,
    hasUiTenTurns,
    pageErrorCount: pageErrors.length,
    consoleErrorCount: consoleErrors.length,
    performance: {
      validationDurationMs: Date.now() - validationStartAt,
      mainMenuReadyMs: mainMenuReadyAt === null ? null : (mainMenuReadyAt - validationStartAt),
      worldEntryReadyMs: worldEntryReadyAt === null ? null : (worldEntryReadyAt - validationStartAt),
    },
    screenshotPath,
  };

  fs.writeFileSync(path.join(tmpDir, 'web-validation-result.json'), JSON.stringify(result, null, 2));
  fs.writeFileSync(path.join(tmpDir, 'web-validation-summary.json'), JSON.stringify(summary, null, 2));

  if (state.validationError) {
    throw new Error(`Web validation error: ${state.validationError}`);
  }
  if (!hasSettlerValidation || !hasWarriorValidation) {
    throw new Error(`Missing settler/warrior checks in Start new game notes: ${startNotes}`);
  }
  if (!uiClickFeature || uiClickFeature.status !== 'PASS') {
    throw new Error(`UI click core loop did not pass: ${JSON.stringify(uiClickFeature || null)}`);
  }
  if (!hasUiFoundCity || !hasUiConstruction || !hasUiTech || !hasUiTenTurns) {
    throw new Error(`UI click loop notes missing required flow checkpoints: ${uiClickNotes}`);
  }
  if (failingFeatures.length > 0) {
    throw new Error(`Validation has failing features: ${JSON.stringify(failingFeatures)}`);
  }
  if (pageErrors.length > 0) {
    throw new Error(`Page errors detected: ${pageErrors.join('\n')}`);
  }
  if (consoleErrors.length > 0) {
    throw new Error(`Console errors detected: ${consoleErrors.join('\n')}`);
  }
  const nonFaviconFailures = requestFailures.filter((entry) => !/favicon\.ico$/i.test(entry.url));
  if (nonFaviconFailures.length > 0) {
    throw new Error(`Request failures detected: ${JSON.stringify(nonFaviconFailures)}`);
  }

  console.log(JSON.stringify(summary, null, 2));
}

main().catch((err) => {
  console.error(err && err.stack ? err.stack : String(err));
  process.exit(1);
});
