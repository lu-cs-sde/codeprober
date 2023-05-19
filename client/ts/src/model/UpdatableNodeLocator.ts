import { NodeLocator } from '../protocol';
import adjustLocator from './adjustLocator';

const createImmutableLocator = (source: UpdatableNodeLocator): UpdatableNodeLocator => {
  return {
    get: () => source.get(),
    set: () => {},
    adjust: () => {},
    isMutable: () => false,
    createMutableClone: () => createMutableLocator(JSON.parse(JSON.stringify(source.get()))),
  };
};

const createMutableLocator = (locator: NodeLocator): UpdatableNodeLocator => {
  return {
    get: () => locator,
    set: (val) => {
      locator = val;
    },
    adjust: (adjusters) => {
      adjusters.forEach(adj => adjustLocator(adj, locator));
    },
    isMutable: () => true,
    createMutableClone: () => createMutableLocator(JSON.parse(JSON.stringify(locator))),
  }
};

interface UpdatableNodeLocator {
  get: () => NodeLocator;
  set: (newVal: NodeLocator) => void;
  adjust: (adjusters: LocationAdjuster[]) => void;
  isMutable: () => boolean;
  createMutableClone: () => UpdatableNodeLocator;
}

export { createImmutableLocator, createMutableLocator };
export default UpdatableNodeLocator;
