
function deepEqual(
  a: any,
  b: any,
  options?: { ignoreUndefinedDiff?: boolean, verbose?: boolean }): boolean {
  if (a === b) return true;
  if (a == null || b == null) {
    if (options?.verbose) {
      console.log('not equal: one side is null');
    }
    return false;
  }
  if (typeof a !== typeof b) {
    if (options?.verbose) {
      console.log('not equal: different types');
    }
    return false;
  }
  if (typeof a !== 'object') {
    if (options?.verbose) {
      console.log('not equal: not objects, but', typeof a);
      console.log('a:', a);
      console.log('b:', b);
    }
    return false;
  }
  if (Array.isArray(a) !== Array.isArray(b)) {
    if (options?.verbose) {
      console.log('not equal: array vs non-array');
    }
    return false;
  }

  let keysA = Object.keys(a);
  let keysB = Object.keys(b);
  if (options?.ignoreUndefinedDiff) {
    keysA = keysA.filter(x => a[x] !== undefined)
    keysB = keysB.filter(x => b[x] !== undefined);
  }

  if (keysA.length !== keysB.length) {
    if (options?.verbose) {
      console.log('not equal: different number of keys', keysA, 'vs', keysB);
    }
    return false;
  }

  const keySet = new Set(keysA);
  if (!keysB.every(key => keySet.has(key))) {
    if (options?.verbose) {
      console.log('not equal: different keys');
    }
    return false;
  }

  return keysA.every(key => deepEqual(a[key], b[key], options));
}

export default deepEqual;
