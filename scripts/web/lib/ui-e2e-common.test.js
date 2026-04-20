const test = require('node:test');
const assert = require('node:assert/strict');

const { getActionableRequestFailures, startMainOnce, waitForUiProbeResult, waitForUiProbeStart } = require('./ui-e2e-common');

class FakePage {
  constructor(states, tick) {
    this.states = states;
    this.tick = tick;
    this.evaluateCalls = 0;
    this.mainCalls = 0;
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
      __uncivBootInProgress: state.bootInProgress === true,
      __uncivBootToken: state.bootToken || null,
      __uncivBootFailures: Number(state.bootFailures || 0),
      __uncivUiProbeBootInvoked: state.bootInvoked === true,
      location: { search: state.search || '' },
      main: () => {
        this.mainCalls += 1;
      },
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

test('startMainOnce respects the generated auto-bootstrap markers before falling back to manual main()', async () => {
  await withFakeClock(async (tick) => {
    const page = new FakePage([
      {
        runnerSelected: 'none',
        runnerReason: 'bootstrap-start',
        search: '?uiProbe=1',
      },
    ], tick);

    await startMainOnce(page, 1000, '__uncivUiProbeBootInvoked');
    assert.equal(page.mainCalls, 0);
  });
});

test('waitForUiProbeStart tolerates the bootstrap-start placeholder before uiProbe selection', async () => {
  await withFakeClock(async (tick) => {
    const page = new FakePage([
      {
        runnerSelected: 'none',
        runnerReason: 'bootstrap-start',
        search: '?uiProbe=1',
        bootInvoked: true,
      },
      {
        runnerSelected: 'uiProbe',
        runnerReason: 'uiProbe query flag enabled',
        search: '?uiProbe=1',
        bootInvoked: true,
      },
    ], tick);

    await waitForUiProbeStart(page, 'unit', 1000);
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

test('getActionableRequestFailures ignores aborted blob URLs', () => {
  const failures = getActionableRequestFailures([
    {
      label: 'host',
      url: 'blob:http://127.0.0.1:18080/example',
      error: 'net::ERR_ABORTED',
    },
    {
      label: 'host',
      url: 'http://127.0.0.1:18080/assets/Icons.png',
      error: 'net::ERR_ABORTED',
    },
    {
      label: 'host',
      url: 'http://127.0.0.1:18080/api/save',
      error: 'net::ERR_CONNECTION_REFUSED',
    },
  ]);

  assert.deepEqual(failures, [
    {
      label: 'host',
      url: 'http://127.0.0.1:18080/api/save',
      error: 'net::ERR_CONNECTION_REFUSED',
    },
  ]);
});

test('getActionableRequestFailures keeps non-aborted blob failures', () => {
  const failures = getActionableRequestFailures([
    {
      label: 'host',
      url: 'blob:http://127.0.0.1:18080/example',
      error: 'net::ERR_FAILED',
    },
  ]);

  assert.deepEqual(failures, [
    {
      label: 'host',
      url: 'blob:http://127.0.0.1:18080/example',
      error: 'net::ERR_FAILED',
    },
  ]);
});
