import { NodeLocator, RpcBodyLine } from '../protocol';

const findLocatorWithNestingPath = (path: number[], rootLines: RpcBodyLine[]): NodeLocator | null => {
  const step = (index: number, from: RpcBodyLine[]): NodeLocator | null => {
    let position = path[index];

    // We want something at index `position`, but stdio messages don't contribute to indexes (see 'excludeStdIoFromPaths' in encodeRpcBodyLines).
    // Iterate until we have seen `position` number of non-stdio elements.
    for (let i = 0; i < from.length; ++i) {
      switch (from[i].type) {
        case 'stdout':
        case 'stderr':
          break;

          default:
            if (position <= 0) {
              position = i;
              i = from.length;
              break;
            }
            --position;
            break;
      }
    }
    const line = from[position];
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
