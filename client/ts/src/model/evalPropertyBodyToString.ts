import { assertUnreachable } from '../hacks';
import { NodeLocator, RpcBodyLine } from '../protocol';

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

export default evalPropertyBodyToString;
