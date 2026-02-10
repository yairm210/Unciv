const fs = require('fs');
const path = require('path');
const { chromium, firefox, webkit } = require('playwright');

(async () => {
  const outPath = path.resolve('tmp/js-browser-tests-result.json');
  const url = process.env.WEB_URL || 'http://127.0.0.1:18080/index.html?jstests=1';
  const browserName = String(process.env.WEB_BROWSER || 'chromium').toLowerCase();
  const headlessToken = String(process.env.HEADLESS || 'false').toLowerCase();
  const headless = headlessToken === '1' || headlessToken === 'true' || headlessToken === 'yes';
  const timeoutMs = Number(process.env.TIMEOUT_MS || 900000);

  const consoleErrors = [];
  const consoleWarnings = [];
  const pageErrors = [];
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

  const browser = await browserType.launch({ headless, slowMo: headless ? 0 : 40 });
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });

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
    pageErrors.push(text);
    process.stdout.write(`[pageerror] ${text}\n`);
  });

  let status = 'UNKNOWN';
  let jsResult = null;
  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 120000 });
    const pollStart = Date.now();
    while (true) {
      const state = await page.evaluate(() => ({
        done: window.__uncivJsTestsDone === true,
        state: window.__uncivJsTestsState || null,
        error: window.__uncivJsTestsError || null,
        json: window.__uncivJsTestsResultJson || null,
      }));

      if (state.error) throw new Error(`JS test runtime error: ${state.error}`);
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
          throw new Error(`Timed out after ${timeoutMs}ms waiting for JS test completion.`);
        }
        break;
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
    url,
    browser: browserName,
    headless,
    timestamp: new Date().toISOString(),
    consoleErrorCount: consoleErrors.length,
    ignoredConsoleErrorCount: ignoredConsoleErrors.length,
    consoleWarningCount: consoleWarnings.length,
    pageErrorCount: pageErrors.length,
    consoleErrors,
    ignoredConsoleErrors,
    consoleWarnings,
    pageErrors,
    jsResult,
  };

  fs.writeFileSync(outPath, JSON.stringify(report, null, 2));
  await page.screenshot({ path: path.resolve('tmp/js-browser-tests-last.png'), fullPage: true }).catch(() => {});
  await browser.close();

  process.stdout.write(`Wrote ${outPath}\n`);
  if (status !== 'PASSED') process.exit(2);
})();
