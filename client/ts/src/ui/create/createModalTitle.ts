import createKeyboardListNavigationController, { KeyboardListNavigationController } from './createKeyboardListNavigationController';
import showWindow from "./showWindow";

interface ExtraAction {
  title: string;
  invoke: () => void;
  shouldBeDisplayed?: () => boolean;
}
type CreateModalTitleArgs = {
  renderLeft: (container: HTMLElement) => void;
  extraActions?: ExtraAction[];
  onClose: (() => void) | null;
  shouldAutoCloseOnWorkspaceSwitch?: boolean;
};

const createOverflowButton = (
  extraActions: ExtraAction[],
): HTMLElement => {

  const overflowButton = document.createElement('img');
  overflowButton.src = 'icons/more_vert_white_24dp.svg';
  overflowButton.classList.add('clickHighlightOnHover');
  overflowButton.onmousedown = (e) => { e.stopPropagation(); }
  overflowButton.onclick = () => {

    let modalContainer: HTMLElement | null = null;
    const cleanup = () => {
      contextMenu.remove();
      window.removeEventListener('mousedown', onWindowMouseDown, true);
    }
    // Listen in the capture phase so this runs before any element on the page
    // can stop propagation. That means we must explicitly ignore clicks that
    // land inside the menu itself, otherwise it would close on every click.
    const onWindowMouseDown = (e: MouseEvent) => {
      const target = e.target as Node | null;
      if (modalContainer && target && modalContainer.contains(target)) {
        return;
      }
      cleanup();
    }
    const rowList: HTMLElement[] = [];
    let navCtrl: KeyboardListNavigationController | null = null;
    let isFirstRender = true;
    const contextMenu = showWindow({
      onForceClose: cleanup,
      render: (container, { root: modalWindowRoot }) => {
        modalContainer = container;
        if (isFirstRender) {
          isFirstRender = false;
          navCtrl = createKeyboardListNavigationController({ focusParent: () => modalWindowRoot.focus(), listItems: rowList })
          modalWindowRoot.addEventListener('keydown', e => {
            // Only intercept keydown outside .context-menu-row's.
            if (e.key === 'ArrowDown' && !(e.target as HTMLElement | null)?.closest('.context-menu-row')) {
              navCtrl?.focusFirst(e);
            }
          });
        }
        rowList.length = 0;
        container.addEventListener('mousedown', (e) => {
          e.stopPropagation();
          e.stopImmediatePropagation();
        });
        const hidden = document.createElement('button');
        hidden.classList.add('modalCloseButton');
        hidden.style.display = 'none';
        hidden.onclick = cleanup;
        container.appendChild(hidden);
        container.classList.add('context-menu');

        extraActions.forEach((action) => {
          if (action.shouldBeDisplayed && !action.shouldBeDisplayed()) {
            return;
          }
          const row = document.createElement('div')
          rowList.push(row);
          row.classList.add('context-menu-row');
          const onclick = () => {
            cleanup();
            action.invoke();
          };
          row.onclick = onclick;
          row.tabIndex = 0;
          navCtrl?.register(row, onclick);
          if (rowList.length === 1) row.focus();

          row.appendChild(document.createElement('span')).innerText = action.title;

          container.appendChild(row);
        });
      },
    });
    window.addEventListener('mousedown', onWindowMouseDown, true);
  }
  return overflowButton;
};

const createModalTitle = (args: CreateModalTitleArgs) => {
  const { renderLeft, extraActions, onClose } = args;
  const titleRowHolder = document.createElement('div');
  titleRowHolder.classList.add('modalTitle');

  const titleRowLeft = document.createElement('div');
  titleRowLeft.style.margin = 'auto 0';

  renderLeft(titleRowLeft);
  titleRowHolder.appendChild(titleRowLeft);

  const buttons = document.createElement('div');
  buttons.classList.add('button-holder');

  if (extraActions && extraActions.length > 0) {
    const overflowButton = createOverflowButton(extraActions);
    overflowButton.classList.add('modalOverflowButton');
    buttons.appendChild(overflowButton);
  }

  if (onClose) {
    const closeButton = document.createElement('div');
    closeButton.classList.add('modalCloseButton');
    if (args.shouldAutoCloseOnWorkspaceSwitch) {
      closeButton.classList.add('auto-click-on-workspace-switch');
    }
    const textHolder = document.createElement('span');
    textHolder.innerText = '𝖷';
    closeButton.appendChild(textHolder);
    closeButton.classList.add('clickHighlightOnHover');
    closeButton.onmousedown = (e) => { e.stopPropagation(); }
    closeButton.onclick = () => onClose();
    buttons.appendChild(closeButton);
  }

  titleRowHolder.appendChild(buttons);

  return {
    element: titleRowHolder
  };
}

export { createOverflowButton }
export default createModalTitle;
