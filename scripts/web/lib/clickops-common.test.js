const test = require('node:test');
const assert = require('node:assert/strict');

const { waitForClickOpsStart } = require('./clickops-common');

class FakePage {
  constructor(states, tick) {
    this.states = states;
    this.tick = tick;
    this.evaluateCalls = 0;
  }

  async evaluate(fn) {
    const state = this.states[Math.min(this.evaluateCalls, this.states.length - 1)] || {};
    this.evaluateCalls += 1;

    const previousWindow = global.window;
    global.window = {
      location: { search: state.search || '', href: `http://127.0.0.1:18080/index.html${state.search || ''}` },
      __uncivRunnerSelected: state.runnerSelected || null,
      __uncivRunnerReason: state.runnerReason || null,
      __uncivClickOpsStateJson: state.clickOpsState || null,
      __uncivClickOpsTargetsJson: state.clickOpsTargets || null,
      __uncivClickOpsError: state.clickOpsError || null,
      __uncivBootStarted: state.bootInvoked === true,
      __uncivClickOpsBootInvoked: state.bootInvoked === true,
      main: () => {},
    };

    try {
      return fn();
    } finally {
      global.window = previousWindow;
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

test('waitForClickOpsStart tolerates the bootstrap-start placeholder before clickOps selection', async () => {
  await withFakeClock(async (tick) => {
    const page = new FakePage([
      {
        runnerSelected: 'none',
        runnerReason: 'bootstrap-start',
        search: '?clickOps=1',
        bootInvoked: true,
      },
      {
        runnerSelected: 'clickOps',
        runnerReason: 'clickOps query flag enabled',
        search: '?clickOps=1',
        bootInvoked: true,
      },
    ], tick);

    await waitForClickOpsStart(page, 'unit', 1000);
    assert.ok(true);
  });
});

