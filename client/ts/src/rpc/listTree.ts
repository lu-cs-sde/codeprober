import { buildTextRpc } from './TextRpc';

interface ListedTreeNode {
  type: 'node';
  locator: NodeLocator;
  name?: string;
  children: (ListedTreeNode[]) | { type: 'placeholder', num: number };
}
export { ListedTreeNode }
export default buildTextRpc<{
  attr: { name: 'meta:listTreeUpwards' | 'meta:listTreeDownwards'; };
  locator: NodeLocator;
}, {
  body?: RpcBodyLine[];
  locator: NodeLocator;
  nodes: ListedTreeNode;
}>();
