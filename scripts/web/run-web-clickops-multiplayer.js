const path = require('path');
const { randomUUID } = require('crypto');
const zlib = require('zlib');
const {
  attachDiagnostics,
  ensureTmpDir,
  launchChromium,
  writeJson,
  ensureClickOpsBoot,
  ensureNoProbeFlags,
  getSnapshot,
  waitForState,
  waitForTarget,
  clickTarget,
  fillTargetWithText,
  dismissPopups,
} = require('./lib/clickops-common');

const HOST_TEST_USER_ID = '00000000-0000-0000-0000-0000000000a1';
const GUEST_TEST_USER_ID = '00000000-0000-0000-0000-0000000000b2';
const SHARED_TEST_PASSWORD = 'webtest-pass';

function hasMessage(state, token) {
  if (!state || !Array.isArray(state.chatMessages)) return false;
  return state.chatMessages.some((message) => String(message || '').includes(token));
}

async function clickCheckbox(page, label, targetId, timeoutMs) {
  const { target } = await waitForTarget(page, {
    label,
    targetId,
    timeoutMs,
    allowScroll: true,
  });
  const clickX = Math.floor(Number(target.x || 0) + Math.min(18, Number(target.width || 0) / 3));
  const clickY = Math.floor(Number(target.y || 0) + Number(target.height || 0) / 2);
  await page.mouse.click(clickX, clickY);
}

async function configureMultiplayerPlayerIds(hostPage, report, timeoutMs) {
  await fillTargetWithText(hostPage, {
    label: 'host',
    targetId: 'newgame.player_id_input',
    text: GUEST_TEST_USER_ID,
    timeoutMs,
    allowScroll: true,
  });
  await waitForState(hostPage, {
    label: 'host',
    timeoutMs,
    description: 'first multiplayer player assigned guest user id',
    predicate: (_, snapshot) => {
      const target = snapshot.targets.find((item) => item && item.id === 'newgame.player_id_input');
      return !!target && typeof target.text === 'string' && target.text.trim() === GUEST_TEST_USER_ID;
    },
  });

  const slotTypeTarget = await waitForTarget(hostPage, {
    label: 'host',
    targetId: 'newgame.player_type.1',
    timeoutMs,
    allowScroll: true,
  });
  const slotTypeText = String(slotTypeTarget.target.text || '').trim().toLowerCase();
  if (slotTypeText.includes('ai')) {
    await clickTarget(hostPage, {
      label: 'host',
      targetId: 'newgame.player_type.1',
      timeoutMs,
      allowScroll: true,
    });
  } else if (!slotTypeText.includes('human')) {
    throw new Error(`Unexpected second-player type label: ${slotTypeTarget.target.text}`);
  }

  await fillTargetWithText(hostPage, {
    label: 'host',
    targetId: 'newgame.player_id_input.1',
    text: HOST_TEST_USER_ID,
    timeoutMs,
    allowScroll: true,
  });
  await waitForState(hostPage, {
    label: 'host',
    timeoutMs,
    description: 'second multiplayer player assigned host user id',
    predicate: (_, snapshot) => {
      const target = snapshot.targets.find((item) => item && item.id === 'newgame.player_id_input.1');
      return !!target && typeof target.text === 'string' && target.text.trim() === HOST_TEST_USER_ID;
    },
  });
  report.hostActions.push('configured slot 1 as guest and slot 2 as host');
}

