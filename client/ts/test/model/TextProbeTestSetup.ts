import assert from 'node:assert';

// Mock a few things. We don't need the whole DOM,
// just location and localStorage for `settings.ts`.
const mock = global as any;
mock.window = mock;
mock.location = { search: '' };
mock.localStorage = { getItem: () => null, setItem: () => undefined, };

import { Node } from './setupTestModalEnv';
import SpanFlasher from '../../src/model/SpanFlasher';

interface Span {
  lineStart: number;
  colStart: number;
  lineEnd: number;
  colEnd: number;
}

const mkNode = (type: string, start: number, end: number, props: Node['props'] = {}): Node => ({ type, start, end, props, });
const nodes: { [id: string]: Node } = {
  add: mkNode('Add', (1 << 12) + 1, (1 << 12) + 5, { lhs: () => nodes.var, rhs: () => nodes.num, }),
  var: mkNode('Var', (1 << 12) + 1, (1 << 12) + 2),
  num: mkNode('Num', (1 << 12) + 4, (1 << 12) + 5),

  call: mkNode('Call', (3 << 12) + 1, (5 << 12) + 2, { arg: () => nodes.bool }),
  bool: mkNode('Bool', (4 << 12) + 3, (4 << 12) + 7),
};
const sourceDocument = {
  lines: [
    // This line must be kept in synk with the line/col references above/below
    `a + 2 // [[Add.lhs]]`,
    ``,
    `f(     // [[$c:=Call]] [[$ca:=Call.arg]]`,
    `  true // [[$b:=Bool]] [[Call.arg=Bool]] [[Bool=$c.arg]]`,
    `)`,
  ],
  nodes: Object.values(nodes),
}

/**
 * Hard-coded locations of all hoverable text probe locations in the sourceDocument above.
 */
const textProbeLocations = {
  [`txtprobe:Add`]:           { start: (1 << 12) + 12, end: (1 << 12) + 14 },
  [`txtprobe:Add.lhs`]:       { start: (1 << 12) + 16, end: (1 << 12) + 18 },

  [`txtprobe:$c`]:            { start: (3 << 12) + 13, end: (3 << 12) + 14 },
  [`txtprobe:$c:=Call`]:      { start: (3 << 12) + 17, end: (3 << 12) + 20 },

  [`txtprobe:$ca`]:           { start: (3 << 12) + 26, end: (3 << 12) + 28 },
  [`txtprobe:$ca->Call`]:     { start: (3 << 12) + 31, end: (3 << 12) + 34 },
  [`txtprobe:$ca->Call.arg`]: { start: (3 << 12) + 36, end: (3 << 12) + 38 },

  [`txtprobe:$b`]:            { start: (4 << 12) + 13, end: (4 << 12) + 14 },
  [`txtprobe:$b:=Bool`]:      { start: (4 << 12) + 17, end: (4 << 12) + 20 },

  [`txtprobe:Call`]:          { start: (4 << 12) + 26, end: (4 << 12) + 29 },
  [`txtprobe:Call.arg`]:      { start: (4 << 12) + 31, end: (4 << 12) + 33 },
  [`txtprobe:Call.arg=Bool`]: { start: (4 << 12) + 35, end: (4 << 12) + 38 },

  [`txtprobe:Bool`]:          { start: (4 << 12) + 44, end: (4 << 12) + 47 },
  [`txtprobe:Bool->$c`]:      { start: (4 << 12) + 49, end: (4 << 12) + 50 },
  [`txtprobe:Bool->$c.arg`]:  { start: (4 << 12) + 52, end: (4 << 12) + 54 },
};

// Merge of node locations and text probe locations
const expectedFlashLocations: { [tag: string]: { start: number, end: number } } = { ...textProbeLocations };
Object.values(nodes).forEach(node => {
  expectedFlashLocations[`node:${node.type}`] = node;
});

const assertFlashedLocations = (actual: Span[], expectedLocs: string[]) => {
  const flashedLocations: string[] = [];
  actual.forEach((span) => {
    const start = (span.lineStart << 12) + span.colStart;
    const end = (span.lineEnd << 12) + span.colEnd;

    const expLoc = Object.entries(expectedFlashLocations).find(([k, v]) => v.start === start && v.end === end);
    if (expLoc) {
      flashedLocations.push(expLoc[0]);
    } else {
      console.log('Flash [start:end]=[', start, ':', end, ']');
      console.log('Available flash locs:');
      Object.entries(expectedFlashLocations).forEach(([k, v]) => console.log(k, v));
      assert.fail(`Unexpected flash location: ${JSON.stringify(span)}`);
    }
  });

  flashedLocations.sort();
  assert.deepStrictEqual(flashedLocations, expectedLocs);
}

const setupSpanFlasher = (): { flasher: SpanFlasher, flashes: Span[] } => {
  let flashes: Span[] = [];
  const flasher: SpanFlasher = {
    quickFlash: (spans) => { flashes.push(...spans); },
    flash: (spans, removeHighlightsOnMove) => { flashes.push(...spans); },
    clear: () => { flashes.length = 0 },
  };
  return { flasher, flashes };
}

export { nodes, sourceDocument, textProbeLocations, Span, expectedFlashLocations, assertFlashedLocations, setupSpanFlasher }
