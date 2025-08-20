/// <reference types="node" />
import { describe, it } from 'node:test';
import assert from 'node:assert';

import cullingTaskSubmitterFactory from '../../src/model/cullingTaskSubmitterFactory';

const sleep = (ms: number) => new Promise(res => setTimeout(res, ms));

describe('cullingTaskSubmitterFactory', () => {
  it('should only execute the last submitted function', async () => {
    const sub = cullingTaskSubmitterFactory(1)();

    sub.submit(() => { assert.fail(); })
    sub.submit(() => { assert.fail(); })

    let called = 0;
    sub.submit(() => { ++called })

    await sleep(10);
    assert.strictEqual(called, 1);
  });

  [true, false].forEach((sleepFirst) => {
    it(`should wait for the last func to finish, when sleepFirst:${sleepFirst}`, async () => {
      const sub = cullingTaskSubmitterFactory(1)();

      let state: 'idle' | 'running' | 'done' = 'idle';
      sub.submit(() => {
        return new Promise((res) => {
          state = 'running';
          setTimeout(() => {
            state = 'done';
            res('');
          }, 20);
        });
      });

      assert.strictEqual(state, 'idle');
      if (sleepFirst) {
        await sleep(10);
        assert.strictEqual(state, 'running');
      }
      await sub.fireImmediately();
      assert.strictEqual(state, 'done');
    });
  });

});
