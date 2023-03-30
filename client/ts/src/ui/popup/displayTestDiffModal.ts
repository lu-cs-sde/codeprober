import ModalEnv from '../../model/ModalEnv';
import TestCase from '../../model/test/TestCase';
import settings from '../../settings';
import createLoadingSpinner from '../create/createLoadingSpinner';
import createModalTitle from '../create/createModalTitle';
import showWindow from '../create/showWindow';
import renderProbeModalTitleLeft from '../renderProbeModalTitleLeft';
import UIElements from '../UIElements';
import displayProbeModal from './displayProbeModal';
import encodeRpcBodyLines from './encodeRpcBodyLines';


const preventDragOnClick = (elem: HTMLElement) => {
  elem.onmousedown = (e) => {
    e.stopPropagation();
    e.preventDefault();
  }
};
const displayTestDiffModal = (
  env: ModalEnv,
  modalPos: ModalPosition | null,
  locator: NodeLocator,
  attr: AstAttrWithValue,
  testCategory: string,
  testCase: TestCase,
) => {
  const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;

  type TabType = 'output' | 'node' | 'source' | 'settings';
  let activeTab: TabType = 'output';

  let isCleanedUp = false;
  const cleanup = () => {
    isCleanedUp = true;
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
  let saveSelfAsProbe = false;

  const createTitle = () => {
    const onClose = () => {
      queryWindow.remove();
      cleanup();
    };
    return createModalTitle({
      // renderLeft: (container) => renderProbeModalTitleLeft(
      //   env, container,
      //   onClose,
      //   () => queryWindow.getPos(),
      //   null, locator, attr,
      // ),
      renderLeft: (container) => {
        const head = document.createElement('span');
        head.classList.add('syntax-modifier');
        head.innerText = `Test:`;
        container.appendChild(head);
        head.style.marginRight = '0.5rem';

        const tail = document.createElement('span');
        tail.classList.add('stream-arg-msg');
        tail.innerText = testCase.name;
        container.appendChild(tail);
      },
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
            saveSelfAsProbe = true;
            env.triggerWindowSave();
            window.location.reload();
          }
        },
        {
          title: 'Delete Test (cannot be undone)',
          invoke: () => {
            env.testManager.removeTest(testCategory, testCase.name)
              .then(onClose)
              .catch((err) => {
                console.warn('Failed removing test', testCategory, '>', testCase.name, err);
              });
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
          if (isCleanedUp) return;
          const testStatus = promiseResults[0];

          loading = false;
          if (refreshOnDone) {
            refreshOnDone = false;
            queryWindow.refresh();
          }
          if (cancelToken.cancelled) { return; }
          while (root.firstChild) root.removeChild(root.firstChild);

          const titleRow = createTitle();
          root.append(titleRow.element);

          const testReport = typeof testStatus === 'object' ? testStatus.report : '';

          let contentUpdater = (tab: TabType) => {};

          const localRefreshListeners: (() => void)[] = [];
          env.onChangeListeners[queryId] = () => localRefreshListeners.forEach(lrl => lrl());
          (() => {

            const buttonRow = document.createElement('div');
            buttonRow.style.display = 'flex';
            buttonRow.style.flexDirection = 'row';
            buttonRow.style.justifyContent = 'space-between';
            root.append(buttonRow);

            const infoSelector = document.createElement('div');
            infoSelector.classList.add('test-diff-info-selector');
            buttonRow.append(infoSelector);

            const someOutputErr = testReport === 'failed-eval' || typeof testReport == 'object' && testReport.output !== 'pass';
            const someLocatorErr = testReport === 'failed-eval' || typeof testReport === 'object' && [
              testReport.sourceLocators, testReport.attrArgLocators, testReport.outputLocators
            ].some(loc => loc !== 'pass')
            const infos: { name: string, type: TabType, btn?: HTMLButtonElement }[] = [
              { name: `Output Diff ${someOutputErr ? '❌' : '✅'}`, type: 'output' },
              { name: `Node Diff ${someLocatorErr ? '❌' : '✅'}`, type: 'node' },
              { name: 'Source Code', type: 'source' },
              { name: 'Settings', type: 'settings' },
            ];

            infos.forEach((info, index) => {
              const btn = document.createElement('button');
              btn.classList.add(info.type === activeTab ? 'tab-button-active' : 'tab-button-inactive');
              btn.innerText = info.name;
              preventDragOnClick(btn);
              btn.onclick = () => {
                contentUpdater(info.type);
                btn.classList.remove('tab-button-inactive');
                btn.classList.add('tab-button-active');
                infos.forEach((other, otherIdx) => {
                  if (otherIdx !== index) {
                    other.btn?.classList?.remove('tab-button-active');
                    other.btn?.classList?.add('tab-button-inactive');
                  }
                })
              };
              infoSelector.appendChild(btn);
              infos[index].btn = btn;
            });

            const refreshSourceButtonText = () => {
              const sourceBtn = infos.find(i => i.type === 'source')?.btn;
              if (sourceBtn) {
                if (testCase.src === settings.getEditorContents()) {
                  sourceBtn.innerText = `Source Code ✅`;
                } else {
                  sourceBtn.innerText = `Source Code ⚠️`;
                }
              }
            };

            refreshSourceButtonText();
            localRefreshListeners.push(refreshSourceButtonText);

            // Add save button
            (() => {
              if (testReport === 'pass') { return; }
              const btn = document.createElement('button');
              btn.style.margin = 'auto 0';
              btn.classList.add('tab-button-inactive');
              btn.innerText = `Save 💾`;
              preventDragOnClick(btn);
              if (testStatus === 'failed-fetching') {
                btn.disabled = true;
              } else {
                btn.onclick = () => {
                  const patchedAssert = ((): TestCase['assert'] => {
                    switch (testCase.assert.type) {
                      case 'identity':
                      case 'set':
                        return { ...testCase.assert, lines: testStatus.lines };
                      case 'smoke':
                        default:
                          return testCase.assert;
                    }
                  })();
                  env.testManager.addTest(testCategory, {
                    ...testCase,
                    assert: patchedAssert,
                  }, true);
                };
              }
              buttonRow.appendChild(btn);
            })();

            const hr = document.createElement('hr');
            hr.style.marginTop = '0';
            hr.style.marginBottom = '0';
            root.appendChild(hr);
          })();


          const contentRoot = document.createElement('div');

          // let activeTab: TabType = 'settings'; // Different from actual first tab so as to detect first tab change
          contentUpdater = (tab) => {
            if (tab === activeTab) { return; }
            activeTab = tab;
            while (contentRoot.firstChild) contentRoot.removeChild(contentRoot.firstChild);
            switch (tab) {
              case 'output':
              default: {
                const addSplitTitle = (target: HTMLElement, title: string) => {
                  const row = document.createElement('div');
                  row.style.margin = '0.125rem';
                  row.style.textAlign = 'center';
                  row.style.height = '1.5rem';
                  row.style.display = 'flex';
                  target.appendChild(row);

                  const lbl = document.createElement('span');
                  lbl.innerText = title;
                  lbl.style.margin = 'auto';
                  row.appendChild(lbl);

                  const hr = document.createElement('hr');
                  hr.style.marginTop = '0';
                  target.appendChild(hr);

                  return lbl;
                };
                const splitPane = document.createElement('div');
                splitPane.style.display = 'grid';
                splitPane.style.gridTemplateColumns = '1fr 1px 1fr';

                const leftPane = document.createElement('div');
                addSplitTitle(leftPane, 'Expected');
                console.log('tstatus:', testStatus);
                switch (testCase.assert.type) {
                  case 'identity':
                    case 'set': {
                      leftPane.appendChild(encodeRpcBodyLines(env, testCase.assert.lines, (line) => {
                        if (typeof testReport === 'object' && typeof testReport.output === 'object') {
                          if (testReport.output.unmatchedValid.includes(line)) {
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
                    if (typeof testReport === 'object' && typeof testReport.output === 'object') {
                      if (testReport.output.invalid.includes(line)) {
                        return 'error';
                      }
                    }
                    return 'default';
                  }));
                }
                splitPane.appendChild(rightPane);

                contentRoot.appendChild(splitPane);
                break;
              }
              case 'node': {
                contentRoot.appendChild(document.createTextNode('todo node tab'));
                break;
              }
              case 'source': {
                const wrapper = document.createElement('div');
                wrapper.style.padding = '0.25rem';
                contentRoot.appendChild(wrapper);

                const same = testCase.src === settings.getEditorContents();
                if (same) {
                  wrapper.appendChild(document.createTextNode(`Current text in CodeProber matches the source code used for this test case.`));
                  wrapper.appendChild(document.createElement('br'));
                  wrapper.appendChild(document.createTextNode(`Press`));
                  const btn = document.createElement('button');
                  preventDragOnClick(btn);
                  btn.style.margin = '0 0.125rem';
                  btn.innerText = `Open Probe`;
                  btn.onclick = () => {
                    displayProbeModal(env, null, locator, attr);
                  };
                  wrapper.appendChild(btn);
                  wrapper.appendChild(document.createTextNode(`to explore the probe that created this test.`));
                } else {
                  wrapper.appendChild(document.createTextNode(`Current text in CodeProber does not match the source code used for this test case.`));
                  wrapper.appendChild(document.createElement('br'));
                  wrapper.appendChild(document.createTextNode(`Press`));
                  const btn = document.createElement('button');
                  preventDragOnClick(btn);
                  btn.style.margin = '0 0.125rem';
                  btn.innerText = `Load Source`;
                  btn.onclick = () => {
                    env.setLocalState(testCase.src);
                  };
                  wrapper.appendChild(btn);
                  wrapper.appendChild(document.createTextNode(`to replace CodeProber text with the test source.`));
                }

                wrapper.appendChild(document.createElement('hr'));

                const addExplanationLine = (head: string, value: string, valueClass = '') => {
                  const line = document.createElement('div');

                  const headNode = document.createElement('span');
                  headNode.style.display = 'inline-block';
                  headNode.style.marginRight = '1rem';
                  headNode.style.textAlign = 'right';
                  headNode.style.minWidth = '6rem';
                  headNode.innerText = head;
                  line.appendChild(headNode);

                  const valueNode = document.createElement('span');
                  valueNode.innerText = value;
                  if (valueClass) {
                    valueNode.classList.add(valueClass);
                  }
                  line.appendChild(valueNode);
                  wrapper.appendChild(line);
                }
                addExplanationLine('Node type:', (locator.result.label || locator.result.type).split('.').slice(-1)[0], 'syntax-type');
                addExplanationLine('Property:', attr.name, 'syntax-attr');
                if (locator.result.external) {
                  addExplanationLine('AST Node:', `${locator.result.label || locator.result.type} in external file`);
                } else {
                  // const span = startEndToSpan(locator.result.start, locator.result.end);
                  // const linesTail = span.lineStart === span.lineEnd
                  //   ? ` $${span.lineStart}`
                  //   : `s ${span.lineStart}→${span.lineEnd}`;
                  // addExplanationLine('AST Node:', `${locator.result.label || locator.result.type} on line${linesTail}`);
                }
                addExplanationLine('Source Code', '⬇️');

                testCase.src.split('\n').forEach((line, lineIdx) => {


                  const lineContainer = document.createElement('div');

                  lineContainer.classList.add('test-case-source-code-line');
                  if (!locator.result.external) {
                    const startLineIdx = (locator.result.start >>> 12) - 1;
                    const endLineIdx = (locator.result.end >>> 12) - 1;
                    if (lineIdx >= startLineIdx && lineIdx <= endLineIdx) {
                      lineContainer.setAttribute('data-relevant', 'true');
                    }
                  }
                  lineContainer.appendChild((() => {
                    const pos = document.createElement('span');
                    pos.innerText = `${lineIdx + 1}: `.padStart(3);
                    return pos;
                  })());
                  lineContainer.appendChild((() => {
                    const pre = document.createElement('span');
                    pre.innerText = line;
                    return pre;
                  })());

                  wrapper.appendChild(lineContainer);
                });
                break;
              }
              case 'settings': {
                const wrapper = document.createElement('div');
                wrapper.style.padding = '0.25rem';
                contentRoot.appendChild(wrapper);

                const header = document.createElement('p');
                header.style.margin = '0.25rem 0';
                header.innerText = `Each saves the current CodeProber settings upon being created. This test contains:`;

                const translateSelectorValToHumanLabel = (val: string, selector: HTMLElement | null) => {
                  if (!selector) {
                    console.warn('no selector for', val);
                    return val;
                  }

                  for (let i = 0; i < selector.childElementCount; ++i) {
                    const opt = selector.children[i];
                    if (opt.nodeName === 'OPTION') {
                      if ((opt as HTMLOptionElement).value === val) {
                        return opt.innerHTML;
                      }
                    }
                  }
                  console.warn(`Not sure how to translate ${val} to human-readable value`)
                  return val;
                };
                const ul = document.createElement('ul');
                ul.style.margin = '0 0 0.25rem';
                const uiElements = new UIElements();
                [
                  ['Position Recovery', translateSelectorValToHumanLabel(testCase.posRecovery, uiElements.positionRecoverySelector)],
                  ['Cache Strategy', translateSelectorValToHumanLabel(testCase.cache, uiElements.astCacheStrategySelector)],
                  ['File suffix', testCase.tmpSuffix],
                  ['Main args', testCase.mainArgs ? `[${testCase.mainArgs.join(', ')}]` : 'none'],
                ].forEach(([name, val]) => {
                  const li = document.createElement('li');
                  li.innerText = `${name} : ${val}`;
                  ul.appendChild(li);
                });
                contentRoot.appendChild(ul);
                // contentRoot.appendChild(document.createTextNode('todo settings tab'));
                break;
              }
            }
          };

          root.appendChild(contentRoot);

          const initialTab = activeTab;
          // Set to anything different in order to force first update to go through
          activeTab = initialTab === 'output' ? 'node' : 'output';
          contentUpdater(initialTab);

          localRefreshListeners.push(() => {
            if (activeTab === 'source') {
              activeTab = 'node';
              contentUpdater('source');
            }
          })

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
          root.innerText = 'Failed refreshing test diff..';
          console.warn('Failed refreshing test w/ args:', JSON.stringify({ locator, attr }, null, 2));
          setTimeout(() => { // TODO remove this again, once the title bar is added so we can close it
            queryWindow.remove();
            cleanup();
          }, 2000);
        })
    },
  });

  env.testManager.addListener(queryId, (change) => {
    if (change === 'test-status-update') {
      queryWindow.refresh();
    }
  });
  env.probeWindowStateSavers[queryId] = (target) => {
    if (saveSelfAsProbe) {
      target.push({
        modalPos: queryWindow.getPos(),
        data: {
          type: 'probe',
          locator,
          attr,
        },
      });
    }
  }
}

export default displayTestDiffModal;
