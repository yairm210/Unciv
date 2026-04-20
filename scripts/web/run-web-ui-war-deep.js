const { runWarProbe } = require('./lib/ui-war-runner');

const timeoutMs = Number(process.env.WEB_UI_WAR_DEEP_TIMEOUT_MS || '120000');

runWarProbe({
  role: 'war_deep',
  outputFilename: 'web-ui-war-deep-result.json',
  screenshotFilename: 'web-ui-war-deep-latest.png',
  timeoutMs,
  assertions: [
    (result, _report, splitForces) => {
      if (!result) return 'missing probe result payload';
      if (result.passed !== true) return `probe reported passed=false: ${result.notes || 'no notes'}`;
      if (result.diplomacyStateTransitionsObserved !== true) return 'war_deep did not observe diplomacy transitions';
      const forces = splitForces(result.forcesObserved);
      if (forces.length < 2) {
        return `war_deep requires at least 2 force types (forces=${result.forcesObserved || ''})`;
      }
      return null;
    },
  ],
}).catch((err) => {
  process.stderr.write(`${err && err.stack ? err.stack : String(err)}\n`);
  process.exit(1);
});
