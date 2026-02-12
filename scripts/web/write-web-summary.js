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
  const performancePath = path.join(tmpDir, 'performance-budget-summary.json');
  const multiplayerPath = path.join(tmpDir, 'web-multiplayer-multi-instance-result.json');

  const validation = optionalJson(validationPath);
  const jsSuite = optionalJson(jsSuitePath);
  const regression = optionalJson(regressionPath);
  const performance = optionalJson(performancePath);
  const multiplayer = optionalJson(multiplayerPath);

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
    lines.push(`- web_profile: ${validation.webProfile || 'phase4-full'}`);
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
    lines.push(`- web_profile: ${jsSuite.webProfile || 'phase4-full'}`);
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

  if (performance) {
    lines.push('### Performance Budget');
    lines.push(`- issues: ${Array.isArray(performance.issues) ? performance.issues.length : 0}`);
    const measurements = performance.measurements || {};
    lines.push(`- validation_duration_ms: ${measurements.validationDurationMs ?? 0}`);
    lines.push(`- main_menu_ready_ms: ${measurements.mainMenuReadyMs ?? 0}`);
    lines.push(`- world_entry_ready_ms: ${measurements.worldEntryReadyMs ?? 0}`);
    lines.push(`- unciv_js_bytes: ${measurements.uncivJsBytes ?? 0}`);
    lines.push(`- dist_bytes: ${measurements.distBytes ?? 0}`);
    if (Array.isArray(performance.issues) && performance.issues.length > 0) {
      for (const issue of performance.issues) {
        lines.push(`- issue: ${issue}`);
      }
    }
    lines.push('');
  }

  if (multiplayer) {
    lines.push('### Multiplayer Multi-Instance');
    lines.push(`- status: ${multiplayer.status || 'UNKNOWN'}`);
    lines.push(`- game_id: ${multiplayer.gameId || 'unknown'}`);
    lines.push(`- browser: ${multiplayer.browser || 'chromium'}`);
    lines.push(`- host_turn_sync: ${multiplayer.host?.turnSyncObserved === true ? 'yes' : 'no'}`);
    lines.push(`- guest_turn_sync: ${multiplayer.guest?.turnSyncObserved === true ? 'yes' : 'no'}`);
    lines.push(`- host_peer_chat: ${multiplayer.host?.peerChatObserved === true ? 'yes' : 'no'}`);
    lines.push(`- guest_peer_chat: ${multiplayer.guest?.peerChatObserved === true ? 'yes' : 'no'}`);
    lines.push(`- host_console_errors: ${Array.isArray(multiplayer.hostConsoleErrors) ? multiplayer.hostConsoleErrors.length : 0}`);
    lines.push(`- guest_console_errors: ${Array.isArray(multiplayer.guestConsoleErrors) ? multiplayer.guestConsoleErrors.length : 0}`);
    lines.push(`- host_page_errors: ${Array.isArray(multiplayer.hostPageErrors) ? multiplayer.hostPageErrors.length : 0}`);
    lines.push(`- guest_page_errors: ${Array.isArray(multiplayer.guestPageErrors) ? multiplayer.guestPageErrors.length : 0}`);
    if (Array.isArray(multiplayer.failures) && multiplayer.failures.length > 0) {
      for (const failure of multiplayer.failures) {
        lines.push(`- failure: ${failure}`);
      }
    }
    lines.push('');
  }

  process.stdout.write(lines.join('\n') + '\n');
}

main();