async function verifyServerGamePayload(mpServer, gameId) {
  const fileUrl = `${String(mpServer).replace(/\/+$/, '')}/files/${encodeURIComponent(gameId)}`;
  const auth = Buffer.from(`${HOST_TEST_USER_ID}:${SHARED_TEST_PASSWORD}`, 'utf8').toString('base64');
  const response = await fetch(fileUrl, {
    method: 'GET',
    headers: { Authorization: `Basic ${auth}` },
  });
  if (!response.ok) {
    throw new Error(`Host game payload fetch failed: ${response.status} ${response.statusText} (${fileUrl})`);
  }
  const body = (await response.text()).trim();
  let plain = body;
  try {
    plain = zlib.gunzipSync(Buffer.from(body, 'base64')).toString('utf8');
  } catch (_) {
    // Keep non-gzip payload as-is for diagnostics.
  }
  const normalized = String(plain || '').trim();
  if (!normalized) {
    throw new Error('Host uploaded an empty multiplayer game payload.');
  }
  if (normalized === '{}') {
    throw new Error(
      'Host uploaded empty JSON for multiplayer game state ({}). '
      + 'Web serializer is dropping GameInfo fields; guest cannot load the game.',
    );
  }
  if (normalized.startsWith('WEBSNAP:')) {
    throw new Error(
      `Host uploaded local-only WEBSNAP token (${normalized}). `
      + 'This payload is not transferable across browser instances.',
    );
  }
}

async function waitForMainMenu(page, label, timeoutMs) {
  await waitForState(page, {
    label,
    timeoutMs,
    description: 'main menu clickOps ready',
    predicate: (state) => state && state.screen === 'MainMenuScreen',
  });
  await waitForTarget(page, { label, targetId: 'main.start_new_game', timeoutMs });
}

async function waitForGameRowOrRefresh(page, label, gameId, timeoutMs) {
  const startedAt = Date.now();
  const targetId = `mp.game_row.${gameId}`;
  while (Date.now() - startedAt <= timeoutMs) {
    const snapshot = await getSnapshot(page, label);
    const rowTarget = snapshot.targets.find((item) => item && item.id === targetId && item.visible === true);
    if (rowTarget) return rowTarget;
    const refresh = snapshot.targets.find((item) => item && item.id === 'mp.refresh_list' && item.visible === true && item.enabled === true);
    if (refresh) {
      const x = Math.floor(Number(refresh.x || 0) + Number(refresh.width || 0) / 2);
      const y = Math.floor(Number(refresh.y || 0) + Number(refresh.height || 0) / 2);
      await page.mouse.click(x, y);
    }
    await page.waitForTimeout(350);
  }
  throw new Error(`[${label}] timed out waiting for game row target [${targetId}]`);
}

function hasTurnCommitted(beforeState, afterState) {
  const beforeTurn = Number(beforeState && beforeState.turn ? beforeState.turn : 0);
  const afterTurn = Number(afterState && afterState.turn ? afterState.turn : 0);
  const beforePlayer = String(beforeState && beforeState.currentPlayer ? beforeState.currentPlayer : '');
  const afterPlayer = String(afterState && afterState.currentPlayer ? afterState.currentPlayer : '');
  if (afterTurn > beforeTurn) return true;
  if (afterPlayer && beforePlayer && afterPlayer !== beforePlayer) return true;
  return false;
}

async function selectFirstAvailableTech(page, label, timeoutMs) {
  const techSnapshot = await waitForState(page, {
    label,
    timeoutMs,
    description: 'tech picker with selectable technologies',
    predicate: (state, snapshot) => {
      if (!state || state.screen !== 'TechPickerScreen') return false;
      return snapshot.targets.some((item) => item && item.id && item.id.startsWith('tech-option:') && item.visible === true && item.enabled === true);
    },
  });
  const techTarget = techSnapshot.targets.find((item) => item && item.id && item.id.startsWith('tech-option:') && item.visible === true && item.enabled === true);
  if (!techTarget) {
    throw new Error(`[${label}] tech picker is open but no selectable tech target was found`);
  }
  const x = Math.floor(Number(techTarget.x || 0) + Number(techTarget.width || 0) / 2);
  const y = Math.floor(Number(techTarget.y || 0) + Number(techTarget.height || 0) / 2);
  await page.mouse.click(x, y);
  await page.waitForTimeout(120);
  await clickTarget(page, { label, targetId: 'tech-picker-confirm', timeoutMs, allowScroll: true });
  await waitForState(page, {
    label,
    timeoutMs,
    description: 'return to world after selecting technology',
    predicate: (state) => state && state.screen === 'WorldScreen',
  });
}

