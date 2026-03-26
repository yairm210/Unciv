const fs = require('fs');
const path = require('path');
const { randomUUID } = require('crypto');
const {
  attachDiagnostics,
  ensureTmpDir,
  getActionableRequestFailures,
  installBlobDiagnostics,
  launchChromium,
  readBlobDiagnostics,
  writeJson,
  ensureClickOpsBoot,
  ensureNoProbeFlags,
  waitForState,
  waitForTarget,
  clickTarget,
  dismissPopups,
} = require('./lib/clickops-common');

function summarizeBlobDiagnostics(diagnostics) {
  const events = diagnostics && Array.isArray(diagnostics.events) ? diagnostics.events : [];
  const countsByKind = {};
  for (const event of events) {
    const kind = String(event && event.kind ? event.kind : 'unknown');
    countsByKind[kind] = (countsByKind[kind] || 0) + 1;
  }
  return {
    eventCount: events.length,
    countsByKind,
  };
}

async function waitForMainMenu(page, timeoutMs) {
  await waitForState(page, {
    label: 'file-io',
    timeoutMs,
    description: 'main menu clickOps ready',
    predicate: (state) => state && state.screen === 'MainMenuScreen',
  });
  await waitForTarget(page, {
    label: 'file-io',
    targetId: 'main.start_new_game',
    timeoutMs,
  });
}

async function waitForWorld(page, timeoutMs, gameId) {
  return waitForState(page, {
    label: 'file-io',
    timeoutMs,
    description: gameId ? `world screen for game ${gameId}` : 'world screen',
    predicate: (state) => state
      && state.screen === 'WorldScreen'
      && (!gameId || String(state.gameId || '') === String(gameId)),
  });
}

async function openWorldMenu(page, targetId, timeoutMs) {
  await dismissPopups(page, { label: 'file-io', timeoutMs: 12000 });
  await waitForState(page, {
    label: 'file-io',
    timeoutMs: 12000,
    description: 'world popup dismissed before opening menu',
    predicate: (state) => state && state.screen === 'WorldScreen' && state.hasPopup !== true,
  });
  await clickTarget(page, {
    label: 'file-io',
    targetId: 'world.menu_open',
    timeoutMs,
  });
  await waitForTarget(page, {
    label: 'file-io',
    targetId,
    timeoutMs,
  });
}

