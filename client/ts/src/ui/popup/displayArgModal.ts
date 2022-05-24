import createModalTitle from "../create/createModalTitle";
import createTextSpanIndicator from "../create/createTextSpanIndicator";
import registerNodeSelector from "../create/registerNodeSelector";
import registerOnHover from "../create/registerOnHover";
import showModal from "../create/showWindow";
import displayAttributeModal from "./displayAttributeModal";
import displayProbeModal from "./displayProbeModal";
import formatAttr, { formatAttrType } from "./formatAttr";

const cancelLocatorRequest = () => {
  if (!window.ActiveLocatorRequest) {
    return;
  }
  document.body.classList.remove('locator-request-active');
  delete window.ActiveLocatorRequest;
};

const startLocatorRequest = (onSelected: (locator: NodeLocator) => void) => {
  document.body.classList.add('locator-request-active');
  const callback: ActiveNodeLocatorRequest = {
    submit: (loc: NodeLocator) => {
      cancelLocatorRequest();
      onSelected(loc);
    },
  };
  window.ActiveLocatorRequest = callback;
  return callback;
}

const displayArgModal = (env: ModalEnv, modalPos: ModalPosition, locator: NodeLocator, attr: AstAttrWithValue) => {
  let lastLocatorRequest: ActiveNodeLocatorRequest | null = null;
  const cleanup = () => {
    if (window.ActiveLocatorRequest === lastLocatorRequest) {
      cancelLocatorRequest();
    }
  };
  const args = attr.args;
  if (!args || !args.length) {
    throw new Error('Created arg modal for attribute without arguments - create probe modal instead');
  }

  const createTitle = () => {
    return createModalTitle({
      extraActions: [
        {
          title: 'Duplicate window',
          invoke: () => {
            const pos = popup.getPos();
            displayArgModal(env, { x: pos.x + 10, y: pos.y + 10 }, locator, attr);
          },
        }
      ],
      renderLeft: (container) => {
        const headType = document.createElement('span');
        headType.classList.add('syntax-type');
        headType.innerText = `${locator.result.type}`;

        const headAttr = document.createElement('span');
        headAttr.classList.add('syntax-attr');
        headAttr.innerText = `.${formatAttr(attr)}`;

        container.appendChild(headType);
        container.appendChild(headAttr);
      },
      onClose: () => {
        popup.remove();
        cleanup();
      },
    });
  };
  const popup = showModal({
    pos: modalPos,
    rootStyle: `
      min-width: 16rem;
      min-height: 4rem;
    `,
    render: (root) => {
      root.appendChild(createTitle().element);

      console.log('Show arg modal : ', JSON.stringify(attr, null, 2));
      const attrList = document.createElement('div');
      attrList.classList.add('attr-arg-list');

      const argValues: (null | string | number | boolean | NodeLocator)[] = [];

      const proceed = () => {
        popup.remove();
        displayProbeModal(env, popup.getPos(), locator, {
          name: attr.name,
          args: attr.args?.map((arg, argIdx) => ({
            ...arg,
            value: argValues[argIdx],
          })),
        });
      }

      args.forEach((arg, argIdx) => {
        // const inpId = generateInputId();

        // const argRow = document.createElement('div');
        // argRow.style.display = 'flex';

        const argHeader = document.createElement('div');
        argHeader.style.marginLeft = 'auto';
        argHeader.style.marginRight = '0.25rem';

        const argType = document.createElement('span');
        argType.classList.add('syntax-type');
        argType.innerText = formatAttrType(arg.type);
        argHeader.appendChild(argType);

        // argHeader.appendChild(document.createTextNode(` ${arg.name}`));
        attrList.appendChild(argHeader);

        const setupTextInput = (init: (elem: HTMLInputElement) => void, cleanupValue: (val: string) => string) => {
          const inp = document.createElement('input');
          inp.classList.add('attr-arg-input-text');
          init(inp);
          inp.placeholder = arg.name;
          inp.oninput = () => {
            argValues[argIdx] = cleanupValue(inp.value);
          };
          inp.onkeydown = (e) => {
            if (e.key === 'Enter') {
              proceed();
            }
          };
          if (argIdx === 0) {
            setTimeout(() => inp.focus(), 100);
          }
          return inp;
        };
        const setupTwoPillInput = (
          init: (parent: HTMLElement, left: HTMLElement, right: HTMLElement) => void,
          getActive: () => ('left' | 'right'),
          onClick: (node: ('left' | 'right'), refreshActiveIndicator: () => void) => void,
        ) => {
          const inp = document.createElement('div');
          inp.style.display = 'grid';
          inp.style.justifyItems = 'center';
          inp.style.gridGap = '4px';
          inp.style.gridTemplateColumns = 'auto auto';

          const left = document.createElement('span');
          const right = document.createElement('span');

          const updateActiveElem = () => {
            const set = (target: HTMLElement, attr: string, on: boolean) => {
              const isOn = target.classList.contains(attr);
              console.log('updateActiveELem', isOn, on);
              if (isOn === on) {
                return;
              }
              if (on) {
                target.classList.add(attr);
              } else {
                target.classList.remove(attr);
              }
            }
            const setActive = (target: HTMLElement, active: boolean) => {
              set(target, 'attr-input-twopill-selected', active);
              set(target, 'attr-input-twopill-unselected', !active);
            }
            const active = getActive();
            setActive(left, active === 'left');
            setActive(right, active === 'right');
          };
          [left, right].forEach((btn) => {
            btn.classList.add('clickHighlightOnHover');
            btn.classList.add('attr-input-twopill');
            // btn.style.border = '1px solid #888';
            btn.style.width = '100%';
            btn.style.height = '100%';
            btn.style.textAlign = 'center';
            // btn.classList.add('syntax-modifier');
            btn.onclick = () => {
              onClick((btn === left) ? 'left' : 'right', updateActiveElem);
            }
          })

          updateActiveElem();

          inp.appendChild(left);
          inp.appendChild(right);
          init(inp, left, right);
          return inp;
        };
        // inp.style.marginLeft = '1rem';
        switch (arg.type) {
          case "int": {
            argValues[argIdx] = arg.value || '0';
            attrList.appendChild(setupTextInput(
              (elem) => {
                elem.type = 'number';
                elem.value = `${parseInt(`${argValues[argIdx]}`, 10) || ''}`;
              },
              (val) => `${parseInt(val, 10) || 0}`
            ));
            break;
          }
          case "boolean": {
            argValues[argIdx] = `${arg.value === 'true'}`;
            attrList.appendChild(setupTwoPillInput(
              (parent, left, right) => {
                left.innerText = 'true';
                right.innerText = 'false';
              },
              () => argValues[argIdx] === 'true' ? 'left' : 'right',
              (node, updateActive) => {
                argValues[argIdx] = `${node === 'left'}`;
                updateActive();
              }
            ))
            break;
          }
          default:
            if (arg.isNodeType) {
              argValues[argIdx] = arg.value || null;

              let pickedNodePanel = document.createElement('div');

              let pickedNodeHighlighter: (on: boolean) => void = () => {};
              registerOnHover(pickedNodePanel, (on) => pickedNodeHighlighter(on));
              let state: 'null' | 'node' = (argValues[argIdx] && typeof argValues[argIdx] === 'object') ? 'node' : 'null';
              const refreshPickedNode = () => {
                while (pickedNodePanel.firstChild) {
                  pickedNodePanel.firstChild.remove();
                }
                pickedNodePanel.style.fontStyle = 'unset';
                pickedNodePanel.classList.remove('clickHighlightOnHover');
                pickedNodeHighlighter = () => {};
                // const state = argValues[argIdx];
                if (state === 'null') {
                  pickedNodePanel.style.display = 'hidden';
                  pickedNodePanel.style.height= '0px';
                  return;
                }
                pickedNodePanel.style.height= '';
                const pickedNode = argValues[argIdx];
                if (!pickedNode || typeof pickedNode !== 'object') {
                  pickedNodePanel.style.display = 'block';
                  pickedNodePanel.style.fontStyle = 'italic';
                  pickedNodePanel.innerText = 'No node picked yet..';
                  return;
                }
                // if (typeof state !== 'object') {
                //   console.warn('unknown state', state);
                //   pickedNodePanel.style.display = 'none';
                //   return;
                // }

                const nodeWrapper = document.createElement('div');
                registerNodeSelector(nodeWrapper, () => pickedNode);
                nodeWrapper.addEventListener('click', () => {
                  displayAttributeModal(env, null, pickedNode);

                })
                const span = startEndToSpan(pickedNode.result.start, pickedNode.result.end);
                nodeWrapper.appendChild(createTextSpanIndicator({
                  span,
                }));
                const typeNode = document.createElement('span');
                typeNode.classList.add('syntax-type');
                typeNode.innerText = pickedNode.result.type;
                nodeWrapper.appendChild(typeNode);

                pickedNodePanel.appendChild(nodeWrapper);
                pickedNodePanel.classList.add('clickHighlightOnHover');
                pickedNodeHighlighter = (on) => env.updateSpanHighlight(on ? span : null);
              };
              refreshPickedNode();
              attrList.appendChild(setupTwoPillInput(
                (parent, left, right) => {
                  left.innerText = 'null';
                  // right.innerText = '';
                  // right.classList.add('locator-symbol');
                  right.style.display = 'flex';
                  right.style.flexDirection= 'row';
                  right.style.justifyContent= 'space-around';

                  const lbl = document.createElement('span');
                  lbl.innerText = 'Select node';
                  lbl.style.margin = 'auto';
                  right.appendChild(lbl);

                  const icon = document.createElement('img');
                  icon.src = '/icons/my_location_white_24dp.svg';
                  icon.style.height = '18px';
                  icon.style.alignSelf = 'center';
                  icon.style.margin = '0 4px 0 0';
                  right.appendChild(icon);
                },
                () => state === 'null' ? 'left' : 'right',
                (node, updateActive) => {
                  if (node === 'left') {
                    state = 'null';
                    argValues[argIdx] = null;
                    cancelLocatorRequest();
                  } else {
                    state = 'node';
                    lastLocatorRequest = startLocatorRequest(locator => {
                      argValues[argIdx] = locator;
                      refreshPickedNode();
                      updateActive();
                    });
                  }
                  refreshPickedNode();
                  updateActive();
                }
              ));
              attrList.appendChild(document.createElement('span')); // <-- for grid alignment
              attrList.appendChild(pickedNodePanel);
              break;
            }
            console.warn('Unknown arg type', arg.type, ', defaulting to string input');
            // Fall through
          case 'java.lang.String': {
            argValues[argIdx] = arg.value || '';
            attrList.appendChild(setupTextInput((elem) => {
              elem.type = 'text';
              elem.value = `${argValues[argIdx]}`;
            }, id => id));
            break;
          }
        }

        // attrList.appendChild(argRow);
      });

      root.appendChild(attrList);

      const submitWrapper = document.createElement('div');
      submitWrapper.style.marginTop = '0.5rem';

      const submit = document.createElement('input');
      submit.type = 'submit';
      submit.classList.add('attr-list-submit');
      submit.style.display = 'block';
      // submit.style.margin = 'auto';
      submit.style.margin = '0.5rem';
      submit.value = 'OK';
      submit.onmousedown = (e) => {
        e.stopPropagation();
      }

      submit.onclick = (e) => {
        e.preventDefault();
        e.stopPropagation();
        proceed();
      }
      submitWrapper.appendChild(submit);

      root.appendChild(submitWrapper);
    },
  });
}

export default displayArgModal;
