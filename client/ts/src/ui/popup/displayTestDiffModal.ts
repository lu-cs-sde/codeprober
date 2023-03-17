import ModalEnv from '../../model/ModalEnv';
import TestCase from '../../model/test/TestCase';
import settings from '../../settings';
import createLoadingSpinner from '../create/createLoadingSpinner';
import createModalTitle from '../create/createModalTitle';
import showWindow from '../create/showWindow';
import renderProbeModalTitleLeft from '../renderProbeModalTitleLeft';
import encodeRpcBodyLines from './encodeRpcBodyLines';

const displayTestDiffModal = (
  env: ModalEnv,
  modalPos: ModalPosition | null,
  locator: NodeLocator,
  attr: AstAttrWithValue,
  testCategory: string,
  testCase: TestCase,
) => {
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;

  const cleanup = () => {
    delete env.onChangeListeners[queryId];
    delete env.probeMarkers[queryId];
    delete env.probeWindowStateSavers[queryId];
    env.currentlyLoadingModals.delete(queryId);
    env.testManager.removeListener(queryId);
    // env.triggerWindowSave();
    // if (localErrors.length > 0) {
    //   env.updateMarkers();
    // }
    // stickyController.cleanup();
  };

  const createTitle = () => {
    const onClose = () => {
      queryWindow.remove();
      cleanup();
    };
    return createModalTitle({
      renderLeft: (container) => renderProbeModalTitleLeft(
        env, container,
        onClose,
        () => queryWindow.getPos(),
        null, locator, attr,
      ),
      onClose,
      extraActions: [
        {
          title: 'Load state into editor',
          invoke: () => {
            settings.setAstCacheStrategy(testCase.cache);
            settings.setMainArgsOverride(testCase.mainArgs);
            settings.setPositionRecoveryStrategy(testCase.posRecovery);
            if (testCase.tmpSuffix && testCase.tmpSuffix !== settings.getCurrentFileSuffix()) {
              settings.setCustomFileSuffix(testCase.tmpSuffix);
            }
            settings.setEditorContents(testCase.src);
            window.location.reload();
          }
        }
      ]
    });
  };


  let lastSpinner: HTMLElement | null = null;
  let isFirstRender = true;
  let loading = false;
  let refreshOnDone = false;
  const queryWindow = showWindow({
    pos: modalPos,
    rootStyle: `
      min-width: 32rem;
      min-height: fit-content;
    `,
    resizable: true,
    // onFinishedMove: () => env.triggerWindowSave(),
    render: (root, { cancelToken }) => {
      if (lastSpinner != null) {
        lastSpinner.style.display = 'inline-block';
        lastSpinner = null;
      } else if (isFirstRender) {
        isFirstRender = false;
        while (root.firstChild) root.removeChild(root.firstChild);
        root.appendChild(createTitle().element);
        const spinner = createLoadingSpinner();
        spinner.classList.add('absoluteCenter');
        const spinnerWrapper = document.createElement('div');
        spinnerWrapper.style.height = '7rem' ;
        spinnerWrapper.style.display = 'block' ;
        spinnerWrapper.style.position = 'relative';
        spinnerWrapper.appendChild(spinner);
        root.appendChild(spinnerWrapper);
      }
      loading = true;

      Promise.all([
        // env.performRpcQuery({
        //   attr,
        //   locator,
        // }),
        env.testManager.getTestStatus(testCategory, testCase.name)
      ]).then((promiseResults) => {
          // const parsed: RpcResponse = promiseResults[0];
          const testStatus = promiseResults[0];

          // const body = parsed.body;
          loading = false;
          if (refreshOnDone) {
            refreshOnDone = false;
            queryWindow.refresh();
          }
          if (cancelToken.cancelled) { return; }
          while (root.firstChild) root.removeChild(root.firstChild);

          const titleRow = createTitle();
          root.append(titleRow.element);


          const addSplitTitle = (target: HTMLElement, title: string) => {
            const row = document.createElement('div');
            row.style.margin = '0.125rem';
            row.style.textAlign = 'center';
            // root.style.background =
            row.innerText = title;
            target.appendChild(row);

            const hr = document.createElement('hr');
            hr.style.marginTop = '0';
            target.appendChild(hr);
          };
          const splitPane = document.createElement('div');
          splitPane.style.display = 'grid';
          splitPane.style.gridTemplateColumns = '1fr 1px 1fr';

          const testReport = typeof testStatus === 'object' ? testStatus.report : '';

          const leftPane = document.createElement('div');
          addSplitTitle(leftPane, 'Expected');
          console.log('tstatus:', testStatus);
          switch (testCase.assert.type) {
            case 'identity':
              case 'set': {
                leftPane.appendChild(encodeRpcBodyLines(env, testCase.assert.lines, (line) => {
                  if (typeof testReport === 'object') {
                    if (testReport.unmatchedValid.includes(line)) {
                      return 'unmatched';
                    }
                  }
                  return 'default';
                }));
                break;
              }
              case 'smoke': {
                leftPane.appendChild(document.createTextNode(`
                  Smoke Test -> expected no error
                `.trim()));
              }
              default: {
                leftPane.appendChild(document.createTextNode(`
                  <Unknown Test Type>
                `.trim()));
              }
          }
          splitPane.appendChild(leftPane);

          const divider = document.createElement('div');
          divider.classList.add('vertical-separator');
          splitPane.appendChild(divider);

          const rightPane = document.createElement('div');
          addSplitTitle(rightPane, 'Actual');
          if (testStatus === 'failed-fetching') {
            rightPane.appendChild(document.createElement(`
              Failed val
            `.trim()));
          } else {
            rightPane.appendChild(encodeRpcBodyLines(env, testStatus.lines, (line) => {
              if (typeof testReport === 'object') {
                if (testReport.invalid.includes(line)) {
                  return 'error';
                }
              }
              return 'default';
            }));
          }
          splitPane.appendChild(rightPane);

          root.appendChild(splitPane);

          const spinner = createLoadingSpinner();
          spinner.style.display = 'none';
          spinner.classList.add('absoluteCenter');
          lastSpinner = spinner;
          root.appendChild(spinner);
        })
        .catch(err => {
          loading = false;
          if (refreshOnDone) {
            refreshOnDone = false;
            queryWindow.refresh();
            return;
          }
          if (cancelToken.cancelled) { return; }
          console.log('TestDiffModal RPC catch', err);
          root.innerHTML = '';
          root.innerText = 'Failed refreshing probe..';
          console.warn('Failed refreshing probe w/ args:', JSON.stringify({ locator, attr }, null, 2));
        })
    },
  });

  env.testManager.addListener(queryId, (change) => {
    if (change === 'test-status-update') {
      queryWindow.refresh();
    }
  });
}

export default displayTestDiffModal;
