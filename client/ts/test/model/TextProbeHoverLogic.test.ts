/// <reference types="node" />
import { describe, it } from 'node:test';
import assert from 'node:assert';

// Import this early, it mocks things as a side effect
import { assertFlashedLocations, nodes, setupSpanFlasher, sourceDocument, textProbeLocations } from './TextProbeTestSetup';

// Then other imports
import { createProbeHoverLogic } from '../../src/model/TextProbeHoverLogic';
import { createTextProbeEvaluator } from '../../src/model/TextProbeEvaluator';
import setupTestModalEnv from './setupTestModalEnv';

describe('TextProbeHoverLogic', () => {
  const setup = () => {
    const { flasher, flashes } = setupSpanFlasher();
    const env = setupTestModalEnv({
      nodes: Object.values(nodes),
      src: sourceDocument.lines.join('\n'),
    });
    const logic = createProbeHoverLogic({ env, flasher })

    return { flashes, flasher, env, logic };
  }

  const setupAndHover = async (key: keyof typeof textProbeLocations) => {
    const data = setup();
    const where = textProbeLocations[key].start;
    const hoverRes = await data.logic.hover(createTextProbeEvaluator(data.env), where >>> 12, where & 0xFFF);
    if (!hoverRes) assert.fail('Hover failed');
    return { ...data, hoverRes };
  }

  const simpleHoverToFlashTestCases: Partial<Record<keyof typeof textProbeLocations, string[]>> = {
    "txtprobe:Add": ['node:Add'],
    "txtprobe:Add.lhs": ['node:Var'],
    "txtprobe:$b:=Bool": ['node:Bool'],
    "txtprobe:$c": ['node:Call'],
    "txtprobe:$ca": ['node:Bool'],
    "txtprobe:$ca->Call": ['node:Call'],
    "txtprobe:$ca->Call.arg": ['node:Bool'],
    "txtprobe:Call": ['node:Call'],
    "txtprobe:Call.arg": ['node:Bool'],
    "txtprobe:Bool": ['node:Bool'],
    "txtprobe:Bool->$c": ['node:Call'],
    "txtprobe:Bool->$c.arg": ['node:Bool'],
  };

  Object.entries(simpleHoverToFlashTestCases).forEach(([key, vals]) => {
    it(`hovers ${key} correctly`, async () => {
      const { flashes } = await setupAndHover(key as any);
      assertFlashedLocations(flashes, [...vals, key /* each key should flash their own location too */ ])
    });
  });

  it('Does no flashing on rhs of Call.arg=', async () => {
    const { flashes, } = await setupAndHover('txtprobe:Call.arg=Bool')
    // On a probe like [[Call.arg=Bool]], the right-hand side is interpreted as a String
    // It doesn't make much sense if it flashed a node location, even if the String happens
    // to be a valid query.
    assert.equal(flashes.length, 0);
  });
});
