

const assertUnreachable = (val: never) => {
  console.warn('Exhaustive switch matched default case - typedefs are out of date');
  console.warn('Related value:', val);
}

export { assertUnreachable }
