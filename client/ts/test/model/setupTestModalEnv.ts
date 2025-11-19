import ModalEnv from '../../src/model/ModalEnv';
import { EvaluatePropertyReq, EvaluatePropertyRes, ListPropertiesReq, ListPropertiesRes, NodeLocator, RpcBodyLine, SynchronousEvaluationResult } from '../../src/protocol';

interface Node {
  type: string;
  start: number;
  end: number;
  props: { [name: string]: () => any };
}
const nodeToLocator = (node: Node): NodeLocator => ({ steps: [], result: { ...node, depth: 0 }, });


interface TestModalEnvArgs {
  nodes: Node[];
  src: string;
}

const looksLikeTestNode = (val: any) => {
  return val?.type && val?.start && val?.end;
}

const setupTestModalEnv = (setupArgs: TestModalEnvArgs): ModalEnv => {

  const evaluateProperty = (req: EvaluatePropertyReq): SynchronousEvaluationResult | null => {
    const { name, args } = req.property;
    const wrapLines = (lines: RpcBodyLine[]): SynchronousEvaluationResult => ({
      body: lines,
      totalTime: 0, parseTime: 0, createLocatorTime: 0, applyLocatorTime: 0, attrEvalTime: 0, listNodesTime: 0, listPropertiesTime: 0,
    });
    const encodeValue = (val: any): SynchronousEvaluationResult => {
      if (!val) {
        return wrapLines([{ type: 'plain', value: 'null' }])
      }
      if (looksLikeTestNode(val)) {
        return wrapLines([{ type: 'node', value: nodeToLocator(val as Node)}]);
      }
      console.log('unknown prop result value:', val);
      throw new Error('Unknown response value');
    }
    switch (name) {
      case 'm:NodesWithProperty': {
        // Search probe
        let founds = [...setupArgs.nodes];
        if (args?.length) {
          const attrFilter = args[0];
          if (attrFilter?.type === 'string' && attrFilter?.value) {
            founds = founds.filter(x => !!x.props[attrFilter.value]);
          }
          const predicates = args[1];
          if (predicates?.type === 'string' && predicates?.value) {
            founds = founds.filter(x => predicates.value.split('&').every(part => {
              if (part.startsWith('this<:')) {
                return x.type === part.slice('this<:'.length);
              }
              if (part.startsWith('@lineSpan~=')) {
                const exp = Number.parseInt(part.slice('@lineSpan~='.length), 10);
                return x.start >> 12 <= exp && x.end >> 12 >= exp;
              }
              console.error('Unknown predicate', part);
              return false;
            }))
          }
        }
        return wrapLines([{ type: 'arr', value: founds.map<RpcBodyLine>(node => ({ type: 'node', value: nodeToLocator(node) })), }]);
      }
      case 'm:AttrChain': {
        const tgtNode = setupArgs.nodes.find(node => (node.start === req.locator.result.start) && (node.end === req.locator.result.end));
        if (!tgtNode) {
          throw new Error('Bad locator in request');
        }
        let lastVal = tgtNode;
        args?.forEach((step) => {
          if (step.type !== 'string') {
            throw new Error('Bad step');
          }
          lastVal = lastVal.props[step.value]?.()
        })
        return encodeValue(lastVal);
      }
      default: {
        // Some normal property
        const tgtNode = setupArgs.nodes.find(node => (node.start === req.locator.result.start) && (node.end === req.locator.result.end));
        if (!tgtNode) {
          throw new Error('Bad locator in request');
        }
        const val = tgtNode.props[name]?.();
        return encodeValue(val);
      }
    }
  }
  const listProperties = async (req: ListPropertiesReq): Promise<ListPropertiesRes> => {
    let node = setupArgs.nodes.find(x => x.type === req.locator.result.type);
    if (!node) {
      throw new Error('Bad locator in request');
    }
    if (req.attrChain) {
      for (let i = 0; i < req.attrChain.length; ++i) {
        const res = node.props[req.attrChain[i]]?.();
        if (looksLikeTestNode(res)) {
          node = res as Node;
        } else {
          throw new Error(`Bad attr chain (faulty step: ${req.attrChain[i]})`);
        }
      }
    }
    return { body: [], properties: Object.keys(node.props).map(name => ({ name })), };
  }
  const env: Partial<ModalEnv> = ({
    performTypedRpc: async <Req, Res>(req) => {
      if (req.type === 'EvaluateProperty') {
        const value = evaluateProperty(req);
        if (!value) { return Promise.reject('Bad request'); }
        let res: EvaluatePropertyRes = {
          response: { type: 'sync', value, },
        };
        return res as Res;
      }
      if (req.type === 'ListProperties') {
        return listProperties(req) as Res;
      }
      console.log('Unknown rpc', req);
      return Promise.reject('Unknown Request');
    },
    createParsingRequestData: () => ({
      cache: 'FULL',
      posRecovery: 'ALTERNATE_PARENT_CHILD',
      src: { type: 'text', value: setupArgs.src },
      tmpSuffix: '.tmp',
    }),
    getLocalState: () => setupArgs.src,
    createJobId: () => 1,
    statisticsCollector: { addProbeEvaluationTime: () => { } },
  });
  return env as ModalEnv;
}

export { Node, nodeToLocator }
export default setupTestModalEnv;
