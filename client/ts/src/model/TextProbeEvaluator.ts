import { NodeLocator, ParsingRequestData, ParsingSource, PropertyArg, RpcBodyLine, SynchronousEvaluationResult, TALStep } from '../protocol';
import ModalEnv from './ModalEnv';
import reallyDoEvaluateProperty from '../network/evaluateProperty';

type GeneralTextMatch = {
  index: number;
  full: string;
  contents: string;
}
const createGeneralTextMatcher = () => {
  const reg = /\[\[(?:(((?!\[\[).)*))\]\](?!\])/g;

  const exec = (line: string): GeneralTextMatch | null => {
    const match = reg.exec(line);
    if (!match) {
      return null;
    }
    const [full, contents] = match;
    if (!contents.match(/^(\$|\w)/)) {
      // Starting with space or somethign similar, ignore it
      return exec(line);
    }
    return {
      index: match.index,
      full,
      contents,
    };
  };
  return { exec }
}

type Line = { lineIdx: number, value: string };

const isAssignmentMatch = (match: AssignmentMatch | ProbeMatch): match is AssignmentMatch => typeof (match as any).srcVal !== 'undefined';
type FullFileMatch = {
  lines: Line[];
  assignments: (AssignmentMatch & { lineIdx: number })[];
  probes: (ProbeMatch & { lineIdx: number })[];
  matchesOnLine: (lineIdx: number) => (FullFileMatch['assignments'][number] | FullFileMatch['probes'][number])[];
}
const matchFullFile = (fullFile: string) => {
  const lines = fullFile.split('\n').map((value, lineIdx) => ({ lineIdx, value }));
  let ret: FullFileMatch = {
    lines,
    assignments: [],
    probes: [],
    matchesOnLine: (lineIdx) => [
      ...ret.assignments.filter(x => x.lineIdx === lineIdx),
      ...ret.probes.filter(x => x.lineIdx === lineIdx),
    ]
  };
  for (let lineIdx = 0; lineIdx < lines.length; ++lineIdx) {
    const line = lines[lineIdx];
    const matcher = createGeneralTextMatcher();
    let match;
    while ((match = matcher.exec(line.value)) != null) {
      const contents = match.contents;

      const assign = matchAssignment(contents);
      if (assign) {
        console.log('matched assign:', assign);
        ret.assignments.push({ ...assign, lineIdx, index: assign.index + match.index + 2 /* 2 for "[[" */ });
      } else {
        const probe = matchTypedProbeRegex({ lineIdx, value: contents });
        if (probe) {
          ret.probes.push({ ...probe, lineIdx, index: probe.index + match.index + 2 /* 2 for "[[" */ });
        }
      }
    }
  }
  return ret;
}

type NodeAndAttrChainMatch = {
  full: string;
  lineIdx: number;
  index: number;
  nodeType: string;
  nodeIndex?: number;
  attrNames: string[];
}
const matchNodeAndAttrChain = (line: Line, strict = false): NodeAndAttrChainMatch | null => {
  const reg = strict
    ? /^(\$?\w+)(\[\d+\])?((?:\.(?:l:)?\w*)*$)/g
    :  /(\$?\w+)(\[\d+\])?((?:\.(?:l:)?\w*)*)/g
    ;
  const match = reg.exec(line.value);
  if (!match) {
    return null;
  }
  // if (strict && match.index > 0) {
  //   return null;
  // }
  const [full, nodeType, nodeIndex, attrNames] = match;
  return {
    full,
    lineIdx: line.lineIdx,
    index: match.index,
    nodeType,
    nodeIndex: nodeIndex ? +nodeIndex.slice(1, -1) : undefined,
    attrNames: attrNames ? attrNames.slice(1).split('.') : [],
  };
}

type ProbeAssertion = {
  exclamation: boolean;
  tilde: boolean;
  expectVal?: string;

}
type ProbeMatch = {
  index: number;
  full: string;
  lhs: NodeAndAttrChainMatch;
  rhs?: ProbeAssertion;
}
const matchTypedProbeRegex = (line: Line): ProbeMatch | null => {
  const nodeMatch = matchNodeAndAttrChain(line);
  if (!nodeMatch) {
    return null;
  }
  const expectMatch = nodeMatch.full.length === line.value.length
    ? null
    : /(!?)(~?)(?:=((.)*))?/g.exec(line.value.slice(nodeMatch.full.length));
  if (!expectMatch) {
    return {
      index: nodeMatch.index,
      full: nodeMatch.full,
      lhs: nodeMatch,
    };
  }
  const [expectFull, exclamation, tilde, expectVal] = expectMatch;
  return {
    index: nodeMatch.index,
    full: line.value.slice(nodeMatch.index, nodeMatch.index + nodeMatch.full.length + expectFull.length),
    lhs: nodeMatch,
    rhs: {
      exclamation: !!exclamation,
      tilde: !!tilde,
      expectVal: typeof expectVal === 'string' ? expectVal : undefined,
    }
  };
}

interface AssignmentMatch {
  index: number;
  full: string;
  nodeType: string;
  srcVal: string;
}
const matchAssignment = (line: string) => {
  const reg = /(\$\w+):=(.*)/g;
  const match = reg.exec(line);
  if (!match) {
    return null;
  }
  const [full, nodeType, srcVal] = match;
  return {
    index: match.index,
    full,
    nodeType,
    srcVal,
  };
}

const doEvaluateProperty = async (env: ModalEnv, evalArgs: {
  locator: NodeLocator;
  prop: string;
  args?: PropertyArg[];
  parsingData?: ParsingRequestData;
}): Promise<SynchronousEvaluationResult | 'stopped'> => {
  const rpcQueryStart = performance.now();
  const res = await reallyDoEvaluateProperty(env, {
    captureStdout: false,
    locator: evalArgs.locator,
    property: { name: evalArgs.prop, args: evalArgs.args },
    src: evalArgs.parsingData ?? env.createParsingRequestData(),
    type: 'EvaluateProperty',
    skipResultLocator: true,
    flattenForTextProbes: true,
  }).fetch();
  if (res !== 'stopped') {
    if (typeof res.totalTime === 'number'
      && typeof res.parseTime === 'number'
      && typeof res.createLocatorTime === 'number'
      && typeof res.applyLocatorTime === 'number'
      && typeof res.attrEvalTime === 'number') {
      env.statisticsCollector.addProbeEvaluationTime({
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

const doEvaluatePropertyChain = async (env: ModalEnv, evalArgs: {
  locator: NodeLocator;
  propChain: string[];
  parsingData?: ParsingRequestData;
}): Promise<SynchronousEvaluationResult | 'stopped' | BrokenNodeChain> => {
  const first = await doEvaluateProperty(env, { locator: evalArgs.locator, prop: evalArgs.propChain[0], parsingData: evalArgs.parsingData, });
  if (first === 'stopped') {
    return 'stopped';
  }
  let prevResult = first;
  for (let subsequentIdx = 1; subsequentIdx < evalArgs.propChain.length; ++subsequentIdx) {
    // Previous result must be a node locator
    if (prevResult.body[0]?.type !== 'node') {
      return { type: 'broken-node-chain', brokenIndex: subsequentIdx - 1 };
    }
    const chainedLocator = prevResult.body[0].value;

    const nextRes = await doEvaluateProperty(env, { locator: chainedLocator, prop: evalArgs.propChain[subsequentIdx], parsingData: evalArgs.parsingData });
    if (nextRes === 'stopped') {
      return 'stopped';
    }
    prevResult = nextRes;
  }
  return prevResult;
}

const doListNodes = async (env: ModalEnv, listArgs: {
  attrFilter: string;
  predicate: string;
  lineIdx: number;
  parsingData?: ParsingRequestData;
}): Promise<NodeLocator[] | null> => {
  const rootNode: TALStep = { type: '<ROOT>', start: ((listArgs.lineIdx + 1) << 12) + 1, end: ((listArgs.lineIdx + 1) << 12) + 4095, depth: 0 };
  const propResult = await doEvaluateProperty(env, {
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

type RangedError = { type: 'error', msg: string, errRange: { colStart: number, colEnd: number } };
type NodeAndAttrResult =
    { type: 'success', output: RpcBodyLine[] }
  | RangedError

const doEvaluateNodeAndAttr = async (
  env: ModalEnv,
  variables: TextProbeEvaluator['variables'],
  lines: Line[],
  args: NodeAndAttrChainMatch,
  preqData: ParsingRequestData,
): Promise<NodeAndAttrResult> => {
  let matchingNodes: NodeLocator[] | null = null;
  if (args.nodeType.startsWith('$')) {
    const lines = variables[args.nodeType];
    if (lines) {
      matchingNodes = [];
      if (!args.attrNames.length) {
        return { type: 'success', output: lines };
      }
      lines.forEach(row => {
        if (row.type === 'node') {
          matchingNodes?.push(row.value);
        }
      });
    }
  } else {
    matchingNodes = await doListNodes(env, {
      attrFilter: args.attrNames[0] ?? '',
      predicate: `this<:${args.nodeType}&@lineSpan~=${args.lineIdx + 1}`,
      lineIdx: args.lineIdx,
      parsingData: preqData,
    });
  }
  // let errMsg: string | null = null;
  const line = lines[args.lineIdx];
  if (!matchingNodes?.length) {
    const typeStart = line.value.indexOf(args.nodeType, args.index);
    // const firstAttrEnd = line.indexOf(match.attrNames[0], line.indexOf('.', typeStart)) + match.attrNames[0].length;
    let firstAttrEnd;
    if (args.attrNames.length) {
      firstAttrEnd = line.value.indexOf(args.attrNames[0], line.value.indexOf('.', typeStart)) + args.attrNames[0].length;
    } else {
      firstAttrEnd = typeStart + args.nodeType.length;
    }
    return {
      type: 'error',
      msg: (args.nodeType.startsWith('$') && !variables[args.nodeType]) ? `No such variable` : `No matching nodes`,
      errRange: { colStart: typeStart + 1, colEnd: firstAttrEnd },
    };
  }
  let matchedNode: NodeLocator | null = null;
  if (args.nodeIndex !== undefined) {
    if (args.nodeIndex < 0 || args.nodeIndex >= matchingNodes.length) {
      const typeEnd = line.value.indexOf(args.nodeType, args.index) + args.nodeType.length;
      const idxStart = line.value.indexOf(`${args.nodeIndex}`, typeEnd);
      return {
        type: 'error',
        msg: `Invalid index`,
        errRange: { colStart: idxStart + 1, colEnd: idxStart + `${args.nodeIndex}`.length },
      };
    }
    matchedNode = matchingNodes[args.nodeIndex];
  } else {
    if (matchingNodes.length === 1) {
      // Only one node, no need for an index
      matchedNode = matchingNodes[0];
    } else {
      const typeStart = line.value.indexOf(args.nodeType, args.index);
      // addErr(lineIdx, typeStart + 1, line.value.indexOf('.', typeStart), `Ambiguous, add "[idx]" to select which "${args.nodeType}" to match. 0 ≤ idx < ${matchingNodes.length}`);
      // return { anyFailure: true, errMsg };
      return {
        type: 'error',
        msg: `${matchingNodes.length} nodes of type "${args.nodeType}". Add [idx] to disambiguate, e.g. "${args.nodeType}[0]"`,
        errRange: { colStart: typeStart + 1, colEnd: line.value.indexOf('.', typeStart) },
      };
    }
  }
  if (!args.attrNames.length) {
    return {
      type: 'success',
      output: [{ type: 'node', value: matchedNode }]
    };
  }

  const attrEvalResult = await doEvaluatePropertyChain(env, {
    locator: matchedNode,
    propChain: args.attrNames,
    parsingData: preqData,
  });
  if (attrEvalResult == 'stopped') {
    // return { anyFailure: false, errMsg };
    return {
      type: 'error',
      msg: `Evaluation stopped`,
      errRange: { colStart: args.index, colEnd: args.index + args.full.length },
    }
  }
  if (isBrokenNodeChain(attrEvalResult)) {
    let attrStart = line.value.indexOf('.', args.index) + 1;
    for (let i = 0; i < attrEvalResult.brokenIndex; ++i) {
      attrStart = line.value.indexOf('.', attrStart) + 1;
    }
    return {
      type: 'error',
      msg: `No AST node returned by "${args.attrNames[attrEvalResult.brokenIndex]}"`, // `Invalid attribute chain`,
      errRange: { colStart: attrStart + 1, colEnd: attrStart + args.attrNames[attrEvalResult.brokenIndex].length },
    }
  }
  if (attrEvalResult.body.length === 1 && attrEvalResult.body[0].type === 'plain' && attrEvalResult.body[0].value.startsWith(`No such attribute '${args.attrNames[args.attrNames.length - 1]}' on `)) {
    let lastAttrStart = args.index;
    for (let i = 0; i < args.attrNames.length; ++i) {
      lastAttrStart = line.value.indexOf('.', lastAttrStart) + 1;
    }
    return {
      type: 'error',
      msg: `No such attribute`,
      errRange: { colStart: lastAttrStart + 1, colEnd: lastAttrStart + args.attrNames[args.attrNames.length - 1].length },
    };
  }
  return {
    type: 'success',
    output: attrEvalResult.body,
  };
};

type VariableLoadResult = NodeAndAttrResult & { assign: AssignmentMatch, lineIdx: number, };
const doLoadVariables = async (evaluator: TextProbeEvaluator): Promise<VariableLoadResult[]> => {
  const ret: VariableLoadResult[] = [];

  for (let i = 0; i < evaluator.fileMatches.assignments.length; ++i) {
    const assign = evaluator.fileMatches.assignments[i];
    const lineIdx = assign.lineIdx;

    if (assign.srcVal.startsWith('$')) {
      const col = assign.index + assign.full.length - assign.srcVal.length;
      ret.push({
        type: 'error',
        assign,
        lineIdx,
        msg: `Cannot use variables on right-hand side of assignment`,
        errRange: { colStart: col, colEnd: col + assign.srcVal.length },
      });
      continue;
    }

    const srcMatch = evaluator.matchNodeAndAttrChain({ lineIdx, value: assign.srcVal }, true);
    if (!srcMatch) {
      const col = assign.index + assign.full.length - assign.srcVal.length;
      ret.push({
        type: 'error',
        assign,
        lineIdx,
        msg: !!assign.srcVal ? `Invalid syntax` : `Missing src val`,
        errRange: { colStart: col, colEnd: col + assign.srcVal.length },
      })
      continue;
    }

    srcMatch.index = assign.index + assign.full.indexOf('=') + 1;
    const actual = await evaluator.evaluateNodeAndAttr(srcMatch);
    if (actual.type === 'success') {
      if (evaluator.variables[assign.nodeType]) {
        ret.push({
          type: 'error',
          assign,
          lineIdx,
          msg: `Duplicate definition of ${assign.nodeType}`,
          errRange: { colStart: assign.index + 1, colEnd: assign.index + assign.full.length },
        });
        continue;
      }
      evaluator.variables[assign.nodeType] = actual.output;
    }
    ret.push({ ...actual, assign, lineIdx, });
  }
  return ret;
}

interface TextProbeEvaluator {
  variables: { [id: string]: RpcBodyLine[] };
  loadVariables: () => Promise<VariableLoadResult[]>;
  parsingData: ParsingRequestData;
  fileMatches: FullFileMatch;
  matchNodeAndAttrChain: typeof matchNodeAndAttrChain;
  evaluateNodeAndAttr: (match: NodeAndAttrChainMatch) => Promise<NodeAndAttrResult>;
  listNodes: (args: {
    attrFilter: string;
    predicate: string;
    lineIdx: number;
  }) => Promise<NodeLocator[] | null>;
  evaluatePropertyChain: (args: {
    locator: NodeLocator;
    propChain: string[];
  }) => Promise<SynchronousEvaluationResult | 'stopped' | BrokenNodeChain>;
  evaluateProperty: (args: {
    locator: NodeLocator;
    prop: string;
    args?: PropertyArg[];
  }) => Promise<SynchronousEvaluationResult | 'stopped'>;
}

const createTextProbeEvaluator = (env: ModalEnv, parsingSource?: string | { src: ParsingSource, contents: string }): TextProbeEvaluator => {
  let parsingData: ParsingRequestData;
  let fullFile;
  if (!parsingSource) {
    parsingData = env.createParsingRequestData();
    fullFile = env.getLocalState();
  } else if (typeof parsingSource === 'string') {
    parsingData = { ...env.createParsingRequestData(), src: { type: 'text', value: parsingSource } };
    fullFile = parsingSource;
  } else {
    parsingData = { ...env.createParsingRequestData(), src: parsingSource.src };
    fullFile = parsingSource.contents;
  }
  const variables: TextProbeEvaluator['variables'] = {};
  const fileMatches: TextProbeEvaluator['fileMatches'] = matchFullFile(fullFile);

  let variableLoadPromise: ReturnType<TextProbeEvaluator['loadVariables']> | null = null;
  const loadVariables: TextProbeEvaluator['loadVariables'] = async () => {
    if (!variableLoadPromise) {
      variableLoadPromise = doLoadVariables(ret);
    }
    return variableLoadPromise;
    // return doLoadVariables(ret);
  };
  const evaluateNodeAndAttr: TextProbeEvaluator['evaluateNodeAndAttr'] = async (match) => {
    return doEvaluateNodeAndAttr(env, variables, fileMatches.lines, match, parsingData);
  }
  const listNodes: TextProbeEvaluator['listNodes'] = async (args) => {
    return doListNodes(env, { ...args, parsingData });
  };
  const evaluatePropertyChain: TextProbeEvaluator['evaluatePropertyChain'] = async (args) => {
    return doEvaluatePropertyChain(env, { ...args, parsingData });
  };
  const evaluateProperty: TextProbeEvaluator['evaluateProperty'] = async (args) => {
    return doEvaluateProperty(env, { ...args, parsingData });
  };

  const ret: TextProbeEvaluator = {
    variables,
    loadVariables,
    parsingData,
    fileMatches,
    matchNodeAndAttrChain,
    evaluateNodeAndAttr,
    listNodes,
    evaluatePropertyChain,
    evaluateProperty,
  };
  return ret;
}

export { createTextProbeEvaluator, isAssignmentMatch, isBrokenNodeChain, NodeAndAttrChainMatch };
export default TextProbeEvaluator;
