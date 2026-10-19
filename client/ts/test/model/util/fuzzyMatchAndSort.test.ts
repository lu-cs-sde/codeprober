/// <reference types="node" />
import { describe, it } from 'node:test';
import assert from 'node:assert';

import fuzzyMatchAndSort from '../../../src/model/util/fuzzyMatchAndSort';

const performSort = (filter: string, items: string[]): string[] => fuzzyMatchAndSort(items.map(item => ({ item, name: item})), filter).map(x => x.item.item);


// Like performSort, but randomly shuffle the order of the items a bit and assert that the same result is always computed.
const performRandomizedSort = (filter: string, items: string[]): string[] => {
  const initialResult = performSort(filter, items);

  for (let i = 0; i < 32; ++i) {
    const a = (Math.random() * items.length)|0;
    const b = (Math.random() * items.length)|0;
    const tmp = items[a];
    items[a] = items[b];
    items[b] = tmp;
    assert.deepStrictEqual(performSort(filter, items), initialResult);
  }

  return initialResult;
}

describe('fuzzyMatchAndSort', () => {
  it('should prioritize zero gaps', () => {
    const result = performRandomizedSort('pe', ['apple', 'banana', 'pear']);
    assert.deepStrictEqual(result, [
      'pear',   // zero gap
      'apple',  // 1 gap
      'banana', // does not match
    ]);
  });
  it('should prioritize start of word matches', () => {
    const result = performRandomizedSort('oo', ['solo', 'food', 'oboe', 'an oolong tea', 'oolong tea']);

    assert.deepStrictEqual(result, [
      'oolong tea', // start of sentence, zero gaps
      'an oolong tea', // start of word, zero gaps
      'food', // zero gaps
      'oboe', // start of sentence, 1 gap
      'solo' // middle of sentence, 1 gap
    ])
  });
  it('should retain list index on equally good matches', () => {
    assert.deepStrictEqual(performSort('oo', ['tool', 'x', 'pool']), ['tool', 'pool', 'x'])
    assert.deepStrictEqual(performSort('oo', ['pool', 'x', 'tool']), ['pool', 'tool', 'x'])
  })
  it('should be case insensitive, for the filter and words', () => {
    const result = performSort('Oo', ['FOOD', 'foOd', 'x', 'fOod', 'food']);
    assert.deepStrictEqual(result, ['FOOD', 'foOd', 'fOod', 'food', 'x']);
  });
  it('should allow empty filter', () => {
    const result = performSort('', ['foo', 'bar']);
    assert.deepStrictEqual(result, ['foo', 'bar']);
  });
  it('should allow empty item list', () => {
    const result = performSort('foo', []);
    assert.deepStrictEqual(result, []);
  });
  it('should ignore whitespace in filter', () => {
    const result = performSort('f o o', ['bar', 'foo']);
    assert.deepStrictEqual(result, ['foo', 'bar']);
  });
});
