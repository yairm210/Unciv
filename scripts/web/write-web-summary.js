const fs = require('fs');
const path = require('path');

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function optionalJson(filePath) {
  if (!fs.existsSync(filePath)) return null;
  return readJson(filePath);
}

function main() {
  const tmpDir = path.join(process.cwd(), 'tmp');
  const validationPath = path.join(tmpDir, 'web-validation-summary.json');
  const jsSuitePath = path.join(tmpDir, 'js-browser-tests-result.json');
  const regressionPath = path.join(tmpDir, 'regression-diff-summary.json');

  const validation = optionalJson(validationPath);
  const jsSuite = optionalJson(jsSuitePath);
  const regression = optionalJson(regressionPath);

  const lines = [];
  lines.push('## Web E2E Summary');
  lines.push('');

  if (validation) {
    const counts = validation.counts || {};
    lines.push('### Gameplay Validation');
    lines.push(`- pass: ${counts.pass ?? 0}`);
    lines.push(`- fail: ${counts.fail ?? 0}`);
    lines.push(`- blocked: ${counts.blocked ?? 0}`);
    lines.push(`- disabled_by_design: ${counts.disabledByDesign ?? 0}`);
    lines.push(`- web_profile: ${validation.webProfile || 'phase1'}`);
    lines.push(`- browser: ${validation.browser || 'chromium'}`);
    lines.push(`- settler_validation: ${validation.hasSettlerValidation === true ? 'yes' : 'no'}`);
    lines.push(`- warrior_validation: ${validation.hasWarriorValidation === true ? 'yes' : 'no'}`);
    lines.push(`- page_errors: ${validation.pageErrorCount ?? 0}`);
    lines.push(`- console_errors: ${validation.consoleErrorCount ?? 0}`);
    lines.push('');
  } else {
    lines.push('### Gameplay Validation');
    lines.push('- missing summary artifact');
    lines.push('');
  }

  if (jsSuite) {
    const jsSummary = (jsSuite.jsResult && jsSuite.jsResult.summary) || {};
    lines.push('### Browser JS Suite');
    lines.push(`- status: ${jsSuite.status || 'UNKNOWN'}`);
    lines.push(`- browser: ${jsSuite.browser || 'chromium'}`);
    lines.push(`- web_profile: ${jsSuite.webProfile || 'phase1'}`);
    lines.push(`- total_run: ${jsSummary.totalRun ?? 0}`);
    lines.push(`- total_failures: ${jsSummary.totalFailures ?? 0}`);
    lines.push(`- total_ignored: ${jsSummary.totalIgnored ?? 0}`);
    lines.push(`- page_errors: ${jsSuite.pageErrorCount ?? 0}`);
    lines.push(`- console_errors: ${jsSuite.consoleErrorCount ?? 0}`);
    lines.push(`- ignored_console_errors: ${jsSuite.ignoredConsoleErrorCount ?? 0}`);
    lines.push('');
  } else {
    lines.push('### Browser JS Suite');
    lines.push('- missing suite artifact');
    lines.push('');
  }

  if (regression) {
    lines.push('### Regression Gate');
    lines.push(`- baseline_commit: ${regression.baselineCommit || 'unknown'}`);
    lines.push(`- issues: ${Array.isArray(regression.regressionIssues) ? regression.regressionIssues.length : 0}`);
    if (Array.isArray(regression.regressionIssues) && regression.regressionIssues.length > 0) {
      for (const issue of regression.regressionIssues) {
        lines.push(`- issue: ${issue}`);
      }
    }
    lines.push('');
  }

  process.stdout.write(lines.join('\n') + '\n');
}

main();
