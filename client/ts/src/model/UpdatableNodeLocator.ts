import { NodeLocator } from '../protocol';
import adjustLocator from './adjustLocator';

const createImmutableLocator = (source: UpdatableNodeLocator): UpdatableNodeLocator => {
  // const callbacks: { [id: string]: () => void } = {};
  // const ourOwnListenerId = `immut-${(Math.random() * Number.MAX_SAFE_INTEGER)|0}`;
  // source.setUpdateCallback()
  // HMM How would this be cleaned up? Does it need to be?

  return {
    get: () => source.get(),
    set: () => {},
    adjust: () => {},
    isMutable: () => false,
    createMutableClone: () => createMutableLocator(JSON.parse(JSON.stringify(source.get()))),
    // setUpdateCallback: (id, callback) => {
    //   if (callback) {
    //     callbacks[id] = callback;
    //   } else {
    //     delete callbacks[id];
    //   }
    // },
  };
};

const createMutableLocator = (locator: NodeLocator): UpdatableNodeLocator => {
  // const callbacks: { [id: string]: () => void } = {};
  return {
    get: () => locator,
    set: (val) => {
      locator = val;
      // Object.values(callbacks).forEach(cb => cb());
    },
    adjust: (adjusters) => {
      adjusters.forEach(adj => adjustLocator(adj, locator));
    },
    isMutable: () => true,
    createMutableClone: () => createMutableLocator(JSON.parse(JSON.stringify(locator))),
    // setUpdateCallback: (id, callback) => {
    //   if (callback) {
    //     callbacks[id] = callback;
    //   } else {
    //     delete callbacks[id];
    //   }
    // }
  }
};

interface UpdatableNodeLocator {
  get: () => NodeLocator;
  set: (newVal: NodeLocator) => void;
  adjust: (adjusters: LocationAdjuster[]) => void;
  isMutable: () => boolean;
  createMutableClone: () => UpdatableNodeLocator;
  // setUpdateCallback: (id: string, callback: (() => void) | null) => void;
}

export { createImmutableLocator, createMutableLocator };
export default UpdatableNodeLocator;
