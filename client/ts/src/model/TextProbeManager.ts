import { assertUnreachable } from '../hacks';

import { NodeLocator, ParsingSource, RpcBodyLine } from '../protocol';
import settings from '../settings';
import ModalEnv from './ModalEnv'
import SourcedDiagnostic from './SourcedDiagnostic';
import { createSpanFlasher } from './SpanFlasher';
import { CompletionResponse, createTextProbeCompleteLogic } from './TextProbeCompleteLogic';
import { createTextProbeEvaluator } from './TextProbeEvaluator';
import { createProbeHoverLogic, HoverResult } from './TextProbeHoverLogic';

interface TextProbeManagerArgs {
  env: ModalEnv;
  onFinishedCheckingActiveFile: (res: TextProbeCheckResults) => void;
}

interface TextProbeCheckResults {
  numPass: number;
  numFail: number;
}

interface TextProbeManager {
  hover: (line: number, column: number) => Promise<HoverResult | null>,
  complete: (line: number, column: number) => Promise<CompletionResponse | null>,
  checkFile: (requestSrc: ParsingSource, knownSrc: string) => Promise<TextProbeCheckResults | null>,
};

type TextProbeStyle = 'angle-brackets' | 'disabled';

const doCompareLhsAndRhs = (
  lhsEval: RpcBodyLine[],
  rhsEval: RpcBodyLine[],
  tilde: boolean,
): { actual?: string, comparisonSuccess: boolean } => {
  let actual: string | undefined = undefined;
  let comparisonSuccess: boolean;
  if (tilde) {
    // Either a contains-in-array checking, or string.contains checking
    if (lhsEval[0].type === 'arr' && rhsEval[0].type === 'node') {
      // Array contains
      const needle = JSON.stringify(rhsEval[0]);
      comparisonSuccess = lhsEval[0].value.some(row => JSON.stringify(row) === needle);
    } else {
      // String contains
      const lhsStr = evalPropertyBodyToString(lhsEval);
      const rhsStr = evalPropertyBodyToString(rhsEval);
      comparisonSuccess = lhsStr.includes(rhsStr);
      actual = lhsStr;
    }
  } else {
    // Non-contains checking. Either precise node locator comparison, or less precise toString-comparison
    if (lhsEval[0].type === 'node' && rhsEval[0].type === 'node') {
      // Node locator comparison
      const lhsStr = JSON.stringify(lhsEval[0].value);
      const rhsStr = JSON.stringify(rhsEval[0].value);
      comparisonSuccess = lhsStr == rhsStr;
    } else {
      // (Less-precise) String comparison
      const lhsStr = evalPropertyBodyToString(lhsEval);
      const rhsStr = evalPropertyBodyToString(rhsEval);
      actual = lhsStr;
      comparisonSuccess = lhsStr === rhsStr;
    }
  }
  return { actual, comparisonSuccess };
};

