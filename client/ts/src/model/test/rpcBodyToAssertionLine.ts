import { AssertionLine } from './TestCase';


const rpcBodyToTestBody = (line: RpcBodyLine): AssertionLine | null => {
  if (typeof line === 'string') {
    return line;
  }
  if (Array.isArray(line)) {
    return line.map(rpcBodyToTestBody).filter(Boolean) as AssertionLine[];
  }

  switch (line.type) {
    case 'node': return { naive: line.value, robust: line.value };
    default: {
      switch (line.type) {
        case 'stdout':
        case 'stderr':
          // Do not keep these
          return null;

        case 'stream-arg':
        default:
          return line.value;
      }
    }
  }
}

const rpcLinesToAssertionLines = (lines: RpcBodyLine[]): AssertionLine[] => {
  const mapped = lines.map(rpcBodyToTestBody);
  return mapped.filter(Boolean) as AssertionLine[];
};

export { rpcLinesToAssertionLines }
export default rpcBodyToTestBody;
