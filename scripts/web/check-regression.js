const fs = require('fs');
const path = require('path');

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function fail(msg) {
  throw new Error(msg);
}

function main() {
  const rootDir = process.cwd();
  const tmpDir = path.join(rootDir, 'tmp');
  const baselinePath = process.env.WEB_BASELINE_PATH || path.join(rootDir, 'web', 'baseline', 'regression-baseline.json');
  const validationSummaryPath = process.env.WEB_VALIDATION_SUMMARY_PATH || path.join(tmpDir, 'web-validation-summary.json');
  const jsSuitePath = process.env.WEB_JS_SUITE_PATH || path.join(tmpDir, 'js-browser-tests-result.json');
  const outputPath = process.env.WEB_REGRESSION_OUTPUT_PATH || path.join(tmpDir, 'regression-diff-summary.json');

  const baseline = readJson(baselinePath);
  const webValidation = readJson(validationSummaryPath);
  const jsSuite = readJson(jsSuitePath);

  const issues = [];
  const counts = webValidation.counts || {};
  const jsSummary = (jsSuite.jsResult && jsSuite.jsResult.summary) || {};

  if (counts.pass < baseline.webValidation.pass) {
    issues.push(`web validation pass count regressed: ${counts.pass} < ${baseline.webValidation.pass}`);
  }
  if (counts.fail > baseline.webValidation.fail) {
    issues.push(`web validation fail count regressed: ${counts.fail} > ${baseline.webValidation.fail}`);
  }
  if (counts.blocked > baseline.webValidation.blocked) {
    issues.push(`web validation blocked count regressed: ${counts.blocked} > ${baseline.webValidation.blocked}`);
  }
  if (webValidation.hasSettlerValidation !== baseline.webValidation.hasSettlerValidation) {
    issues.push('settler validation marker missing');
  }
  if (webValidation.hasWarriorValidation !== baseline.webValidation.hasWarriorValidation) {
    issues.push('warrior validation marker missing');
  }
  if ((webValidation.pageErrorCount || 0) > baseline.webValidation.maxPageErrors) {
    issues.push(`web validation page errors regressed: ${webValidation.pageErrorCount} > ${baseline.webValidation.maxPageErrors}`);
  }
  if ((webValidation.consoleErrorCount || 0) > baseline.webValidation.maxConsoleErrors) {
    issues.push(`web validation console errors regressed: ${webValidation.consoleErrorCount} > ${baseline.webValidation.maxConsoleErrors}`);
  }

  if (jsSuite.status !== 'PASSED') {
    issues.push(`browser JS suite status is not PASSED: ${jsSuite.status}`);
  }
  if ((jsSummary.totalFailures || 0) > baseline.browserJsSuite.maxFailures) {
    issues.push(`browser JS failures regressed: ${jsSummary.totalFailures} > ${baseline.browserJsSuite.maxFailures}`);
  }
  if ((jsSummary.totalRun || 0) < baseline.browserJsSuite.minTotalRun) {
    issues.push(`browser JS totalRun regressed: ${jsSummary.totalRun} < ${baseline.browserJsSuite.minTotalRun}`);
  }
  if ((jsSuite.pageErrorCount || 0) > baseline.browserJsSuite.maxPageErrors) {
    issues.push(`browser JS page errors regressed: ${jsSuite.pageErrorCount} > ${baseline.browserJsSuite.maxPageErrors}`);
  }
  if ((jsSuite.consoleErrorCount || 0) > baseline.browserJsSuite.maxConsoleErrors) {
    issues.push(`browser JS console errors regressed: ${jsSuite.consoleErrorCount} > ${baseline.browserJsSuite.maxConsoleErrors}`);
  }

  const report = {
    baselinePath,
    validationSummaryPath,
    jsSuitePath,
    baselineCommit: baseline.sourceCommit,
    generatedAt: new Date().toISOString(),
    webValidation: {
      baseline: baseline.webValidation,
      candidate: {
        pass: counts.pass || 0,
        fail: counts.fail || 0,
        blocked: counts.blocked || 0,
        disabledByDesign: counts.disabledByDesign || 0,
        hasSettlerValidation: !!webValidation.hasSettlerValidation,
        hasWarriorValidation: !!webValidation.hasWarriorValidation,
        pageErrorCount: webValidation.pageErrorCount || 0,
        consoleErrorCount: webValidation.consoleErrorCount || 0
      }
    },
    browserJsSuite: {
      baseline: baseline.browserJsSuite,
      candidate: {
        status: jsSuite.status || 'UNKNOWN',
        totalRun: jsSummary.totalRun || 0,
        totalFailures: jsSummary.totalFailures || 0,
        totalIgnored: jsSummary.totalIgnored || 0,
        pageErrorCount: jsSuite.pageErrorCount || 0,
        consoleErrorCount: jsSuite.consoleErrorCount || 0
      }
    },
    regressionIssues: issues
  };

  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, JSON.stringify(report, null, 2));
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);

  if (issues.length > 0) {
    fail(`Regression checks failed:\n- ${issues.join('\n- ')}`);
  }
}

try {
  main();
} catch (err) {
  process.stderr.write(`${err && err.stack ? err.stack : String(err)}\n`);
  process.exit(1);
}
