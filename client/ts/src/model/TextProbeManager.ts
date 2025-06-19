import { assertUnreachable } from '../hacks';

import { ListPropertiesReq, ListPropertiesRes, NodeLocator, ParsingRequestData, ParsingSource, RpcBodyLine, TALStep } from '../protocol';
import settings from '../settings';
import { lastKnownMousePos } from '../ui/create/attachDragToX';
import displayAttributeModal from '../ui/popup/displayAttributeModal';
import displayProbeModal from '../ui/popup/displayProbeModal';
import startEndToSpan from '../ui/startEndToSpan';
import createCullingTaskSubmitterFactory from './cullingTaskSubmitterFactory';
import ModalEnv from './ModalEnv'
import SourcedDiagnostic from './SourcedDiagnostic';
import TextProbeEvaluator, { createTextProbeEvaluator, isAssignmentMatch, isBrokenNodeChain, NodeAndAttrChainMatch } from './TextProbeEvaluator';
import { createMutableLocator } from './UpdatableNodeLocator';
import { NestedWindows } from './WindowState';

interface TextProbeManagerArgs {
  env: ModalEnv;
  onFinishedCheckingActiveFile: (res: TextProbeCheckResults) => void;
}

interface TextProbeCheckResults {
  numPass: number;
  numFail: number;
}

type HoverResult = {
  range: {
    startLineNumber: number;
    startColumn: number;
    endLineNumber: number;
    endColumn: number;
  };
  contents: {
    value: string; isTrusted?: boolean;
  }[];
}
interface CompletionItem {
  label: string;
  insertText?: string;
  kind: number; // See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#completionItemKind
  sortText?: string;
  detail?: string;
}
interface TextProbeManager {
  hover: (line: number, column: number) => Promise<HoverResult | null>,
  complete: (line: number, column: number) => Promise<CompletionItem[] | null>,
  checkFile: (requestSrc: ParsingSource, knownSrc: string) => Promise<TextProbeCheckResults | null>,
};

type TextProbeStyle = 'angle-brackets' | 'disabled';

// const createForgivingProbeRegex = () => {
//   // Neede for autocompletion
//   const reg = /\[\[(\$?\w*)(\[\d+\])?((?:\.(?:l:)?\w*)*)?(:?)(!?)(~?)(?:=(((?!\[\[).)*))?\]\](?!\])/g;

//   return {
//     exec: (line: string) => {
//       const match = reg.exec(line);
//       if (!match) {
//         return null;
//       }
//       const [full, nodeType, nodeIndex, attrNames] = match;
//       return {
//         index: match.index,
//         full,
//         nodeType,
//         nodeIndex: nodeIndex ? +nodeIndex.slice(1, -1) : undefined,
//         attrNames: attrNames ? attrNames.slice(1).split('.') : undefined,
//       };

//     }
//   }
// }

