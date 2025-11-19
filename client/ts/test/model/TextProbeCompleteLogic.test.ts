/// <reference types="node" />
import { describe, it } from 'node:test';
import assert from 'node:assert';

// Import this early, it mocks things as a side effect
import { assertFlashedLocations, nodes, setupSpanFlasher, sourceDocument, textProbeLocations } from './TextProbeTestSetup';

// Then other imports
import { CompletionItem, createTextProbeCompleteLogic } from '../../src/model/TextProbeCompleteLogic';
import { createTextProbeEvaluator } from '../../src/model/TextProbeEvaluator';
import setupTestModalEnv from './setupTestModalEnv';

describe('TextProbeCompleteLogic', () => {
  const setup = () => {
    const { flasher, flashes } = setupSpanFlasher();
    const env = setupTestModalEnv({
      nodes: Object.values(nodes),
      src: sourceDocument.lines.join('\n'),
    });
    const logic = createTextProbeCompleteLogic({ env, flasher })

    return { flashes, flasher, env, logic };
  }

  const setupAndComplete = async (key: keyof typeof textProbeLocations, colOffset = 0) => {
    const data = setup();
    const where = textProbeLocations[key].start;
    const completeRes = await data.logic.complete(createTextProbeEvaluator(data.env), where >>> 12, (where & 0xFFF) + colOffset);
    if (!completeRes) assert.fail('Complete failed');
    return { ...data, completeRes };
  }

  const assertCompletionItems = (actualItems: CompletionItem[], expected: string[]) => {
    const actual = actualItems.map(x => x.label);
    if (expected.length != actualItems.length) {
      assert.fail(`Unexpected number of completion items. Actual: ${actual.join(', ')}`)
    }
    if (!expected.every(val => actual.includes(val))) {
      assert.fail(`Completion items did not contain all expected labels. Actual: ${actual.join(', ')}`);
    }
  }

  const vars = ['$c', '$ca', '$b'];
  const simpleCompleteToItemsTestCases: Partial<Record<keyof typeof textProbeLocations, string[]>> = {
    "txtprobe:Add": ['Program', 'Add', 'Var', 'Num', ...vars],
    "txtprobe:Add.lhs": ['lhs', 'rhs'],
    "txtprobe:$b:=Bool": ['Program', 'Bool', 'Call'],
    "txtprobe:$c": [],
    "txtprobe:$ca": [],
    "txtprobe:$ca->Call": ['Program', 'Call'],
    "txtprobe:$ca->Call.arg": ['arg'],
    "txtprobe:Call": ['Program', 'Call', 'Bool', ...vars],
    "txtprobe:Call.arg": ['arg'],
    "txtprobe:Bool": ['Program', 'Call', 'Bool', ...vars],
    "txtprobe:Bool->$c": ['Bool'],
    "txtprobe:Bool->$c.arg": ['arg'],
    "txtprobe:Program.call.arg": ['arg'],
  };

  Object.entries(simpleCompleteToItemsTestCases).forEach(([key, vals]) => {
    it(`completes ${key} correctly`, async () => {
      if (!vals.length) {
        // Expect no completions
        const data = setup();
        const where = textProbeLocations[key].start;
        const completeRes = await data.logic.complete(createTextProbeEvaluator(data.env), where >>> 12, (where & 0xFFF));
        if (completeRes) assert.fail('Complete succeeded, expected it to fail');
      } else {
        // Expect some completions
        const { completeRes } = await setupAndComplete(key as any);
        assertCompletionItems(completeRes.items, vals);
      }
    });
  });

  it(`includes variables on right side after dollar sign`, async () => {
    const { completeRes } = await setupAndComplete(`txtprobe:Bool->$c`, 1);
    assertCompletionItems(completeRes.items, vars);
  });

  it(`flashes on prop completion`, async () => {
    const { flashes } = await setupAndComplete(`txtprobe:Call.arg`);
    assertFlashedLocations(flashes, [`node:Call`, 'txtprobe:Call']);
  });

  it(`flashes on node focus`, async () => {
    const { flashes, completeRes } = await setupAndComplete(`txtprobe:Add`);
    assertFlashedLocations(flashes, []);

    (window as any).OnCompletionItemFocused(completeRes.items.find(x => x.label === 'Add'));
    assertFlashedLocations(flashes, ['node:Add']);
    (window as any).OnCompletionItemListClosed();
    assertFlashedLocations(flashes, []);

    (window as any).OnCompletionItemFocused(completeRes.items.find(x => x.label === 'Num'));
    assertFlashedLocations(flashes, ['node:Num']);
    (window as any).OnCompletionItemListClosed();
    assertFlashedLocations(flashes, []);
  });
});
