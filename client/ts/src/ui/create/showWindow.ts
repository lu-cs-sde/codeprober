import attachDragToMove from "./attachDragToMove";
import attachDragToX, { modalZIndexGenerator } from "./attachDragToX";

interface CancelToken {
  cancelled?: boolean;
}

interface ShowWindowArgs {
  render: (container: HTMLElement, info: { cancelToken: CancelToken; bringToFront: () => void; }) => void;
  onForceClose: () => void,
  pos?: ModalPosition | null;
  size?: { width: number, height: number },
  rootStyle?: string;
  onFinishedMove?: () => void;
  onOngoingResize?: () => void;
  onFinishedResize?: () => void;
  resizable?: boolean;
  debugLabel?: string;
}

interface ShowWindowResult {
  remove: () => void;
  refresh: () => void;
  getPos: () => ModalPosition;
  getSize: () => ({ width: number, height: number });
  bumpIntoScreen: () => void;
}

const showWindow = (args: ShowWindowArgs): ShowWindowResult => {
  const { render, pos: initialPos, size: initialSize, rootStyle, resizable } = args;
  const root = document.createElement('div');
  root.tabIndex = 0;
  root.classList.add('modalWindow');
  root.classList.add('modalWindowColoring');
  (root.style as any) = `${rootStyle || ''}`;

  const bringToFront = () => {
    root.style.zIndex = `${modalZIndexGenerator()}`;
  };
  bringToFront();
  root.style.maxWidth = '40vw';

  root.onkeydown = (e) => {
    if (e.key === 'Escape') {
      const btn = root.querySelector('.modalCloseButton') as HTMLButtonElement;
      if (btn) {
        e.stopPropagation();
        btn.onclick?.(null as any);
      }
    }
  }

  let lastCancelToken: CancelToken = {};

  const contentRoot = document.createElement('div');
  contentRoot.style.overflow = 'auto';
  contentRoot.style.position = 'relative';
  contentRoot.style.top = '0px';
  contentRoot.style.left = '0px';
  const maxHeight = "90vh";
  contentRoot.style.maxHeight = maxHeight;
  render(contentRoot, { cancelToken: lastCancelToken, bringToFront });
  root.appendChild(contentRoot);


  let reiszeCleanup:( () => void) | null = null;
  if (resizable) {
    const resizePositioner = document.createElement('div');
    resizePositioner.style.right = '0px';
    resizePositioner.style.bottom = '0px';
    resizePositioner.style.cursor = 'nwse-resize';
    resizePositioner.style.width = 'fit-contents';
    resizePositioner.style.height = '0px';
    resizePositioner.style.position = 'sticky';
    resizePositioner.style.zIndex = '99';

    const resizeButton = document.createElement('div');
    resizeButton.style.position = 'absolute';
    resizeButton.style.right = '0px';
    resizeButton.style.bottom = '0px';
    resizeButton.style.height = '0.5rem';
    resizeButton.style.width = '0.5rem';
    resizeButton.style.borderRight = '4px solid gray';
    resizeButton.style.borderBottom = '4px solid gray';
    resizePositioner.appendChild(resizeButton);

    root.appendChild(resizePositioner)

    const size = { w: 1, h: 1 };
    reiszeCleanup = attachDragToX(resizePositioner,
      () => {
        size.w = root.clientWidth;
        size.h = root.clientHeight;
      },
      (dx, dy) => {
        const newW = Math.max(32, size.w + dx);
        const newH = Math.max(32, size.h + dy);
        // console.log('setting ', `${root.clientWidth + dx}px`);
        root.style.width = `${newW}px`;
        root.style.height = `${newH}px`;
        root.style.maxWidth = 'fit-content';
        root.style.maxHeight = maxHeight;
        args.onOngoingResize?.();
      },
      args.onFinishedResize).cleanup;
  }
  if (initialSize) {
    root.style.width = `${initialSize.width}px`;
    root.style.height = `${initialSize.height}px`;
    root.style.maxWidth = 'fit-content';
    root.style.maxHeight = maxHeight;
  }

  document.body.appendChild(root);
  const dragToMove = attachDragToMove(root, initialPos, args.onFinishedMove);
  return {
    remove: () => {
      root.remove();
      dragToMove.cleanup();
      reiszeCleanup?.();
    },
    refresh: () => {
      lastCancelToken.cancelled = true;
      lastCancelToken = {};
      // root.innerHTML = '';
      render(contentRoot, { cancelToken: lastCancelToken, bringToFront });
    },
    getPos: dragToMove.getPos,
    getSize: () => ({ width: root.clientWidth, height: root.clientHeight }),
    bumpIntoScreen: dragToMove.bumpIntoScreen,
  };
};


export { ShowWindowArgs, ShowWindowResult, CancelToken };
export default showWindow;