async function commitGuestTurn(page, report, beforeState, timeoutMs) {
  const startedAt = Date.now();
  while (Date.now() - startedAt <= timeoutMs) {
    await dismissPopups(page, { label: 'guest', timeoutMs: 8000 }).catch(() => {});
    const snapshot = await getSnapshot(page, 'guest');
    const state = snapshot.state || null;
    if (state && state.screen === 'TechPickerScreen') {
      await selectFirstAvailableTech(page, 'guest', Math.min(30000, timeoutMs));
      continue;
    }
    if (state && state.screen === 'WorldScreen') {
      if (hasTurnCommitted(beforeState, state)) return state;
      const nextTurn = snapshot.targets.find((item) => item && item.id === 'world.next_turn' && item.visible === true && item.enabled === true);
      if (nextTurn) {
        const x = Math.floor(Number(nextTurn.x || 0) + Number(nextTurn.width || 0) / 2);
        const y = Math.floor(Number(nextTurn.y || 0) + Number(nextTurn.height || 0) / 2);
        await page.mouse.click(x, y);
        await page.waitForTimeout(300);
        continue;
      }
      const techOpen = snapshot.targets.find((item) => item && item.id === 'world.tech_open' && item.visible === true && item.enabled === true);
      if (techOpen) {
        await clickTarget(page, { label: 'guest', targetId: 'world.tech_open', timeoutMs: 15000, allowScroll: true });
        await page.waitForTimeout(200);
        continue;
      }
    }
    await page.waitForTimeout(250);
  }
  throw new Error('Guest could not commit end-turn action from world state.');
}

async function maybePassHostTurn(hostPage, report, timeoutMs) {
  const startedAt = Date.now();
  while (Date.now() - startedAt <= timeoutMs) {
    const hostSnapshot = await getSnapshot(hostPage, 'host');
    if (!hostSnapshot.state || hostSnapshot.state.screen !== 'WorldScreen') {
      await hostPage.waitForTimeout(120);
      continue;
    }
    if (hostSnapshot.state.hasPopup === true) {
      await dismissPopups(hostPage, { label: 'host', timeoutMs: 8000 }).catch(() => {});
    }
    if (hostSnapshot.state.isPlayersTurn === true) {
      const nextTurn = hostSnapshot.targets.find((item) => item && item.id === 'world.next_turn' && item.visible === true && item.enabled === true);
      if (nextTurn) {
        const x = Math.floor(Number(nextTurn.x || 0) + Number(nextTurn.width || 0) / 2);
        const y = Math.floor(Number(nextTurn.y || 0) + Number(nextTurn.height || 0) / 2);
        await hostPage.mouse.click(x, y);
        report.hostActions.push('host clicked world.next_turn to hand off turn');
      }
    }
    await hostPage.waitForTimeout(300);
  }
}

async function ensureGuestCanAct(guestPage, hostPage, report, timeoutMs) {
  const startedAt = Date.now();
  while (Date.now() - startedAt <= timeoutMs) {
    const guestSnapshot = await getSnapshot(guestPage, 'guest');
    if (guestSnapshot.state && guestSnapshot.state.screen === 'WorldScreen' && guestSnapshot.state.isPlayersTurn === true) {
      return guestSnapshot.state;
    }
    await maybePassHostTurn(hostPage, report, 1500);
    await guestPage.waitForTimeout(250);
  }
  throw new Error('Guest never reached playable turn in world screen.');
}

