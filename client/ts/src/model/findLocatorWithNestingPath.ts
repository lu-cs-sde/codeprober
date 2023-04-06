import { NodeLocator, RpcBodyLine } from '../protocol';

const findLocatorWithNestingPath = (path: number[], rootLines: RpcBodyLine[]): NodeLocator | null => {
  const step = (index: number, from: RpcBodyLine[]): NodeLocator | null => {
    const line = rootLines[path[index]];
    if (!line) {
      return null;
    }
    if (index >= path.length - 1) {
      switch (line.type) {
        case 'node':
          return line.value;

        default:
          return null;
      }
    }
    switch (line.type) {
      case 'arr':
        return step(index + 1, line.value);

      default:
        return null;
    }
  };
  return step(0, rootLines);
}

export default findLocatorWithNestingPath;
