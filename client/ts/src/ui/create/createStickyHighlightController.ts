import ModalEnv from '../../model/ModalEnv';
import UpdatableNodeLocator from '../../model/UpdatableNodeLocator';
import { NodeLocator } from '../../protocol';
import startEndToSpan from '../startEndToSpan';

interface StickyHighlightController {
  onClick: () => void;
  cleanup: () => void;
  configure: (target: HTMLElement, locator: UpdatableNodeLocator) => void;
  getActiveColor: () => string | undefined;
}

const createStickyHighlightController = (env: ModalEnv, initialColorClass = ''): StickyHighlightController => {
  const stickyId = `sticky-highlight-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
  let activeStickyColorClass = initialColorClass;
  if (activeStickyColorClass && !/monaco-rag-highlight-sticky-\d/.test(activeStickyColorClass)) {
    console.warn('Invalid initial sticky color:', activeStickyColorClass);
    activeStickyColorClass = '';
  }

  let currentTarget: HTMLElement | null = null;
  let currentLocator: UpdatableNodeLocator | null = null;

  const applySticky = () => {
    if (!currentTarget || !currentLocator) return;

    env.setStickyHighlight(stickyId, {
      classNames: [
        `monaco-rag-highlight-sticky`,
        activeStickyColorClass,
      ],
      span: startEndToSpan(currentLocator.get().result.start, currentLocator.get().result.end),
    });
    currentTarget.classList.add(`monaco-rag-highlight-sticky`);
    currentTarget.classList.add(activeStickyColorClass);
  };

  const pickNewColor = () => {
    for (let i = 0; i < 10; ++i) {
      document.querySelector
      activeStickyColorClass = `monaco-rag-highlight-sticky-${i}`;
      if (!!document.querySelector(`.${activeStickyColorClass}`)) {
        activeStickyColorClass = '';
      } else {
        break;
      }
    }
    if (!activeStickyColorClass) {
      // More than 10 colors active, pick one pseudorandomly instead
      activeStickyColorClass = `monaco-rag-highlight-sticky-${(Math.random() * 10)|0}`;
    }
  };

  return {
    onClick: () => {
      if (!activeStickyColorClass) {
        pickNewColor();
        applySticky();
      } else {
        env.clearStickyHighlight(stickyId);
        if (activeStickyColorClass) {
          currentTarget?.classList?.remove(`monaco-rag-highlight-sticky`);
          currentTarget?.classList?.remove(activeStickyColorClass);
          activeStickyColorClass = '';
        }
      }
    },
    cleanup: () => {
      if (activeStickyColorClass) {
        env.clearStickyHighlight(stickyId);
      }
    },
    configure: (target, locator) => {
      currentTarget = target;
      currentLocator = locator;
      if (activeStickyColorClass) {
        applySticky();
      } else {
        env.clearStickyHighlight(stickyId);
      }
    },
    getActiveColor: () => activeStickyColorClass || undefined,
  }
}

export { StickyHighlightController }
export default createStickyHighlightController;
