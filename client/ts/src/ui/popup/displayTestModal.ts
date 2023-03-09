import ModalEnv from '../../model/ModalEnv';
import TestManager from '../../model/TestManager';
import createLoadingSpinner from '../create/createLoadingSpinner';
import createModalTitle from '../create/createModalTitle';
import showWindow from '../create/showWindow';

const displayTestModal = (
  env: ModalEnv,
  onClose: () => void,
) => {
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;

  const cleanup = () => {
    onClose();
    popup.remove();
  };

  let categories: string[] | 'loading' | 'failed-listing' = 'loading';

  const popup = showWindow({
    rootStyle: `
      width: 32rem;
      min-height: 8rem;
    `,
    resizable: true,
    render: (root) => {
      while (root.firstChild) root.removeChild(root.firstChild);
      root.appendChild(createModalTitle({
        renderLeft: (container) => {
          const header = document.createElement('span');
          header.innerText = `Test Suites`;
          container.appendChild(header);
        },
        onClose: cleanup,
      }).element);


      if (typeof categories === 'string') {
        if (categories === 'loading') {
          root.style.display = 'contents';
          const spinner = createLoadingSpinner();
          spinner.classList.add('absoluteCenter');
          root.appendChild(spinner);
        } else {

          const textHolder = document.createElement('div');
          textHolder.style.padding = '0.5rem';
          textHolder.innerText = `Failed listing test categories, have you set '-Dcpr.testDir=<a valid directory>'?`;

        }
      } else {
        // root.style.display = 'flex';
        const rowList = document.createElement('div');
        rowList.style.display = 'flex';
        rowList.style.flexDirection = 'column';
        rowList.style.margin = '0 0 auto';
        root.appendChild(rowList);

        categories.forEach(cat => {
          const row = document.createElement('div');
          row.classList.add('test-category');

          const title = document.createElement('span');
          title.innerText = cat.lastIndexOf('.') > 0 ? cat.substring(0, cat.lastIndexOf('.')) : cat;
          row.appendChild(title);

          const icons = document.createElement('div');
          row.appendChild(icons);

          const status = document.createElement('span');
          status.innerText = '5/5 ✅';
          icons.appendChild(status);

          rowList.appendChild(row);
        });
      }

      // const paragraphs = getHelpContents(type);
      // paragraphs.forEach(p => {
      //   if (!p) {
      //     textHolder.appendChild(document.createElement('br'));
      //     return;
      //   }
      //   if (typeof p !== 'string') {
      //     textHolder.appendChild(p);
      //     return;
      //   }
      //   const node = document.createElement('p');
      //   node.appendChild(document.createTextNode(p));
      //   node.style.marginTop = '0';
      //   node.style.marginBottom = '0';
      //   textHolder.appendChild(node);
      // });

      // root.appendChild(textHolder);
    }
  });

  env.testManager.listCategories()
    .then((result) => {
      categories = result;
      popup.refresh();
    });
}


export default displayTestModal;
