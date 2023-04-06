import { diffArray } from '../../dependencies/onp/index';
import { RpcBodyLine, TestCase, NestedTest } from '../../protocol';
import { rpcLinesToAssertionLines } from './rpcBodyToAssertionLine';
import { NestedTestResponse, TestStatus } from './TestManager';

const flattenLines = (lines: RpcBodyLine[], callback: (path: number[], line: RpcBodyLine) => void) => {
  lines = rpcLinesToAssertionLines(lines);
  lines.forEach((line, idx) => {
    switch (line.type) {
      case 'arr': {
        flattenLines(line.value, (path, line) => callback([idx, ...path], line));
        break;
      }
      default: {
        callback([idx], line);
        break;
      }
    }
  });
};
const createEncounterCounter = (): ((id: number[]) => number) => {
  const encounterCounter: { [path: string]: number } = {};
  return (path) => {
    const encoded = JSON.stringify(path);
    const prevEncounters = encounterCounter[encoded] ?? 0;
    encounterCounter[encoded] = prevEncounters + 1;
    return prevEncounters;
  };
}
const flattenNestedTestResponses = (tests: NestedTestResponse[], callback: (path: number[], line: RpcBodyLine) => void) => {
  const encounterCounter = createEncounterCounter();
  tests.forEach(test => {
    if (test.result === 'could-not-find-node') {
      return;
    }
    const encIndex = encounterCounter(test.path);

    flattenLines(test.result.body, (path, line) => callback([...test.path, encIndex, ...path], line));
    flattenNestedTestResponses(test.result.nested, (path, line) => callback([...test.path, encIndex, ...path], line));
  })
}

interface FlattenedLine {
  path: number[];
  line: RpcBodyLine;
  side: 'left'|'right';
  toString: () => string;
}
const createFlattenedLine = (path: number[], line: RpcBodyLine, side: 'left'|'right') => {
  return {
    path, line, side,
    toString: () => `${JSON.stringify(path.slice(0, path.length - 1))} - ${JSON.stringify(line)}`,
  };
}
const compareTestResult = (assertionType: 'IDENTITY' | 'SET', tcase: NestedTestResponse, evalRes: NestedTestResponse): TestStatus => {

  // const flattenedExpected: { [id: string]: FlattenedLine } = {};
  const flattenedExpected: FlattenedLine[] = [];
  const addExpected = (path: number[], line: RpcBodyLine) => flattenedExpected.push(createFlattenedLine(path, line, 'left'));
  if (tcase.result !== 'could-not-find-node') {
    flattenLines(tcase.result.body, addExpected);
    flattenNestedTestResponses(tcase.result.nested, addExpected);
  }



  // const flattenedActual: { [id: string]: RpcBodyLine } = {};
  const flattenedActual: FlattenedLine[] = [];
  const addActual = (path: number[], line: RpcBodyLine) => flattenedActual.push(createFlattenedLine(path, line, 'right'));
  // const addActual = (path: number[], line: RpcBodyLine) => flattenedActual[JSON.stringify(path)] = line;
  if (evalRes.result !== 'could-not-find-node') {
    flattenLines(evalRes.result.body, addActual);
    flattenNestedTestResponses(evalRes.result.nested, addActual);
  }
  let someErr = false;

  if (assertionType === 'SET') {
    const sorter = (a: FlattenedLine, b: FlattenedLine) => {

      const lhs = a.toString();
      const rhs = b.toString();
      if (lhs == rhs) {
        return 0;
      }
      return lhs < rhs ? -1 : 1;
    }
    flattenedExpected.sort(sorter);
    flattenedActual.sort(sorter);
  }
  const retExp: TestStatus['expectedMarkers'] = {};
  const retAct: TestStatus['actualMarkers'] = {};

  const diffResult = diffArray(flattenedExpected, flattenedActual);
  diffResult.results.forEach(result => {
    if (!result.state) {
      return; // No diff, no marker to add
    }
    someErr = true;
    if (result.state === -1) {
      // Expected line not matched
      retExp[JSON.stringify(result.left.path)] = 'error';
    } else {
      // Unexpected actual line
      retAct[JSON.stringify(result.right.path)] = 'error';
    }
  });

  return {
    overall: someErr ? 'error' : 'ok',
    expectedMarkers: retExp,
    actualMarkers: retAct,
  };
}

// export { TestComparisonReport }
export default compareTestResult;
