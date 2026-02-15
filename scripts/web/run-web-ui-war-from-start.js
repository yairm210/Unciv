const { runWarProbe } = require('./lib/ui-war-runner');

const timeoutMs = Number(process.env.WEB_UI_WAR_FROM_START_TIMEOUT_MS || '120000');

runWarProbe({
  role: 'war_from_start',
  outputFilename: 'web-ui-war-from-start-result.json',
  screenshotFilename: 'web-ui-war-from-start-latest.png',
  timeoutMs,
  assertions: [
    (result, _report, splitForces) => {
      if (!result) return 'missing probe result payload';
      if (result.passed !== true) return `probe reported passed=false: ${result.notes || 'no notes'}`;
      if (result.warDeclaredObserved !== true) return 'war_from_start did not observe war declaration';
      if (result.combatExchangesObserved !== true) return 'war_from_start did not observe combat exchange';
      if (result.multiTurnProgressObserved !== true) return 'war_from_start did not observe multi-turn progress';
      const forces = splitForces(result.forcesObserved);
      if (!forces.includes('warrior') && !forces.includes('melee')) {
        return `war_from_start missing warrior/melee force token (forces=${result.forcesObserved || ''})`;
      }
      return null;
    },
  ],
}).catch((err) => {
  process.stderr.write(`${err && err.stack ? err.stack : String(err)}\n`);
  process.exit(1);
});
