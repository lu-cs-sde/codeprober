import { assertUnreachable } from '../../hacks';
import { RpcBodyLine, Tracing } from '../../protocol';

const rpcBodyToTestBody = (line: RpcBodyLine): RpcBodyLine | null => {
  switch (line.type) {
    case 'plain':
    case 'streamArg':
    case 'node':
      return line;

    case 'highlightMsg':
      return { type: 'plain', value: line.value.msg };

    case 'dotGraph':
      // No dot support in tests..?
      return { type: 'plain', value: line.value };

    case 'stdout':
    case 'stderr':
      // Do not keep these
      return null;

    case 'arr':
      return { type: 'arr', value: line.value.map(rpcBodyToTestBody).filter(Boolean) as RpcBodyLine[] };

    case 'tracing':
      // Should we really keep the tracing for tests? Not a very good thing to test I think.
      const encodeTrace = (tr: Tracing): RpcBodyLine => {
        const result = rpcBodyToTestBody(tr.result);
        return {
          type: 'arr',
          value: [
            { type: 'node', value: tr.node },
            { type: 'plain', value: `.${tr.prop.name}` },
            { type: 'arr', value: tr.dependencies.map(encodeTrace) },
            ...(result ? [result] : []),
          ],
        };
      }
      return encodeTrace(line.value);

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