async function loadLatestOnHost(hostPage, gameId, expectedState, timeoutMs) {
  const startedAt = Date.now();
  while (Date.now() - startedAt <= timeoutMs) {
    await dismissPopups(hostPage, { label: 'host', timeoutMs: 4000 }).catch(() => {});
    await clickTarget(hostPage, { label: 'host', targetId: 'world.multiplayer_status_open', timeoutMs: 12000 });
    await waitForState(hostPage, {
      label: 'host',
      timeoutMs: 15000,
      description: 'multiplayer status popup open',
      predicate: (state) => state && state.hasPopup === true,
    });
    await waitForGameRowOrRefresh(hostPage, 'host', gameId, 15000);
    await clickTarget(hostPage, { label: 'host', targetId: `mp.game_row.${gameId}`, timeoutMs: 12000 });
    await clickTarget(hostPage, { label: 'host', targetId: 'world.load_latest_multiplayer', timeoutMs: 12000 });
    const loaded = await waitForState(hostPage, {
      label: 'host',
      timeoutMs: 25000,
      description: 'host world reloaded after multiplayer sync',
      predicate: (state) => state
        && state.screen === 'WorldScreen'
        && Number(state.turn) === Number(expectedState.turn)
        && String(state.currentPlayer || '') === String(expectedState.currentPlayer || ''),
    }).catch(() => null);
    if (loaded && loaded.state) return loaded.state;
  }
  throw new Error(
    `Host failed to observe multiplayer sync state turn=${expectedState.turn} currentPlayer=${expectedState.currentPlayer}.`,
  );
}

async function sendChatMessage(page, label, message, timeoutMs) {
  await dismissPopups(page, { label, timeoutMs: 8000 }).catch(() => {});
  await clickTarget(page, { label, targetId: 'world.chat_open', timeoutMs });
  await waitForState(page, {
    label,
    timeoutMs,
    description: 'chat popup open',
    predicate: (state) => state && state.hasPopup === true,
  });
  await fillTargetWithText(page, {
    label,
    targetId: 'chat.input',
    text: message,
    timeoutMs,
  });
  await clickTarget(page, { label, targetId: 'chat.send', timeoutMs });
  await waitForState(page, {
    label,
    timeoutMs,
    description: `chat message echoed for token ${message}`,
    predicate: (state) => hasMessage(state, message),
  });
}

