import { NodeLocator, TALStep } from '../protocol';
import settings from '../settings';
import { lastKnownMousePos } from '../ui/create/attachDragToX';
import displayAttributeModal from '../ui/popup/displayAttributeModal';
import displayProbeModal from '../ui/popup/displayProbeModal';
import startEndToSpan from '../ui/startEndToSpan';
import ModalEnv from './ModalEnv';
import SpanFlasher from './SpanFlasher';
import TextProbeEvaluator, { extractPreviousNodeForAttributeChain, isAssignmentMatch, isBrokenNodeChain, NodeAndAttrChainMatch } from './TextProbeEvaluator';
import { createMutableLocator } from './UpdatableNodeLocator';
import { NestedWindows } from './WindowState';

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

interface TextProbeHoverLogic {
  hover: (evaluator: TextProbeEvaluator, line: number, column: number) => Promise<HoverResult | null>,
}

interface CreateProbeHoverLogicArgs {
  env: ModalEnv;
  flasher: SpanFlasher;
}

const createProbeHoverLogic = (args: CreateProbeHoverLogicArgs): TextProbeHoverLogic => {
  const { flasher } = args;

  const resolveNode = async (evaluator: TextProbeEvaluator, match: NodeAndAttrChainMatch): Promise<NodeLocator | null> => {
    if (match.nodeType.startsWith('$')) {
      await evaluator.loadVariables();
      const varVal = evaluator.variables[match.nodeType];
      return varVal?.[0]?.type === 'node' ? varVal[0].value : null;
    }
    const listedNodes = await evaluator.listNodes({ attrFilter: match.attrNames[0] ?? '', predicate: `this<:${match.nodeType}&@lineSpan~=${match.lineIdx + 1}`, lineIdx: match.lineIdx });
    return listedNodes?.[match.nodeIndex ?? 0] ?? null;
  }

  const doHover = async (evaluator: TextProbeEvaluator, line: number, column: number): Promise<HoverResult | null> => {
    if (settings.getTextProbeStyle() === 'disabled') {
      return null;
    }
    const lines = evaluator.fileMatches.lines;
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
      const flashNode = (node: TALStep, colStart: number, colEnd: number) => {
        const flashes = [];
        if (!node.external) {
          flashes.push(startEndToSpan(node.start, node.end));
        }
        flashes.push({
          lineStart: line + 1, colStart,
          lineEnd: line + 1, colEnd,
        });
        flasher.flash(flashes, true);
      };
      const evaluateAndFlashChain = async (locator: NodeLocator, propChain: string[], flashStart: number, flashEnd: number) => {
        const res = await evaluator.evaluatePropertyChain({ locator, propChain, });
        if (res === 'stopped' || isBrokenNodeChain(res) || res[0]?.type !== 'node') {
          return;
        }
        const node = res[0].value.result;
        flashNode(node, flashStart, flashEnd);
      }
      const maybeHoverAttrChain = async (locator: NodeLocator, match: NodeAndAttrChainMatch, searchStart: number): Promise<{ newSearchStart: number, didHoverSomething: boolean }> => {
        let didHoverSomething = false;
        for (let propIdx = 0; propIdx < match.attrNames.length; ++propIdx) {
          const propStart = lines[line].value.indexOf('.', searchStart) + 1;
          const propEnd = propStart + match.attrNames[propIdx].length;
          searchStart = propEnd;
          if (column >= propStart && column < propEnd) {
            didHoverSomething = true;
            await evaluateAndFlashChain(locator, match.attrNames.slice(0, propIdx + 1), propStart + 1, propEnd);
            return { newSearchStart: searchStart, didHoverSomething }
          }
        }
        return { newSearchStart: searchStart, didHoverSomething: false };
      }
      const maybeHoverNodeTypeOrAttrChain = async (locator: NodeLocator, match: NodeAndAttrChainMatch, searchStart: number): ReturnType<typeof maybeHoverAttrChain> => {
        const typeEnd = searchStart + match.nodeType.length;
        if (column < typeEnd) {
          flashNode(locator.result, searchStart + 1, typeEnd);
          return { newSearchStart: searchStart, didHoverSomething: true };
        }
        const hoverRes = await maybeHoverAttrChain(locator, match, searchStart);
        if (hoverRes.didHoverSomething) {
          return { newSearchStart: searchStart, didHoverSomething: true };
        }
        return hoverRes;
      };
      const returnHoverInfo = (locator: NodeLocator, match: NodeAndAttrChainMatch, rhsExpectVal?: string) => {
        (window as any).CPR_CMD_OPEN_TEXTPROBE_CALLBACK = async () => {
          if (!match.attrNames.length) {
            displayAttributeModal(args.env, null, createMutableLocator(locator));
            return;
          }
          const displayForNode = async (locator: NodeLocator, m: NodeAndAttrChainMatch, offset: { x: number, y: number }) => {

            const nestedWindows: NestedWindows = {};
            let nestTarget = nestedWindows;
            let nestLocator = locator;
            let prevStepWasArr = false;
            for (let chainAttrIdx = 0; chainAttrIdx < m.attrNames.length; ++chainAttrIdx) {
              const chainAttr = m.attrNames[chainAttrIdx];
              const res = await evaluator.evaluateProperty({ locator: nestLocator, prop: chainAttr });
              if (res === 'stopped' || isBrokenNodeChain(res)) {
                break;
              }
              if (chainAttrIdx > 0) {
                let newNest: NestedWindows = {};
                nestTarget[prevStepWasArr ? '[0,0]' : '[0]'] = [
                  { data: { type: 'probe', locator, property: { name: chainAttr }, nested: newNest } },
                ];
                nestTarget = newNest;
              }
              const next = extractPreviousNodeForAttributeChain(res);
              if (next === 'no-such-node') {
                break;
              }
              nestLocator = next;
              prevStepWasArr = res[0]?.type == 'arr';
            }
            displayProbeModal(args.env,
              { x: lastKnownMousePos.x + offset.x, y: lastKnownMousePos.y + offset.y },
              createMutableLocator(locator),
              { name: m.attrNames[0] },
              nestedWindows
            );
          }

          await displayForNode(locator, match, { x: 0, y: 0 });

          if (rhsExpectVal) {
            // Maybe open for right side as well
            const rhsMatch = evaluator.matchNodeAndAttrChain({ lineIdx: match.lineIdx, value: rhsExpectVal });
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
            value: `[${match.attrNames.length ? `Open Normal Probe` : `Create Normal Probe`}](command:${(window as any).CPR_CMD_OPEN_TEXTPROBE_ID})`, isTrusted: true
          }],
        };
      };
      if (isAssignmentMatch(match)) {
        const typeStart = match.index;
        const typeEnd = match.index + match.nodeType.length;
        if (column >= typeStart && column < typeEnd) {
          // Hovering the left side of an assignment.
          // We should resolve the right side, and flash that
          const rhsMatch = evaluator.matchNodeAndAttrChain({ lineIdx: match.lineIdx, value: match.srcVal });
          if (rhsMatch) {
            const res = await resolveNode(evaluator, rhsMatch)
            if (res) {
              if (rhsMatch.attrNames.length) {
                await evaluateAndFlashChain(res, rhsMatch.attrNames, typeStart + 1, typeEnd);
              } else {
                flashNode(res.result, typeStart + 1, typeEnd);
              }
              return returnHoverInfo(res, rhsMatch, undefined);
            }
          }
          return null;
        }
        // Not left side, maybe hovering right side?
        const rhsStart = match.index + match.full.indexOf('=') + 1;
        const rhsEnd = rhsStart + match.srcVal.length;
        if (column < rhsStart || column >= rhsEnd) {
          return null;
        }
        // Hovering right side of assignment
        const rhsMatch = evaluator.matchNodeAndAttrChain({ lineIdx: match.lineIdx, value: match.srcVal });
        if (!rhsMatch) {
          return null;
        }
        const node = await resolveNode(evaluator, rhsMatch);
        if (!node) {
          return null;
        }
        if (!rhsMatch.attrNames.length) {
          // Right side is just a type ref, like "[[$x:=Y]]"
          // Flash the Y
          flashNode(node.result, rhsStart + 1, rhsEnd);
          return returnHoverInfo(node, rhsMatch, undefined);
        } else {
          const hoverRes = await maybeHoverNodeTypeOrAttrChain(node, rhsMatch, rhsStart);
          if (hoverRes.didHoverSomething) {
            return returnHoverInfo(node, rhsMatch, undefined);
          }
        }
        return null;
      }
      // Else, Probe
      const locator = await resolveNode(evaluator, match.lhs);
      if (!locator) {
        console.log('no locator')
        return null;
      }
      const typeStart = match.index;
      let typeEnd;
      if (match.lhs.attrNames.length) {
        typeEnd = lines[line].value.indexOf('.', typeStart);
      } else {
        typeEnd = typeStart + match.lhs.full.length;
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

        const lhsHoverRes = await maybeHoverAttrChain(locator, match.lhs, searchStart);
        searchStart = lhsHoverRes.newSearchStart;
        if (!lhsHoverRes.didHoverSomething && match.rhs?.expectVal?.startsWith('$')) {
          const resultStart = lines[line].value.indexOf('=', searchStart) + 1;
          const resultEnd = resultStart + match.rhs.expectVal.length;
          if (column >= resultStart && column < resultEnd) {
            const rhsMatch = evaluator.matchNodeAndAttrChain({ lineIdx: match.lineIdx, value: match.rhs.expectVal });
            if (rhsMatch) {
              const rhsLocator = await resolveNode(evaluator, rhsMatch);
              if (rhsLocator) {
                await maybeHoverNodeTypeOrAttrChain(rhsLocator, rhsMatch, resultStart);
              }
            }
          }
        }
      }
      return returnHoverInfo(locator, match.lhs, match.rhs?.expectVal);
    }

    return null;
  }

  const hover: TextProbeHoverLogic['hover'] = async (evaluator, line, column) => {
    const res = await doHover(evaluator, line, column);
    if (res === null) {
      flasher.clear();
    }
    return res;
  }

  return { hover, }
}

export { createProbeHoverLogic, HoverResult }
export default TextProbeHoverLogic;
