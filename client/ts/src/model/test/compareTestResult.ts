import { rpcLinesToAssertionLines } from './rpcBodyToAssertionLine';
import TestCase, { AssertionLine } from './TestCase';

interface LocatorDiffSet {
  [index: number]: 'same-result-but-different-steps' | 'different-result' | undefined;
}

type OutputDiffReport =
    'pass'
  | 'different-number-of-lines'
  | {
    type: 'difference-at-lines',
    invalid: number[];
    unmatchedValid: number[];
  }
  ;
type LocatorDiffReport =
    'pass'
  | 'different-number-of-locators'
  | LocatorDiffSet
  ;

type TestComparisonReport =
    'pass'
  | 'failed-eval'
  | {
    output: OutputDiffReport;
    sourceLocators: LocatorDiffReport;
    attrArgLocators: LocatorDiffReport;
    outputLocators: LocatorDiffReport;
  }
  ;


// TODO return type, append | 'same-output-but-different-locators'
const compareTestResult = (tc: TestCase, actual: { locator: NodeLocator, lines: AssertionLine[] }): TestComparisonReport => {

  const argValueToNodeLocator = (val: ArgValue): NodeLocator | null => typeof val === 'object' ? val : null;
  const forEachLocator = (locator: NodeLocator, callback: (locator: NodeLocator) => void) => {
    callback(locator);
    locator.steps.forEach(step => {
      if (step.type === 'nta') {
        step.value.args.forEach(arg => {
          arg.args?.forEach(val => {
            const vv = argValueToNodeLocator(val.value);
            if (vv) {
              console.log('.. > recurse')
              forEachLocator(vv, callback);
            }
          });
        });
      }
    });
  };
  const flattenLocator = (locator: NodeLocator): NodeLocator[] => {
    const res: NodeLocator[] = [];
    forEachLocator(locator, loc => res.push(loc));
    return res;
  }


  const createLocatorComparison = (expected: NodeLocator[], actual: NodeLocator[]): LocatorDiffReport => {
    const flattenedExpected = expected.reduce<NodeLocator[]>((acc, add) => acc.concat(flattenLocator(add)), []);
    const flattenedActual = actual.reduce<NodeLocator[]>((acc, add) => acc.concat(flattenLocator(add)), []);
    if (flattenedExpected.length != flattenedActual.length) {
      return 'different-number-of-locators';
    }

    const diffIndexes: LocatorDiffSet = {};
    flattenedExpected.forEach((exp, idx) => {
      if (JSON.stringify(exp) !== JSON.stringify(flattenedActual[idx])) {
        if (JSON.stringify(exp.result) === JSON.stringify(flattenedActual[idx].result)) {
          diffIndexes[idx] = 'same-result-but-different-steps';
        } else {
          diffIndexes[idx] = 'different-result';
        }
      }
    })
    if (Object.keys(diffIndexes).length === 0) {
      return 'pass';
    }
    return diffIndexes;
  }

  const getLocatorsFromAssertionLines = (lines: AssertionLine[]): NodeLocator[] => {
    const res: NodeLocator[] = [];
    lines.forEach(line => {
      if (Array.isArray(line)) {
        res.push(...getLocatorsFromAssertionLines(line));
      } else if (typeof line === 'object') {
        res.push(line.robust);
      }
    })
    return res;
  };

  const getFullReport = (output: OutputDiffReport): TestComparisonReport => {
    const report: TestComparisonReport = ({
      output,
      sourceLocators: createLocatorComparison([tc.locator.robust], [actual.locator]),
      attrArgLocators: 'pass', // TODO server should respond here with updated attr as well
      outputLocators: tc.assert.type === 'smoke'
      ? 'pass'
      : createLocatorComparison(
          getLocatorsFromAssertionLines(rpcLinesToAssertionLines(tc.assert.lines)),
          getLocatorsFromAssertionLines(actual.lines)
        ),
    });
    if (Object.values(report).every(ent => ent === 'pass')) {
      return 'pass';
    }
    return report;
  }

  const stripStepsFromAssertionLine = (line: AssertionLine): AssertionLine => {
    if (typeof line === 'string') {
      return line;
    }
    if (Array.isArray(line)) {
      return line.map(stripStepsFromAssertionLine);
    }
    return {
      robust: { result: line.robust.result, steps: [], },
      naive: { result: line.naive.result, steps: [], }
    };
  };

  const actualLines = actual.lines;
  switch (tc.assert.type) {
    case 'identity': {
      const expectedOutputStrings = rpcLinesToAssertionLines(tc.assert.lines).map(stripStepsFromAssertionLine).map(l => JSON.stringify(l));
      const actualOutputStrings = actualLines.map(stripStepsFromAssertionLine).map(l => JSON.stringify(l));
      const unmatchedValidLines: number[] = [];
      const invalidLines: number[] = [];
      let pushValidLines = true;

      let expIdx = 0;
      let actIdx = 0;
      while (expIdx < expectedOutputStrings.length && actIdx < actualOutputStrings.length) {
        const e = expectedOutputStrings[expIdx];
        const a = actualOutputStrings[actIdx];
        // const e = JSON.stringify(expected[expIdx]);
        // const a = JSON.stringify(actualLines[actIdx]);

        if (e === a) {
          ++expIdx;
          ++actIdx;
        } else {
          // invalidLines.push(actIdx);

          const nextExp = (offset: number) => (expIdx + offset) < expectedOutputStrings.length ? expectedOutputStrings[expIdx + offset] : null;
          const nextAct = (offset: number) => (actIdx + offset) < actualOutputStrings.length ? actualOutputStrings[actIdx + offset] : null;
          let foundMatch = false;
          for (let offset = 1; offset <= 3; ++offset) {
            if (e == nextAct(offset)) {
              console.log('found e->', offset, ' compromise at ', expIdx, '/', actIdx);
              // unmatchedValidLines.push(expIdx);
              for (let report = 0; report < offset; ++report) {
                invalidLines.push(actIdx + report);
              }
              actIdx += offset;
              foundMatch = true;
              ++actIdx;
              ++expIdx;
              break;
            }
            if (a == nextExp(offset)) {
              console.log('Found act(1)->', offset, ' compromise at ', expIdx, '/', actIdx);
              // console.log('the conte')
              // invalidLines.push(actIdx);
              for (let report = 0; report < offset; ++report) {
                unmatchedValidLines.push(expIdx + report);
              }
              expIdx += offset;
              ++actIdx;
              foundMatch = true;
              ++actIdx;
              ++expIdx
              break;
            }
          }
          if (!foundMatch) {
            console.log('couldnt find compromise at ', expIdx, '/', actIdx, '; reporting both');
            invalidLines.push(actIdx);
            unmatchedValidLines.push(expIdx);
            ++actIdx;
            ++expIdx;
          }
        }
      }
      if (invalidLines.length !== 0) {
        while (actIdx < actualLines.length) {
          invalidLines.push(actIdx);
          ++actIdx;
        }
        while (expIdx < expectedOutputStrings.length) {
          unmatchedValidLines.push(expIdx);
          ++expIdx;
        }
        return getFullReport({
          type: 'difference-at-lines',
          invalid: invalidLines,
          unmatchedValid: unmatchedValidLines
        });
      }
      if (expectedOutputStrings.length != actualOutputStrings.length) {
        console.log('diff length, exp:', expectedOutputStrings.length, ', actualLines:', actualLines.length);
        return getFullReport('different-number-of-lines');
      }
      return getFullReport('pass');
    }

    case 'set': {
      const expected = rpcLinesToAssertionLines(tc.assert.lines);

      if (expected.length != actualLines.length) {
        return getFullReport('different-number-of-lines');
      }
      const count = (lines: AssertionLine[]): { [id: string]: number } => {
        const ret: { [id: string]: number } = {};
        lines.forEach(line => {
          const str = JSON.stringify(line);
          ret[str] = (ret[str] || 0) + 1;
        });
        return ret;
      };

      const expCount = count(expected);
      const actCount = count(actualLines);

      const invalidLines: number[] = [];
      actualLines.forEach((line, lineIdx) => {
        const key = JSON.stringify(line);

        if (expCount[key] !== actCount[key]) {
          invalidLines.push(lineIdx);
        }
      });

      return getFullReport(invalidLines.length === 0 ? 'pass' : { type: 'difference-at-lines', invalid: invalidLines, unmatchedValid: [] });
    }

    case 'smoke':
    default: {
      // TODO check if output contains error or not
      return 'pass';
    }
  }
}

export { TestComparisonReport }
export default compareTestResult;
