import { assertUnreachable } from '../../hacks';
import { RpcBodyLine } from '../../protocol';

const rpcBodyToTestBody = (line: RpcBodyLine): RpcBodyLine | null => {
  switch (line.type) {
    case 'plain':
    case 'streamArg':
    case 'node':
      return line;

    case 'dotGraph':
      // No dot support in tests..?
      return { type: 'plain', value: line.value };

    case 'stdout':
    case 'stderr':
      // Do not keep these
      return null;

    case 'arr':
      return { type: 'arr', value: line.value.map(rpcBodyToTestBody).filter(Boolean) as RpcBodyLine[] };

    default: {
      assertUnreachable(line);
      return line;
    }
  }
}

const rpcLinesToAssertionLines = (lines: RpcBodyLine[]): RpcBodyLine[] => {
  const mapped = lines.map(rpcBodyToTestBody);
  return mapped.filter(Boolean) as RpcBodyLine[];
};

export { rpcLinesToAssertionLines }
export default rpcBodyToTestBody;
