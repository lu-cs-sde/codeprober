import { assertUnreachable } from '../hacks';
import doEvaluateProperty from '../network/evaluateProperty';
import { ListPropertiesReq, ListPropertiesRes, NodeLocator, ParsingRequestData, ParsingSource, PropertyArg, RpcBodyLine, SynchronousEvaluationResult, TALStep } from '../protocol';
import settings from '../settings';
import displayProbeModal from '../ui/popup/displayProbeModal';
import createCullingTaskSubmitterFactory from './cullingTaskSubmitterFactory';
import ModalEnv from './ModalEnv'
import SourcedDiagnostic from './SourcedDiagnostic';
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
interface TextProbeManager {
  hover: (line: number, column: number) => Promise<HoverResult | null>,
  complete: (line: number, column: number) => Promise<string[] | null>,
  checkFile: (requestSrc: ParsingSource, knownSrc: string) => Promise<TextProbeCheckResults | null>,
};

type TextProbeStyle = 'angle-brackets' | 'disabled';

const createTypedProbeRegex = () => {
  const reg = /\[\[(\w+)(\[\d+\])?((?:\.\w*)+)(!?)(~?)(?:=(((?!\[\[).)*))?\]\](?!\])/g;

  return {
    exec: (line: string) => {
      const match = reg.exec(line);
      if (!match) {
        return null;
      }
      const [full, nodeType, nodeIndex, attrNames, exclamation, tilde, expectVal] = match;
      return {
        index: match.index,
        full,
        nodeType,
        nodeIndex: nodeIndex ? +nodeIndex.slice(1, -1) : undefined,
        attrNames: attrNames.slice(1).split('.'),
        exclamation: !!exclamation,
        tilde: !!tilde,
        expectVal: typeof expectVal === 'string' ? expectVal : undefined,
      };
    }
  }
}

const createForgivingProbeRegex = () => {
  // Neede for autocompletion
  const reg = /\[\[(\w*)(\[\d+\])?((?:\.\w*)+)?\]\](?!\])/g;

  return {
    exec: (line: string) => {
      const match = reg.exec(line);
      if (!match) {
        return null;
      }
      const [full, nodeType, nodeIndex, attrNames] = match;
      return {
        index: match.index,
        full,
        nodeType,
        nodeIndex: nodeIndex ? +nodeIndex.slice(1, -1) : undefined,
        attrNames: attrNames ? attrNames.slice(1).split('.') : undefined,
      };

    }
  }

}

