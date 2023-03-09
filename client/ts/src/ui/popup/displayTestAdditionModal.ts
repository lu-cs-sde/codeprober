import ModalEnv from '../../model/ModalEnv';
import { AssertionLine } from '../../model/TestCase';
import settings from '../../settings';
import createLoadingSpinner from '../create/createLoadingSpinner';
import createModalTitle from '../create/createModalTitle';
import showWindow from '../create/showWindow';


const displayTestAdditionModal = (env: ModalEnv, modalPos: ModalPosition, locator: NodeLocator, attribute: AstAttrWithValue, output: RpcBodyLine[]) => {
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;

  // Capture this immediately, in case the user modifies text while this dialog is open
  const srcText = settings.getEditorContents() ?? '';
  let categories: string[] | 'loading' | 'failed-listing' = 'loading';

  const cleanup = () => {
    popup.remove();
  }
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
          header.innerText = `Add Test`;
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
        const addRow = (title: string, cb: (container: HTMLElement) => void) => {
          const row = document.createElement('div');
          row.style.padding = '0.5rem';
          row.style.display = 'grid';
          row.style.gridTemplateColumns = '8rem 1fr';

          const titleNode = document.createElement('span');
          titleNode.innerText = title;
          titleNode.style.textAlign = 'end';
          titleNode.style.marginRight = '0.25rem';
          row.appendChild(titleNode);
          // row.style.flexDirection = 'row';
          // row.style.justifyContent= 'space-between';
          cb(row);
          root.appendChild(row);
        };

        type AssertionType = 'Exact Match' | 'Set Comparison' | 'Smoke Test';
        const state: {category: string, name: string, assertionType: AssertionType } = {
          category: '',
          name: '',
          assertionType: 'Exact Match'
        };
        let update = () => {};

        ((() => {
          const datalistId = `${queryId}-categories`;
          const row = document.createElement('div');
          row.innerHTML = `
            <span style="text-align: end; margin-right: 0.25rem;">Category</span>
            <input id="cat-${queryId}" list="${datalistId}"> </input>
          `;
          row.style.padding = '0.5rem';
          row.style.display = 'grid';
          row.style.gridTemplateColumns = '8rem 1fr';

          const category = row.querySelector(`#cat-${queryId}`) as HTMLInputElement;
          // row.appendChild(category);
          // category.outerHTML = `<input list=${datalistId}></input>`;
          category.type = 'text';

          const datalist = document.createElement('datalist');
          datalist.id = datalistId;
          [...categories, 'foo', 'bar'].forEach(cat => {
            const opt = document.createElement('option');
            opt.value = cat.lastIndexOf('.') > 0 ? cat.slice(0, cat.lastIndexOf('.')) : cat;
            datalist.appendChild(opt);
          })
          row.appendChild(datalist);

          console.log('cat:', category);
          category.oninput = () => {
            console.log('change???');
            state.category = category.value;
            console.log('cat: ', state.category);
            update();
          }
          root.appendChild(row);
        })());

        addRow('Name', (row) => {
          const name = document.createElement('input');
          name.type = 'text';
          row.appendChild(name);

          name.oninput = () => {
            state.name = name.value;
            update();
          };
        });

        addRow('Assertion Type', (row) => {
          const opts = document.createElement('select');
          opts.value = state.assertionType[0];
          row.appendChild(opts);

          const assertionTypeList: AssertionType[] = ['Exact Match', 'Set Comparison', 'Smoke Test'];

          assertionTypeList.forEach((option) => {
            const val = document.createElement('option');
            val.value = option;
            val.innerText = option;
            opts.appendChild(val);
          });

          opts.oninput = () => {
            state.assertionType = opts.value as AssertionType;
            update();
          };
        });

        let commitButton = document.createElement('button');
        addRow('', (row) => {
          commitButton.innerText = 'Add';
          row.appendChild(commitButton);
          commitButton.onclick = () => {
            // typeof line === 'string' ? line : { naive: line, robust: line }
            const lineEncoder = (line: RpcBodyLine): AssertionLine => {
              if (typeof line === 'string') {
                return line;
              }
              if (Array.isArray(line)) {
                return line.map(lineEncoder);
              }

              switch (line.type) {
                case 'node': return { naive: line.value, robust: line.value };
                default: return line.value;
              }
            };
            const lines = output.map(lineEncoder);
            env.testManager.addTest(
              state.category,
              {
                type: 'test',
                assert: state.assertionType === 'Exact Match' ?
                {
                  type: 'identity',
                  lines,
                } : state.assertionType === 'Set Comparison' ?
                {
                  type: 'set',
                  lines,
                } : {
                  type: 'smoke'
                },
                attribute,
                locator: { naive: locator, robust: locator },
                name: state.name,
                src: srcText,
              }
            )
          }
        });


        addRow('', (row) => {
          const errMsg = document.createElement('p');
          errMsg.style.textAlign = 'center';
          errMsg.classList.add('captured-stderr')
          row.appendChild(errMsg);
          update = () => {
            commitButton.disabled = true;
            if (!state.category) {
              errMsg.innerText = `Missing category. The test will be saved in a file called '<category>.json'`;
            } else if (!/^[a-zA-Z0-9-]+$/.test(state.category)) {
              errMsg.innerText = `Invalid category, please only use '[a-zA-Z0-9-]' in the name, i.e A-Z, numbers and dash (-).'`;
            } else if (!state.name) {
              errMsg.innerText = `Missing test name`;
            } else {
              commitButton.disabled = false;
              errMsg.innerText = '';
            }
          };
        });
        update();
      }

    },
  });

  env.testManager.listCategories()
    .then((result) => {
      categories = result;
      popup.refresh();
    })
}

export default displayTestAdditionModal;
