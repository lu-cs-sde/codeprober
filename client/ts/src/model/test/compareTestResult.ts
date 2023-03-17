import rpcBodyToTestBody, { rpcLinesToAssertionLines } from './rpcBodyToAssertionLine';
import TestCase, { AssertionLine } from './TestCase';

type TestComparisonReport = 'pass'
  | 'invalid-number-of-lines'
  | {
      type: 'error-at-lines',
      invalid: number[];
      unmatchedValid: number[];
    }
  ;

// TODO return type, append | 'same-output-but-different-locators'
const compareTestResult = (tc: TestCase, actual: AssertionLine[]): TestComparisonReport => {

  switch (tc.assert.type) {
    case 'identity': {
      const expected = rpcLinesToAssertionLines(tc.assert.lines);

      const unmatchedValidLines: number[] = [];
      const invalidLines: number[] = [];
      let pushValidLines = true;

      let expIdx = 0;
      let actIdx = 0;
      while (expIdx < expected.length && actIdx < actual.length) {
        const e = JSON.stringify(expected[expIdx]);
        const a = JSON.stringify(actual[actIdx]);

        if (e === a) {
          ++expIdx;
          ++actIdx;
        } else {
          // invalidLines.push(actIdx);

          const nextExp = (offset: number) => (expIdx + offset) < expected.length ? JSON.stringify(expected[expIdx + offset]) : null;
          const nextAct = (offset: number) => (actIdx + offset) < actual.length ? JSON.stringify(actual[actIdx + offset]) : null;
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
        while (actIdx < actual.length) {
          invalidLines.push(actIdx);
          ++actIdx;
        }
        while (expIdx < expected.length) {
          unmatchedValidLines.push(expIdx);
          ++expIdx;
        }
        return  { type: 'error-at-lines', invalid: invalidLines, unmatchedValid: unmatchedValidLines };
      }
      if (expected.length != actual.length) {
        console.log('diff length, exp:', expected.length, ', actual:', actual.length);
        return 'invalid-number-of-lines';
      }
      return 'pass';
    }

    case 'set': {
      const expected = rpcLinesToAssertionLines(tc.assert.lines);

      if (expected.length != actual.length) {
        return 'invalid-number-of-lines';
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
      const actCount = count(actual);

      const invalidLines: number[] = [];
      actual.forEach((line, lineIdx) => {
        const key = JSON.stringify(line);

        if (expCount[key] !== actCount[key]) {
          invalidLines.push(lineIdx);
        }
      });

      return invalidLines.length === 0 ? 'pass' : { type: 'error-at-lines', invalid: invalidLines, unmatchedValid: [] };
    }

    case 'smoke':
    default: {
      return 'pass';
    }
  }
}

export { TestComparisonReport }
export default compareTestResult;
