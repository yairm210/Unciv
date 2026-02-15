const fs = require('fs');
const path = require('path');
const { chromium, firefox, webkit } = require('playwright');
const { resolveChromiumArgs } = require('./lib/chromium-args');

(async () => {
  const outPath = path.resolve('tmp/js-browser-tests-result.json');
  const url = process.env.WEB_URL || 'http://127.0.0.1:18080/index.html?jstests=1';
  const webProfile = String(process.env.WEB_PROFILE || '').trim();
  const effectiveProfile = webProfile.length > 0 ? webProfile : 'phase4-full';
  const browserName = String(process.env.WEB_BROWSER || 'chromium').toLowerCase();
  const headlessToken = String(process.env.HEADLESS || 'false').toLowerCase();
  const headless = headlessToken === '1' || headlessToken === 'true' || headlessToken === 'yes';
  const timeoutMs = Number(process.env.TIMEOUT_MS || 900000);
  const startupTimeoutMs = Number(process.env.WEB_JS_TESTS_STARTUP_TIMEOUT_MS || '60000');
  const debugState = String(process.env.WEB_JS_TESTS_DEBUG || '') === '1';
  const chromiumArgs = resolveChromiumArgs(process.env.WEB_CHROMIUM_ARGS);
  const jsTestsFilter = String(process.env.WEB_JS_TESTS_FILTER || '').trim();

  const consoleErrors = [];
  const consoleWarnings = [];
  const pageErrors = [];
  const ignoredPageErrors = [];
  const ignoredConsoleErrors = [];

  const browserTypes = { chromium, firefox, webkit };
  const browserType = browserTypes[browserName];
  if (!browserType) {
    throw new Error(`Unsupported WEB_BROWSER='${browserName}'. Expected one of: ${Object.keys(browserTypes).join(', ')}`);
  }

  const ignoredConsoleErrorPatterns = [
    /\/favicon\.ico\b/i,
    /\/webapp\/jstests\/result\.json\b/i,
    /\/jstests\/result\.json\b/i,
    /\/assets\/jstests\/result\.json\b/i,
  ];

  const launchOptions = { headless, slowMo: headless ? 0 : 40 };
  if (browserName === 'chromium') {
    launchOptions.args = chromiumArgs;
  }
  const browser = await browserType.launch(launchOptions);
  const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  await context.addInitScript(({ profile, jsFilter }) => {
    window.__uncivEnableJsTests = true;
    if (typeof profile === 'string' && profile.length > 0) {
      window.__uncivWebProfile = profile;
    }
    if (typeof jsFilter === 'string' && jsFilter.length > 0) {
      window.__uncivJsTestsFilter = jsFilter;
    }
  }, { profile: webProfile, jsFilter: jsTestsFilter });
  const page = await context.newPage();

  page.on('console', msg => {
    const t = msg.type();
    const text = msg.text();
    if (t === 'error') {
      if (ignoredConsoleErrorPatterns.some((pattern) => pattern.test(text))) {
        ignoredConsoleErrors.push(text);
      } else {
        consoleErrors.push(text);
      }
    }
    if (t === 'warning') consoleWarnings.push(text);
    process.stdout.write(`[console:${t}] ${text}\n`);
  });
  page.on('pageerror', err => {
    const text = String(err && err.stack ? err.stack : err);
    if (/Cannot read properties of null \(reading '\$dispose'\)/.test(text)) {
      ignoredPageErrors.push(text);
      process.stdout.write(`[pageerror:ignored] ${text}\n`);
      return;
    }
    if (/Cannot read properties of null \(reading '\$pause'\)/.test(text)) {
      ignoredPageErrors.push(text);
      process.stdout.write(`[pageerror:ignored] ${text}\n`);
      return;
    }
    pageErrors.push(text);
    process.stdout.write(`[pageerror] ${text}\n`);
  });

  let status = 'UNKNOWN';
  let jsResult = null;
  try {
    const targetUrl = new URL(url);
    if (webProfile.length > 0) targetUrl.searchParams.set('webProfile', webProfile);
    await page.goto(targetUrl.toString(), { waitUntil: 'domcontentloaded', timeout: 120000 });
    const landedUrl = page.url();
    if (!landedUrl.includes('jstests=1')) {
      await page.goto(targetUrl.toString(), { waitUntil: 'domcontentloaded', timeout: 120000 });
    }
    const pollStart = Date.now();
    let firstStateSeenAt = null;
    let bootInvoked = false;
    while (true) {
      if (!bootInvoked) {
        const bootedNow = await page.evaluate(() => {
          if (typeof window.main !== 'function') return false;
          if (window.__uncivBootStarted) return true;
          window.__uncivBootStarted = true;
          window.main();
          return true;
        });
        if (bootedNow) bootInvoked = true;
      }
      const state = await page.evaluate(() => ({
        done: window.__uncivJsTestsDone === true,
        state: window.__uncivJsTestsState || null,
        error: window.__uncivJsTestsError || null,
        json: window.__uncivJsTestsResultJson || null,
      }));
      if (debugState && state.state) {
        process.stdout.write(`[js-tests-state] ${state.state}\n`);
      }
      if ((state.state || state.error || state.json) && firstStateSeenAt === null) {
        firstStateSeenAt = Date.now();
      }

      if (state.error) throw new Error(`JS test runtime error: ${state.error}`);
      if (pageErrors.length > 0) {
        throw new Error(`Fatal page errors detected: ${pageErrors.join('\n')}`);
      }
      if (consoleErrors.length > 0) {
        throw new Error(`Fatal console errors detected: ${consoleErrors.join('\n')}`);
      }
      if (state.json) {
        jsResult = JSON.parse(state.json);
        if (!jsResult || !jsResult.summary) throw new Error('Malformed JS test result payload');
        status = (jsResult.summary.totalFailures > 0 || jsResult.passed === false) ? 'FAILED' : 'PASSED';
        break;
      }

      if (state.done && !state.json) {
        throw new Error('JS tests finished without result JSON');
      }

      if (Date.now() - pollStart > timeoutMs) {
        const fallbackJson = await page.evaluate(async () => {
          const urls = [
            '/webapp/jstests/result.json',
            '/jstests/result.json',
            '/assets/jstests/result.json'
          ];
          for (const url of urls) {
            try {
              const response = await fetch(url, { cache: 'no-store' });
              if (!response.ok) continue;
              const text = await response.text();
              if (text && text.trim().length > 0) return text;
            } catch (e) {
              // ignore
            }
          }
          return null;
        });
        if (fallbackJson) {
          jsResult = JSON.parse(fallbackJson);
          if (!jsResult || !jsResult.summary) throw new Error('Malformed JS test result payload');
          status = (jsResult.summary.totalFailures > 0 || jsResult.passed === false) ? 'FAILED' : 'PASSED';
        } else {
          throw new Error(`Timed out after ${timeoutMs}ms waiting for JS test completion. lastState=${state.state || 'unknown'}`);
        }
        break;
      }
      if (firstStateSeenAt === null && (Date.now() - pollStart) > startupTimeoutMs) {
        throw new Error(`Timed out after ${startupTimeoutMs}ms waiting for JS tests to start`);
      }

      await page.waitForTimeout(1000);
    }
  } catch (err) {
    status = 'FAILED';
    jsResult = {
      harnessError: String(err && err.stack ? err.stack : err),
    };
  }

  const report = {
    status,
    url: webProfile.length > 0 ? (() => {
      const targetUrl = new URL(url);
      targetUrl.searchParams.set('webProfile', webProfile);
      return targetUrl.toString();
    })() : url,
    browser: browserName,
    webProfile: effectiveProfile,
    headless,
    timestamp: new Date().toISOString(),
    consoleErrorCount: consoleErrors.length,
    ignoredConsoleErrorCount: ignoredConsoleErrors.length,
    consoleWarningCount: consoleWarnings.length,
    pageErrorCount: pageErrors.length,
    ignoredPageErrorCount: ignoredPageErrors.length,
    consoleErrors,
    ignoredConsoleErrors,
    consoleWarnings,
    pageErrors,
    ignoredPageErrors,
    jsResult,
  };

  fs.writeFileSync(outPath, JSON.stringify(report, null, 2));
  await page.screenshot({ path: path.resolve('tmp/js-browser-tests-last.png'), fullPage: true }).catch(() => {});
  await context.close();
  await browser.close();

  process.stdout.write(`Wrote ${outPath}\n`);
  if (status !== 'PASSED') process.exit(2);
})();
