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
        const lhsEval = await evaluator.evaluateNodeAndAttr(match.lhs);
        const span: Span = { lineStart: lineIdx + 1, colStart: match.index - 1, lineEnd: lineIdx + 1, colEnd: match.index + match.full.length + 2 };
        if (lhsEval.type === 'error') {
          addSquiggly(lineIdx, lhsEval.errRange.colStart, lhsEval.errRange.colEnd, lhsEval.msg);
          addStickyBox(
            ['elp-result-fail'], span,
            ['elp-actual-result-err'], lhsEval.msg,
          );
          combinedResults.numFail++;
          continue;
        }
        // Else, success!
        if (typeof match.rhs?.expectVal === 'undefined') {
          addStickyBox(
            ['elp-result-probe'], span,
            [`elp-actual-result-probe`], `Result: ${evalPropertyBodyToString(lhsEval.output)}`,
          );
          continue;
        }
        // Else, an assertion!
        if (match.rhs.expectVal.startsWith('$')) {
          // Do not flatten to string, make full-precision comparison
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
          const rhsEval = await evaluator.evaluateNodeAndAttr(rhsMatch);
          if (rhsEval.type === 'error') {
            addSquiggly(lineIdx, rhsEval.errRange.colStart, rhsEval.errRange.colEnd, rhsEval.msg);
            addStickyBox(
              ['elp-result-fail'], span,
              ['elp-actual-result-err'], rhsEval.msg,
            );
            combinedResults.numFail++;
            continue;
          }
          // Else, success!
          // Ugly stringify comparison, should work but is not the most efficient
          const lhsFiltered = filterPropertyBodyForHighIshPrecisionComparison(lhsEval.output);
          const rhsFiltered = filterPropertyBodyForHighIshPrecisionComparison(rhsEval.output);
          const rawComparisonSuccess = JSON.stringify(lhsFiltered) === JSON.stringify(rhsFiltered);
          const adjustedComparisonSuccsess = match.rhs.exclamation ? !rawComparisonSuccess : rawComparisonSuccess;
          if (adjustedComparisonSuccsess) {
            combinedResults.numPass++;
            args.env.setStickyHighlight(allocateStickyId(), {
              classNames: ['elp-result-success'], span,
            });
            continue;
          }

          const errMsg = `Assertion failed.`;
          addSquiggly(lineIdx, span.colStart + 2, span.colEnd - 2, errMsg);
          addStickyBox(
            ['elp-result-fail'], span,
            ['elp-actual-result-err'], errMsg,
          );
          combinedResults.numFail++;
        } else {
          // Flatten to string and make string comparison
          const cmp = evalPropertyBodyToString(lhsEval.output);
          const rawComparisonSuccess = match.rhs.tilde ? cmp.includes(match.rhs.expectVal) : cmp === match.rhs.expectVal;
          const adjustedComparisonSuccsess = match.rhs.exclamation ? !rawComparisonSuccess : rawComparisonSuccess;
          if (adjustedComparisonSuccsess) {
            combinedResults.numPass++;
            args.env.setStickyHighlight(allocateStickyId(), {
              classNames: ['elp-result-success'], span,
            });
          } else {
            const errMsg = `Actual: ${cmp}`;
            addSquiggly(lineIdx, match.index + match.full.indexOf(match.rhs.expectVal), span.colEnd - 1, errMsg);
            addStickyBox(
              ['elp-result-fail'], span,
              ['elp-actual-result-err'], errMsg,
            );
            combinedResults.numFail++;

          }
        }
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
          const lhsFiltered = filterPropertyBodyForHighIshPrecisionComparison(lhsResult.output);
          const rhsFiltered = filterPropertyBodyForHighIshPrecisionComparison(rhsResult.output);
          const rawComparisonSuccess = JSON.stringify(lhsFiltered) === JSON.stringify(rhsFiltered);
          const adjustedComparisonSuccsess = rhs.exclamation ? !rawComparisonSuccess : rawComparisonSuccess;
          if (adjustedComparisonSuccsess) {
            ret.numPass++;
          } else {
            ret.numFail++;
          }
        } else {
          const cmp = evalPropertyBodyToString(lhsResult.output);
          const rawComparisonSuccess = rhs.tilde ? cmp.includes(expectVal) : (cmp === expectVal);
          const adjustedComparisonSuccsess = rhs.exclamation ? !rawComparisonSuccess : rawComparisonSuccess;
          if (adjustedComparisonSuccsess) {
            ret.numPass++;
          } else {
            ret.numFail++;
          }
        }
      };
      if (args.env.workerProcessCount !== undefined) {
        postLoopWaits.push(handleMatch());
      } else {
        await handleMatch();
      }
      // }
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