async function main() {
  const tmpDir = ensureTmpDir();
  const outputPath = path.join(tmpDir, 'web-clickops-multiplayer-result.json');
  const hostScreenshotPath = path.join(tmpDir, 'web-clickops-multiplayer-host-latest.png');
  const guestScreenshotPath = path.join(tmpDir, 'web-clickops-multiplayer-guest-latest.png');
  const baseUrl = String(process.env.WEB_BASE_URL || 'http://127.0.0.1:18080').trim().replace(/\/+$/, '');
  const mpServer = String(process.env.WEB_MP_SERVER || 'http://127.0.0.1:19090').trim();
  const webProfile = String(process.env.WEB_PROFILE || 'phase4-full').trim() || 'phase4-full';
  const timeoutMs = Number(process.env.WEB_CLICKS_MULTIPLAYER_TIMEOUT_MS || '240000');
  const startupTimeoutMs = Number(process.env.WEB_CLICKS_MULTIPLAYER_STARTUP_TIMEOUT_MS || '45000');
  const startupAttempts = Number(process.env.WEB_CLICKS_MULTIPLAYER_STARTUP_ATTEMPTS || '3');
  const runId = randomUUID();

  const report = {
    status: 'UNKNOWN',
    generatedAt: new Date().toISOString(),
    runId,
    browser: 'chromium',
    webProfile,
    timeoutMs,
    baseUrl,
    mpServer,
    hostState: null,
    guestState: null,
    gameId: null,
    hostActions: [],
    guestActions: [],
    pageErrors: [],
    consoleErrors: [],
    requestFailures: [],
    hostScreenshotPath,
    guestScreenshotPath,
  };

  let browser;
  let hostContext;
  let guestContext;
  let hostPage;
  let guestPage;
  try {
    browser = await launchChromium();
    hostContext = await browser.newContext({
      viewport: { width: 1600, height: 1200 },
      permissions: ['clipboard-read', 'clipboard-write'],
    });
    guestContext = await browser.newContext({
      viewport: { width: 1600, height: 1200 },
      permissions: ['clipboard-read', 'clipboard-write'],
    });
    hostPage = await hostContext.newPage();
    guestPage = await guestContext.newPage();
    attachDiagnostics(hostPage, report, 'host');
    attachDiagnostics(guestPage, report, 'guest');

    const hostUrl = new URL('/index.html', `${baseUrl}/`);
    hostUrl.searchParams.set('webtest', '1');
    hostUrl.searchParams.set('webProfile', webProfile);
    hostUrl.searchParams.set('clickOps', '1');
    hostUrl.searchParams.set('clickOpsRole', 'host');
    hostUrl.searchParams.set('mpServer', mpServer);

    const guestUrl = new URL(hostUrl.toString());
    guestUrl.searchParams.set('clickOpsRole', 'guest');

    await ensureClickOpsBoot(guestPage, {
      url: guestUrl,
      label: 'guest',
      startupTimeoutMs,
      startupAttempts,
      onRetry: (attempt, reason) => {
        process.stdout.write(`[guest] startup retry ${attempt + 1}/${startupAttempts}: ${reason}\n`);
        report.pageErrors = report.pageErrors.filter((entry) => !String(entry).startsWith('[guest]'));
        report.consoleErrors = report.consoleErrors.filter((entry) => !String(entry).startsWith('[guest]'));
        report.requestFailures = report.requestFailures.filter((entry) => entry.label !== 'guest');
      },
    });
    await ensureClickOpsBoot(hostPage, {
      url: hostUrl,
      label: 'host',
      startupTimeoutMs,
      startupAttempts,
      onRetry: (attempt, reason) => {
        process.stdout.write(`[host] startup retry ${attempt + 1}/${startupAttempts}: ${reason}\n`);
        report.pageErrors = report.pageErrors.filter((entry) => !String(entry).startsWith('[host]'));
        report.consoleErrors = report.consoleErrors.filter((entry) => !String(entry).startsWith('[host]'));
        report.requestFailures = report.requestFailures.filter((entry) => entry.label !== 'host');
      },
    });

    ensureNoProbeFlags(hostPage.url(), 'host');
    ensureNoProbeFlags(guestPage.url(), 'guest');
    await waitForMainMenu(hostPage, 'host', timeoutMs);
    await waitForMainMenu(guestPage, 'guest', timeoutMs);

    await dismissPopups(hostPage, { label: 'host', timeoutMs: 8000 }).catch(() => {});
    await clickTarget(hostPage, { label: 'host', targetId: 'main.start_new_game', timeoutMs });
    await waitForState(hostPage, {
      label: 'host',
      timeoutMs,
      description: 'host new game screen',
      predicate: (state) => state && state.screen === 'NewGameScreen',
    });
    await hostPage.mouse.move(220, 420).catch(() => {});
    const mpToggleBefore = await waitForTarget(hostPage, {
      label: 'host',
      targetId: 'newgame.online_multiplayer',
      timeoutMs,
      allowScroll: true,
    });
    report.hostActions.push(`online toggle before click checked=${mpToggleBefore.target.checked} x=${mpToggleBefore.target.x} y=${mpToggleBefore.target.y} w=${mpToggleBefore.target.width} h=${mpToggleBefore.target.height}`);
    if (mpToggleBefore.target.checked !== true) {
      for (let attempt = 0; attempt < 12; attempt += 1) {
        await clickCheckbox(hostPage, 'host', 'newgame.online_multiplayer', timeoutMs);
        await hostPage.waitForTimeout(220);
        const afterClickSnapshot = await getSnapshot(hostPage, 'host');
        const toggleAfterClick = afterClickSnapshot.targets.find((item) => item && item.id === 'newgame.online_multiplayer') || null;
        const setCurrentAfterClick = afterClickSnapshot.targets.find((item) => item && item.id === 'newgame.set_current_user' && item.visible === true) || null;
        report.hostActions.push(`online toggle click attempt=${attempt + 1} checked=${toggleAfterClick ? toggleAfterClick.checked : 'missing'} y=${toggleAfterClick ? toggleAfterClick.y : 'na'} setCurrentUserVisible=${setCurrentAfterClick ? 'yes' : 'no'}`);
        if ((toggleAfterClick && toggleAfterClick.checked === true) || setCurrentAfterClick) break;
      }
      await dismissPopups(hostPage, { label: 'host', timeoutMs: 12000 }).catch(() => {});
      try {
        await waitForState(hostPage, {
          label: 'host',
          timeoutMs,
          description: 'online multiplayer controls visible',
          predicate: (_, snapshot) => {
            const toggle = snapshot.targets.find((item) => item && item.id === 'newgame.online_multiplayer');
            if (toggle && toggle.checked === true) return true;
            const setCurrentUser = snapshot.targets.find((item) => item && item.id === 'newgame.set_current_user' && item.visible === true);
            return !!setCurrentUser;
          },
        });
      } catch (err) {
        const failedSnapshot = await getSnapshot(hostPage, 'host');
        const failedToggle = failedSnapshot.targets.find((item) => item && item.id === 'newgame.online_multiplayer') || null;
        const failedSetCurrent = failedSnapshot.targets.find((item) => item && item.id === 'newgame.set_current_user') || null;
        const failedStartGame = failedSnapshot.targets.find((item) => item && item.id === 'newgame.start_game') || null;
        report.hostActions.push(`online toggle wait failed checked=${failedToggle ? failedToggle.checked : 'missing'} x=${failedToggle ? failedToggle.x : 'na'} y=${failedToggle ? failedToggle.y : 'na'} setCurrentUserVisible=${failedSetCurrent ? failedSetCurrent.visible : 'missing'} startGameY=${failedStartGame ? failedStartGame.y : 'missing'}`);
        throw err;
      }
    }
    await configureMultiplayerPlayerIds(hostPage, report, timeoutMs);
    await dismissPopups(hostPage, { label: 'host', timeoutMs: 8000 }).catch(() => {});
    await clickTarget(hostPage, { label: 'host', targetId: 'newgame.start_game', timeoutMs });
    const hostWorld = await waitForState(hostPage, {
      label: 'host',
      timeoutMs,
      description: 'host world loaded with multiplayer game',
      predicate: (state) => state && state.screen === 'WorldScreen' && typeof state.gameId === 'string' && state.gameId.length > 0,
    });
    report.hostState = hostWorld.state;
    report.gameId = hostWorld.state.gameId;
    const hostTurnBefore = Number(hostWorld.state.turn || 0);
    report.hostActions.push(`created multiplayer game via UI gameId=${report.gameId} turn=${hostTurnBefore}`);
    await verifyServerGamePayload(mpServer, report.gameId);
    report.hostActions.push('verified host multiplayer payload uploaded as transferable game data');

    await dismissPopups(guestPage, { label: 'guest', timeoutMs: 8000 }).catch(() => {});
    await clickTarget(guestPage, { label: 'guest', targetId: 'menu.multiplayer', timeoutMs });
    await waitForState(guestPage, {
      label: 'guest',
      timeoutMs,
      description: 'guest multiplayer screen',
      predicate: (state) => state && state.screen === 'MultiplayerScreen',
    });
    await clickTarget(guestPage, { label: 'guest', targetId: 'mp.add_game', timeoutMs });
    await waitForState(guestPage, {
      label: 'guest',
      timeoutMs,
      description: 'guest add multiplayer game screen',
      predicate: (state) => state && state.screen === 'AddMultiplayerGameScreen',
    });
    await fillTargetWithText(guestPage, {
      label: 'guest',
      targetId: 'mp.game_id_input',
      text: report.gameId,
      timeoutMs,
    });
    await clickTarget(guestPage, { label: 'guest', targetId: 'mp.save_game', timeoutMs });
    await waitForState(guestPage, {
      label: 'guest',
      timeoutMs,
      description: 'guest multiplayer list after save game id',
      predicate: (state) => state && state.screen === 'MultiplayerScreen',
    });
    await waitForGameRowOrRefresh(guestPage, 'guest', report.gameId, 30000);
    await clickTarget(guestPage, { label: 'guest', targetId: `mp.game_row.${report.gameId}`, timeoutMs });
    await clickTarget(guestPage, { label: 'guest', targetId: 'mp.join_game', timeoutMs });
    const guestWorld = await waitForState(guestPage, {
      label: 'guest',
      timeoutMs,
      description: 'guest joined world screen',
      predicate: (state) => state && state.screen === 'WorldScreen' && state.gameId === report.gameId,
    });
    report.guestState = guestWorld.state;
    report.guestActions.push(`joined multiplayer game via UI gameId=${report.gameId}`);

    const playableGuestState = await ensureGuestCanAct(guestPage, hostPage, report, 90000);
    const guestAfterTurn = await commitGuestTurn(guestPage, report, playableGuestState, 90000);
    report.guestState = guestAfterTurn;
    report.guestActions.push(
      `guest committed turn change turn=${playableGuestState.turn}->${guestAfterTurn.turn} `
      + `player=${playableGuestState.currentPlayer}->${guestAfterTurn.currentPlayer}`,
    );

    const hostReloaded = await loadLatestOnHost(hostPage, report.gameId, guestAfterTurn, 90000);
    report.hostState = hostReloaded;
    report.hostActions.push(`host loaded latest multiplayer state turn=${hostReloaded.turn} player=${hostReloaded.currentPlayer}`);
    if (Number(hostReloaded.turn) === hostTurnBefore && String(hostReloaded.currentPlayer || '') === String(hostWorld.state.currentPlayer || '')) {
      throw new Error(
        `Host multiplayer state did not move after guest action `
        + `(turn ${hostTurnBefore} player ${hostWorld.state.currentPlayer}).`,
      );
    }

    const hostToken = `clickops-host-${runId}`;
    const guestToken = `clickops-guest-${runId}`;
    await sendChatMessage(hostPage, 'host', hostToken, 30000);
    await sendChatMessage(guestPage, 'guest', guestToken, 30000);
    await waitForState(hostPage, {
      label: 'host',
      timeoutMs: 40000,
      description: 'host observed guest chat message',
      predicate: (state) => hasMessage(state, guestToken),
    });
    await waitForState(guestPage, {
      label: 'guest',
      timeoutMs: 40000,
      description: 'guest observed host chat message',
      predicate: (state) => hasMessage(state, hostToken),
    });
    report.hostActions.push('host observed bidirectional chat');
    report.guestActions.push('guest observed bidirectional chat');

    const nonFaviconFailures = report.requestFailures.filter((entry) => !/favicon\.ico$/i.test(String(entry.url || '')));
    const failures = [];
    if (report.pageErrors.length > 0) failures.push(`page errors detected: ${report.pageErrors.length}`);
    if (report.consoleErrors.length > 0) failures.push(`console errors detected: ${report.consoleErrors.length}`);
    if (nonFaviconFailures.length > 0) failures.push(`request failures detected: ${nonFaviconFailures.length}`);
    if (!report.gameId) failures.push('missing game id from clickops host flow');
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
    if (hostPage) await hostPage.screenshot({ path: hostScreenshotPath, fullPage: true }).catch(() => {});
    if (guestPage) await guestPage.screenshot({ path: guestScreenshotPath, fullPage: true }).catch(() => {});
    if (hostContext) await hostContext.close().catch(() => {});
    if (guestContext) await guestContext.close().catch(() => {});
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
