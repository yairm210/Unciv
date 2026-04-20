const { runWarProbe } = require('./lib/ui-war-runner');

const timeoutMs = Number(process.env.WEB_UI_WAR_PREWORLD_TIMEOUT_MS || '120000');

runWarProbe({
  role: 'war_preworld',
  outputFilename: 'web-ui-war-preworld-result.json',
  screenshotFilename: 'web-ui-war-preworld-latest.png',
  timeoutMs,
  assertions: [
    (result, _report, splitForces) => {
      if (!result) return 'missing probe result payload';
      if (result.passed !== true) return `probe reported passed=false: ${result.notes || 'no notes'}`;
      if (result.warDeclaredObserved !== true) return 'war_preworld did not observe war declaration';
      if (result.cityCaptureObserved !== true) return 'war_preworld did not observe city capture';
      if (result.peaceObserved !== true) return 'war_preworld did not observe peace';
      if (result.diplomacyStateTransitionsObserved !== true) return 'war_preworld did not observe diplomacy transitions';
      if (result.multiTurnProgressObserved !== true) return 'war_preworld did not observe post-peace turn progress';
      const forces = splitForces(result.forcesObserved);
      if (!forces.includes('warrior') && !forces.includes('melee')) {
        return `war_preworld missing warrior/melee force token (forces=${result.forcesObserved || ''})`;
      }
      return null;
    },
  ],
}).catch((err) => {
  process.stderr.write(`${err && err.stack ? err.stack : String(err)}\n`);
  process.exit(1);
});
