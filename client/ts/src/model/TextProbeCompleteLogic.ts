import { ListPropertiesReq, ListPropertiesRes, NodeLocator, TALStep } from '../protocol';
import settings from '../settings';
import startEndToSpan from '../ui/startEndToSpan';
import evalPropertyBodyToString from './evalPropertyBodyToString';
import ModalEnv from './ModalEnv';
import SpanFlasher from './SpanFlasher';
import TextProbeEvaluator, { isAssignmentMatch, isBrokenNodeChain, NodeAndAttrChainMatch } from './TextProbeEvaluator';

interface CompletionItem {
  label: string;
  insertText: string;
  kind: number; // See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#completionItemKind
  sortText?: string;
  detail?: string;
}

interface CompletionResponse {
  from: { line: number, column: number };
  items: CompletionItem[];
}

interface TextProbeCompleteLogic {
  complete: (evaluator: TextProbeEvaluator, line: number, column: number) => Promise<CompletionResponse | null>,
}

interface CreateTextProbeCompleteLogicArgs {
  env: ModalEnv;
  flasher: SpanFlasher;
}

const createTextProbeCompleteLogic = (args: CreateTextProbeCompleteLogicArgs): TextProbeCompleteLogic => {
  const { flasher } = args;

  const complete: TextProbeCompleteLogic['complete'] = async (evaluator, line, column) => {
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
    ): Promise<CompletionResponse | null> => {
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
        if ((item as any)?.nodeIndex === 'undefined') {
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
        flasher.flash([startEndToSpan(node.start, node.end + 1)]);
      };
      return {
        from: { line: line + 1, column: column + 1 - existingText.length },
        items: matchingNodes.map((node, nodeIndex) => {
          const type = node.label ?? node.type;
          const label = type.split('.').slice(-1)[0];
          let detail: string | undefined = undefined;
          if (type.startsWith('$')) {
            // It is a variable, add the type as detail
            detail = node.type.split('.').slice(-1)[0];
          }
          const ret: (CompletionItem & { nodeIndex: number }) = { label, insertText: label, kind: 7, nodeIndex, sortText: `${matchingNodes.length - nodeIndex}`.padStart(4, '0'), detail };
          if (!duplicateTypes.has(type)) {
            if (label.length > existingText.length && label.startsWith(existingText)) {
              ret.insertText = label.slice(existingText.length);
            }
            return ret;
          }
          let idx = (nodeListCounter[type] ?? -1) + 1;
          nodeListCounter[type] = idx;

          const indexed = `${label}[${idx}]`;
          ret.label = indexed;
          ret.insertText = indexed;
          return ret;
        })
      };
    }
    const completeProp = async (
      nodeType: string,
      nodeIndex: number | undefined,
      prerequisiteAttrs: string[], previousStepSpan: [number, number],
      existingText: string,
    ): Promise<CompletionResponse | null> => {
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
        if (chainResult === 'stopped' || isBrokenNodeChain(chainResult) || chainResult[0]?.type !== 'node') {
          // Ignore, default to flashing the origin node
        } else {
          // Else, flash the more precise node
          locator = chainResult[0].value;
        }
      }
      flasher.flash([
        {
          lineStart: line + 1, colStart: previousStepSpan[0],
          lineEnd: line + 1, colEnd: previousStepSpan[1] + 1,
        },
        {
          lineStart: locator.result.start >>> 12, colStart: locator.result.start & 0xFFF,
          lineEnd: locator.result.end >>> 12, colEnd: (locator.result.end & 0xFFF) + 1,
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
        attrChain: prerequisiteAttrs
      });
      if (!props.properties) {
        return null;
      }
      const zeroArgPropNames = new Set(props.properties.filter(prop => (prop.args?.length ?? 0) === 0).map(prop => prop.name));
      return {
        from: { line: line + 1, column: column + 1 - existingText.length },
        items: [...zeroArgPropNames].sort().map(label => {
          return { label, insertText: label, kind: 2 };
        })
      };
    }
    const completeExpectedValue = async (
      nodeType: string,
      nodeIndex: number | undefined,
      attrNames: string[],
      existingText: string,
    ): Promise<CompletionResponse | null> => {
      let locator: NodeLocator | undefined = undefined;
      if (nodeType.startsWith('$')) {
        await evaluator.loadVariables();
        const varData = evaluator.variables[nodeType];
        if (varData && !attrNames.length) {
          const cmp = evalPropertyBodyToString(varData);
          return {
            from: { line: line + 1, column: column + 1 },
            items: [{ label: cmp, insertText: cmp, kind: 15 }],
          };
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
        return {
          from: { line: line + 1, column: column + 1 },
          items: [{ label: cmp, insertText: cmp, kind: 15 }],
        };
      }
      const attrEvalResult = await evaluator.evaluatePropertyChain({
        locator,
        propChain: attrNames,
      });
      if (attrEvalResult == 'stopped' || isBrokenNodeChain(attrEvalResult)) {
        return null;
      }
      if (attrEvalResult[0]?.type === 'node') {
        const node = attrEvalResult[0].value.result;
        if (!node.external) {
          flasher.flash([startEndToSpan(node.start, node.end + 1)]);
          window.OnCompletionItemListClosed = () => {
            flasher.clear();
          }
        }
      }
      const cmp = evalPropertyBodyToString(attrEvalResult)
      return {
        from: { line: line + 1, column: column + 1 - existingText.length },
        items: [{ label: cmp, insertText: cmp, kind: 15 }],
      };
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
      const maybeCompleteTypeOrAttr = async (m: NodeAndAttrChainMatch, typeFilter: PermittedTypeFilter = 'allow-all'): Promise<ReturnType<TextProbeCompleteLogic['complete']>> => {
        if (column >= typeStart && column <= typeEnd) {
          return completeType(typeFilter, lines[line].value.slice(typeStart, column));
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
            return completeProp(m.nodeType, m.nodeIndex, m.attrNames.slice(0, attrIdx), [typeStart + 1, attrStart - 1], attr.slice(0, column - attrStart));
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
      typeEnd = computeTypeEnd(lhsMatch.lhs);

      const lhsComplete = await maybeCompleteTypeOrAttr(lhsMatch.lhs);
      if (lhsComplete) {
        return lhsComplete;
      }

      if (!lhsMatch.rhs) {
        return null;
      }
      const currExpectVal = lhsMatch.rhs.expectVal ?? '';
      const expectStart = lines[line].value.indexOf('=', attrSearchStart) + 1;
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
          return maybeCompleteTypeOrAttr(rhsMatch, 'forbid-types');
        } else {
          return completeExpectedValue(lhsMatch.lhs.nodeType, lhsMatch.lhs.nodeIndex, lhsMatch.lhs.attrNames, currExpectVal.slice(0, column - expectStart));
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
        return completeType('allow-all', lines[line].value.slice(typeStart, column));
      }
    }

    return null;
  };

  return { complete, };
}

export { CompletionItem, CompletionResponse, createTextProbeCompleteLogic }
export default TextProbeCompleteLogic;
