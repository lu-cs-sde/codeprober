import showWindow from "./showWindow";

interface ExtraAction {
  title: string;
  invoke: () => void;
}
type CreateModalTitleArgs = { renderLeft: (container: HTMLElement) => void; extraActions?: ExtraAction[]; onClose: () => void; };

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
    const overflowButton = document.createElement('img');
    overflowButton.src = '/icons/more_vert_white_24dp.svg';
    overflowButton.classList.add('modalOverflowButton');
    overflowButton.classList.add('clickHighlightOnHover');
    overflowButton.onmousedown = (e) => { e.stopPropagation(); }
    overflowButton.onclick = () => {

      const cleanup = () => {
        contextMenu.remove();
        window.removeEventListener('mousedown', cleanup);
      }
      const contextMenu = showWindow({
        rootStyle: `
          // width: 16rem;
        `,
        render: (container) => {
          container.addEventListener('mousedown', (e) => {
            console.log('container.onmousedown..');
            e.stopPropagation();
            e.stopImmediatePropagation();
          });
          const hidden = document.createElement('button');
          hidden.classList.add('modalCloseButton');
          hidden.style.display = 'none';
          hidden.onclick = cleanup;
          container.appendChild(hidden);

          extraActions.forEach((action) => {
            const row = document.createElement('div')
            row.classList.add('context-menu-row');
            row.onclick = () => {
              cleanup();
              action.invoke();
            }

            const title = document.createElement('span');
            title.innerText = action.title;
            row.appendChild(title);

            // const icon = document.createElement('img');
            // icon.src = '/icons/content_copy_white_24dp.svg';
            // row.appendChild(icon);
            // row.style.border = '1px solid red';
            container.appendChild(row);
          });
        },
      })
      window.addEventListener('mousedown', () => {
        console.log('window.onmousedown')
        cleanup();
      });
    }

    buttons.appendChild(overflowButton);
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
}

export default createModalTitle;