interface SpanFlasher {
  quickFlash: (spans: Span[]) => void;
  flash: (spans: Span[], removeHighlightsOnMove?: boolean) => void;
  clear: () => void;
}
const createSpanFlasher = (env: ModalEnv): SpanFlasher => {
  let activeFlashCleanup = () => { };
  let activeFlashSpan: Span[] | null = null;
  const activeSpanReferenceHoverPos: typeof lastKnownMousePos = { ...lastKnownMousePos };
  const flash: SpanFlasher['flash'] = (spans, removeHighlightsOnMove) => {
    // Remove invalid spans
    spans = spans.filter(x => x.lineStart && x.lineEnd);
    const act = activeFlashSpan;
    if (act && JSON.stringify(act) === JSON.stringify(spans) /* bit hacky comparison, but it works */) {
      // Already flashing this, update the hover pos reference and exit
      Object.assign(activeSpanReferenceHoverPos, lastKnownMousePos);
      return;
    }
    activeFlashSpan = spans;
    const flashes: { id: string, sticky: StickyHighlight }[] = spans.map(span => ({
      id: `flash-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`,
      sticky: { span, classNames: [span.lineStart === span.lineEnd ? 'elp-flash' : 'elp-flash-multiline'] }
    }));
    flashes.forEach(flash => env.setStickyHighlight(flash.id, flash.sticky));
    const cleanup = () => {
      flashes.forEach(flash => env.clearStickyHighlight(flash.id));
      activeFlashSpan = null;
      window.removeEventListener('mousemove', cleanup);
    };
    Object.assign(activeSpanReferenceHoverPos, lastKnownMousePos);
    if (removeHighlightsOnMove) {
      window.addEventListener('mousemove', (e) => {
        const dx = e.x - activeSpanReferenceHoverPos.x;
        const dy = e.y - activeSpanReferenceHoverPos.y;
        if (Math.hypot(dx, dy) > 24 /* arbitrary distance */) {
          cleanup();
        }
      })
    }
    activeFlashCleanup();
    activeFlashCleanup = cleanup;
  }

  const quickFlash: SpanFlasher['quickFlash'] = (spans) => {
    flash(spans);
    const cleaner = activeFlashCleanup;
    setTimeout(() => {
      if (activeFlashCleanup === cleaner) {
        activeFlashCleanup();
      } {
        // Else, we have been replaced by another highlight
      }
    }, 500);
  };
  const clear: SpanFlasher['clear'] = () => {
    activeFlashCleanup();
  }
  return { flash, clear, quickFlash };

}

