const fs = require('fs');
const path = require('path');
const { chromium, firefox, webkit } = require('playwright');

async function main() {
  const baseUrl = process.env.WEB_BASE_URL || 'http://127.0.0.1:8000';
  const webProfile = String(process.env.WEB_PROFILE || '').trim();
  const browserName = String(process.env.WEB_BROWSER || 'chromium').toLowerCase();
  const timeoutMs = Number(process.env.WEB_VALIDATION_TIMEOUT_MS || '430000');
  const headless = (process.env.HEADLESS || '1') !== '0';
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

  if (debugState) {
    console.log(`validation_debug_start profile=${webProfile || 'phase1'} baseUrl=${baseUrl} browser=${browserName} headless=${headless}`);
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
    if (/Failed to load resource/i.test(text)) return;
    if (msg.type() === 'error' || /Fatal Error|Uncaught throwable|JavaError|\[ERROR\]/i.test(text)) {
      consoleErrors.push(`[${msg.type()}] ${text}`);
    }
  });

  let state = null;
  let completed = false;
  let lastState = null;
  try {
    const targetUrl = new URL('/index.html', baseUrl);
    targetUrl.searchParams.set('webtest', '1');
    if (webProfile.length > 0) targetUrl.searchParams.set('webProfile', webProfile);
    for (let attempt = 1; attempt <= 2; attempt += 1) {
      await page.goto(targetUrl.toString(), { waitUntil: 'domcontentloaded', timeout: 120000 });
      const landedUrl = page.url();
      if (!landedUrl.includes('webtest=1')) {
        await page.goto(targetUrl.toString(), { waitUntil: 'domcontentloaded', timeout: 120000 });
      }
      const deadline = Date.now() + timeoutMs;
      while (Date.now() < deadline) {
        if (!state || !state.validationState) {
          const hasMainNow = await page.evaluate(() => typeof window.main === 'function');
          if (hasMainNow) {
            await page.evaluate(() => {
              if (!window.__uncivBootStarted && typeof window.main === 'function') {
                window.__uncivBootStarted = true;
                window.main();
              }
            });
          }
        }
        state = await page.evaluate(() => ({
          validationState: window.__uncivWebValidationState || null,
          validationError: window.__uncivWebValidationError || null,
          validationResultJson: window.__uncivWebValidationResultJson || null,
        }));
        if (debugState && state.validationState !== lastState) {
          lastState = state.validationState;
          console.log(`validation_state=${String(lastState)}`);
        }
        if (state.validationError || state.validationResultJson || state.validationState === 'done') {
          completed = true;
          break;
        }
        if (pageErrors.length > 0) {
          throw new Error(`Page errors detected before validation completed: ${pageErrors.join('\n')}`);
        }
        if (consoleErrors.length > 0) {
          throw new Error(`Console errors detected before validation completed: ${consoleErrors.join('\n')}`);
        }
        await page.waitForTimeout(1000);
      }
      if (completed) break;
      if (attempt < 2) {
        await page.waitForTimeout(1000);
      }
    }

    if (!completed) {
      throw new Error(`Timed out waiting for web validation state. state=${JSON.stringify(state)} pageErrors=${JSON.stringify(pageErrors)} consoleErrors=${JSON.stringify(consoleErrors)}`);
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
  const startNotes = startGameFeature ? String(startGameFeature.notes || '') : '';
  const hasSettlerValidation = startNotes.includes('Settler founding validated');
  const hasWarriorValidation = startNotes.includes('Warrior melee combat validated');

  const summary = {
    generatedAt: result.generatedAt,
    browser: browserName,
    webProfile: webProfile || 'phase1',
    counts: result.summary,
    startNewGame: startGameFeature,
    hasSettlerValidation,
    hasWarriorValidation,
    pageErrorCount: pageErrors.length,
    consoleErrorCount: consoleErrors.length,
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
