import { assertUnreachable } from '../hacks';
import doEvaluateProperty from '../network/evaluateProperty';
import { ListPropertiesReq, ListPropertiesRes, NodeLocator, ParsingRequestData, ParsingSource, PropertyArg, RpcBodyLine, SynchronousEvaluationResult, TALStep } from '../protocol';
import settings from '../settings';
import createCullingTaskSubmitterFactory from './cullingTaskSubmitterFactory';
import ModalEnv from './ModalEnv'

interface TextProbeManagerArgs {
  env: ModalEnv;
  onFinishedCheckingActiveFile: (res: TextProbeCheckResults) => void;
}

interface TextProbeCheckResults {
  numPass: number;
  numFail: number;
}

interface TextProbeManager {
  complete: (line: number, column: number) => Promise<string[] | null>,
  checkFile: (requestSrc: ParsingSource, knownSrc: string) => Promise<TextProbeCheckResults | null>,
};

type TextProbeStyle = 'angle-brackets' | 'disabled';

const createTypedProbeRegex = () => {
  const reg = /\[\[(\w+)(\[\d+\])?((?:\.\w+)+)(!?)(~?)(?:=(((?!\[\[).)*))?\]\](?!\])/g;

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

  const evaluatePropertyChain = async (evalArgs: {
    locator: NodeLocator;
    propChain: string[];
    parsingData?: ParsingRequestData;
  }): Promise<SynchronousEvaluationResult | 'stopped' | 'broken-node-chain'> => {
    const first = await evaluateProperty({ locator: evalArgs.locator, prop: evalArgs.propChain[0], parsingData: evalArgs.parsingData, });
    if (first === 'stopped') {
      return 'stopped';
    }
    let prevResult = first;
    for (let subsequentIdx = 1; subsequentIdx < evalArgs.propChain.length; ++subsequentIdx) {
      // Previous result must be a node locator
      if (prevResult.body[0]?.type !== 'node') {
        console.log('prevResult does not look like a reference attribute:', prevResult.body);
        return 'broken-node-chain';
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

  let activeRefresh = false;
  let repeatOnDone = false;
  const activeStickies: string[] = [];
  const doRefresh = async () => {
    if (settings.getTextProbeStyle() === 'disabled') {
      for (let i = 0; i < activeStickies.length; ++i) {
        args.env.clearStickyHighlight(activeStickies[i]);
      }
      activeStickies.length = 0;
      args.onFinishedCheckingActiveFile({ numFail: 0, numPass: 0 })
      return;
    }
    if (activeRefresh) {
      repeatOnDone = true;
      return;
    }
    // args.env.clearStickyHighlight(queryId);
    activeRefresh = true;
    repeatOnDone = false;
    let nextStickyIndex = 0;
    const getStickyId = () => {
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
          } else {
            let matchedNode: NodeLocator | null = null;
            if (match.nodeIndex !== undefined) {
              console.log('got nodeindex:', match.nodeIndex);
              if (match.nodeIndex < 0 || match.nodeIndex >= matchingNodes.length) {
                ++numFail;
                errMsg = `Invalid index`;
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
              if (attrEvalResult === 'broken-node-chain') {
                ++numFail;
                errMsg = 'Invalid attribute chain';
              } else {
                const cmp = evalPropertyBodyToString(attrEvalResult.body)
                if (match.expectVal === undefined) {
                  if (!errMsg) {
                    // Just a probe, save the result as a message
                    errMsg = `Actual: ${cmp}`;
                  }
                } else {
                  const rawComparisonSuccess = match.tilde ? cmp.includes(match.expectVal) : (cmp === match.expectVal);
                  const adjustedComparisonSuccsess = match.exclamation ? !rawComparisonSuccess : rawComparisonSuccess;
                  if (adjustedComparisonSuccsess) {
                    ++numPass;
                  } else {
                    console.log('fail, "', match.expectVal, '" != "', cmp, '"');
                    errMsg = `Actual: ${cmp}`;
                    ++numFail;
                  }
                }
              }
            }
          }

          // console.log('combining..', numPass, numFail, errMsg);
          combinedResults.numPass += numPass;
          combinedResults.numFail += numFail;

          const span: Span = { lineStart: (lineIdx + 1), colStart: match.index + 1, lineEnd: (lineIdx + 1), colEnd: (match.index + match.full.length) };
          const inlineTextSpan = { ...span, colStart: span.colEnd - 2, colEnd: span.colEnd - 1 };
          args.env.setStickyHighlight(getStickyId(), {
            classNames: [numFail ? 'elp-result-fail' : (numPass ? 'elp-result-success' : 'elp-result-probe')],
            span,
          });
          if (errMsg) {
            args.env.setStickyHighlight(getStickyId(), {
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
        if (chainResult === 'stopped' || chainResult === 'broken-node-chain') {
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
      if (attrEvalResult == 'stopped' || attrEvalResult === 'broken-node-chain') {
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
          if (evalRes === 'broken-node-chain') {
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


  return { complete, checkFile }
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
