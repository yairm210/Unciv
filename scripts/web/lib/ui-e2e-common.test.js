const test = require('node:test');
const assert = require('node:assert/strict');

const { waitForUiProbeResult } = require('./ui-e2e-common');

class FakePage {
  constructor(states, tick) {
    this.states = states;
    this.tick = tick;
    this.evaluateCalls = 0;
  }

  async evaluate(fn, arg) {
    const state = this.states[Math.min(this.evaluateCalls, this.states.length - 1)] || {};
    this.evaluateCalls += 1;

    const previousWindow = global.window;
    const previousDocument = global.document;
    global.window = {
      __uncivUiProbeState: state.state || null,
      __uncivUiProbeError: state.error || null,
      __uncivUiProbeResultJson: state.json || null,
      __uncivUiProbeStepLogJson: state.steps || null,
      __uncivRunnerSelected: state.runnerSelected || null,
      __uncivRunnerReason: state.runnerReason || null,
      __uncivBootstrapTraceJson: state.bootstrapTraceJson || null,
      __uncivBootStarted: state.bootInvoked === true,
      __uncivUiProbeBootInvoked: state.bootInvoked === true,
      location: { search: state.search || '' },
      main: () => {},
    };
    global.document = {
      readyState: state.readyState || 'complete',
    };

    try {
      return arg === undefined ? fn() : fn(arg);
    } finally {
      global.window = previousWindow;
      global.document = previousDocument;
    }
  }

  async waitForTimeout(ms) {
    this.tick(ms);
  }
}

async function withFakeClock(fn) {
  const originalNow = Date.now;
  let now = 0;
  Date.now = () => now;
  try {
    return await fn((ms) => {
      now += ms;
    });
  } finally {
    Date.now = originalNow;
  }
}

test('waitForUiProbeResult returns the normal in-loop result payload', async () => {
  await withFakeClock(async (tick) => {
    const page = new FakePage([
      {
        state: 'done',
        json: JSON.stringify({ passed: true, notes: 'ready' }),
        steps: JSON.stringify({ steps: [{ state: 'done' }] }),
        runnerSelected: 'uiProbe',
        runnerReason: 'uiProbe query flag enabled',
        search: '?uiProbe=1',
        bootInvoked: true,
      },
    ], tick);

    const probe = await waitForUiProbeResult(page, 'unit', 1000, null);
    assert.equal(probe.result.passed, true);
    assert.equal(probe.result.notes, 'ready');
    assert.equal(probe.stepLog.steps[0].state, 'done');
  });
});

test('waitForUiProbeResult accepts a result published at the timeout boundary', async () => {
  const originalStdoutWrite = process.stdout.write;
  process.stdout.write = () => true;
  try {
    await withFakeClock(async (tick) => {
      const page = new FakePage([
        {
          state: 'running:war_deep:turns-b',
          runnerSelected: 'uiProbe',
          runnerReason: 'uiProbe query flag enabled',
          search: '?uiProbe=1',
          bootInvoked: true,
        },
        {
          state: 'done',
          json: JSON.stringify({ passed: true, notes: 'late but valid' }),
          steps: JSON.stringify({ steps: [{ state: 'done', note: 'finished' }] }),
          runnerSelected: 'uiProbe',
          runnerReason: 'uiProbe query flag enabled',
          search: '?uiProbe=1',
          bootInvoked: true,
        },
      ], tick);

      const probe = await waitForUiProbeResult(page, 'unit', 100, null);
      assert.equal(probe.result.passed, true);
      assert.equal(probe.result.notes, 'late but valid');
      assert.equal(probe.stepLog.steps[0].note, 'finished');
    });
  } finally {
    process.stdout.write = originalStdoutWrite;
  }
});
