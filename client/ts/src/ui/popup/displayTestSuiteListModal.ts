import ModalEnv from '../../model/ModalEnv';
import createLoadingSpinner from '../create/createLoadingSpinner';
import createModalTitle from '../create/createModalTitle';
import showWindow from '../create/showWindow';
import displayTestSuiteModal from './displayTestSuiteModal';

const displayTestSuiteListModal = (
  env: ModalEnv,
  onClose: () => void,
) => {
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;

  let isClosed = false;
  const cleanup = () => {
    isClosed = true;
    onClose();
    popup.remove();
    env.testManager.removeListener(queryId);
  };

  let categories: string[] | 'loading' | 'failed-listing' = 'loading';
  let changeCallbackCounter = 0;

  let localTestSuiteKnowledge: { [suiteId: string]: { pass: number, fail: number }} = {};
  const getLocalKnowledge = (suiteId: string) => {
    return localTestSuiteKnowledge[suiteId] = localTestSuiteKnowledge[suiteId] || { pass: 0, fail: 0 };
  };
  const popup = showWindow({
    rootStyle: `
      width: 16rem;
      min-width: 4rem;
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
        extraActions: (typeof categories === 'string' || categories.length === 0) ? [] : [
          {
            title: 'Run all',
            invoke: () => {
              localTestSuiteKnowledge = {};
              (async () => {
                const suites = await env.testManager.listTestSuiteCategories();
                if (suites === 'failed-listing') {
                  return;
                }
                for (let i = 0; i < suites.length; ++i) {
                  localTestSuiteKnowledge[suites[i]] = { fail: 0, pass: 0 };
                }
                popup.refresh();
                // Very intentionally evaluate this sequentially (not Promise.all)
                // Don't want to send a billion requests at once
                for (let i = 0; i < suites.length; ++i) {
                  const suite = suites[i];
                  let cases = await env.testManager.getTestSuite(suite);
                  console.log('Running test', suite, ', list:', cases);
                  if (cases === 'failed-fetching') {
                    return;
                  };
                  if (isClosed) {
                    return;
                  }
                  cases = [...cases];
                  // For caching purposes, sort so that all equal-text cases are evaluated next to each other
                  cases.sort((a, b) => {
                    if (a.src !== b.src) {
                      return a.src < b.src ? -1 : 1;
                    }
                    if (a.cache != b.cache) {
                      return a.cache < b.cache ? -1 : 1;
                    }
                    // Close enough to equal
                    return 0;
                  })

                  for (let j = 0; j < cases.length; ++j) {
                    const expectedChangeCallback = changeCallbackCounter + 1;
                    const status = await env.testManager.getTestStatus(suite, cases[j].name);
                    console.log('Ran test', suite, '>', cases[j].name, ', status:', status);
                    if (isClosed) {
                      return;
                    }
                    if (status === 'failed-fetching' || status.report !== 'pass') {
                      ++getLocalKnowledge(suite).fail;
                    } else if (status.report === 'pass') {
                      ++getLocalKnowledge(suite).pass;
                    }
                    if (changeCallbackCounter !== expectedChangeCallback) {
                      // Result was cached, force reload to
                      popup.refresh();
                    }

                    // await new Promise((res) => setTimeout(res, 10));
                  }
                }

              })()
                .catch((asdasd) => {
                  console.warn('Error when running all tests');
                })
            },
          }
        ]
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

        if (categories.length == 0) {
          const emptyMsg = document.createElement('div');
          emptyMsg.innerText = 'No tests here. Create them from the "..." menu in a probe.';
          emptyMsg.style.textAlign = 'center';
          rowList.appendChild(emptyMsg);
        }

        categories.forEach(cat => {
          const row = document.createElement('div');
          row.classList.add('test-suite');

          const title = document.createElement('span');
          title.innerText = cat;
          title.style.marginRight = '1rem';
          row.appendChild(title);

          const icons = document.createElement('div');
          row.appendChild(icons);

          const status = document.createElement('span');

          env.testManager.getTestSuite(cat).then((res) => {
            if (res === 'failed-fetching') {
              status.innerText = `<failed fetching>`;
            } else if (res.length === 0) {
              status.innerText = `Empty suite`;
            } else {
              if (localTestSuiteKnowledge[cat]) {
                const know = localTestSuiteKnowledge[cat];
                if ((know.pass + know.fail) === res.length) {
                  if (know.fail === 0) {
                    status.innerText = `✅`;
                  } else {
                    status.innerText = `Fail ${know.fail}/${res.length} ❌`;
                  }
                } else {
                  // Loading
                  console.log('loading, know:', know, '; res.length:', res.length);
                  const pct = ((know.fail + know.pass) * 100 / res.length) | 0;
                 status.innerText = `${pct}% ⏳`;
               }
              } else {
                status.innerText = `${res.length} ${res.length === 1 ? 'test' : 'tests'}`;
              }
            }
          });
          icons.appendChild(status);

          row.onclick = () => displayTestSuiteModal(env, cat);

          rowList.appendChild(row);
        });
      }
    }
  });


  const reloadList = () => {
    env.testManager.listTestSuiteCategories()
      .then((result) => {
        categories = result;
        popup.refresh();
      });
  };
  reloadList();

  env.testManager.addListener(queryId, (change) => {
    ++changeCallbackCounter;
    if (change === 'added-test' || change == 'removed-test') {
      localTestSuiteKnowledge = {};
    }
    reloadList();
  });
}


export default displayTestSuiteListModal;
