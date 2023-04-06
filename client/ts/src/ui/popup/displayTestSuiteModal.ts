import ModalEnv from '../../model/ModalEnv';
import { TestCase } from '../../protocol';
import createLoadingSpinner from '../create/createLoadingSpinner';
import createModalTitle from '../create/createModalTitle';
import showWindow from '../create/showWindow';
import displayTestDiffModal from './displayTestDiffModal';

const displayTestSuiteModal = (
  env: ModalEnv,
  category: string,
) => {
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;

  const cleanup = () => {
    popup.remove();
    env.testManager.removeListener(queryId);
  };

  let contents: TestCase[] | 'loading' | 'failed-fetching' = 'loading';

  const popup = showWindow({
    rootStyle: `
      min-width: 16rem;
      min-height: fit-content;
    `,
    onForceClose: cleanup,
    resizable: true,
    render: (root) => {
      while (root.firstChild) root.removeChild(root.firstChild);
      root.appendChild(createModalTitle({
        renderLeft: (container) => {
          const header = document.createElement('span');
          header.innerText = `Test Suite: ${category}`;
          container.appendChild(header);
        },
        onClose: cleanup,
        extraActions: [
          {
            title: 'Save all tests as expected behavior',
            invoke: () => {
              const confirmation = confirm(`Are you sure? This cannot be undone. One or more of the test failures may represent meaningful information, which will be lost if you save it as 'expected' behavior`);
              if (!confirmation) {
                return;
              }
              (async () => {
                const suite = await env.testManager.getTestSuite(category);
                if (suite === 'failed-fetching') {
                  return;
                }
                for (let i = 0; i < suite.length; ++i) {
                  const tcase = suite[i];
                  // if (tcase === '')
                  const evalRes = await env.testManager.evaluateTest(category, tcase.name);
                  if (evalRes === 'failed-fetching') {
                    continue;
                  }
                  if (evalRes.status.overall === 'ok') {
                    continue;
                  }
                  const mapped = env.testManager.convertTestResponseToTest(tcase, evalRes.output);
                  if (mapped === null) {
                    continue;
                  }
                  await env.testManager.addTest(category, mapped, true);
                }
              })();
            }
          }
        ],
      }).element);

      if (typeof contents === 'string') {
        if (contents === 'loading') {
          root.style.display = 'contents';
          const spinner = createLoadingSpinner();
          spinner.classList.add('absoluteCenter');
          root.appendChild(spinner);
        } else {
          const textHolder = document.createElement('div');
          textHolder.style.padding = '0.5rem';
          textHolder.innerText = `Failed fetch test content, have you set '-Dcpr.testDir=<a valid directory>'? If so, check if ${category}.json exists and contains valid JSON.`;
        }
      } else {
        // root.style.display = 'flex';
        const rowList = document.createElement('div');
        rowList.style.display = 'flex';
        rowList.style.flexDirection = 'column';
        rowList.style.margin = '0 0 auto';
        root.appendChild(rowList);

        // console.log('render contents', contents);
        contents.forEach(tc => {
          const row = document.createElement('div');
          row.classList.add('test-case');

          const title = document.createElement('span');
          title.innerText = tc.name;
          title.style.marginRight = '1rem';
          row.appendChild(title);

          const icons = document.createElement('div');
          row.appendChild(icons);

          const status = document.createElement('span');
          status.innerText = `⏳`;
          row.style.order = '3';
          env.testManager.evaluateTest(category, tc.name).then(result => {
            // console.log('tstatus for', category, '>', tc.name, ':', result);
            if (result == 'failed-fetching') {
              status.innerText = `<Evaluation Error>`;
            } else {
              const report = result.status.overall;
              // if (typeof report === 'string') {
                switch (report) {
                  case 'ok': {
                    status.innerText = `✅`;
                    row.style.order = '5';
                    break;
                  }
                  case 'error': {
                    status.innerText = `❌`;
                    row.style.order = '1';
                    break;
                  }
                  default: {
                    console.warn('Unknown test report string value:', report);
                  }
                }
              // } else {
              //   if (report.output === 'pass') {
              //     // "only" locator diff, should be less severe
              //     console.log('partial test failure?', JSON.stringify(report))
              //     status.innerText = `⚠️`;
              //   } else {
              //     status.innerText = `❌`;
              //     row.style.order = '1';

              //   }
              // }
            }
          });
          icons.appendChild(status);

          row.onclick = () => {
            displayTestDiffModal(env, null, tc.locator, tc.property, category, tc.name);
          };

          rowList.appendChild(row);
        });
      }
    }
  });


  const reloadList = () => {
      env.testManager.getTestSuite(category)
        .then((result) => {
          contents = result;
          popup.refresh();
        });
    };
    reloadList();

  env.testManager.addListener(queryId, reloadList);
}


export default displayTestSuiteModal;