const setupTextProbeManager = (args: TextProbeManagerArgs): TextProbeManager => {
  const refreshDispatcher = args.env.createCullingTaskSubmitter()
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;

  const diagnostics: SourcedDiagnostic[] = [];
  args.env.probeMarkers[queryId] = diagnostics;

  let activeRefresh = false;
  let repeatOnDone = false;
  const activeStickies: string[] = [];
  const doRefresh = async () => {
    const hadDiagnosticsBefore = diagnostics.length;
    if (settings.getTextProbeStyle() === 'disabled') {
      for (let i = 0; i < activeStickies.length; ++i) {
        args.env.clearStickyHighlight(activeStickies[i]);
      }
      activeStickies.length = 0;
      args.onFinishedCheckingActiveFile({ numFail: 0, numPass: 0 })
      if (hadDiagnosticsBefore) {
        diagnostics.length = 0;
        args.env.updateMarkers();
      }
      return;
    }
    if (activeRefresh) {
      repeatOnDone = true;
      return;
    }
    const newDiagnostics: SourcedDiagnostic[] = [];
    activeRefresh = true;
    repeatOnDone = false;
    let nextStickyIndex = 0;
    const allocateStickyId = () => {
      let stickyId;
      if (nextStickyIndex >= activeStickies.length) {
        stickyId = `${queryId}-${nextStickyIndex}`;
        activeStickies.push(stickyId);
      } else {
        stickyId = activeStickies[nextStickyIndex];
      }
      ++nextStickyIndex;
      return stickyId;
    }


    const combinedResults: TextProbeCheckResults = { numPass: 0, numFail: 0 };
    try {
      const evaluator = createTextProbeEvaluator(args.env);

      function addSquiggly(lineIdx: number, colStart: number, colEnd: number, msg: string) {
        newDiagnostics.push({ type: 'ERROR', start: ((lineIdx + 1) << 12) + colStart, end: ((lineIdx + 1) << 12) + colEnd, msg, source: 'Text Probe' });
      }
      function addStickyBox(boxClass: string[], boxSpan: Span, stickyClass: string[], stickyContent: string | null, stickySpan?: Span) {
        args.env.setStickyHighlight(allocateStickyId(), {
          classNames: boxClass,
          span: boxSpan,
        });
        args.env.setStickyHighlight(allocateStickyId(), {
          classNames: [],
          span: stickySpan ?? { ...boxSpan, colStart: boxSpan.colEnd - 2, colEnd: boxSpan.colEnd - 1 },
          content: stickyContent ?? undefined,
          contentClassNames: stickyClass,
        });

      }

      (await evaluator.loadVariables()).forEach(vres => {
        const span: Span = { lineStart: vres.lineIdx + 1, colStart: vres.assign.index - 1, lineEnd: vres.lineIdx + 1, colEnd: vres.assign.index + vres.assign.full.length + 2 };

        switch (vres.type) {
          case 'success': {
            args.env.setStickyHighlight(allocateStickyId(), {
              classNames: ['elp-result-stored'],
              span,
            });
            break;
          }
          case 'error': {
            addSquiggly(vres.lineIdx, vres.errRange.colStart, vres.errRange.colEnd, vres.msg);
            addStickyBox(
              ['elp-result-fail'], span,
              ['elp-actual-result-err'], vres.msg,
            )
            combinedResults.numFail++;
            break;
          }
        }
      });

      // Second, go through all non-assignments
      for (let i = 0; i < evaluator.fileMatches.probes.length; ++i) {
        const match = evaluator.fileMatches.probes[i];
        const lineIdx = match.lineIdx;
        const lhsEvalResult = await evaluator.evaluateNodeAndAttr(match.lhs);
        const span: Span = { lineStart: lineIdx + 1, colStart: match.index - 1, lineEnd: lineIdx + 1, colEnd: match.index + match.full.length + 2 };
        if (lhsEvalResult.type === 'error') {
          addSquiggly(lineIdx, lhsEvalResult.errRange.colStart, lhsEvalResult.errRange.colEnd, lhsEvalResult.msg);
          addStickyBox(
            ['elp-result-fail'], span,
            ['elp-actual-result-err'], lhsEvalResult.msg,
          );
          combinedResults.numFail++;
          continue;
        }
        // Else, success!
        if (typeof match.rhs?.expectVal === 'undefined') {
          addStickyBox(
            ['elp-result-probe'], span,
            [`elp-actual-result-probe`], `Result: ${evalPropertyBodyToString(lhsEvalResult.output)}`,
          );
          continue;
        }
        const lhsEval = lhsEvalResult.output;
        let rhsEval: RpcBodyLine[];
        if (match.rhs.expectVal.startsWith('$')) {
          const rhsMatch = evaluator.matchNodeAndAttrChain({ lineIdx, value: match.rhs.expectVal }, true);
          if (!rhsMatch) {
            const errMsg = `Invalid syntax`;
            combinedResults.numFail++;
            addSquiggly(lineIdx, match.index + match.full.indexOf(match.rhs.expectVal), span.colEnd - 1, errMsg);
            addStickyBox(
              ['elp-result-fail'], span,
              ['elp-actual-result-err'], errMsg,
            );
            continue;
          }
          rhsMatch.index = match.index + match.full.indexOf('$', 2);
          const rhsEvalRes = await evaluator.evaluateNodeAndAttr(rhsMatch);
          if (rhsEvalRes.type === 'error') {
            addSquiggly(lineIdx, rhsEvalRes.errRange.colStart, rhsEvalRes.errRange.colEnd, rhsEvalRes.msg);
            addStickyBox(
              ['elp-result-fail'], span,
              ['elp-actual-result-err'], rhsEvalRes.msg,
            );
            combinedResults.numFail++;
            continue;
          }
          rhsEval = rhsEvalRes.output;
        } else {
          // rhs is plain string
          rhsEval = [{ type: 'plain', value: match.rhs.expectVal }];
        }

        const { actual, comparisonSuccess } = doCompareLhsAndRhs(lhsEval, rhsEval, match.rhs.tilde);
        const adjustedComparisonSuccsess = match.rhs.exclamation ? !comparisonSuccess : comparisonSuccess;
        if (adjustedComparisonSuccsess) {
          combinedResults.numPass++;
          args.env.setStickyHighlight(allocateStickyId(), {
            classNames: ['elp-result-success'], span,
          });
        } else {
          const errMsg = actual ? `Actual: ${actual}` : `Assertion failed`;
          addSquiggly(lineIdx, match.index + match.full.indexOf(match.rhs.expectVal), span.colEnd - 1, errMsg);
          addStickyBox(
            ['elp-result-fail'], span,
            ['elp-actual-result-err'], errMsg,
          );
          combinedResults.numFail++;

        }
        // }
      }
    } catch (e) {
      console.warn('Error during refresh', e);
    }
    diagnostics.length = 0;
    diagnostics.push(...newDiagnostics);
    if (diagnostics.length || hadDiagnosticsBefore) {
      args.env.updateMarkers();
    }
    for (let i = nextStickyIndex; i < activeStickies.length; ++i) {
      args.env.clearStickyHighlight(activeStickies[i]);
    }
    activeStickies.length = nextStickyIndex;

    activeRefresh = false;
    args.onFinishedCheckingActiveFile(combinedResults)
    if (repeatOnDone) {
      refresh();
    }
  }

  const refresh = () => refreshDispatcher.submit(doRefresh);

  args.env.onChangeListeners[queryId] = refresh;
  refresh();


  const flasher = createSpanFlasher(args.env);

  const hoverLogic = createProbeHoverLogic({ env: args.env, flasher, });
  const hover: TextProbeManager['hover'] = async (line, column) => {
    if (settings.getTextProbeStyle() === 'disabled') {
      return null;
    }
    return hoverLogic.hover(createTextProbeEvaluator(args.env), line, column);
  }

  const completeLogic = createTextProbeCompleteLogic({ env: args.env, flasher, });
  const complete: TextProbeManager['complete'] = async (line, column) => {
    if (settings.getTextProbeStyle() === 'disabled') {
      return null;
    }
    return completeLogic.complete(createTextProbeEvaluator(args.env), line, column);
  }

  const checkFile: TextProbeManager['checkFile'] = async (requestSrc, knownSrc) => {
    const ret: TextProbeCheckResults = {
      numPass: 0,
      numFail: 0,
    };
    if (settings.getTextProbeStyle() === 'disabled') {
      return ret;
    }
    const evaluator = createTextProbeEvaluator(args.env, { src: requestSrc, contents: knownSrc });
    const varLoadRes = await evaluator.loadVariables();
    varLoadRes.forEach((res) => {
      if (res.type === 'error') {
        ret.numFail++;
      }
    });
    const postLoopWaits: Promise<any>[] = [];
    for (let i = 0; i < evaluator.fileMatches.probes.length; ++i) {
      const match = evaluator.fileMatches.probes[i];
      const handleMatch = async () => {
        const lhsResult = await evaluator.evaluateNodeAndAttr(match.lhs)
        if (lhsResult.type === 'error') {
          ret.numFail++;
          return;
        }

        const rhs = match.rhs;
        const expectVal = rhs?.expectVal;
        if (!rhs || typeof expectVal !== 'string') {
          // Just a probe, no assertion, no need to check further
          ret.numPass++;
          return;
        }
        const lhsEval = lhsResult.output;
        let rhsEval: RpcBodyLine[];
        if (expectVal?.startsWith('$')) {
          const rhsMatch = evaluator.matchNodeAndAttrChain({ lineIdx: match.lineIdx, value: expectVal }, true);
          if (!rhsMatch) {
            ret.numFail++;
            return;
          }
          const rhsResult = await evaluator.evaluateNodeAndAttr(rhsMatch);
          if (rhsResult.type === 'error') {
            ret.numFail++;
            return;
          }
          rhsEval = rhsResult.output;
        } else {
          rhsEval = [{ type: 'plain', value: expectVal }];
        };

        const { comparisonSuccess } = doCompareLhsAndRhs(lhsEval, rhsEval, rhs.tilde);
        const adjustedComparisonSuccsess = rhs.exclamation ? !comparisonSuccess : comparisonSuccess;
        if (adjustedComparisonSuccsess) {
          ret.numPass++;
        } else {
          ret.numFail++;
        }
      }
      if (args.env.workerProcessCount !== undefined) {
        postLoopWaits.push(handleMatch());
      } else {
        await handleMatch();
      }
    }
    await Promise.all(postLoopWaits);

    return ret;
  };

  return { hover, complete, checkFile }
}

