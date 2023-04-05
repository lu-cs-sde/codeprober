import { buildTextRpc } from './TextRpc';

export default buildTextRpc<{
  attr: { name: 'meta:listNodes'; };
  locator: {
    result: TypeAtLocStep;
    steps: [];
  };
}, {
  nodes?: NodeLocator[];
  body?: RpcBodyLine[];
}>();
