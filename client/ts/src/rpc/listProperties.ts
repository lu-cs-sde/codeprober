import { buildTextRpc } from './TextRpc';

export default buildTextRpc<{
  attr: { name: 'meta:listAllProperties' | 'meta:listProperties'; };
  locator: NodeLocator;
}, {
  properties?: AstAttr[];
  body?: RpcBodyLine[];
}>();
