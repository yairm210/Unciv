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
  const uiCorePath = path.join(tmpDir, 'web-ui-core-loop-result.json');
  const uiMultiplayerPath = path.join(tmpDir, 'web-ui-multiplayer-result.json');
  const uiMapEditorPath = path.join(tmpDir, 'web-ui-map-editor-result.json');
  const uiWarFromStartPath = path.join(tmpDir, 'web-ui-war-from-start-result.json');
  const uiWarPreworldPath = path.join(tmpDir, 'web-ui-war-preworld-result.json');
  const uiWarDeepPath = path.join(tmpDir, 'web-ui-war-deep-result.json');

  const validation = optionalJson(validationPath);
  const jsSuite = optionalJson(jsSuitePath);
  const regression = optionalJson(regressionPath);
  const performance = optionalJson(performancePath);
  const multiplayer = optionalJson(multiplayerPath);
  const uiCore = optionalJson(uiCorePath);
  const uiMultiplayer = optionalJson(uiMultiplayerPath);
  const uiMapEditor = optionalJson(uiMapEditorPath);
  const uiWarFromStart = optionalJson(uiWarFromStartPath);
  const uiWarPreworld = optionalJson(uiWarPreworldPath);
  const uiWarDeep = optionalJson(uiWarDeepPath);

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

  if (uiCore) {
    lines.push('### UI Core Loop (30s)');
    lines.push(`- status: ${uiCore.status || 'UNKNOWN'}`);
    lines.push(`- run_id: ${uiCore.runId || 'unknown'}`);
    lines.push(`- passed: ${uiCore.result?.passed === true ? 'yes' : 'no'}`);
    lines.push(`- notes: ${uiCore.result?.notes || ''}`);
    lines.push(`- timeout_ms: ${uiCore.timeoutMs ?? 0}`);
    lines.push(`- page_errors: ${Array.isArray(uiCore.pageErrors) ? uiCore.pageErrors.length : 0}`);
    lines.push(`- console_errors: ${Array.isArray(uiCore.consoleErrors) ? uiCore.consoleErrors.length : 0}`);
    if (Array.isArray(uiCore.failures) && uiCore.failures.length > 0) {
      for (const failure of uiCore.failures) {
        lines.push(`- failure: ${failure}`);
      }
    }
    lines.push('');
  }

  if (uiMultiplayer) {
    lines.push('### UI Multiplayer (30s)');
    lines.push(`- status: ${uiMultiplayer.status || 'UNKNOWN'}`);
    lines.push(`- run_id: ${uiMultiplayer.runId || 'unknown'}`);
    lines.push(`- host_passed: ${uiMultiplayer.host?.passed === true ? 'yes' : 'no'}`);
    lines.push(`- guest_passed: ${uiMultiplayer.guest?.passed === true ? 'yes' : 'no'}`);
    lines.push(`- host_turn_sync: ${uiMultiplayer.host?.turnSyncObserved === true ? 'yes' : 'no'}`);
    lines.push(`- guest_turn_sync: ${uiMultiplayer.guest?.turnSyncObserved === true ? 'yes' : 'no'}`);
    lines.push(`- host_chat: ${uiMultiplayer.host?.bidirectionalChatObserved === true ? 'yes' : 'no'}`);
    lines.push(`- guest_chat: ${uiMultiplayer.guest?.bidirectionalChatObserved === true ? 'yes' : 'no'}`);
    lines.push(`- guest_reconnect: ${uiMultiplayer.guest?.reconnectObserved === true ? 'yes' : 'no'}`);
    lines.push(`- timeout_ms: ${uiMultiplayer.timeoutMs ?? 0}`);
    lines.push(`- page_errors: ${Array.isArray(uiMultiplayer.pageErrors) ? uiMultiplayer.pageErrors.length : 0}`);
    lines.push(`- console_errors: ${Array.isArray(uiMultiplayer.consoleErrors) ? uiMultiplayer.consoleErrors.length : 0}`);
    if (Array.isArray(uiMultiplayer.failures) && uiMultiplayer.failures.length > 0) {
      for (const failure of uiMultiplayer.failures) {
        lines.push(`- failure: ${failure}`);
      }
    }
    lines.push('');
  }

  if (uiMapEditor) {
    lines.push('### UI Map Editor (30s)');
    lines.push(`- status: ${uiMapEditor.status || 'UNKNOWN'}`);
    lines.push(`- run_id: ${uiMapEditor.runId || 'unknown'}`);
    lines.push(`- passed: ${uiMapEditor.result?.passed === true ? 'yes' : 'no'}`);
    lines.push(`- notes: ${uiMapEditor.result?.notes || ''}`);
    lines.push(`- timeout_ms: ${uiMapEditor.timeoutMs ?? 0}`);
    lines.push(`- page_errors: ${Array.isArray(uiMapEditor.pageErrors) ? uiMapEditor.pageErrors.length : 0}`);
    lines.push(`- console_errors: ${Array.isArray(uiMapEditor.consoleErrors) ? uiMapEditor.consoleErrors.length : 0}`);
    if (Array.isArray(uiMapEditor.failures) && uiMapEditor.failures.length > 0) {
      for (const failure of uiMapEditor.failures) {
        lines.push(`- failure: ${failure}`);
      }
    }
    lines.push('');
  }

  if (uiWarFromStart) {
    lines.push('### UI War From Start (30s)');
    lines.push(`- status: ${uiWarFromStart.status || 'UNKNOWN'}`);
    lines.push(`- run_id: ${uiWarFromStart.runId || 'unknown'}`);
    lines.push(`- passed: ${uiWarFromStart.result?.passed === true ? 'yes' : 'no'}`);
    lines.push(`- war_declared: ${uiWarFromStart.result?.warDeclaredObserved === true ? 'yes' : 'no'}`);
    lines.push(`- combat_exchanges: ${uiWarFromStart.result?.combatExchangesObserved === true ? 'yes' : 'no'}`);
    lines.push(`- diplo_transitions: ${uiWarFromStart.result?.diplomacyStateTransitionsObserved === true ? 'yes' : 'no'}`);
    lines.push(`- multi_turn_progress: ${uiWarFromStart.result?.multiTurnProgressObserved === true ? 'yes' : 'no'}`);
    lines.push(`- forces_observed: ${uiWarFromStart.result?.forcesObserved || ''}`);
    lines.push(`- notes: ${uiWarFromStart.result?.notes || ''}`);
    lines.push(`- timeout_ms: ${uiWarFromStart.timeoutMs ?? 0}`);
    lines.push(`- page_errors: ${Array.isArray(uiWarFromStart.pageErrors) ? uiWarFromStart.pageErrors.length : 0}`);
    lines.push(`- console_errors: ${Array.isArray(uiWarFromStart.consoleErrors) ? uiWarFromStart.consoleErrors.length : 0}`);
    if (Array.isArray(uiWarFromStart.failures) && uiWarFromStart.failures.length > 0) {
      for (const failure of uiWarFromStart.failures) {
        lines.push(`- failure: ${failure}`);
      }
    }
    lines.push('');
  }

  if (uiWarPreworld) {
    lines.push('### UI War Preworld (30s)');
    lines.push(`- status: ${uiWarPreworld.status || 'UNKNOWN'}`);
    lines.push(`- run_id: ${uiWarPreworld.runId || 'unknown'}`);
    lines.push(`- passed: ${uiWarPreworld.result?.passed === true ? 'yes' : 'no'}`);
    lines.push(`- war_declared: ${uiWarPreworld.result?.warDeclaredObserved === true ? 'yes' : 'no'}`);
    lines.push(`- combat_exchanges: ${uiWarPreworld.result?.combatExchangesObserved === true ? 'yes' : 'no'}`);
    lines.push(`- city_capture: ${uiWarPreworld.result?.cityCaptureObserved === true ? 'yes' : 'no'}`);
    lines.push(`- peace_observed: ${uiWarPreworld.result?.peaceObserved === true ? 'yes' : 'no'}`);
    lines.push(`- diplo_transitions: ${uiWarPreworld.result?.diplomacyStateTransitionsObserved === true ? 'yes' : 'no'}`);
    lines.push(`- multi_turn_progress: ${uiWarPreworld.result?.multiTurnProgressObserved === true ? 'yes' : 'no'}`);
    lines.push(`- forces_observed: ${uiWarPreworld.result?.forcesObserved || ''}`);
    lines.push(`- notes: ${uiWarPreworld.result?.notes || ''}`);
    lines.push(`- timeout_ms: ${uiWarPreworld.timeoutMs ?? 0}`);
    lines.push(`- page_errors: ${Array.isArray(uiWarPreworld.pageErrors) ? uiWarPreworld.pageErrors.length : 0}`);
    lines.push(`- console_errors: ${Array.isArray(uiWarPreworld.consoleErrors) ? uiWarPreworld.consoleErrors.length : 0}`);
    if (Array.isArray(uiWarPreworld.failures) && uiWarPreworld.failures.length > 0) {
      for (const failure of uiWarPreworld.failures) {
        lines.push(`- failure: ${failure}`);
      }
    }
    lines.push('');
  }

  if (uiWarDeep) {
    lines.push('### UI War Deep');
    lines.push(`- status: ${uiWarDeep.status || 'UNKNOWN'}`);
    lines.push(`- run_id: ${uiWarDeep.runId || 'unknown'}`);
    lines.push(`- passed: ${uiWarDeep.result?.passed === true ? 'yes' : 'no'}`);
    lines.push(`- war_declared: ${uiWarDeep.result?.warDeclaredObserved === true ? 'yes' : 'no'}`);
    lines.push(`- combat_exchanges: ${uiWarDeep.result?.combatExchangesObserved === true ? 'yes' : 'no'}`);
    lines.push(`- city_capture: ${uiWarDeep.result?.cityCaptureObserved === true ? 'yes' : 'no'}`);
    lines.push(`- peace_observed: ${uiWarDeep.result?.peaceObserved === true ? 'yes' : 'no'}`);
    lines.push(`- diplo_transitions: ${uiWarDeep.result?.diplomacyStateTransitionsObserved === true ? 'yes' : 'no'}`);
    lines.push(`- multi_turn_progress: ${uiWarDeep.result?.multiTurnProgressObserved === true ? 'yes' : 'no'}`);
    lines.push(`- forces_observed: ${uiWarDeep.result?.forcesObserved || ''}`);
    lines.push(`- notes: ${uiWarDeep.result?.notes || ''}`);
    lines.push(`- timeout_ms: ${uiWarDeep.timeoutMs ?? 0}`);
    lines.push(`- page_errors: ${Array.isArray(uiWarDeep.pageErrors) ? uiWarDeep.pageErrors.length : 0}`);
    lines.push(`- console_errors: ${Array.isArray(uiWarDeep.consoleErrors) ? uiWarDeep.consoleErrors.length : 0}`);
    if (Array.isArray(uiWarDeep.failures) && uiWarDeep.failures.length > 0) {
      for (const failure of uiWarDeep.failures) {
        lines.push(`- failure: ${failure}`);
      }
    }
    lines.push('');
  }

  process.stdout.write(lines.join('\n') + '\n');
}

main();