const filterPropertyBodyForHighIshPrecisionComparison = (body: RpcBodyLine[]): RpcBodyLine[] => body.filter(
  x => !(x.type === 'plain' && !x.value.trim())
);
const evalPropertyBodyToString = (body: RpcBodyLine[]): string => {
  const nodeToString = (node: NodeLocator): string => {
    const ret = node.result.label ?? node.result.type;
    return ret.slice(ret.lastIndexOf('.') + 1);
  }
  const lineToComparisonString = (line: RpcBodyLine): string => {
    switch (line.type) {
      case 'plain':
      case 'stdout':
      case 'stderr':
      case 'streamArg':
      case 'dotGraph':
      case 'html':
        return line.value;
      case 'arr':
        let mapped = [];
        for (let idx = 0; idx < line.value.length; ++idx) {
          if (line.value[idx].type === 'node' && line.value[idx + 1]?.type === 'plain' && line.value[idx + 1].value === '\n') {
            const justNode = lineToComparisonString(line.value[idx]);
            if (line.value.length === 2) {
              return justNode;
            }
            mapped.push(justNode);
            ++idx;
          } else {
            mapped.push(lineToComparisonString(line.value[idx]));
          }
        }
        return `[${mapped.join(', ')}]`;
      case 'node':
        return nodeToString(line.value);
      case 'highlightMsg':
        return line.value.msg;
      case 'tracing':
        return lineToComparisonString(line.value.result);
      case 'nodeContainer':
        return `${nodeToString(line.value.node)}${line.value.body ? `[${lineToComparisonString(line.value.body)}]` : ''}`;
      default:
        assertUnreachable(line);
        return '';
    }
  }
  return lineToComparisonString(body.length === 1 ? body[0] : { type: 'arr', value: body });
}

export { setupTextProbeManager, TextProbeCheckResults, TextProbeStyle, evalPropertyBodyToString };
export default TextProbeManager;
