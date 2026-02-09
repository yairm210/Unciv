const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

async function main() {
  const baseUrl = process.env.WEB_BASE_URL || 'http://127.0.0.1:8000';
  const timeoutMs = Number(process.env.WEB_VALIDATION_TIMEOUT_MS || '430000');
  const headless = (process.env.HEADLESS || '1') !== '0';
  const screenshotPath = path.resolve('/Users/haimlamper/Unciv/tmp/web-validation-latest.png');

  const pageErrors = [];
  const consoleErrors = [];

  const browser = await chromium.launch({ headless });
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    permissions: ['clipboard-read', 'clipboard-write'],
  });
  const page = await context.newPage();

  page.on('pageerror', (err) => {
    pageErrors.push(String(err));
  });
  page.on('console', (msg) => {
    const text = msg.text();
    if (msg.type() === 'error' || /Fatal Error|Uncaught throwable|JavaError|\[ERROR\]/i.test(text)) {
      consoleErrors.push(`[${msg.type()}] ${text}`);
    }
  });

  await page.goto(`${baseUrl}/index.html?webtest=1`, { waitUntil: 'domcontentloaded', timeout: 120000 });
  await page.waitForFunction(() => window.__uncivWebValidationDone === true, null, { timeout: timeoutMs });
  await page.waitForTimeout(5000);

  const state = await page.evaluate(() => ({
    validationState: window.__uncivWebValidationState || null,
    validationError: window.__uncivWebValidationError || null,
    validationResultJson: window.__uncivWebValidationResultJson || null,
  }));

  await page.screenshot({ path: screenshotPath, fullPage: true });
  await browser.close();

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
    counts: result.summary,
    startNewGame: startGameFeature,
    hasSettlerValidation,
    hasWarriorValidation,
    pageErrorCount: pageErrors.length,
    consoleErrorCount: consoleErrors.length,
    screenshotPath,
  };

  fs.writeFileSync('/Users/haimlamper/Unciv/tmp/web-validation-result.json', JSON.stringify(result, null, 2));
  fs.writeFileSync('/Users/haimlamper/Unciv/tmp/web-validation-summary.json', JSON.stringify(summary, null, 2));

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

  console.log(JSON.stringify(summary, null, 2));
}

main().catch((err) => {
  console.error(err && err.stack ? err.stack : String(err));
  process.exit(1);
});
