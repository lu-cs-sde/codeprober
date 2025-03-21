import ModalEnv from '../../model/ModalEnv';
import { rpcLinesToAssertionLines } from '../../model/test/rpcBodyToAssertionLine';
import { NestedTestResponse } from '../../model/test/TestManager';
import { createImmutableLocator, createMutableLocator } from '../../model/UpdatableNodeLocator';
import { NestedWindows, WindowStateDataProbe } from '../../model/WindowState';
import { Property, NodeLocator, TestCase, RpcBodyLine, NestedTest } from '../../protocol';
import settings from '../../settings';
import createInlineWindowManager from '../create/createInlineWindowManager';
import createLoadingSpinner from '../create/createLoadingSpinner';
import createModalTitle from '../create/createModalTitle';
import showWindow from '../create/showWindow';
import renderProbeModalTitleLeft from '../renderProbeModalTitleLeft';
import UIElements from '../UIElements';
import displayHelp from './displayHelp';
import displayProbeModal, { searchProbePropertyName } from './displayProbeModal';
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
  property: Property,
  testCategory: string,
  testCaseName: string,
  // testCase: TestCase,
) => {
  const queryId = `tcdiff-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;

  type TabType = 'output' | 'node' | 'source' | 'settings';
  let activeTab: TabType = 'output';
  // let lastKnownCapturedStdout;
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

  let lastLoadedTestCase: TestCase | null = null;

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
        tail.innerText = testCaseName;
        container.appendChild(tail);
      },
      onClose,
      extraActions: [
        ...(
          lastLoadedTestCase != null ? (() => {
            const tc = lastLoadedTestCase;
            return [
              {
                title: 'Load state into editor',
                invoke: () => {
                  settings.setAstCacheStrategy(tc.src.cache);
                  settings.setMainArgsOverride(tc.src.mainArgs ?? null);
                  settings.setPositionRecoveryStrategy(tc.src.posRecovery);
                  if (tc.src.tmpSuffix && tc.src.tmpSuffix !== settings.getCurrentFileSuffix()) {
                    settings.setCustomFileSuffix(tc.src.tmpSuffix);
                  }
                  settings.setEditorContents(tc.src.src.value);
                  saveSelfAsProbe = true;
                  env.triggerWindowSave();
                  window.location.reload();
                }
              },
              {
                title: 'Delete Test (cannot be undone)',
                invoke: () => {
                  env.testManager.removeTest(testCategory, tc.name)
                    .then(onClose)
                    .catch((err) => {
                      console.warn('Failed removing test', testCategory, '>', tc.name, err);
                    });
                }
              },
            ]
          })() : []
        ),
      ]
    });
  };


  let lastSpinner: HTMLElement | null = null;
  let isFirstRender = true;
  let refreshOnDone = false;
  const queryWindow = showWindow({
    pos: modalPos,
    rootStyle: `
      min-width: 32rem;
      min-height: fit-content;
    `,
    onForceClose: cleanup,
    resizable: true,
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

      env.testManager.evaluateTest(testCategory, testCaseName)
        .then((evaluationResult) => {
          if (isCleanedUp) return;

          if (refreshOnDone) {
            refreshOnDone = false;
            queryWindow.refresh();
          }
          if (cancelToken.cancelled) { return; }
          while (root.firstChild) root.removeChild(root.firstChild);


          if (evaluationResult === 'failed-fetching') {
            root.append(createTitle().element);
            root.appendChild(document.createTextNode(`Failed running test, please check the server log for more information`));
            lastLoadedTestCase = null;
            return;
          }

          const testStatus = evaluationResult.status;
          const testCase = evaluationResult.test;
          lastLoadedTestCase = testCase;

          root.append(createTitle().element);


          let contentUpdater = (tab: TabType) => {};

          const localRefreshListeners: (() => void)[] = [];
          env.onChangeListeners[queryId] = () => localRefreshListeners.forEach(lrl => lrl());
          const someOutputErr = testStatus.overall === 'error';
          // const someLocatorErr = testReport.overall === 'error' || typeof testReport === 'object' && [
            //   testReport.sourceLocators, testReport.attrArgLocators, testReport.outputLocators
            // ].some(loc => loc !== 'pass');
          const captureStdioSetting = settings.shouldCaptureStdio();
          localRefreshListeners.push(() => {
            if (settings.shouldCaptureStdio() !== captureStdioSetting) {
              queryWindow.refresh();
            }
          });
          (() => {

            const buttonRow = document.createElement('div');
            buttonRow.style.display = 'flex';
            buttonRow.style.flexDirection = 'row';
            buttonRow.style.justifyContent = 'space-between';
            root.append(buttonRow);

            const infoSelector = document.createElement('div');
            infoSelector.classList.add('test-diff-info-selector');
            buttonRow.append(infoSelector);

            const infos: { name: string, type: TabType, btn?: HTMLButtonElement }[] = [
              { name: `Output Diff ${someOutputErr ? '❌' : '✅'}`, type: 'output' },
              // { name: `Node Diff ${someLocatorErr ? '❌' : '✅'}`, type: 'node' },
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
                if (testCase.src.src.value === settings.getEditorContents()) {
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
              if (testStatus.overall === 'ok') { return; }
              const result = evaluationResult.output.result;
              const mapped = env.testManager.convertTestResponseToTest(testCase, evaluationResult.output);
              if (mapped === null) {
                return;
              }
              // const mapped = nestedTestResponseToTest(evaluationResult.output);
              // if (mapped === null) {
              //   return;
              // }

              const btn = document.createElement('button');
              btn.style.margin = 'auto 0';
              btn.classList.add('tab-button-inactive');
              btn.innerText = `Save 💾`;
              preventDragOnClick(btn);
                btn.onclick = () => {
                  env.testManager.addTest(testCategory, mapped, true)
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
                switch (testCase.assertType) {
                  case 'IDENTITY':
                    case 'SET': {
                      const doEncodeRpcLines = (target: HTMLElement, lines: RpcBodyLine[], nests: NestedTest[], markerPrefix: number[]) => {
                        const lhsInlineWindowManager = createInlineWindowManager();
                        if (!captureStdioSetting) {
                          lines = rpcLinesToAssertionLines(lines);
                        }
                        target.appendChild(encodeRpcBodyLines(env, lines, {
                          lateInteractivityEnabledChecker: () => false,
                          excludeStdIoFromPaths: true,
                          capWidths: true,
                          decorator: (line) => {
                            const key = JSON.stringify([...markerPrefix, ...line]);
                            const marker = testStatus.expectedMarkers[key];
                            if (marker === 'error') {
                              return 'unmatched';
                            }
                            return 'default';
                          },
                          disableNodeSelectors: true,
                          disableInlineExpansionButton: true,
                          nodeLocatorExpanderHandler: {
                            getReusableExpansionArea: () => null,
                            onCreate: ({ path, locatorRoot, expansionArea, locator }) => {
                              const encodedPath = JSON.stringify(path);
                              const relatedNests = nests.filter(nest => JSON.stringify(nest.path) === encodedPath);
                              if (relatedNests.length === 0) {
                                return;
                              }
                              const wrappedLocator = createImmutableLocator(createMutableLocator(locator));
                              const area = lhsInlineWindowManager.getArea(path, locatorRoot, expansionArea, wrappedLocator, queryWindow.bumpIntoScreen);
                              // const env = area.getNestedModalEnv(modal)
                              relatedNests.forEach((nest, nestIdx) => {
                                const localWindow = area.add({
                                  onForceClose: () => { },
                                  render: (root, ) => {
                                    root.style.display = 'flex';
                                    root.style.flexDirection = 'column';
                                    root.appendChild(createModalTitle({
                                      renderLeft: (target) => renderProbeModalTitleLeft(null, target, null, () => localWindow.getPos(), null, wrappedLocator, nest.property, {}, 'minimal-nested'),
                                      onClose: null,
                                    }).element);


                                    doEncodeRpcLines(root, nest.expectedOutput, nest.nestedProperties, [...path, nestIdx]);
                                  },
                                })
                              });
                            },
                            onClick: () => {},
                          }
                        }));
                      };

                      doEncodeRpcLines(leftPane,  testCase.expectedOutput, testCase.nestedProperties, []);
                      break;
                    }
                    case 'SMOKE': {
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

                const doEncodeRpcLines = (target: HTMLElement, lines: RpcBodyLine[], nests: NestedTestResponse[], markerPrefix: number[]) => {
                  const rhsInlineWindowManager = createInlineWindowManager();
                  if (!captureStdioSetting) {
                    lines = rpcLinesToAssertionLines(lines);
                  }
                  target.appendChild(encodeRpcBodyLines(env, lines, {
                    lateInteractivityEnabledChecker: () => testCase.src.src.value === env.getLocalState(),
                    excludeStdIoFromPaths: true,
                    capWidths: true,
                    decorator: (line) => {
                      const key = JSON.stringify([...markerPrefix, ...line]);
                      const marker = testStatus.actualMarkers[key];
                      if (marker === 'error') {
                        return 'error';
                      }
                      return 'default';
                    },
                    disableNodeSelectors: true,
                    disableInlineExpansionButton: true,
                    nodeLocatorExpanderHandler: {
                      getReusableExpansionArea: () => null,
                      onCreate: ({ path, locatorRoot, expansionArea, locator, isFresh }) => {
                        // if (isFresh) {
                        //   if (testCase.property.name === searchProbePropertyName) {
                            // TODO force expand meta/query properties here?
                        //   }
                        // }

                        const encodedPath = JSON.stringify(path);
                        const relatedNests = nests.filter(nest => JSON.stringify(nest.path) === encodedPath);
                        if (relatedNests.length === 0) {
                          return;
                        }
                        const wrappedLocator = createImmutableLocator(createMutableLocator(locator));
                        const area = rhsInlineWindowManager.getArea(path, locatorRoot, expansionArea, wrappedLocator, queryWindow.bumpIntoScreen);
                        // const env = area.getNestedModalEnv(modal)
                        relatedNests.forEach((nest, nestIdx) => {
                          const localWindow = area.add({
                            onForceClose: () => { },
                            debugLabel: `testdiff:right:${JSON.stringify(markerPrefix)}:nest:${nestIdx}`,
                            render: (root, ) => {
                              root.style.display = 'flex';
                              root.style.flexDirection = 'column';
                              root.appendChild(createModalTitle({
                                renderLeft: (target) => renderProbeModalTitleLeft(null, target, null, () => localWindow.getPos(), null, wrappedLocator, nest.property, {}, 'minimal-nested'),
                                onClose: null,
                              }).element);

                              if (nest.result !== 'could-not-find-node') {
                                doEncodeRpcLines(root, nest.result.body, nest.result.nested, [...path, nestIdx]);
                              }
                            },
                          })
                        });
                      },
                      onClick: () => {},
                    }
                  }));
                }


                if (evaluationResult.output && evaluationResult.output.result !== 'could-not-find-node') {
                  doEncodeRpcLines(rightPane, evaluationResult.output.result.body, evaluationResult.output.result.nested, []);
                }

                  // rightPane.appendChild(encodeRpcBodyLines(env, testStatus.lines, {
                  //   lateInteractivityEnabledChecker: () => testCase.src.text === env.getLocalState(),
                  //   decorator: (line) => {
                  //     if (typeof testReport === 'object' && typeof testReport.output === 'object') {
                  //       if (testReport.output.invalid.includes(line)) {
                  //         return 'error';
                  //       }
                  //     }
                  //     return 'default';
                  //   },
                  //   disableNodeSelectors: true,
                  // }));
                splitPane.appendChild(rightPane);

                contentRoot.appendChild(splitPane);
                break;
              }
              case 'node': {
                const wrapper = document.createElement('div');
                wrapper.style.padding = '0.25rem';
                contentRoot.appendChild(wrapper);

                const addP = (msg: string) => {
                  const explanationTail = document.createElement('p');
                  explanationTail.textContent = msg;
                  wrapper.appendChild(explanationTail);
                }
                const addTail = () => {
                  addP( `You can inspect the difference by opening a probe from 'Source Code' tab. If you are OK with the differences, click 'Save 💾'.`);
                };

                // if (someLocatorErr && someOutputErr) {
                //   addP(`Both property output and AST node reference(s) involved in property execution differ. This could mean that that both property behavior and AST structure has changed since this test was constructed.`);
                //   addTail();

                // }else if (someLocatorErr) {
                //   wrapper.appendChild(document.createTextNode(`Property output is the same, but the AST node reference(s) involved in property execution are different. This could be because the AST structure has changed since this test was constructed. This might not be a problem.`));
                //   addTail();

                // } else
                if (someOutputErr) {
                  wrapper.appendChild(document.createTextNode('Output differs, but the AST node reference(s) involved in property execution are identical. This indicates that a property behaves differently to when the test was constructed.'));
                  addTail();

                } else {
                  wrapper.appendChild(document.createTextNode('All AST node references involved in property execution are identical to expected values.'));

                }
                break;
              }
              case 'source': {
                const wrapper = document.createElement('div');
                wrapper.style.padding = '0.25rem';
                contentRoot.appendChild(wrapper);

                const same = testCase.src.src.value === env.getLocalState();
                const addText = (msg: string) => wrapper.appendChild(document.createTextNode(msg));
                const addHelp = (type: HelpType) => {
                  const btn = document.createElement('button');
                  btn.innerText = `?`;
                  btn.style.marginLeft = '0.25rem';
                  btn.style.borderRadius = '50%';
                  btn.onclick = () => displayHelp(type);
                  wrapper.appendChild(btn);
                }
                if (same) {
                  // addText(`Current text in CodeProber matches the source code used for this test case.`);
                  addText(`Test code == CodeProber code.`);
                  addHelp('test-code-vs-codeprober-code');

                  wrapper.appendChild(document.createElement('br'));
                  wrapper.appendChild(document.createTextNode(`Press`));
                  const btn = document.createElement('button');
                  preventDragOnClick(btn);
                  btn.style.margin = '0 0.125rem';
                  btn.innerText = `Open Probe`;
                  btn.onclick = () => {
                    const nestedTestToProbeData = (test: NestedTest): WindowStateDataProbe => {
                      const inner: NestedWindows = {};
                      test.nestedProperties.forEach((nest) => {
                        const key = JSON.stringify(nest.path);
                        inner[key] = inner[key] ?? [];
                        inner[key].push({
                          data: nestedTestToProbeData(nest),
                        });
                      });
                      return {
                        type: 'probe',
                        locator: testCase.locator,
                        property: test.property,
                        nested: inner,
                      };
                    }
                    // const windows: NestedWindows = {};

                    // testCase.nestedProperties.forEach(nest => {
                    //   const key = JSON.stringify(nest.path);
                    //   windows[key] = windows[key] ?? [];
                    //   windows[key].push({
                    //     data: nestedTestToProbeData({
                    //       path: [],
                    //       expectedOutput: testCase.expectedOutput,
                    //       nestedProperties: testCase.nestedProperties,
                    //       property: testCase.property,
                    //     }),
                    //     modalPos: { x: 0, y: 0 },
                    //   });
                    //   // windows[]
                    //   // windows[key].push({
                    //   //   modalPos: null,
                    //   //   data: {
                    //   //     type: 'probe',
                    //   //     locator: testCase.locator,
                    //   //     nested:
                    //   //   }
                    //   // })

                    //   })
                    displayProbeModal(env, null, createMutableLocator(locator), property, nestedTestToProbeData({
                      path: [],
                      expectedOutput: testCase.expectedOutput,
                      nestedProperties: testCase.nestedProperties,
                      property: testCase.property,
                    }).nested);
                  };
                  wrapper.appendChild(btn);
                  wrapper.appendChild(document.createTextNode(`to explore the probe that created this test.`));
                } else {
                  addText(`Test code ≠ CodeProber code.`);
                  addHelp('test-code-vs-codeprober-code');
                  // wrapper.appendChild(document.createTextNode(`Current text in CodeProber does not match the source code used for this test case.`));
                  wrapper.appendChild(document.createElement('br'));
                  addText(`Press`);
                  const btn = document.createElement('button');
                  preventDragOnClick(btn);
                  btn.style.margin = '0 0.125rem';
                  btn.innerText = `Load Source`;
                  btn.onclick = () => {
                    env.setLocalState(testCase.src.src.value);
                  };
                  wrapper.appendChild(btn);
                  addText(`to replace CodeProber text with the test source.`);
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
                addExplanationLine('Property:', property.name, 'syntax-attr');
                if (locator.result.external) {
                  addExplanationLine('AST Node:', `${locator.result.label || locator.result.type} in external file`);
                } else {
                  // const span = startEndToSpan(locator.result.start, locator.result.end);
                  // const linesTail = span.lineStart === span.lineEnd
                  //   ? ` $${span.lineStart}`
                  //   : `s ${span.lineStart}→${span.lineEnd}`;
                  // addExplanationLine('AST Node:', `${locator.result.label || locator.result.type} on line${linesTail}`);
                }
                addExplanationLine('Source Code', '⬇️, related line(s) in green.');

                testCase.src.src.value.split('\n').forEach((line, lineIdx) => {


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
                  ['Position Recovery', translateSelectorValToHumanLabel(testCase.src.posRecovery, uiElements.positionRecoverySelector)],
                  ['Cache Strategy', translateSelectorValToHumanLabel(testCase.src.cache, uiElements.astCacheStrategySelector)],
                  ['File suffix', testCase.src.tmpSuffix],
                  ['Main args', testCase.src.mainArgs ? `[${testCase.src.mainArgs.join(', ')}]` : 'none'],
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

          queryWindow.bumpIntoScreen();
        })
        .catch(err => {
          if (refreshOnDone) {
            refreshOnDone = false;
            queryWindow.refresh();
            return;
          }
          if (cancelToken.cancelled) { return; }
          console.log('TestDiffModal RPC catch', err);
          root.innerHTML = '';
          root.innerText = 'Failed refreshing test diff..';
          console.warn('Failed refreshing test w/ args:', JSON.stringify({ locator, property }, null, 2));
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
          property,
          nested: {},
        },
      });
    }
  }
}

export default displayTestDiffModal;
