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
  updateRoot: (newRoot: HTMLElement, newBumperIntoScreen: () => void) => void;
  // refresh: () => void;
}

const createInlineArea = (args: {
  inlineRoot: HTMLElement;
  expansionAreaInsideTheRoot: HTMLElement;
  localWindowStateSaves: ModalEnv['probeWindowStateSavers'];
  bumpContainingWindowIntoScreen: () => void;
  parentArgs: CreateWindowManagerArgs;
}): InlineAreaWithHiddenProperties => {
  let { inlineRoot, expansionAreaInsideTheRoot, bumpContainingWindowIntoScreen } = args;

  const applyActiveRootStyling = (from: string) => {
    inlineRoot.classList.add('inline-window-active');
    inlineRoot.style.border = '1px solid black';
    inlineRoot.style.paddingTop = '0.25rem';
    inlineRoot.style.marginTop = '0.25rem';
    inlineRoot.style.marginBottom = '0.25rem';
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
      probeMarkers: args.parentArgs.probeMarkersOverride ?? parentEnv.probeMarkers,
      updateMarkers: args.parentArgs.updateMarkersOverride ?? parentEnv.updateMarkers,
    }),
    add: (args) => {
      if (activeSubWindowCount === 0) {
        applyActiveRootStyling('add');
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
          const parent = localDiv.parentElement;
          if (parent) parent.removeChild(localDiv);
          --activeSubWindowCount;
          if (activeSubWindowCount === 0) {
            inlineRoot.classList.remove('inline-window-active');
            inlineRoot.style.border = 'none';
            inlineRoot.style.paddingTop = '0';
            inlineRoot.style.marginTop = '0';
            inlineRoot.style.marginBottom = '0';
          }
          const idx =  activeWindowClosers.findIndex(x => x === closer);
          if (idx !== -1) {
            activeWindowClosers.splice(idx, 1);
          } else {
            console.log('could not find index of closer in remove')
          }
        },
        bumpIntoScreen: () => { bumpContainingWindowIntoScreen(); }
      }
    },
    notifyListenersOfChange: () => {
      Object.values(localChangeListeners).forEach(chl => chl());
    }
  };
  return {
    area,
    destroyer: () => {
      [...activeWindowClosers].forEach((closer, idx) => {
        closer()
      });
    },
    updateRoot: (newRoot, newBumper) => {
      inlineRoot = newRoot;
      if (activeSubWindowCount > 0) {
        applyActiveRootStyling(`update:${activeSubWindowCount}`);
      }
      bumpContainingWindowIntoScreen = newBumper;
    },
    // refresh: () => {
    //   activeWindowRefreshers.forEach(closer => closer());
    // },
  };
}

interface InlineWindowManager {
  getPreviousExpansionArea: (areaId: number[]) => HTMLElement | null,
  getPreviouslyAssociatedLocator: (areaId: number[]) => UpdatableNodeLocator | null,
  getArea: (id: number[], inlineRoot: HTMLElement, expansionAreaInsideTheRoot: HTMLElement, associatedLocator: UpdatableNodeLocator, bumpContainingWindowIntoScreen: () => void) => InlineArea;
  getWindowStates: () => ({ [areaId: string]: WindowState[] });
  destroy: () => void,
  conditiionallyDestroyAreas: (predicate: (areaId: number[]) => boolean) => void;
  notifyListenersOfChange: () => void;

}

interface CreateWindowManagerArgs {
  probeMarkersOverride?: ModalEnv['probeMarkers'];
  updateMarkersOverride?: ModalEnv['updateMarkers'];
};
const createInlineWindowManager = (args: CreateWindowManagerArgs = {}): InlineWindowManager => {
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
    getArea: (areaId, inlineRoot, expansionAreaInsideTheRoot, locator, bumpContainingWindowIntoScreen) => {
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
            bumpContainingWindowIntoScreen,
            parentArgs: args,
          })
        };
      } else {
        areas[encodedId].area.updateRoot(inlineRoot, bumpContainingWindowIntoScreen);
      }
      return areas[encodedId].area.area;
    },
    getWindowStates: () => {
      const ret: ReturnType<InlineWindowManager['getWindowStates']> = {};
      Object.entries(areas).forEach(([key, val]) => {
        const states: WindowState[] = [];
        Object.values(val.localWindowStateSaves).forEach(saver => saver(states));
        if (states.length) {
          ret[key] = states;
        }
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


export { InlineWindowManager };
export default createInlineWindowManager;
