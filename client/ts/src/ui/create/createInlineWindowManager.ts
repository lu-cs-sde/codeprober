import ModalEnv from '../../model/ModalEnv';
import UpdatableNodeLocator from '../../model/UpdatableNodeLocator';
import WindowState from '../../model/WindowState';
import { CancelToken, ShowWindowArgs, ShowWindowResult } from './showWindow';

interface InlineArea {
  // attach: (root: HTMLElement, expansionArea: HTMLElement) => void;
  getNestedModalEnv: (parentEnv: ModalEnv) => ModalEnv;
  add: (args: ShowWindowArgs) => ShowWindowResult;
  notifyListenersOfChange: () => void;
}

interface InlineAreaWithHiddenProperties {
  area: InlineArea;
  destroyer: () => void;
  updateRoot: (newRoot: HTMLElement) => void;
  // refresh: () => void;
}

const createInlineArea = (args: {
  inlineRoot: HTMLElement;
  expansionAreaInsideTheRoot: HTMLElement;
  localWindowStateSaves: ModalEnv['probeWindowStateSavers'];
}): InlineAreaWithHiddenProperties => {
  let { inlineRoot, expansionAreaInsideTheRoot } = args;

  const applyActiveRootStyling = () => {
    inlineRoot.style.border = '1px solid black';
    inlineRoot.style.paddingTop = '0.25rem';
  }
  const activeWindowClosers: (() => void)[] = [];
  const localChangeListeners: ModalEnv['onChangeListeners'] = {};
  // const activeWindowRefreshers: (() => void)[] = [];
  let activeSubWindowCount = 0;
  const area: InlineArea = {
    getNestedModalEnv: (parentEnv) => ({
      ...parentEnv,
      showWindow: (args) => area.add(args),
      onChangeListeners: localChangeListeners,
      probeWindowStateSavers: args.localWindowStateSaves,
    }),
    add: (args) => {
      if (activeSubWindowCount === 0) {
        applyActiveRootStyling();
        expansionAreaInsideTheRoot.style.marginTop = '0.25rem';
      }
      ++activeSubWindowCount;
      const closer = () => args.onForceClose();
      activeWindowClosers.push(closer);
      // const refresher = () => renderFn();
      // activeWindowRefreshers.push(refresher);

      const localDiv = document.createElement('div');
      if (args.rootStyle) {
        (localDiv.style as any) = args.rootStyle;
      }
      localDiv.style.overflow = 'scroll';
      localDiv.classList.add('subWindow');
      expansionAreaInsideTheRoot.prepend(localDiv);

      let lastCancelToken: CancelToken = {};
      const renderFn = () => {
        lastCancelToken.cancelled = true;
        lastCancelToken = {};
        args.render(localDiv, {
          cancelToken: lastCancelToken,
          bringToFront: () => {}, // TODO bring this from somewhere
        });
      };
      renderFn();
      return {
        getPos: () => ({ x: 0, y: 0 }),
        getSize: () => ({ width: localDiv.clientWidth, height: localDiv.clientHeight }),
        refresh: () => renderFn(),
        remove: () => {
          console.log('TODO remove me');
          const parent = localDiv.parentElement;
          if (parent) parent.removeChild(localDiv);
          --activeSubWindowCount;
          if (activeSubWindowCount === 0) {
            inlineRoot.style.border = 'none';
          }
          const idx =  activeWindowClosers.findIndex(x => x === closer);
          if (idx !== -1) {
            activeWindowClosers.splice(idx, 1);
            // activeWindowRefreshers.splice(idx, 1);
          }
        },
      }
    },
    notifyListenersOfChange: () => {
      Object.values(localChangeListeners).forEach(chl => chl());
    }
  };
  return {
    area,
    destroyer: () => {
      activeWindowClosers.forEach(closer => closer());
    },
    updateRoot: (newRoot) => {
      inlineRoot = newRoot;
      if (activeSubWindowCount > 0) {
        applyActiveRootStyling();
      }
    },
    // refresh: () => {
    //   activeWindowRefreshers.forEach(closer => closer());
    // },
  };
}

interface InlineWindowManager {
  getPreviousExpansionArea: (areaId: number[]) => HTMLElement | null,
  getPreviouslyAssociatedLocator: (areaId: number[]) => UpdatableNodeLocator | null,
  getArea: (id: number[], inlineRoot: HTMLElement, expansionAreaInsideTheRoot: HTMLElement, associatedLocator: UpdatableNodeLocator) => InlineArea;
  getWindowStates: () => ({ [areaId: string]: WindowState[] });
  destroy: () => void,
  conditiionallyDestroyAreas: (predicate: (areaId: number[]) => boolean) => void;
  notifyListenersOfChange: () => void;

}

const createInlineWindowManager = (): InlineWindowManager => {
  const areas: { [id: string]: {
    localWindowStateSaves: ModalEnv['probeWindowStateSavers'];
    expansionArea: HTMLElement;
    locator: UpdatableNodeLocator;
    area: InlineAreaWithHiddenProperties;
  } } = {};

  const encodeAreaId = (raw: number[]) => JSON.stringify(raw);
  const decodeAreaId = (raw: string): number[] => JSON.parse(raw);

  return {
    getPreviousExpansionArea: (areaId) => areas[encodeAreaId(areaId)]?.expansionArea ?? null,
    getPreviouslyAssociatedLocator: (areaId) => areas[encodeAreaId(areaId)]?.locator ?? null,
    getArea: (areaId, inlineRoot, expansionAreaInsideTheRoot, locator) => {
      const encodedId = encodeAreaId(areaId);
      if (!areas[encodedId]) {
        const localWindowStateSaves: ModalEnv['probeWindowStateSavers'] = {};
        // areaToLocalWindowStateSaves[id] =
        areas[encodedId] = {
          localWindowStateSaves,
          expansionArea: expansionAreaInsideTheRoot,
          locator,
          area: createInlineArea({
            inlineRoot,
            expansionAreaInsideTheRoot,
            localWindowStateSaves,
          })
        };
      } else {
        areas[encodedId].area.updateRoot(inlineRoot);
      }
      return areas[encodedId].area.area;
    },
    getWindowStates: () => {
      const ret: ReturnType<InlineWindowManager['getWindowStates']> = {};
      Object.entries(areas).forEach(([key, val]) => {
        const states: WindowState[] = [];
        Object.values(val.localWindowStateSaves).forEach(saver => saver(states));
        ret[key] = states;
      });
      return ret;
    },
    destroy: () => Object.values(areas).forEach(area => area.area.destroyer()),
    conditiionallyDestroyAreas: (predicate) => {
      Object.entries(areas).forEach(([key, val]) => {
        if (predicate(decodeAreaId(key))) {
          delete areas[key];
          val.area.destroyer();
        }
      });
    },
    notifyListenersOfChange: () => Object.values(areas).forEach(area => area.area.area.notifyListenersOfChange()),
    // refresh: () => Object.values(areas).forEach(area => area.area.refresh()),
  }
}


export default createInlineWindowManager;