const setupTextProbeManager = (args: TextProbeManagerArgs): TextProbeManager => {
  const refreshDispatcher = createCullingTaskSubmitterFactory(1)()
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;

  const diagnostics: SourcedDiagnostic[] = [];
  args.env.probeMarkers[queryId] = () => diagnostics;

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
    diagnostics.length = 0;
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

      // TODO rename to addsquiggly
      function addSquiggly(lineIdx: number, colStart: number, colEnd: number, msg: string) {
        diagnostics.push({ type: 'ERROR', start: ((lineIdx + 1) << 12) + colStart, end: ((lineIdx + 1) << 12) + colEnd, msg, source: 'Text Probe' });
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

  const resolveNode = async (evaluator: TextProbeEvaluator, match: NodeAndAttrChainMatch): Promise<NodeLocator | null> => {
    if (match.nodeType.startsWith('$')) {
      await evaluator.loadVariables();
      const varVal = evaluator.variables[match.nodeType];
      return varVal?.[0]?.type === 'node' ? varVal[0].value : null;
    }
    const listedNodes = await evaluator.listNodes({ attrFilter: match.attrNames[0] ?? '', predicate: `this<:${match.nodeType}&@lineSpan~=${match.lineIdx + 1}`, lineIdx: match.lineIdx });
    return listedNodes?.[match.nodeIndex ?? 0] ?? null;
  }

  const hover: TextProbeManager['hover'] = async (line, column) => {
    const res = await doHover(line, column);
    if (res === null) {
      flasher.clear();
    }
    return res;
  }
  const doHover = async (line: number, column: number): Promise<HoverResult | null> => {
    if (settings.getTextProbeStyle() === 'disabled') {
      return null;
    }
    const evaluator = createTextProbeEvaluator(args.env);
    // const evaluator.fileMatches = matchFullFile(args.env.getLocalState());
    const lines = evaluator.fileMatches.lines;
    // const lines = args.env.getLocalState().split('\n');
    // Make line/col 0-indexed
    --line;
    --column;
    if (line < 0 || line >= lines.length) {
      return null;
    }

    const filtered = evaluator.fileMatches.matchesOnLine(line);
    for (let i = 0; i < filtered.length; ++i) {
      const match = filtered[i];
      const begin = match.index;
      if (column < begin) {
        continue;
      }
      const end = begin + match.full.length;
      if (column >= end) {
        continue;
      }
      if (isAssignmentMatch(match)) {
        // TODO
      } else {
        // Probe
        const locator = await resolveNode(evaluator, match.lhs);
        if (!locator) {
          return null;
        }
        const typeStart = match.index;
        let typeEnd;
        if (match.lhs.attrNames.length) {
          typeEnd = lines[line].value.indexOf('.', typeStart);
        } else {
          typeEnd = typeStart + match.full.length;
        }
        if (column >= typeStart && column < typeEnd) {
          const retFlash = [];
          if (!locator.result.external) {
            retFlash.push(startEndToSpan(locator.result.start, locator.result.end));
          }
          retFlash.push({
            // Type in text probe
            lineStart: line + 1, colStart: typeStart + 1,
            lineEnd: line + 1, colEnd: typeEnd,
          });
          flasher.flash(retFlash, true);
        } else {
          let searchStart = typeEnd;
          let hoveringProp = false;
          for (let propIdx = 0; propIdx < match.lhs.attrNames.length; ++propIdx) {
            const propStart = lines[line].value.indexOf('.', searchStart) + 1;
            const propEnd = propStart + match.lhs.attrNames[propIdx].length;
            searchStart = propEnd;
            if (column >= propStart && column < propEnd) {
              hoveringProp = true;
              evaluator.evaluatePropertyChain({ locator, propChain: match.lhs.attrNames.slice(0, propIdx + 1) })
                // 'then', not 'await'. Do not want to block the hover info from appearing
                .then(res => {
                  if (res === 'stopped' || isBrokenNodeChain(res) || res.body[0].type !== 'node') {
                    return;
                  }
                  const node = res.body[0].value.result;
                  flasher.flash([
                    {
                      lineStart: line + 1, colStart: propStart + 1,
                      lineEnd: line + 1, colEnd: propEnd,
                    },
                    startEndToSpan(node.start, node.end)
                  ], true);
                });
              break;
            }
          }
          if (!hoveringProp && match.rhs?.expectVal) {
            const resultStart = lines[line].value.indexOf('=', searchStart) + 1;
            const resultEnd = resultStart + match.rhs.expectVal.length;
            if (column >= resultStart && column < resultEnd) {
              evaluator.evaluatePropertyChain({ locator, propChain: match.lhs.attrNames })
                .then(res => {
                  if (res === 'stopped' || isBrokenNodeChain(res)) {
                    return;
                  }
                  if (res.body[0].type === 'node') {
                    const node = res.body[0].value.result;
                    const spans = [{
                      lineStart: line + 1, colStart: resultStart + 1,
                      lineEnd: line + 1, colEnd: resultEnd,
                    }];
                    if (!node.external) {
                      spans.push(startEndToSpan(node.start, node.end));
                    }
                    flasher.flash(spans, true);
                  }
                })
            }
          }
        }

        (window as any).CPR_CMD_OPEN_TEXTPROBE_CALLBACK = async () => {
          if (!match.lhs.attrNames.length) {
            displayAttributeModal(args.env, null, createMutableLocator(locator));
            return;
          }
          const displayForNode = async (locator: NodeLocator, m: NodeAndAttrChainMatch, offset: { x: number, y: number }) => {

            const nestedWindows: NestedWindows = {};
            let nestTarget = nestedWindows;
            let nestLocator = locator;
            for (let chainAttrIdx = 0; chainAttrIdx < m.attrNames.length; ++chainAttrIdx) {
              const chainAttr = m.attrNames[chainAttrIdx];
              const res = await evaluator.evaluateProperty({ locator: nestLocator, prop: chainAttr });
              if (res === 'stopped' || isBrokenNodeChain(res)) {
                break;
              }
              if (chainAttrIdx > 0) {
                let newNest: NestedWindows = {};
                nestTarget['[0]'] = [
                  { data: { type: 'probe', locator, property: { name: chainAttr }, nested: newNest } },
                ];
                nestTarget = newNest;
              }
              if (res.body[0]?.type !== 'node') {
                break;
              }
              nestLocator = res.body[0].value;
            }
            displayProbeModal(args.env,
              { x: lastKnownMousePos.x + offset.x, y: lastKnownMousePos.y + offset.y },
              createMutableLocator(locator),
              { name: m.attrNames[0] },
              nestedWindows
            );
          }

          await displayForNode(locator, match.lhs, { x: 0, y: 0 });

          if (match.rhs?.expectVal) {
            // Maybe open for right side as well
            const rhsMatch = evaluator.matchNodeAndAttrChain({ lineIdx: match.lineIdx, value: match.rhs.expectVal});
            if (rhsMatch?.attrNames?.length) {
              const rhsLocator = await resolveNode(evaluator, rhsMatch);
              if (rhsLocator) {
                await displayForNode(rhsLocator, rhsMatch, { x: 64, y: 4 });
              }
            }
          }
        };
        return {
          range: { startLineNumber: line + 1, startColumn: column + 1, endLineNumber: line + 1, endColumn: column + 3 },
          contents: [{
            value: `[${match.lhs.attrNames.length ? `Open Normal Probe` : `Create Normal Probe`}](command:${(window as any).CPR_CMD_OPEN_TEXTPROBE_ID})`, isTrusted: true
          }],
        };
      }
    }

    return null;
  }

  const complete: TextProbeManager['complete'] = async (line, column) => {
    if (settings.getTextProbeStyle() === 'disabled') {
      return null;
    }
    const evaluator = createTextProbeEvaluator(args.env);
    const lines = evaluator.fileMatches.lines;
    // Make line/col 0-indexed
    --line;
    --column;
    if (line < 0 || line >= lines.length) {
      return null;
    }

    type PermittedTypeFilter = 'allow-all' | 'forbid-variables' | 'forbid-types';
    const completeType = async (
      filter: PermittedTypeFilter = 'allow-all',
      existingText = ''
    ): Promise<CompletionItem[] | null> => {
      const matchingNodes: TALStep[] = [];

      const setupPromises: Promise<any>[] = [];
      if (filter !== 'forbid-types') {
        setupPromises.push(evaluator.listNodes({
          attrFilter: '',
          predicate: `@lineSpan~=${line + 1}`,
          lineIdx: line,
        }).then(list => list?.forEach(node => matchingNodes.push(node.result))));
      };
      if (filter !== 'forbid-variables') {
        setupPromises.push(evaluator.loadVariables().then(() => {
          Object.entries(evaluator.variables).forEach(([label, val]) => {
            if (val[0]?.type === 'node') {
              matchingNodes.push({ ...val[0].value.result, label });
            }
          })
        }));
      }
      await Promise.all(setupPromises);
      if (!matchingNodes.length) {
        return null;
      }
      const duplicateTypes = new Set();
      const includedTypes = new Set();
      matchingNodes.forEach(node => {
        const type = node.label ?? node.type;
        if (includedTypes.has(type)) {
          duplicateTypes.add(type);
        }
        includedTypes.add(type);
      });
      let nodeListCounter: { [type: string]: number } = {};
      let lastFlash = -1;
      window.OnCompletionItemListClosed = () => {
        flasher.clear();
      }
      window.OnCompletionItemFocused = item => {
        if ((item as any).nodeIndex === 'undefined') {
          return;
        }
        const nodeIndex = (item as any).nodeIndex;
        if (lastFlash === nodeIndex) {
          return;
        }
        lastFlash = nodeIndex;
        const node = matchingNodes[nodeIndex];
        if (!node) {
          return;
        }
        flasher.flash([startEndToSpan(node.start, node.end)]);
      };
      return matchingNodes.map((node, nodeIndex) => {
        const type = node.label ?? node.type;
        const label = type.split('.').slice(-1)[0];
        let detail: string | undefined = undefined;
        if (type.startsWith('$')) {
          // It is a variable, add the type as detail
          detail = node.type.split('.').slice(-1)[0];
        }
        const ret: (CompletionItem & { nodeIndex: number })= { label, kind: 7, nodeIndex, sortText: `${matchingNodes.length - nodeIndex}`.padStart(4, '0'), detail };
        if (!duplicateTypes.has(type)) {
          if (label.length > existingText.length && label.startsWith(existingText)) {
            ret.insertText = label.slice(existingText.length);
          } else {
            ret.insertText = label;
          }
          return ret;
        }
        let idx = (nodeListCounter[type] ?? -1) + 1;
        nodeListCounter[type] = idx;

        const indexed = `${label}[${idx}]`;
        ret.label = indexed;
        ret.insertText = indexed;
        return ret;
      });
    }
    const completeProp = async (nodeType: string, nodeIndex: number | undefined, prerequisiteAttrs: string[], previousStepSpan: [number, number]) => {
      let locator: NodeLocator | undefined = undefined;
      if (nodeType.startsWith('$')) {
        await evaluator.loadVariables();
        const varData = evaluator.variables[nodeType];
        if (varData?.[0]?.type === 'node') {
          locator = varData[0].value;
        }
      } else {
        const matchingNodes = await evaluator.listNodes({
            attrFilter: '',
            predicate: `this<:${nodeType}&@lineSpan~=${line + 1}`,
            lineIdx: line,
        });
        locator = matchingNodes?.[nodeIndex ?? 0];
      }
      if (!locator) {
        return null;
      }
      if (prerequisiteAttrs.length != 0) {
        const chainResult = await evaluator.evaluatePropertyChain({ locator, propChain: prerequisiteAttrs });
        if (chainResult === 'stopped' || isBrokenNodeChain(chainResult)) {
          return null;
        }
        if (chainResult.body[0]?.type !== 'node') {
          return null;
        }
        locator = chainResult.body[0].value;
      }
      // const typeStart = match.index + 2;
      // const typeEnd = typeStart + match.nodeType.length;

      // const firstPropStart =
      flasher.flash([
        {
          lineStart: line + 1, colStart: previousStepSpan[0],
          lineEnd: line + 1, colEnd: previousStepSpan[1],
        },
        {
          lineStart: locator.result.start >>> 12, colStart: locator.result.start & 0xFFF,
          lineEnd: locator.result.end >>> 12, colEnd: locator.result.end & 0xFFF,
        }
      ])
      window.OnCompletionItemListClosed = () => {
        flasher.clear();
      };

      const props = await args.env.performTypedRpc<ListPropertiesReq, ListPropertiesRes>({
        locator,
        src: evaluator.parsingData,
        type: 'ListProperties',
        all: settings.shouldShowAllProperties(),
      });
      if (!props.properties) {
        return null;
      }
      const zeroArgPropNames = new Set(props.properties.filter(prop => (prop.args?.length ?? 0) === 0).map(prop => prop.name));
      return [...zeroArgPropNames].sort().map(label => {
        return { label, insertText: label, kind: 2 };
      });
    }
    const completeExpectedValue = async (nodeType: string, nodeIndex: number | undefined, attrNames: string[]) => {
      let locator: NodeLocator | undefined = undefined;
      if (nodeType.startsWith('$')) {
        await evaluator.loadVariables();
        const varData = evaluator.variables[nodeType];
        if (varData && !attrNames.length) {
          const cmp = evalPropertyBodyToString(varData);
          return [{ label: cmp, insertText: cmp, kind: 15 }];
        }
        if (varData?.[0]?.type === 'node') {
          locator = varData[0].value;
        }
      } else {
        const matchingNodes = await evaluator.listNodes({
          attrFilter: '',
          predicate: `this<:${nodeType}&@lineSpan~=${line + 1}`,
          lineIdx: line,
        });
        locator = matchingNodes?.[nodeIndex ?? 0];
      }
      if (!locator) {
        return null;
      }
      if (!attrNames.length) {
        const cmp = evalPropertyBodyToString([{ type: 'node', value: locator }]);
        return [{ label: cmp, insertText: cmp, kind: 15 }];
      }
      const attrEvalResult = await evaluator.evaluatePropertyChain({
        locator,
        propChain: attrNames,
      });
      if (attrEvalResult == 'stopped' || isBrokenNodeChain(attrEvalResult)) {
        return null;
      }
      if (attrEvalResult.body[0]?.type === 'node') {
        const node = attrEvalResult.body[0].value.result;
        if (!node.external) {
          flasher.flash([startEndToSpan(node.start, node.end)]);
          window.OnCompletionItemListClosed = () => {
            flasher.clear();
          }
        }
      }
      const cmp = evalPropertyBodyToString(attrEvalResult.body)
      return [{ label: cmp, insertText: cmp, kind: 15 }];
    };

    const filtered = evaluator.fileMatches.matchesOnLine(line);
    for (let i = 0; i < filtered.length; ++i) {
      const lhsMatch = filtered[i];
      if (lhsMatch.index > column) {
        // Cursor is before the match
        continue;
      }
      if (lhsMatch.index + lhsMatch.full.length < column) {
        // Cursor is after the match
        continue;
      }

      let typeStart = lhsMatch.index;
      let typeEnd: number = 0;
      const computeTypeEnd = (m: NodeAndAttrChainMatch) => {
        if (m.nodeIndex !== undefined) {
          return lines[line].value.indexOf(']', typeStart) + 1;
        }
        return typeStart + m.nodeType.length;
      };
      let attrSearchStart = typeEnd;
      const maybeCompleteTypeOrAttr = async (m: NodeAndAttrChainMatch, typeFilter: PermittedTypeFilter = 'allow-all'): Promise<ReturnType<TextProbeManager['complete']>> => {
        if (column >= typeStart && column <= typeEnd) {
          return completeType(typeFilter);
        }
        attrSearchStart = typeEnd;
        for (let attrIdx = 0; attrIdx < m.attrNames.length; ++attrIdx) {
          const attr = m.attrNames[attrIdx];
          const attrStart = attr
            ? lines[line].value.indexOf(attr, attrSearchStart)
            : lines[line].value.indexOf('.', attrSearchStart) + 1;
          const attrEnd = attrStart + attr.length;
          attrSearchStart = attrEnd;
          if (column >= attrStart && column <= attrEnd) {
            return completeProp(m.nodeType, m.nodeIndex, m.attrNames.slice(0, attrIdx), [typeStart+1, attrStart-1]);
          }
        }
        return null;
      };

      if (isAssignmentMatch(lhsMatch)) {
        const expectStart = lines[line].value.indexOf(':=', lhsMatch.index) + 2;
        const expectEnd = lhsMatch.index + lhsMatch.full.length;
        if (column >= expectStart && column <= expectEnd) {
          // Inside src value
          if (!lhsMatch.srcVal) {
            return completeType('forbid-variables');
          }
          const srcMatch = evaluator.matchNodeAndAttrChain({ lineIdx: lhsMatch.lineIdx, value: lhsMatch.srcVal });
          if (!srcMatch) {
            // Still working on the type
            if (/^\w*(\[\d+\])?$/.test(lhsMatch.srcVal)) {
              return completeType('forbid-variables');
            }
            return null;
          }
          typeStart = expectStart;
          typeEnd = computeTypeEnd(srcMatch);

          return maybeCompleteTypeOrAttr({
            ...srcMatch,
            index: expectStart + srcMatch.index,
          }, 'forbid-variables');
        }
        return null;
      }

      // Else, probe!
      typeEnd = computeTypeEnd(lhsMatch.lhs); // lines[line].value.indexOf('.', typeStart);

      const lhsComplete = await maybeCompleteTypeOrAttr(lhsMatch.lhs);
      if (lhsComplete) {
        return lhsComplete;
      }

      if (!lhsMatch.rhs) {
        return null;
      }
      // if (!lhsMatch.rhs.expectVal) {
      //   return null;
      // }
      const currExpectVal = lhsMatch.rhs.expectVal ?? '';
      const expectStart = lines[line].value.indexOf('=', attrSearchStart) + 1;;
      const expectEnd = expectStart + currExpectVal.length;
      if (column >= expectStart && column <= expectEnd) {
        if (column > expectStart && currExpectVal.startsWith('$')) {
          // Actually, we should treat this as a 'normal' query
          const rhsMatch = evaluator.matchNodeAndAttrChain({ lineIdx: line, value: currExpectVal });
          if (!rhsMatch) {
            if (/^\$\w*$/.test(currExpectVal)) {
              return completeType('forbid-types', lines[line].value.slice(expectStart, column));
            }
            return null;
          }
          rhsMatch.index += expectStart;
          typeStart = expectStart;
          typeEnd = computeTypeEnd(rhsMatch);
          return maybeCompleteTypeOrAttr(rhsMatch);
        } else {
          return completeExpectedValue(lhsMatch.lhs.nodeType, lhsMatch.lhs.nodeIndex, lhsMatch.lhs.attrNames);
        }
      }
    }

    const forgivingMatcher = /\[\[(\$?\w*)\]\]/g;
    let match;
    while ((match = forgivingMatcher.exec(lines[line].value)) !== null) {
      const [full, nodeType] = match;
      if (match.index >= column) {
        // Cursor is before the match
        continue;
      }
      if (match.index + full.length <= column) {
        // Cursor is before the match
        continue;
      }
      const typeStart = match.index + 2;
      const typeEnd = Math.max(typeStart + nodeType.length, lines[line].value.indexOf('.', typeStart));
      if (column >= typeStart && column <= typeEnd) {
        return completeType();
      }
      // if (match.attrNames) {
      //   let attrSearchStart = typeEnd;
      //   for (let attrIdx = 0; attrIdx < match.attrNames.length; ++attrIdx) {
      //     const attrStart = lines[line].indexOf('.', attrSearchStart) + 1;;
      //     const attrEnd = attrStart + match.attrNames[attrIdx].length;
      //     attrSearchStart = attrEnd;
      //     if (column >= attrStart && column <= attrEnd) {
      //       return completeProp(match.nodeType, match.nodeIndex, match.attrNames.slice(0, attrIdx), [typeStart, attrStart]);
      //     }
      //   }
      // }
    }

    return null;
  };
  const checkFile: TextProbeManager['checkFile'] = async (requestSrc, knownSrc) => {
    const ret: TextProbeCheckResults = {
      numPass: 0,
      numFail: 0,
    };
    if (settings.getTextProbeStyle() === 'disabled') {
      return ret;
    }
    const evaluator = createTextProbeEvaluator(args.env, { src: requestSrc, contents: knownSrc});
    const varLoadRes = await evaluator.loadVariables();
    varLoadRes.forEach((res) => {
      if (res.type === 'error') {
        ret.numFail++;
      }
    });
    const postLoopWaits: Promise<any>[] = [];
    for (let i = 0; i < evaluator.fileMatches.probes.length; ++i) {
      // const reg = createTypedProbeRegex();
      // let outerMatch: ReturnType<typeof reg['exec']>;
      // while ((outerMatch = reg.exec(lines[lineIdx])) != null) {
        const match = evaluator.fileMatches.probes[i];
        const handleMatch = async () => {
          const lhsResult = await evaluator.evaluateNodeAndAttr(match.lhs)
          if (lhsResult.type === 'error') {
            ret.numFail++;
            return;
          }
          const rhs = match.rhs;
          const expectVal = rhs?.expectVal;
          if (!expectVal) {
            // Just a probe, no assertion, no need to check further
            return;
          }

          if (expectVal?.startsWith('$')) {
            const rhsMatch = evaluator.matchNodeAndAttrChain({ lineIdx: match.lineIdx, value: expectVal}, true);
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

export { setupTextProbeManager, TextProbeCheckResults, TextProbeStyle };
export default TextProbeManager;