const setupTextProbeManager = (args: TextProbeManagerArgs): TextProbeManager => {
  const refreshDispatcher = createCullingTaskSubmitterFactory(1)()
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;

  const evaluateProperty = async (evalArgs: {
    locator: NodeLocator;
    prop: string;
    args?: PropertyArg[];
    parsingData?: ParsingRequestData;
  }): Promise<SynchronousEvaluationResult | 'stopped'> => {
    const rpcQueryStart = performance.now();
    const res = await doEvaluateProperty(args.env, {
      captureStdout: false,
      locator: evalArgs.locator,
      property: { name: evalArgs.prop, args: evalArgs.args },
      src: evalArgs.parsingData ?? args.env.createParsingRequestData(),
      type: 'EvaluateProperty',
      skipResultLocator: true,
    }).fetch();
    if (res !== 'stopped') {
      if (typeof res.totalTime === 'number'
        && typeof res.parseTime === 'number'
        && typeof res.createLocatorTime === 'number'
        && typeof res.applyLocatorTime === 'number'
        && typeof res.attrEvalTime === 'number' ) {
        args.env.statisticsCollector.addProbeEvaluationTime({
          attrEvalMs: res.attrEvalTime / 1_000_000.0,
          fullRpcMs: Math.max(performance.now() - rpcQueryStart),
          serverApplyLocatorMs: res.applyLocatorTime / 1_000_000.0,
          serverCreateLocatorMs: res.createLocatorTime / 1_000_000.0,
          serverParseOnlyMs: res.parseTime / 1_000_000.0,
          serverSideMs: res.totalTime / 1_000_000.0,
        });
      }
    }
    return res;
  }

  type BrokenNodeChain = { type: 'broken-node-chain', brokenIndex: number };
  const isBrokenNodeChain = (val: any): val is BrokenNodeChain => val && (typeof val === 'object') && (val as BrokenNodeChain).type === 'broken-node-chain';

  const evaluatePropertyChain = async (evalArgs: {
    locator: NodeLocator;
    propChain: string[];
    parsingData?: ParsingRequestData;
  }): Promise<SynchronousEvaluationResult | 'stopped' | BrokenNodeChain> => {
    const first = await evaluateProperty({ locator: evalArgs.locator, prop: evalArgs.propChain[0], parsingData: evalArgs.parsingData, });
    if (first === 'stopped') {
      return 'stopped';
    }
    let prevResult = first;
    for (let subsequentIdx = 1; subsequentIdx < evalArgs.propChain.length; ++subsequentIdx) {
      // Previous result must be a node locator
      if (prevResult.body[0]?.type !== 'node') {
        console.log('prevResult does not look like a reference attribute:', prevResult.body);
        return { type: 'broken-node-chain', brokenIndex: subsequentIdx - 1 };
      }
      const chainedLocator = prevResult.body[0].value;

      const nextRes = await evaluateProperty({ locator: chainedLocator, prop: evalArgs.propChain[subsequentIdx], parsingData: evalArgs.parsingData });
      if (nextRes === 'stopped') {
        return 'stopped';
      }
      prevResult = nextRes;
    }
    return prevResult;
  }

  const listNodes = async (listArgs: {
    attrFilter: string;
    predicate: string;
    zeroIndexedLine: number;
    parsingData?: ParsingRequestData;
  }): Promise<NodeLocator[] | null> => {
    const rootNode: TALStep = { type: '<ROOT>', start: ((listArgs.zeroIndexedLine + 1) << 12) + 1, end: ((listArgs.zeroIndexedLine + 1) << 12) + 4095, depth: 0 };
    const propResult = await evaluateProperty({
      locator: { result: rootNode, steps: [] },
      prop: 'm:NodesWithProperty',
      args: [
        { type: 'string', value: listArgs.attrFilter },
        { type: 'string', value: listArgs.predicate }
      ],
      parsingData: listArgs.parsingData,
    });

    if (propResult === 'stopped') {
      return null;
    }

    if (!propResult.body.length || propResult.body[0].type !== 'arr') {
      console.error('Unexpected respose from search query:', propResult);
      return null;
    }
    const queryResultList = propResult.body[0].value;
    const ret: NodeLocator[] = [];
    queryResultList.forEach(line => {
      if (line.type === 'node') {
        ret.push(line.value);
      }
    })
    return ret;
  };

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
      const preqData = args.env.createParsingRequestData();
      const lines = args.env.getLocalState().split('\n');
      for (let lineIdx = 0; lineIdx < lines.length; ++lineIdx) {
        const line = lines[lineIdx];
        const reg = createTypedProbeRegex();
        let match;
        function addErr(colStart: number, colEnd: number, msg: string) {
          diagnostics.push({ type: 'ERROR', start: ((lineIdx + 1) << 12) + colStart, end: ((lineIdx + 1) << 12) + colEnd, msg, source: 'Text Probe' });
        }
        while ((match = reg.exec(line)) !== null) {
          const matchingNodes = await listNodes({
            attrFilter: match.attrNames[0],
            predicate: `this<:${match.nodeType}&@lineSpan~=${lineIdx + 1}`,
            zeroIndexedLine: lineIdx,
            parsingData: preqData,
          });
          let numPass = 0, numFail = 0;
          let errMsg: string | null = null;
          if (!matchingNodes?.length) {
            ++numFail;
            errMsg = `No matching nodes`;
            const typeStart = line.indexOf(match.nodeType, match.index);
            const firstAttrEnd = line.indexOf(match.attrNames[0], line.indexOf('.', typeStart)) + match.attrNames[0].length;
            addErr(typeStart + 1, firstAttrEnd, errMsg);
          } else {
            let matchedNode: NodeLocator | null = null;
            if (match.nodeIndex !== undefined) {
              if (match.nodeIndex < 0 || match.nodeIndex >= matchingNodes.length) {
                ++numFail;
                errMsg = `Invalid index`;
                const typeEnd = line.indexOf(match.nodeType, match.index) + match.nodeType.length;
                const idxStart = line.indexOf(`${match.nodeIndex}`, typeEnd);
                addErr(idxStart + 1, idxStart + `${match.nodeIndex}`.length, `Bad Index`);
              } else {
                matchedNode = matchingNodes[match.nodeIndex];
              }
            } else {
              if (matchingNodes.length === 1) {
                // Only one node, no need for an index
                matchedNode = matchingNodes[0];
              } else {
                ++numFail;
                errMsg = `${matchingNodes.length} nodes of type "${match.nodeType}". Add [idx] to disambiguate, e.g. "${match.nodeType}[0]"`;
                const typeStart = line.indexOf(match.nodeType, match.index);
                addErr(typeStart + 1, line.indexOf('.', typeStart), `Ambiguous, add "[idx]" to select which "${match.nodeType}" to match. 0 ≤ idx < ${matchingNodes.length}`);
              }
            }
            if (matchedNode) {
              const attrEvalResult = await evaluatePropertyChain({
                locator: matchedNode,
                propChain: match.attrNames,
                parsingData: preqData,
              });
              if (attrEvalResult == 'stopped') {
                break;
              }
              if (isBrokenNodeChain(attrEvalResult)) {
                ++numFail;
                errMsg = 'Invalid attribute chain';
                let attrStart = line.indexOf('.', match.index) + 1;
                for (let i = 0; i < attrEvalResult.brokenIndex; ++i) {
                  attrStart = line.indexOf('.', attrStart) + 1;
                }
                addErr(attrStart + 1, attrStart + match.attrNames[attrEvalResult.brokenIndex].length, `No AST node returned by "${match.attrNames[attrEvalResult.brokenIndex]}"`);
              } else {
                const cmp = evalPropertyBodyToString(attrEvalResult.body)
                if (attrEvalResult.body.length === 1 && attrEvalResult.body[0].type === 'plain' && attrEvalResult.body[0].value.startsWith(`No such attribute '${match.attrNames[match.attrNames.length - 1]}' on `)) {
                  let lastAttrStart = match.index;
                  for (let i = 0; i < match.attrNames.length; ++i) {
                    lastAttrStart = line.indexOf('.', lastAttrStart) + 1;
                  }
                  addErr(lastAttrStart + 1, lastAttrStart + match.attrNames[match.attrNames.length - 1].length, 'No such attribute');
                }
                if (match.expectVal === undefined) {
                  if (!errMsg) {
                    // Just a probe, save the result as a message
                    errMsg = `Result: ${cmp}`;
                  }
                } else {
                  const rawComparisonSuccess = match.tilde ? cmp.includes(match.expectVal) : (cmp === match.expectVal);
                  const adjustedComparisonSuccsess = match.exclamation ? !rawComparisonSuccess : rawComparisonSuccess;
                  if (adjustedComparisonSuccsess) {
                    ++numPass;
                  } else {
                    errMsg = `Actual: ${cmp}`;
                    ++numFail;
                  }
                }
              }
            }
          }

          combinedResults.numPass += numPass;
          combinedResults.numFail += numFail;

          const span: Span = { lineStart: (lineIdx + 1), colStart: match.index + 1, lineEnd: (lineIdx + 1), colEnd: (match.index + match.full.length) };
          const inlineTextSpan = { ...span, colStart: span.colEnd - 2, colEnd: span.colEnd - 1 };
          args.env.setStickyHighlight(allocateStickyId(), {
            classNames: [numFail ? 'elp-result-fail' : (numPass ? 'elp-result-success' : 'elp-result-probe')],
            span,
          });
          if (errMsg) {
            args.env.setStickyHighlight(allocateStickyId(), {
              classNames: [],
              span: inlineTextSpan,
              content: errMsg,
              contentClassNames: [`elp-actual-result-${numFail ? 'err' : 'probe'}`],
            });
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

  const hover: TextProbeManager['hover'] = async (line, column) => {
    if (settings.getTextProbeStyle() === 'disabled') {
      return null;
    }
    const lines = args.env.getLocalState().split('\n');
    // Make line/col 0-indexed
    --line;
    --column;
    if (line < 0 || line >= lines.length) {
      return null;
    }

    const reg = createTypedProbeRegex();
    let match;
    while ((match = reg.exec(lines[line])) !== null) {
      const begin = match.index;
      if (column < begin) {
        continue;
      }
      const end = begin + match.full.length;
      if (column >= end) {
        continue;
      }
      const fmatch = match;
      const parsingData = args.env.createParsingRequestData();
      const listedNodes = await listNodes({ attrFilter: fmatch.attrNames[0], predicate: `this<:${fmatch.nodeType}&@lineSpan~=${line + 1}`, zeroIndexedLine: line, parsingData  });
      const locator = listedNodes?.[match.nodeIndex ?? 0];
      if (!locator) {
        return null;
      }

      (window as any).CPR_CMD_OPEN_TEXTPROBE_CALLBACK = async () => {
        const nestedWindows: NestedWindows = {};
        let nestTarget = nestedWindows;
        let nestLocator = locator;
        for (let chainAttrIdx = 0; chainAttrIdx < fmatch.attrNames.length; ++chainAttrIdx) {
          const chainAttr = fmatch.attrNames[chainAttrIdx];
          const res = await evaluateProperty({ locator: nestLocator, prop: chainAttr, parsingData  });
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
        displayProbeModal(args.env, null, createMutableLocator(locator), { name: fmatch.attrNames[0] }, nestedWindows);
      };
      return {
        range: { startLineNumber: line + 1, startColumn: column + 1, endLineNumber: line + 1, endColumn: column + 3},
        contents: [{
          value: `[Open Normal Probe](command:${(window as any).CPR_CMD_OPEN_TEXTPROBE_ID})`, isTrusted: true
        }],
      };
    }

    return null;
  }

  const complete: TextProbeManager['complete'] = async (line, column) => {
    if (settings.getTextProbeStyle() === 'disabled') {
      return null;
    }
    const lines = args.env.getLocalState().split('\n');
    // Make line/col 0-indexed
    --line;
    --column;
    if (line < 0 || line >= lines.length) {
      return null;
    }
    const reg = createTypedProbeRegex();
    let match;

    const completeType = async () => {
      const matchingNodes = await listNodes({
        attrFilter: '',
        predicate: `@lineSpan~=${line + 1}`,
        zeroIndexedLine: line,
      });
      if (matchingNodes === null) {
        return null;
      }
      const types = new Set<string>(matchingNodes.map(loc => loc.result.label ?? loc.result.type));
      return [...types].sort().map(type => type.split('.').slice(-1)[0]);
    }
    const completeProp = async (nodeType: string, nodeIndex: number | undefined, prerequisiteAttrs: string[]) => {
      const parsingData = args.env.createParsingRequestData();
      const matchingNodes = await listNodes({
          attrFilter: '',
          predicate: `this<:${nodeType}&@lineSpan~=${line + 1}`,
          zeroIndexedLine: line,
          parsingData,
      });
      let locator = matchingNodes?.[nodeIndex ?? 0];
      if (!locator) {
        return null;
      }
      if (prerequisiteAttrs.length != 0) {
        const chainResult = await evaluatePropertyChain({ locator, propChain: prerequisiteAttrs, parsingData });
        if (chainResult === 'stopped' || isBrokenNodeChain(chainResult)) {
          return null;
        }
        if (chainResult.body[0]?.type !== 'node') {
          return null;
        }
        locator = chainResult.body[0].value;
      }
      const props = await args.env.performTypedRpc<ListPropertiesReq, ListPropertiesRes>({
        locator,
        src: parsingData,
        type: 'ListProperties',
        all: settings.shouldShowAllProperties(),
      });
      if (!props.properties) {
        return null;
      }
      const zeroArgPropNames = new Set(props.properties.filter(prop => (prop.args?.length ?? 0) === 0).map(prop => prop.name));
      return [...zeroArgPropNames].sort();
    }
    const completeExpectedValue = async (nodeType: string, nodeIndex: number | undefined, attrNames: string[]) => {
      const parsingData = args.env.createParsingRequestData();
      const matchingNodes = await listNodes({
        attrFilter: '',
        predicate: `this<:${nodeType}&@lineSpan~=${line + 1}`,
        zeroIndexedLine: line,
        parsingData,
      });
      let locator = matchingNodes?.[nodeIndex ?? 0];
      if (!locator) {
        return null;
      }
      const attrEvalResult = await evaluatePropertyChain({
        locator,
        propChain: attrNames,
        parsingData,
      });
      if (attrEvalResult == 'stopped' || isBrokenNodeChain(attrEvalResult)) {
        return null;
      }
      const cmp = evalPropertyBodyToString(attrEvalResult.body)
      return [cmp];
    };

    while ((match = reg.exec(lines[line])) !== null) {
      if (match.index >= column) {
        // Cursor is before the match
        continue;
      }
      if (match.index + match.full.length <= column) {
        // Cursor is before the match
        continue;
      }

      const typeStart = match.index + 2;
      const typeEnd = typeStart + match.nodeType.length;
      if (column >= typeStart && column <= typeEnd) {
        return completeType();
      }
      let attrSearchStart = typeEnd;
      for (let attrIdx = 0; attrIdx < match.attrNames.length; ++attrIdx) {
        const attrStart = lines[line].indexOf('.', attrSearchStart) + 1;;
        const attrEnd = attrStart + match.attrNames[attrIdx].length;
        attrSearchStart = attrEnd;
        if (column >= attrStart && column <= attrEnd) {
          return completeProp(match.nodeType, match.nodeIndex, match.attrNames.slice(0, attrIdx));
        }
      }

      if (match.expectVal !== undefined) {
        const expectStart = lines[line].indexOf('=', attrSearchStart) + 1;;
        const expectEnd = expectStart + match.expectVal.length;
        if (column >= expectStart && column <= expectEnd) {
          return completeExpectedValue(match.nodeType, match.nodeIndex, match.attrNames);
        }
      }
    }

    const forgivingReg = createForgivingProbeRegex();
    while ((match = forgivingReg.exec(lines[line])) !== null) {
      if (match.index >= column) {
        // Cursor is before the match
        continue;
      }
      if (match.index + match.full.length <= column) {
        // Cursor is before the match
        continue;
      }
      const typeStart = match.index + 2;
      const typeEnd = typeStart + match.nodeType.length;
      if (column >= typeStart && column <= typeEnd) {
        return completeType();
      }
      if (match.attrNames) {
        let attrSearchStart = typeEnd;
        for (let attrIdx = 0; attrIdx < match.attrNames.length; ++attrIdx) {
          const attrStart = lines[line].indexOf('.', attrSearchStart) + 1;;
          const attrEnd = attrStart + match.attrNames[attrIdx].length;
          attrSearchStart = attrEnd;
          if (column >= attrStart && column <= attrEnd) {
            return completeProp(match.nodeType, match.nodeIndex, match.attrNames.slice(0, attrIdx));
          }
        }
      }
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
    const parsingData: ParsingRequestData = {
      ...args.env.createParsingRequestData(),
      src: requestSrc,
    };
    const lines = knownSrc.split('\n');
    for (let lineIdx = 0; lineIdx < lines.length; ++lineIdx) {
      const reg = createTypedProbeRegex();
      let match: ReturnType<typeof reg['exec']>;
      while ((match = reg.exec(lines[lineIdx])) != null) {
        if (match.expectVal === undefined) {
          // Just a probe, no assertion, no need to check
          continue;
        }
        const allNodes = await listNodes({
          attrFilter: match.attrNames[0],
          predicate: `this<:${match.nodeType}&@lineSpan~=${lineIdx+1}`,
          zeroIndexedLine: lineIdx,
          parsingData,
        });
        if (!allNodes?.length) {
          ret.numFail++;
          continue;
        }
        if (match.nodeIndex === undefined && allNodes.length !== 1) {
          // Must have explicit index when >1 node
          ++ret.numFail;
          continue;
        }
        const locator = allNodes[match.nodeIndex ?? 0];
        if (!locator) {
          // Invalid index
          ++ret.numFail;
          continue;
        }

        // for (let i = 0; i < nodes.length; ++i) {
          const evalRes = await evaluatePropertyChain({ locator, propChain: match.attrNames, parsingData })
          if (evalRes === 'stopped') {
            // TODO is this a failure?
            continue;
          }
          if (isBrokenNodeChain(evalRes)) {
            ret.numFail++;
            continue;
          }
          const cmp = evalPropertyBodyToString(evalRes.body)
          const rawComparisonSuccess = match.tilde ? cmp.includes(match.expectVal) : (cmp === match.expectVal);
          const adjustedComparisonSuccsess = match.exclamation ? !rawComparisonSuccess : rawComparisonSuccess;
          if (adjustedComparisonSuccsess) {
            ret.numPass++;
          } else {
            ret.numFail++;
          }
        // }
      }
    }

    return ret;
  };

  return { hover, complete, checkFile }
}

const evalPropertyBodyToString = (body: RpcBodyLine[]): string => {
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
        const ret = line.value.result.label ?? line.value.result.type;
        return ret.slice(ret.lastIndexOf('.') + 1);
      case 'highlightMsg':
        return line.value.msg;
      case 'tracing':
        return lineToComparisonString(line.value.result);
      default:
        assertUnreachable(line);
        return '';
      }
  }
  return lineToComparisonString(body.length === 1 ? body[0] : { type: 'arr', value: body });
}

export { setupTextProbeManager, TextProbeCheckResults, TextProbeStyle };
export default TextProbeManager;
