

const createStickyHighlightController = (env: ModalEnv) => {
  const stickyId = `sticky-highlight-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
  let activeStickyColorClass = '';

  let currentTarget: HTMLElement | null = null;
  let currentLocator: NodeLocator | null = null;

  const applySticky = () => {
    if (!currentTarget || !currentLocator) return;

    env.setStickyHighlight(stickyId, {
      classNames: [
        `monaco-rag-highlight-sticky`,
        activeStickyColorClass,
      ],
      span: startEndToSpan(currentLocator.result.start, currentLocator.result.end),
    });
    currentTarget.classList.add(`monaco-rag-highlight-sticky`);
    currentTarget.classList.add(activeStickyColorClass);
  };

  return {
    onClick: () => {
      if (!activeStickyColorClass) {
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
    configure: (target: HTMLElement, locator: NodeLocator) => {
      currentTarget = target;
      currentLocator = locator;
      if (activeStickyColorClass) {
        applySticky();
      } else {
        env.clearStickyHighlight(stickyId);
      }
    }
  }
}

export default createStickyHighlightController;
