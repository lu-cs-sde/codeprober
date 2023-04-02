import ModalEnv from '../../model/ModalEnv';
import TestCase from '../../model/test/TestCase';
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
          env.testManager.getTestStatus(category, tc.name).then(result => {
            // console.log('tstatus for', category, '>', tc.name, ':', result);
            if (result == 'failed-fetching') {
              status.innerText = `<Evaluation Error>`;
            } else {
              const report = result.report;
              if (typeof report === 'string') {
                switch (report) {
                  case 'pass': {
                    status.innerText = `✅`;
                    row.style.order = '5';
                    break;
                  }
                  case 'failed-eval': {
                    status.innerText = `❌`;
                  row.style.order = '1';
                  }
                  default: {
                    console.warn('Unknown test report string value:', report);
                  }
                }
              } else {
                if (report.output === 'pass') {
                  // "only" locator diff, should be less severe
                  console.log('partial test failure?', JSON.stringify(report))
                  status.innerText = `⚠️`;
                } else {
                  status.innerText = `❌`;
                  row.style.order = '1';

                }
              }
            }
          });
          // env.testManager.getTestSuite(cat).then((res) => {
          //   if (res === 'failed-fetching') {
          //     status.innerText = `<failed fetching>`;
          //   } else if (res.length === 0) {
          //     status.innerText = `Empty suite`;
          //   } else {
          //     status.innerText = `${res.length}/${res.length} ✅`;
          //   }
          // });
          icons.appendChild(status);

          row.onclick = () => {
            displayTestDiffModal(env, null, tc.locator.robust, tc.attribute, category, tc);
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