async function main() {
  const tmpDir = ensureTmpDir();
  const baseUrl = String(process.env.WEB_BASE_URL || 'http://127.0.0.1:18080').trim().replace(/\/+$/, '');
  const webProfile = String(process.env.WEB_PROFILE || 'phase4-beta').trim() || 'phase4-beta';
  const timeoutMs = Number(process.env.WEB_FILE_IO_TIMEOUT_MS || '180000');
  const startupTimeoutMs = Number(process.env.WEB_FILE_IO_STARTUP_TIMEOUT_MS || '45000');
  const startupAttempts = Number(process.env.WEB_FILE_IO_STARTUP_ATTEMPTS || '3');
  const forceBrowserFallback = String(process.env.WEB_FILE_IO_FORCE_BROWSER_FALLBACK || '1').trim() !== '0';
  const runId = randomUUID();
  const outputPath = path.join(tmpDir, 'web-file-io-smoke-result.json');
  const screenshotPath = path.join(tmpDir, 'web-file-io-smoke-latest.png');
  const downloadPath = path.join(tmpDir, 'web-file-io-smoke-download.unciv');
  const blobDiagnosticsPath = path.join(tmpDir, 'web-file-io-smoke-blob-diagnostics.json');

  const report = {
    status: 'UNKNOWN',
    generatedAt: new Date().toISOString(),
    runId,
    browser: 'chromium',
    webProfile,
    timeoutMs,
    baseUrl,
    forceBrowserFallback,
    nativePickerSupport: {
      beforeSave: false,
      beforeLoad: false,
    },
    initialState: null,
    finalState: null,
    downloadName: null,
    downloadBytes: 0,
    downloadPath,
    pageErrors: [],
    consoleErrors: [],
    requestFailures: [],
    blobDiagnosticsPath,
    blobDiagnostics: {
      eventCount: 0,
      countsByKind: {},
    },
  };

  let browser;
  let context;
  let page;
  try {
    browser = await launchChromium();
    context = await browser.newContext({
      viewport: { width: 1600, height: 1200 },
      acceptDownloads: true,
    });
    page = await context.newPage();
    await page.addInitScript((forceFallback) => {
      if (typeof window === 'undefined') return;
      window.__uncivFileIoSmoke = {
        nativeSavePickerBefore: typeof window.showSaveFilePicker === 'function',
        nativeOpenPickerBefore: typeof window.showOpenFilePicker === 'function',
        forceBrowserFallback: forceFallback === true,
      };
      if (forceFallback !== true) return;
      try { delete window.showSaveFilePicker; } catch (_) {}
      try { delete window.showOpenFilePicker; } catch (_) {}
      window.showSaveFilePicker = undefined;
      window.showOpenFilePicker = undefined;
    }, forceBrowserFallback);
    await installBlobDiagnostics(page, 'file-io');
    attachDiagnostics(page, report, 'file-io');

    const url = new URL('/index.html', `${baseUrl}/`);
    url.searchParams.set('clickOps', '1');
    url.searchParams.set('webProfile', webProfile);
    await ensureClickOpsBoot(page, {
      url,
      label: 'file-io',
      startupTimeoutMs,
      startupAttempts,
      onRetry: (attempt, reason) => {
        process.stdout.write(`[file-io] startup retry ${attempt + 1}/${startupAttempts}: ${reason}\n`);
        report.pageErrors = report.pageErrors.filter((entry) => !String(entry).startsWith('[file-io]'));
        report.consoleErrors = report.consoleErrors.filter((entry) => !String(entry).startsWith('[file-io]'));
        report.requestFailures = report.requestFailures.filter((entry) => entry.label !== 'file-io');
      },
    });

    ensureNoProbeFlags(page.url(), 'file-io');
    await waitForMainMenu(page, timeoutMs);
    await clickTarget(page, {
      label: 'file-io',
      targetId: 'main.start_new_game',
      timeoutMs,
    });
    await waitForState(page, {
      label: 'file-io',
      timeoutMs,
      description: 'new game screen',
      predicate: (state) => state && state.screen === 'NewGameScreen',
    });
    await clickTarget(page, {
      label: 'file-io',
      targetId: 'newgame.start_game',
      timeoutMs,
    });
    const initialWorld = await waitForWorld(page, timeoutMs);
    report.initialState = initialWorld.state;

    report.nativePickerSupport = await page.evaluate(() => ({
      beforeSave: !!(window.__uncivFileIoSmoke && window.__uncivFileIoSmoke.nativeSavePickerBefore),
      beforeLoad: !!(window.__uncivFileIoSmoke && window.__uncivFileIoSmoke.nativeOpenPickerBefore),
    })).catch(() => report.nativePickerSupport);

    await openWorldMenu(page, 'world.menu.save_game', timeoutMs);
    await clickTarget(page, {
      label: 'file-io',
      targetId: 'world.menu.save_game',
      timeoutMs,
    });
    await waitForState(page, {
      label: 'file-io',
      timeoutMs,
      description: 'save game screen',
      predicate: (state) => state && state.screen === 'SaveGameScreen',
    });

    const downloadPromise = page.waitForEvent('download', { timeout: 30000 });
    await clickTarget(page, {
      label: 'file-io',
      targetId: 'save.custom_location',
      timeoutMs,
    });
    const download = await downloadPromise;
    const downloadFailure = await download.failure();
    if (downloadFailure) {
      throw new Error(`Browser download failed: ${downloadFailure}`);
    }
    report.downloadName = download.suggestedFilename();
    await download.saveAs(downloadPath);
    report.downloadBytes = fs.statSync(downloadPath).size;
    if (report.downloadBytes <= 0) {
      throw new Error(`Downloaded save file is empty: ${downloadPath}`);
    }
    await waitForWorld(page, timeoutMs, report.initialState && report.initialState.gameId);
    await page.waitForTimeout(5500);

    await openWorldMenu(page, 'world.menu.load_game', timeoutMs);
    await clickTarget(page, {
      label: 'file-io',
      targetId: 'world.menu.load_game',
      timeoutMs,
    });
    await waitForState(page, {
      label: 'file-io',
      timeoutMs,
      description: 'load game screen',
      predicate: (state) => state && state.screen === 'LoadGameScreen',
    });

    const fileChooserPromise = page.waitForEvent('filechooser', { timeout: 30000 });
    await clickTarget(page, {
      label: 'file-io',
      targetId: 'load.custom_location',
      timeoutMs,
    });
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles(downloadPath);

    const finalWorld = await waitForWorld(page, timeoutMs, report.initialState && report.initialState.gameId);
    report.finalState = finalWorld.state;
    if (String(report.finalState.gameId || '') !== String(report.initialState.gameId || '')) {
      throw new Error(`Loaded gameId mismatch: expected ${report.initialState.gameId} got ${report.finalState.gameId}`);
    }
    if (Number(report.finalState.turn) !== Number(report.initialState.turn)) {
      throw new Error(`Loaded turn mismatch: expected ${report.initialState.turn} got ${report.finalState.turn}`);
    }
    const diagnostics = await readBlobDiagnostics(page, 'file-io');
    report.blobDiagnostics = summarizeBlobDiagnostics(diagnostics);
    writeJson(blobDiagnosticsPath, diagnostics);

    const actionableRequestFailures = getActionableRequestFailures(report.requestFailures);
    const failures = [];
    if (report.pageErrors.length > 0) failures.push(`page errors detected: ${report.pageErrors.length}`);
    if (report.consoleErrors.length > 0) failures.push(`console errors detected: ${report.consoleErrors.length}`);
    if (actionableRequestFailures.length > 0) failures.push(`request failures detected: ${actionableRequestFailures.length}`);
    if (report.downloadBytes <= 0) failures.push('missing downloaded save artifact');
    if ((report.blobDiagnostics.countsByKind.createObjectURL || 0) === 0) failures.push('expected createObjectURL event was not observed');
    if ((report.blobDiagnostics.countsByKind.anchorBlobClick || 0) === 0) failures.push('expected anchorBlobClick event was not observed');
    if ((report.blobDiagnostics.countsByKind.revokeObjectURL || 0) === 0) failures.push('expected revokeObjectURL event was not observed');
    if (failures.length > 0) {
      report.status = 'FAILED';
      report.failures = failures;
    } else {
      report.status = 'PASSED';
    }
  } catch (err) {
    report.status = 'FAILED';
    report.failures = [String(err && err.stack ? err.stack : err)];
  } finally {
    if (page) {
      const diagnostics = await readBlobDiagnostics(page, 'file-io');
      writeJson(blobDiagnosticsPath, diagnostics);
      report.blobDiagnostics = summarizeBlobDiagnostics(diagnostics);
      await page.screenshot({ path: screenshotPath, fullPage: true }).catch(() => {});
    }
    if (context) await context.close().catch(() => {});
    if (browser) await browser.close().catch(() => {});
  }

  writeJson(outputPath, report);
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
  if (report.status !== 'PASSED') process.exit(2);
}

main().catch((err) => {
  process.stderr.write(`${err && err.stack ? err.stack : String(err)}\n`);
  process.exit(1);
});
