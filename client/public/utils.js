
let lastKnownMousePos = { x: 0, y: 0 };
// window.onmousemove = (e) => {
//   lastKnownMousePos.x = e.x;
//   lastKnownMousePos.y = e.y;
// };
let currentMouseDownTracker = null;
const setCurrentMouseDown = (newTracker) => {
  if (currentMouseDownTracker) {
    currentMouseDownTracker.down = false;
  }
  currentMouseDownTracker = newTracker;
}

const modalZIndexGenerator = (() => {
  let counter = 1;
  return () => counter++;
})();
const attachDragToMove = (element, initialPos) => {
  let posX = initialPos?.x ?? lastKnownMousePos.x;
  let posY = initialPos?.y ?? lastKnownMousePos.y;

  const refreshPos = (newX, newY) => {
    posX = newX ?? posX;
    posY = newY ?? posY;
    element.style.top = `${posY}px`;
    element.style.left = `${posX}px`;
  }

  let mouse = {down: false, x: 0, y: 0};
  element.onmousedown = (e) => {
    console.log('got mousedown on', e.foo);
    setCurrentMouseDown(mouse);
    mouse.down = true;
    element.style.zIndex = `${modalZIndexGenerator()}`;
    mouse.x = e.x;
    mouse.y = e.y;
  };
  const onMouseMove = (e) => {
    if (mouse.down) {
      let dx = e.x - mouse.x;
      let dy = e.y - mouse.y;
      refreshPos(posX + dx, posY + dy);
      mouse.x = e.x;
      mouse.y = e.y;
    }
  };
  document.addEventListener('mousemove', onMouseMove);
  element.onmouseup = (e) => {
    mouse.down = false;
  }
  refreshPos();
  return {
    getPos: () => ({ x: posX, y: posY }),
    cleanup: () => {
      document.removeEventListener('mousemove', onMouseMove);
    }
  };
};

const onThemeToggleListeners = [];

const registerOnHover = (element, onHover) => {
  element.onmouseenter = () => onHover(true);
  element.onmouseleave = () => onHover(false);
};

window.MiniEditorUtils = {
  createLoadingSpinner: () => {
    const holder = document.createElement('div');
    holder.classList.add('lds-spinner');
    holder.innerHTML = `<div></div><div></div><div></div><div></div><div></div><div></div><div></div><div></div><div></div><div></div><div></div><div></div>`;
    return holder;
  },
  createTextSpanIndicator: (span, setHighlight) => {
    const indicator = document.createElement('span');
    indicator.style.fontSize = '0.75rem';
    indicator.style.color = 'gray';
    indicator.style.marginLeft = '0.25rem';
    indicator.innerText = `[${span.lineStart}:${span.colStart}→${span.lineEnd}:${span.colEnd}]`;
    if (setHighlight) {
      indicator.classList.add('highlightOnHover');
      MiniEditorUtils.registerOnHover(indicator, setHighlight);
    }
    return indicator;
  },
  createModalTitle: ({ renderLeft, onDuplicate, onClose }) => {
    const titleRowHolder = document.createElement('div');
    titleRowHolder.classList.add('modalTitle');

    const titleRowLeft = document.createElement('div');
    titleRowLeft.style.margin = 'auto 0';

    renderLeft(titleRowLeft);
    titleRowHolder.appendChild(titleRowLeft);

    const buttons = document.createElement('div');
    buttons.classList.add('button-holder');

    if (onDuplicate) {
      const duplicateButton = document.createElement('img');
      duplicateButton.src = '/icons/content_copy_white_24dp.svg';
      duplicateButton.classList.add('modalDuplicateButton');
      // duplicateButton.innerText = '𝖷';
      duplicateButton.classList.add('clickHighlightOnHover');
      duplicateButton.onmousedown = (e) => { e.stopPropagation(); }
      duplicateButton.onclick = () => onDuplicate();
      buttons.appendChild(duplicateButton);
    }

    const closeButton = document.createElement('div');
    closeButton.classList.add('modalCloseButton');
    closeButton.innerText = '𝖷';
    closeButton.classList.add('clickHighlightOnHover');
    closeButton.onmousedown = (e) => { e.stopPropagation(); }
    closeButton.onclick = () => onClose();
    buttons.appendChild(closeButton);

    titleRowHolder.appendChild(buttons);

    return {
      element: titleRowHolder
    };
  },
  getThemeIsLight: () => localStorage.getItem('editor-theme-light') === 'true',
  setThemeIsLight: (light) => {
    localStorage.setItem('editor-theme-light', `${light}`);
    document.body.setAttribute('data-theme-light', `${light}`);
  },
  showModal: ({ render, pos: initialPos, rootStyle }) => {
    const root = document.createElement('div');
    root.classList.add('modalWindow');
    root.style = `${rootStyle || ''}`;
    root.style.zIndex = `${modalZIndexGenerator()}`;

    root.innerHtml = '<i>Loading..</i>';
    let lastCancelToken = {};
    render(root, lastCancelToken);

    document.body.appendChild(root);
    const dragToMove = attachDragToMove(root, initialPos);
    return {
      remove: () => {
        root.remove();
        dragToMove.cleanup();
      },
      refresh: () => {
        lastCancelToken.cancelled = true;
        lastCancelToken = {};
        // root.innerHTML = '';
        render(root, lastCancelToken);
      },
      getPos: dragToMove.getPos,
    };
  },
  registerOnHover,
  addCloseNotice: (didReceiveAtLeastOneMessage) => {
    const ch = document.createElement('div');
    const msg = didReceiveAtLeastOneMessage
      ? 'Lost connection to server, reload to reconnect'
      : 'Couldn\'t connect to server';
    ch.innerHTML = `
    <div style="
      position: absolute;
      top: 4rem;
      left: 20%;
      right: 20%;
      background: #300;
      color: white;
      border: 1px solid red;
      border-radius: 1rem;
      padding: 2rem;
      font-size: 2rem;
      z-index: ${Number.MAX_SAFE_INTEGER};
      text-align: center">
     ${msg}
    </div>
    `
    document.body.appendChild(ch);
  },
}
// document.body.setAttribute('data-theme-light', `${MiniEditorUtils.getThemeIsLight()}`);
