const fs = require('fs');
const path = require('path');

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function intEnv(name, defaultValue) {
  const raw = process.env[name];
  if (!raw) return defaultValue;
  const value = Number(raw);
  return Number.isFinite(value) ? value : defaultValue;
}

function fileSizeOrZero(filePath) {
  if (!fs.existsSync(filePath)) return 0;
  return fs.statSync(filePath).size;
}

function dirSizeRecursive(dirPath) {
  if (!fs.existsSync(dirPath)) return 0;
  const entries = fs.readdirSync(dirPath, { withFileTypes: true });
  let total = 0;
  for (const entry of entries) {
    const entryPath = path.join(dirPath, entry.name);
    if (entry.isDirectory()) total += dirSizeRecursive(entryPath);
    else if (entry.isFile()) total += fs.statSync(entryPath).size;
  }
  return total;
}

function main() {
  const root = process.cwd();
  const tmpDir = path.join(root, 'tmp');
  const summaryPath = process.env.WEB_VALIDATION_SUMMARY_PATH || path.join(tmpDir, 'web-validation-summary.json');
  const distDir = process.env.WEB_DIST_DIR || path.join(root, 'web', 'build', 'dist');
  const outputPath = process.env.WEB_PERF_OUTPUT_PATH || path.join(tmpDir, 'performance-budget-summary.json');

  const maxValidationDurationMs = intEnv('WEB_BUDGET_MAX_VALIDATION_MS', 900000);
  const maxMainMenuReadyMs = intEnv('WEB_BUDGET_MAX_MAIN_MENU_MS', 180000);
  const maxWorldEntryReadyMs = intEnv('WEB_BUDGET_MAX_WORLD_ENTRY_MS', 300000);
  const maxUncivJsBytes = intEnv('WEB_BUDGET_MAX_UNCIV_JS_BYTES', 70000000);
  const maxDistBytes = intEnv('WEB_BUDGET_MAX_DIST_BYTES', 220000000);

  const validationSummary = readJson(summaryPath);
  const perf = validationSummary.performance || {};

  const uncivJsPath = path.join(distDir, 'unciv.js');
  const uncivMapPath = path.join(distDir, 'unciv.js.map');
  const uncivDebugPath = path.join(distDir, 'unciv.js.teavmdbg');

  const uncivJsBytes = fileSizeOrZero(uncivJsPath);
  const uncivMapBytes = fileSizeOrZero(uncivMapPath);
  const uncivDebugBytes = fileSizeOrZero(uncivDebugPath);
  const distBytes = dirSizeRecursive(distDir);

  const issues = [];
  const validationDurationMs = Number(perf.validationDurationMs || 0);
  let mainMenuReadyMs = Number(perf.mainMenuReadyMs || 0);
  let worldEntryReadyMs = Number(perf.worldEntryReadyMs || 0);

  if (validationDurationMs <= 0) issues.push('Missing validationDurationMs from web validation summary.');
  if (mainMenuReadyMs <= 0 && validationDurationMs > 0) {
    mainMenuReadyMs = validationDurationMs;
  }
  if (worldEntryReadyMs <= 0 && validationDurationMs > 0) {
    worldEntryReadyMs = validationDurationMs;
  }

  if (validationDurationMs > maxValidationDurationMs) {
    issues.push(`validationDurationMs ${validationDurationMs} exceeds budget ${maxValidationDurationMs}`);
  }
  if (mainMenuReadyMs > maxMainMenuReadyMs) {
    issues.push(`mainMenuReadyMs ${mainMenuReadyMs} exceeds budget ${maxMainMenuReadyMs}`);
  }
  if (worldEntryReadyMs > maxWorldEntryReadyMs) {
    issues.push(`worldEntryReadyMs ${worldEntryReadyMs} exceeds budget ${maxWorldEntryReadyMs}`);
  }
  if (uncivJsBytes > maxUncivJsBytes) {
    issues.push(`unciv.js size ${uncivJsBytes} exceeds budget ${maxUncivJsBytes}`);
  }
  if (distBytes > maxDistBytes) {
    issues.push(`dist directory size ${distBytes} exceeds budget ${maxDistBytes}`);
  }

  const report = {
    generatedAt: new Date().toISOString(),
    budgets: {
      maxValidationDurationMs,
      maxMainMenuReadyMs,
      maxWorldEntryReadyMs,
      maxUncivJsBytes,
      maxDistBytes,
    },
    measurements: {
      validationDurationMs,
      mainMenuReadyMs,
      worldEntryReadyMs,
      uncivJsBytes,
      uncivMapBytes,
      uncivDebugBytes,
      distBytes,
    },
    issues,
  };

  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, JSON.stringify(report, null, 2));
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);

  if (issues.length > 0) {
    process.exit(1);
  }
}

main();
