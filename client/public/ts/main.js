"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
define("createWebsocketHandler", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    let rpcIdGenerator = 1;
    const createWebsocketHandler = (socket, onClose) => {
        const pendingCallbacks = {};
        const messageHandlers = {
            rpc: ({ id, ...res }) => {
                const handler = pendingCallbacks[id];
                if (handler) {
                    delete pendingCallbacks[id];
                    handler(res);
                }
                else {
                    console.warn('Received RPC response for', id, ', expected one of', Object.keys(pendingCallbacks));
                }
            },
        };
        let didReceiveAtLeastOneMessage = false;
        socket.addEventListener('message', function (event) {
            didReceiveAtLeastOneMessage = true;
            // console.log('Message from server ', event.data);
            const parsed = JSON.parse(event.data);
            if (messageHandlers[parsed.type]) {
                messageHandlers[parsed.type](parsed);
            }
            else {
                console.log('No handler for message', parsed, ', got handlers for', Object.keys(messageHandlers));
            }
        });
        socket.addEventListener('close', () => {
            // Small timeout to reduce risk of it appearing when navigating away
            setTimeout(() => onClose(didReceiveAtLeastOneMessage), 100);
        });
        return {
            on: (id, cb) => messageHandlers[id] = cb,
            sendRpc: (msg) => new Promise(async (res, rej) => {
                const id = rpcIdGenerator++;
                socket.send(JSON.stringify({
                    ...msg,
                    id,
                }));
                const cleanup = () => delete pendingCallbacks[id];
                pendingCallbacks[id] = ({ error, result }) => {
                    cleanup();
                    if (error) {
                        console.warn('RPC request failed', error);
                        rej(error);
                    }
                    else {
                        res(result);
                    }
                };
                setTimeout(() => {
                    cleanup();
                    rej('Timeout');
                }, 30000);
            }),
        };
    };
    exports.default = createWebsocketHandler;
});
define("ui/addConnectionCloseNotice", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const showConnectionCloseNotice = (didReceiveAtLeastOneMessage) => {
        const ch = document.createElement('div');
        const msg = didReceiveAtLeastOneMessage
            ? 'Lost connection to server, reload to reconnect'
            : 'Couldn\'t connect to server';
        ch.innerHTML = `
  <div style="
    position: absolute;
    top: 4rem;
    left: 20%;
    right: 20%;
    background: #300;
    color: white;
    border: 1px solid red;
    border-radius: 1rem;
    padding: 2rem;
    font-size: 2rem;
    z-index: ${Number.MAX_SAFE_INTEGER};
    text-align: center">
   ${msg}
  </div>
  `;
        document.body.appendChild(ch);
    };
    exports.default = showConnectionCloseNotice;
});
define("ui/create/createLoadingSpinner", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const createLoadingSpinner = () => {
        const holder = document.createElement('div');
        holder.classList.add('lds-spinner');
        holder.innerHTML = `<div></div><div></div><div></div><div></div><div></div><div></div><div></div><div></div><div></div><div></div><div></div><div></div>`;
        return holder;
    };
    exports.default = createLoadingSpinner;
});
define("ui/create/attachDragToX", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.lastKnownMousePos = exports.modalZIndexGenerator = void 0;
    let lastKnownMousePos = { x: 0, y: 0 };
    exports.lastKnownMousePos = lastKnownMousePos;
    window.onmousemove = (e) => {
        lastKnownMousePos.x = e.x;
        lastKnownMousePos.y = e.y;
    };
    let currentMouseDownTracker = null;
    window.onmouseup = () => {
        if (currentMouseDownTracker) {
            currentMouseDownTracker.down = false;
            currentMouseDownTracker = null;
        }
    };
    const setCurrentMouseDown = (newTracker) => {
        if (currentMouseDownTracker) {
            currentMouseDownTracker.down = false;
        }
        currentMouseDownTracker = newTracker;
    };
    const modalZIndexGenerator = (() => {
        let counter = 1;
        return () => counter++;
    })();
    exports.modalZIndexGenerator = modalZIndexGenerator;
    const attachDragToX = (element, onBegin, onUpdate, onFinishedMove) => {
        const refreshPos = (newX, newY) => onUpdate(newX !== null && newX !== void 0 ? newX : 0, newY !== null && newY !== void 0 ? newY : 0);
        let mouse = { down: false, x: 0, y: 0 };
        element.onmousedown = (e) => {
            setCurrentMouseDown(mouse);
            // e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();
            e.cancelBubble = true;
            mouse.down = true;
            element.style.zIndex = `${modalZIndexGenerator()}`;
            mouse.x = e.pageX;
            mouse.y = e.pageY;
            onBegin();
        };
        const onMouseMove = (e) => {
            if (mouse.down) {
                let dx = e.pageX - mouse.x;
                let dy = e.pageY - mouse.y;
                refreshPos(dx, dy);
                // mouse.x = e.x;
                // mouse.y = e.y;
            }
        };
        document.addEventListener('mousemove', onMouseMove);
        element.onmouseup = (e) => {
            mouse.down = false;
            onFinishedMove === null || onFinishedMove === void 0 ? void 0 : onFinishedMove();
        };
        // refreshPos();
        return {
            cleanup: () => {
                document.removeEventListener('mousemove', onMouseMove);
            }
        };
    };
    exports.default = attachDragToX;
});
define("ui/create/attachDragToMove", ["require", "exports", "ui/create/attachDragToX"], function (require, exports, attachDragToX_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    attachDragToX_1 = __importStar(attachDragToX_1);
    const attachDragToMove = (element, initialPos, onFinishedMove) => {
        var _a, _b;
        const elemPos = { x: (_a = initialPos === null || initialPos === void 0 ? void 0 : initialPos.x) !== null && _a !== void 0 ? _a : attachDragToX_1.lastKnownMousePos.x, y: (_b = initialPos === null || initialPos === void 0 ? void 0 : initialPos.y) !== null && _b !== void 0 ? _b : attachDragToX_1.lastKnownMousePos.y };
        const startPos = { ...elemPos };
        const onBegin = () => {
            startPos.x = elemPos.x;
            startPos.y = elemPos.y;
        };
        const onUpdate = (dx, dy) => {
            let newX = startPos.x + dx;
            let newY = startPos.y + dy;
            if (newY < 0) {
                newY = 0;
            }
            else {
                newY = Math.min(document.body.clientHeight - element.clientHeight, newY);
            }
            if (newX < 0) {
                newX = 0;
            }
            else {
                newX = Math.min(document.body.clientWidth - element.clientWidth, newX);
            }
            element.style.top = `${newY}px`;
            element.style.left = `${newX}px`;
            elemPos.x = newX;
            elemPos.y = newY;
        };
        onUpdate(0, 0);
        const cleanup = (0, attachDragToX_1.default)(element, onBegin, onUpdate, onFinishedMove).cleanup;
        return {
            cleanup,
            getPos: () => elemPos
        };
    };
    exports.default = attachDragToMove;
});
define("ui/create/showWindow", ["require", "exports", "ui/create/attachDragToMove", "ui/create/attachDragToX"], function (require, exports, attachDragToMove_1, attachDragToX_2) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    attachDragToMove_1 = __importDefault(attachDragToMove_1);
    attachDragToX_2 = __importStar(attachDragToX_2);
    const showWindow = (args) => {
        const { render, pos: initialPos, rootStyle, resizable } = args;
        const root = document.createElement('div');
        root.tabIndex = 0;
        root.classList.add('modalWindow');
        root.style = `${rootStyle || ''}`;
        root.style.zIndex = `${(0, attachDragToX_2.modalZIndexGenerator)()}`;
        root.style.maxWidth = '40vw';
        root.onkeydown = (e) => {
            var _a;
            if (e.key === 'Escape') {
                const btn = root.querySelector('.modalCloseButton');
                if (btn) {
                    e.stopPropagation();
                    (_a = btn.onclick) === null || _a === void 0 ? void 0 : _a.call(btn, null);
                }
            }
        };
        let lastCancelToken = {};
        const contentRoot = document.createElement('div');
        // contentRoot.classList.add('HELLO-FIND-ME');
        contentRoot.style.overflow = 'auto';
        contentRoot.style.position = 'relative';
        contentRoot.style.top = '0px';
        contentRoot.style.left = '0px';
        // contentRoot.style.display = 'contents';
        // contentRoot.style.minHeight = '4rem';
        // contentRoot.style.overflow = 'inherit';
        // contentRoot.style.display = 'contents';
        render(contentRoot, lastCancelToken);
        root.appendChild(contentRoot);
        let reiszeCleanup = null;
        if (resizable) {
            const resizePositioner = document.createElement('div');
            // resizePositioner.style.position = 'absolute';
            resizePositioner.style.right = '0px';
            resizePositioner.style.bottom = '0px';
            resizePositioner.style.cursor = 'nwse-resize';
            resizePositioner.style.width = 'fit-contents';
            resizePositioner.style.height = '0px';
            resizePositioner.style.position = 'sticky';
            const resizeButton = document.createElement('div');
            resizeButton.style.position = 'absolute';
            resizeButton.style.right = '0px';
            resizeButton.style.bottom = '0px';
            resizeButton.style.height = '0.25rem';
            resizeButton.style.width = '0.25rem';
            resizeButton.style.borderRight = '4px solid gray';
            resizeButton.style.borderBottom = '4px solid gray';
            resizePositioner.appendChild(resizeButton);
            root.appendChild(resizePositioner);
            const size = { w: 1, h: 1 };
            reiszeCleanup = (0, attachDragToX_2.default)(resizePositioner, () => {
                size.w = root.clientWidth;
                size.h = root.clientHeight;
            }, (dx, dy) => {
                const newW = Math.max(32, size.w + dx);
                const newH = Math.max(32, size.h + dy);
                // console.log('setting ', `${root.clientWidth + dx}px`);
                root.style.width = `${newW}px`;
                root.style.height = `${newH}px`;
                root.style.maxWidth = 'fit-content';
                root.style.maxHeight = 'fit-content';
            }).cleanup;
        }
        document.body.appendChild(root);
        const dragToMove = (0, attachDragToMove_1.default)(root, initialPos, args.onFinishedMove);
        return {
            remove: () => {
                root.remove();
                dragToMove.cleanup();
                reiszeCleanup === null || reiszeCleanup === void 0 ? void 0 : reiszeCleanup();
            },
            refresh: () => {
                lastCancelToken.cancelled = true;
                lastCancelToken = {};
                // root.innerHTML = '';
                render(contentRoot, lastCancelToken);
            },
            getPos: dragToMove.getPos,
        };
    };
    exports.default = showWindow;
});
define("ui/create/createModalTitle", ["require", "exports", "ui/create/showWindow"], function (require, exports, showWindow_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    showWindow_1 = __importDefault(showWindow_1);
    const createModalTitle = (args) => {
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
            overflowButton.onmousedown = (e) => { e.stopPropagation(); };
            overflowButton.onclick = () => {
                const cleanup = () => {
                    contextMenu.remove();
                    window.removeEventListener('mousedown', cleanup);
                };
                const contextMenu = (0, showWindow_1.default)({
                    rootStyle: `
          // width: 16rem;
        `,
                    render: (container) => {
                        container.addEventListener('mousedown', (e) => {
                            e.stopPropagation();
                            e.stopImmediatePropagation();
                        });
                        const hidden = document.createElement('button');
                        hidden.classList.add('modalCloseButton');
                        hidden.style.display = 'none';
                        hidden.onclick = cleanup;
                        container.appendChild(hidden);
                        extraActions.forEach((action) => {
                            const row = document.createElement('div');
                            row.classList.add('context-menu-row');
                            row.onclick = () => {
                                cleanup();
                                action.invoke();
                            };
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
                });
                window.addEventListener('mousedown', () => {
                    cleanup();
                });
            };
            buttons.appendChild(overflowButton);
        }
        const closeButton = document.createElement('div');
        closeButton.classList.add('modalCloseButton');
        closeButton.innerText = '𝖷';
        closeButton.classList.add('clickHighlightOnHover');
        closeButton.onmousedown = (e) => { e.stopPropagation(); };
        closeButton.onclick = () => onClose();
        buttons.appendChild(closeButton);
        titleRowHolder.appendChild(buttons);
        return {
            element: titleRowHolder
        };
    };
    exports.default = createModalTitle;
});
define("ui/create/registerOnHover", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const registerOnHover = (element, onHover) => {
        element.onmouseenter = () => onHover(true);
        element.onmouseleave = () => onHover(false);
    };
    exports.default = registerOnHover;
});
define("ui/create/createTextSpanIndicator", ["require", "exports", "ui/create/registerOnHover"], function (require, exports, registerOnHover_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    registerOnHover_1 = __importDefault(registerOnHover_1);
    const createTextSpanIndicator = (args) => {
        const { span, marginLeft, onHover } = args;
        const indicator = document.createElement('span');
        indicator.style.fontSize = '0.75rem';
        indicator.style.color = 'gray';
        if (marginLeft) {
            indicator.style.marginLeft = '0.25rem';
        }
        indicator.style.marginRight = '0.25rem';
        indicator.innerText = `[${span.lineStart}:${span.colStart}→${span.lineEnd}:${span.colEnd}]${span.lineStart === 0 && span.colStart === 0 && span.lineEnd === 0 && span.colEnd === 0 ? '⚠️' : ''}`;
        if (onHover) {
            indicator.classList.add('highlightOnHover');
            (0, registerOnHover_1.default)(indicator, onHover);
        }
        return indicator;
    };
    exports.default = createTextSpanIndicator;
});
define("ui/create/registerNodeSelector", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const registerNodeSelector = (elem, getLocator) => {
        elem.classList.add('nodeLocatorContainer');
        elem.addEventListener('click', (e) => {
            if (!window.ActiveLocatorRequest) {
                return;
            }
            e.stopImmediatePropagation();
            e.stopPropagation();
            e.preventDefault();
            window.ActiveLocatorRequest.submit(getLocator());
        });
    };
    exports.default = registerNodeSelector;
});
define("ui/popup/formatAttr", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.formatAttrType = void 0;
    const formatAttrType = (orig) => {
        switch (orig) {
            case 'java.lang.String': return 'String';
            default: return orig;
        }
    };
    exports.formatAttrType = formatAttrType;
    const formatAttr = (attr) => `${attr.name}${(attr.args
        ? `(${attr.args.map(a => formatAttrType(a.type)).join(', ')})`
        : '')}`;
    exports.default = formatAttr;
});
define("ui/popup/displayArgModal", ["require", "exports", "ui/create/createModalTitle", "ui/create/createTextSpanIndicator", "ui/create/registerNodeSelector", "ui/create/registerOnHover", "ui/create/showWindow", "ui/popup/displayAttributeModal", "ui/popup/displayProbeModal", "ui/popup/formatAttr"], function (require, exports, createModalTitle_1, createTextSpanIndicator_1, registerNodeSelector_1, registerOnHover_2, showWindow_2, displayAttributeModal_1, displayProbeModal_1, formatAttr_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createModalTitle_1 = __importDefault(createModalTitle_1);
    createTextSpanIndicator_1 = __importDefault(createTextSpanIndicator_1);
    registerNodeSelector_1 = __importDefault(registerNodeSelector_1);
    registerOnHover_2 = __importDefault(registerOnHover_2);
    showWindow_2 = __importDefault(showWindow_2);
    displayAttributeModal_1 = __importDefault(displayAttributeModal_1);
    displayProbeModal_1 = __importDefault(displayProbeModal_1);
    formatAttr_1 = __importStar(formatAttr_1);
    const cancelLocatorRequest = () => {
        if (!window.ActiveLocatorRequest) {
            return;
        }
        document.body.classList.remove('locator-request-active');
        delete window.ActiveLocatorRequest;
    };
    const startLocatorRequest = (onSelected) => {
        document.body.classList.add('locator-request-active');
        const callback = {
            submit: (loc) => {
                cancelLocatorRequest();
                onSelected(loc);
            },
        };
        window.ActiveLocatorRequest = callback;
        return callback;
    };
    const displayArgModal = (env, modalPos, locator, attr) => {
        let lastLocatorRequest = null;
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
            return (0, createModalTitle_1.default)({
                extraActions: [
                    {
                        title: 'Duplicate window',
                        invoke: () => {
                            const pos = popup.getPos();
                            displayArgModal(env, { x: pos.x + 10, y: pos.y + 10 }, locator, attr);
                        },
                    }
                ],
                renderLeft: (container) => {
                    const headType = document.createElement('span');
                    headType.classList.add('syntax-type');
                    headType.innerText = `${locator.result.type}`;
                    const headAttr = document.createElement('span');
                    headAttr.classList.add('syntax-attr');
                    headAttr.innerText = `.${(0, formatAttr_1.default)(attr)}`;
                    container.appendChild(headType);
                    container.appendChild(headAttr);
                },
                onClose: () => {
                    popup.remove();
                    cleanup();
                },
            });
        };
        const popup = (0, showWindow_2.default)({
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
                const argValues = [];
                const proceed = () => {
                    var _a;
                    popup.remove();
                    (0, displayProbeModal_1.default)(env, popup.getPos(), locator, {
                        name: attr.name,
                        args: (_a = attr.args) === null || _a === void 0 ? void 0 : _a.map((arg, argIdx) => ({
                            ...arg,
                            value: argValues[argIdx],
                        })),
                    });
                };
                args.forEach((arg, argIdx) => {
                    // const inpId = generateInputId();
                    // const argRow = document.createElement('div');
                    // argRow.style.display = 'flex';
                    const argHeader = document.createElement('div');
                    argHeader.style.marginLeft = 'auto';
                    argHeader.style.marginRight = '0.25rem';
                    const argType = document.createElement('span');
                    argType.classList.add('syntax-type');
                    argType.innerText = (0, formatAttr_1.formatAttrType)(arg.type);
                    argHeader.appendChild(argType);
                    // argHeader.appendChild(document.createTextNode(` ${arg.name}`));
                    attrList.appendChild(argHeader);
                    const setupTextInput = (init, cleanupValue) => {
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
                    const setupTwoPillInput = (init, getActive, onClick) => {
                        const inp = document.createElement('div');
                        inp.style.display = 'grid';
                        inp.style.justifyItems = 'center';
                        inp.style.gridGap = '4px';
                        inp.style.gridTemplateColumns = 'auto auto';
                        const left = document.createElement('span');
                        const right = document.createElement('span');
                        const updateActiveElem = () => {
                            const set = (target, attr, on) => {
                                const isOn = target.classList.contains(attr);
                                console.log('updateActiveELem', isOn, on);
                                if (isOn === on) {
                                    return;
                                }
                                if (on) {
                                    target.classList.add(attr);
                                }
                                else {
                                    target.classList.remove(attr);
                                }
                            };
                            const setActive = (target, active) => {
                                set(target, 'attr-input-twopill-selected', active);
                                set(target, 'attr-input-twopill-unselected', !active);
                            };
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
                            };
                        });
                        updateActiveElem();
                        inp.appendChild(left);
                        inp.appendChild(right);
                        init(inp, left, right);
                        return inp;
                    };
                    // inp.style.marginLeft = '1rem';
                    switch (arg.type) {
                        case "int": {
                            argValues[argIdx] = arg.value || '0';
                            attrList.appendChild(setupTextInput((elem) => {
                                elem.type = 'number';
                                elem.value = `${parseInt(`${argValues[argIdx]}`, 10) || ''}`;
                            }, (val) => `${parseInt(val, 10) || 0}`));
                            break;
                        }
                        case "boolean": {
                            argValues[argIdx] = `${arg.value === 'true'}`;
                            attrList.appendChild(setupTwoPillInput((parent, left, right) => {
                                left.innerText = 'true';
                                right.innerText = 'false';
                            }, () => argValues[argIdx] === 'true' ? 'left' : 'right', (node, updateActive) => {
                                argValues[argIdx] = `${node === 'left'}`;
                                updateActive();
                            }));
                            break;
                        }
                        default:
                            if (arg.isNodeType) {
                                argValues[argIdx] = arg.value || null;
                                let pickedNodePanel = document.createElement('div');
                                let pickedNodeHighlighter = () => { };
                                (0, registerOnHover_2.default)(pickedNodePanel, (on) => pickedNodeHighlighter(on));
                                let state = (argValues[argIdx] && typeof argValues[argIdx] === 'object') ? 'node' : 'null';
                                const refreshPickedNode = () => {
                                    while (pickedNodePanel.firstChild) {
                                        pickedNodePanel.firstChild.remove();
                                    }
                                    pickedNodePanel.style.fontStyle = 'unset';
                                    pickedNodePanel.classList.remove('clickHighlightOnHover');
                                    pickedNodeHighlighter = () => { };
                                    // const state = argValues[argIdx];
                                    if (state === 'null') {
                                        pickedNodePanel.style.display = 'hidden';
                                        pickedNodePanel.style.height = '0px';
                                        return;
                                    }
                                    pickedNodePanel.style.height = '';
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
                                    (0, registerNodeSelector_1.default)(nodeWrapper, () => pickedNode);
                                    nodeWrapper.addEventListener('click', () => {
                                        (0, displayAttributeModal_1.default)(env, null, pickedNode);
                                    });
                                    const span = startEndToSpan(pickedNode.result.start, pickedNode.result.end);
                                    nodeWrapper.appendChild((0, createTextSpanIndicator_1.default)({
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
                                attrList.appendChild(setupTwoPillInput((parent, left, right) => {
                                    left.innerText = 'null';
                                    // right.innerText = '';
                                    // right.classList.add('locator-symbol');
                                    right.style.display = 'flex';
                                    right.style.flexDirection = 'row';
                                    right.style.justifyContent = 'space-around';
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
                                }, () => state === 'null' ? 'left' : 'right', (node, updateActive) => {
                                    if (node === 'left') {
                                        state = 'null';
                                        argValues[argIdx] = null;
                                        cancelLocatorRequest();
                                    }
                                    else {
                                        state = 'node';
                                        lastLocatorRequest = startLocatorRequest(locator => {
                                            argValues[argIdx] = locator;
                                            refreshPickedNode();
                                            updateActive();
                                        });
                                    }
                                    refreshPickedNode();
                                    updateActive();
                                }));
                                attrList.appendChild(document.createElement('span')); // <-- for grid alignment
                                attrList.appendChild(pickedNodePanel);
                                break;
                            }
                            console.warn('Unknown arg type', arg.type, ', defaulting to string input');
                        // Fall through
                        case 'java.lang.String': {
                            argValues[argIdx] = arg.value || '';
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
                };
                submit.onclick = (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    proceed();
                };
                submitWrapper.appendChild(submit);
                root.appendChild(submitWrapper);
            },
        });
    };
    exports.default = displayArgModal;
});
define("model/repositoryUrl", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    // Also update versionCheckerFragment.html if the repository changes
    const repositoryUrl = `https://git.cs.lth.se/an6308ri/code-prober`;
    exports.default = repositoryUrl;
});
define("ui/popup/displayHelp", ["require", "exports", "model/repositoryUrl", "ui/create/createModalTitle", "ui/create/showWindow"], function (require, exports, repositoryUrl_1, createModalTitle_2, showWindow_3) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    repositoryUrl_1 = __importDefault(repositoryUrl_1);
    createModalTitle_2 = __importDefault(createModalTitle_2);
    showWindow_3 = __importDefault(showWindow_3);
    const createSyntaxNode = (type, text, margins) => {
        const retNode = document.createElement('span');
        if (type) {
            retNode.classList.add(type);
        }
        if (margins === null || margins === void 0 ? void 0 : margins.includes('left'))
            retNode.style.marginLeft = '0.5rem';
        if (margins === null || margins === void 0 ? void 0 : margins.includes('right'))
            retNode.style.marginRight = '0.5rem';
        retNode.innerText = text;
        return retNode;
    };
    const getHelpTitle = (type) => ({
        'general': 'How to use CodeProber 🔎',
        'recovery-strategy': 'Position recovery',
        'probe-window': 'Probe help',
        'magic-stdout-messages': 'Magic stdout messages',
        'ast-cache-strategy': 'AST caching',
        'syntax-highlighting': 'Syntax Highlighting',
        'main-args-override': 'Main args override',
        'customize-file-suffix': 'Temp file suffix',
        'property-list-usage': 'Property list help',
        'show-all-properties': 'Show all properties',
    })[type];
    const getHelpContents = (type) => {
        const createHeader = (text) => {
            const header = document.createElement('span');
            header.classList.add('syntax-attr');
            header.innerText = text;
            return header;
        };
        const joinElements = (...parts) => {
            const wrapper = document.createElement('div');
            parts.forEach(p => {
                if (typeof p === 'string') {
                    wrapper.appendChild(document.createTextNode(p));
                }
                else {
                    wrapper.appendChild(p);
                }
                return p;
            });
            return wrapper;
        };
        switch (type) {
            case 'general': {
                const exampleVisible = document.createElement('div');
                {
                    const add = (...args) => exampleVisible.appendChild(createSyntaxNode(args[0], args[1], args[2]));
                    exampleVisible.appendChild(document.createTextNode('Example (JastAdd syntax): '));
                    add('syntax-modifier', 'syn', 'right');
                    add('syntax-type', 'boolean List');
                    add('syntax-attr', '.cpr_nodeListVisible', '');
                    add('', '() =', 'right');
                    add('syntax-modifier', 'false', 'false');
                    add('', ';', '');
                }
                const exampleAttrs = document.createElement('div');
                {
                    const add = (...args) => exampleAttrs.appendChild(createSyntaxNode(args[0], args[1], args[2]));
                    exampleAttrs.appendChild(document.createTextNode('Example (JastAdd syntax): '));
                    add('syntax-modifier', 'syn', 'right');
                    add('syntax-type', 'java.util.List<String> Function');
                    add('syntax-attr', '.cpr_propertyListShow', '');
                    add('', '() =', 'right');
                    add('syntax-type', 'Arrays.asList(', '');
                    add('syntax-string', '"eval"', '');
                    add('', ', ', '');
                    add('syntax-string', '"reference"', '');
                    add('', ');', '');
                }
                const exampleView = document.createElement('div');
                {
                    const add = (...args) => exampleView.appendChild(createSyntaxNode(args[0], args[1], args[2]));
                    exampleView.appendChild(document.createTextNode('Example (JastAdd syntax): '));
                    add('syntax-modifier', 'syn', 'right');
                    add('syntax-type', 'Object IntType');
                    add('syntax-attr', '.cpr_getOutput', '');
                    add('', '() =', 'right');
                    add('syntax-string', '"int"');
                    add('', ';', '');
                }
                const viewDefault = document.createElement('pre');
                viewDefault.style.marginTop = '2px';
                viewDefault.style.marginLeft = '2px';
                viewDefault.style.fontSize = '0.875rem';
                viewDefault.innerText = `
encode(value):
  if (value is ASTNode):
    if (value has 'cpr_getOutput'): encode(value.cpr_getOutput())
    else: output(value.location, value.type)

  if (value is Iterator or Iterable):
    for (entry in value): encode(entry)

  if no case above matched: output(value.toString())
`.trim();
                return [
                    `Right click on some text in the editor and click 'Create Probe' to get started.`,
                    `If you get the message 'No AST node at this location', then it likely means that something went wrong during parsing.`,
                    `Look at the terminal where you started code-prober.jar for more information.`,
                    ``,
                    `There are a number of 'magic' attributes you can add to your AST nodes to modify their behavior in this tool.`,
                    `All magic attributes are prefixed with 'cpr_' (CodePRober_) to avoid colliding with your own functionality.`,
                    `There three main magic attributes you may want to add are:`,
                    ``,
                    joinElements(`1) '`, createHeader('cpr_nodeListVisible'), `'. This controls whether or not a node will appear in the 'Create Probe' node list.`),
                    `Default: `,
                    `-    false: for 'List' and 'Opt'. Note: this is only default, you can override it.`,
                    `-     true: for all other types.`,
                    exampleVisible,
                    ``,
                    joinElements(`2) '`, createHeader('cpr_propertyListShow'), `'. A collection (List<String> or String[]) that is used to include extra properties in the property list seen when creating probes.`),
                    `Functions are shown in the property list if all of the following is true:`,
                    `- The function is public.`,
                    `- The argument types are 'String', 'int', 'boolean', or a subtype of the top AST Node type.`,
                    `- One of the following is true:`,
                    `-- The function is an attribute (originates from a jrag file, e.g 'z' in 'syn X Y.z() = ...)`,
                    `-- The function is an AST child accessor (used to get members declared in an .ast file).`,
                    `-- The function name is either 'toString', 'getChild', 'getNumChild' or 'getParent'`,
                    `-- The function name is found in the return value from cpr_propertyListShow()`,
                    `Default: empty array.`,
                    exampleAttrs,
                    ``,
                    joinElements(`3) '`, createHeader('cpr_getOutput'), `'. This controls how a value is shown in the output (lower part) of a probe.`),
                    `Default: encodes one or more options in order. In pseudocode:`,
                    viewDefault,
                    exampleView,
                    ``,
                    `The way this tool is built, it cannot help you find & fix infinite loops.`,
                    joinElements(`For infinite loops we instead recommend you use other tools like `, (() => {
                        const a = document.createElement('a');
                        a.href = 'https://docs.oracle.com/javase/1.5.0/docs/tooldocs/share/jstack.html';
                        a.innerText = 'jstack';
                        a.target = '_blank';
                        return a;
                    })(), ' and/or traditional breakpoint/step-debuggers.'),
                    ``,
                    joinElements(`Contributions welcome at `, (() => {
                        const a = document.createElement('a');
                        a.href = repositoryUrl_1.default;
                        a.innerText = repositoryUrl_1.default;
                        a.target = '_blank';
                        return a;
                    })()),
                ];
            }
            case 'recovery-strategy': {
                const settingsExplanation = document.createElement('div');
                settingsExplanation.style.display = 'grid';
                settingsExplanation.style.gridTemplateColumns = 'auto auto 1fr';
                settingsExplanation.style.gridColumnGap = '0.5rem';
                [
                    [`Fail`, `don\'t try to recover information`],
                    [`Parent`, `search recursively upwards through parent nodes, using the equivalent of 'node.getParent()'`],
                    [`Child`, `search recursively downwards through child nodes, using the equivalent of 'node.getChild(0)'`],
                    [`Parent->Child`, `Try 'Parent'. If no position is found, try 'Child'.`],
                    [`Child->Parent`, `Try 'Child'. If no position is found, try 'Parent'.`],
                    [`Zigzag`, `Similar to 'Parent->Child', but only search one step in one direction, then try the other direction, then another step in the first direction, etc. Initially searches one step upwards.`],
                ].forEach(([head, tail]) => {
                    const headNode = document.createElement('span');
                    headNode.style.textAlign = 'right';
                    headNode.classList.add('syntax-attr');
                    headNode.innerText = head;
                    settingsExplanation.appendChild(headNode);
                    settingsExplanation.appendChild(document.createTextNode('-'));
                    const tailNode = document.createElement('span');
                    tailNode.innerText = tail;
                    settingsExplanation.appendChild(tailNode);
                });
                return [
                    'Some nodes in your AST might be missing location information',
                    'This editor is built around the idea that all AST nodes have positions, and the experience is worsened for nodes where this isn\'t true.',
                    '',
                    'There are two solutions',
                    '',
                    '1) Fix your parser',
                    'Usually position information is missing because of how you structured your parser.',
                    'Maybe you do some sort of desugaring in the parser, and create multiple AST nodes in a single production rule.',
                    'Beaver, for example, will only give a single node position information per production rule, so try to ony create a single node per rule.',
                    `Note that this isn't a solution for nodes generated by NTA's. They will need to use solution 2.`,
                    '',
                    '2) Use a recovery strategy',
                    'If a node is missing location information, then we can sometimes get it from nearby nodes.',
                    'This setting controls just how we search for information. Which option fits best for you depends on how you built your AST.',
                    'Settings:',
                    settingsExplanation,
                    '',
                    `No strategy guarantees success. If position is missing, it will be marked with '⚠️', and you\'ll likely run into problems when using it`,
                    `If you are unsure of what to use, 'Zigzag' is usually a pretty good option.`,
                ];
            }
            case 'probe-window': {
                return [
                    `This window represents an active 'probe'`,
                    `The titlebar shows the input to the probe; namely node type and attribute name.`,
                    `Sometimes you'll also see attribute arguments here, and a pen that lets you edit them.`,
                    `Finally, the title bar show where the probed node exists. You can hover this to highlight the node in the document.`,
                    '',
                    'Below the titlebar is the output of the probe.',
                    `This is the resolved value of the probe, formatted according to the 'cpr_getOutput' logic (see general help window for more on this).`,
                    '',
                    `If you check 'Capture stdio' on the top right, you'll also see any messages printed to System.out and System.err in the window.`,
                    `If the cache strategy is set to 'None' or 'Purge', then each probe will get evaluated in a fresh compiler instance in isolation, so any values and messages you see in the probe window belongs only to that window.`,
                    '',
                    'The probes are automatically reevaluated whenever the document changes.',
                    `The probes also automatically update when the underlying jar file (usually 'compiler.jar') changes.`,
                    `Therefore you can use probes as a sort of automatic test case runner. Write some code, open some probes, then move it to a secondary monitor and continue working on your compiler.`,
                    `Whenever you rebuild your compiler, glance at your probes. They should now display fresh values.`,
                ];
            }
            case 'magic-stdout-messages': {
                const createParent = () => {
                    const parent = document.createElement('div');
                    parent.style.display = 'grid';
                    parent.style.gridTemplateColumns = 'auto 1fr';
                    parent.style.rowGap = '0.125rem';
                    parent.style.columnGap = '0.5rem';
                    return parent;
                };
                let entryParent;
                const createEntry = (pattern, explanation) => {
                    const patternHolder = document.createElement('span');
                    patternHolder.classList.add('syntax-string');
                    patternHolder.style.textAlign = 'right';
                    patternHolder.innerText = pattern;
                    const explanationHolder = document.createElement('span');
                    explanationHolder.innerText = explanation;
                    entryParent.appendChild(patternHolder);
                    entryParent.appendChild(explanationHolder);
                };
                // TODO rename the patterns a bit? Prefix everything by PASTA- perhaps?
                const patternsParent = entryParent = createParent();
                createEntry('ERR@S;E;MSG', 'Show a red squiggly line.');
                createEntry('WARN@S;E;MSG', 'Show a yellow squiggly line.');
                createEntry('INFO@S;E;MSG', 'Show a blue squiggly line.');
                createEntry('LINE-PP@S;E;COL', 'Draw a plain line.');
                createEntry('LINE-PA@S;E;COL', 'Draw line that starts plain and ends with an arrow.');
                createEntry('LINE-AP@S;E;COL', 'Draw line that starts with an arrow and ends plain.');
                createEntry('LINE-AA@S;E;COL', 'Draw line with arrows on both ends.');
                const patternsExamples = entryParent = createParent();
                createEntry('ERR@40964;40966;Hello', `Red squiggly line on line 10, column 4 to 6. Shows 'Hello' when you hover over it`);
                createEntry('INFO@16384;32767;Hi', `Blue squiggly line on the entirety of lines 4, 5, 6 and 7. Shows 'Hi' when you hover over it`);
                createEntry('LINE-PA@4096;20490;#0FFF', `Solid cyan line from start of line 1 to line 5, column 10. Has arrow on the end.`);
                createEntry('LINE-AA@16388;16396;#0F07', `Semi-transparent green double-sided arrow on line of line 4 from column 4 to 12`);
                const sampleAttr = document.createElement('pre');
                sampleAttr.style.marginTop = '6px';
                sampleAttr.style.marginLeft = '2px';
                sampleAttr.style.fontSize = '0.875rem';
                sampleAttr.innerText = `
aspect MagicOutputDemo {
  void ASTNode.outputMagic(String type, String arg) {
    System.out.println(type + "@" + getStart() + ";" + getEnd() + ";" + arg);
  }
  coll HashSet<ASTNode> Program.thingsToHighlightBlue() root Program;
  MyNodeType contributes this
    to Program.thingsToHighlightBlue()
    for program();

  syn Object Program.drawBlueSquigglys() {
    for (ASTNode node : thingsToHighlightBlue()) {
      node.outputMagic("INFO", "This thing is highlighted because [..]");
    }
    return null;
  }
}
`.trim();
                const copyButton = document.createElement('button');
                copyButton.innerText = 'Copy to clipboard';
                copyButton.onclick = () => {
                    navigator.clipboard.writeText(sampleAttr.innerText);
                };
                return [
                    `There are a number of 'magic' messages you can print to System.out.`,
                    `Whenever probes are evaluated, these messages are intercepted (even if 'Capture stdio' isn't checked!).`,
                    `The patterns and effects of the magic messages are shown below:`,
                    '',
                    patternsParent,
                    '',
                    `'S' and 'E' stand for 'start' and 'end', and are ints containing line and column. 20 bits for line, 12 for column, e.g: 0xLLLLLCCC.`,
                    'Example: 20493 represents line 5 and column 13 (20493 = (5 << 12) + 13).',
                    '',
                    `'MSG' is any string. This string is displayed when you hover over the squiggly lines`,
                    '',
                    `'COL' is a hex-encoded color in the form #RGBA.`,
                    'Example: #F007 (semi-transparent red)',
                    '',
                    'Some example messages and their effects are listed below:',
                    patternsExamples,
                    '',
                    `The arrows don't work (or only partially work) for lines that are connected to offscreen or invalid positions.`,
                    'For example, if you try to draw a line with one end at line 2, column 5, but that line only has 3 characters, then the line will instead point at column 3.',
                    '',
                    `These special messages can be used as a some custom styling/renderer to help understand how your compiler works.`,
                    `The following code can be used as a starting point`,
                    sampleAttr,
                    copyButton,
                    `Once you have the code in an aspect and have recompiled, open a probe for the attribute 'drawBlueSquigglys' to see all instances of 'MyNodeType' have blue lines under them.`,
                    'Note that the squiggly lines (and all other arrows/lines) only remain as long as their related probe window remains open.'
                ];
            }
            case "ast-cache-strategy": {
                const settingsExplanation = document.createElement('div');
                settingsExplanation.style.display = 'grid';
                settingsExplanation.style.gridTemplateColumns = 'auto auto 1fr';
                settingsExplanation.style.gridColumnGap = '0.5rem';
                [
                    [`Full`, `Cache everything`],
                    [`Partial`, `Cache the AST, but call 'flushTreeCache' on the root before evaluating any probe. This ensures that cached attributes are invoked for every probe.`],
                    [`None`, `Don't cache the AST.`],
                    [`Purge`, `Don't cache the AST or even the underlying jar file, fully reload from the file system each time. This resets all global state, but kills the JVMs ability to optimize your code. This is terrible for performance.`],
                ].forEach(([head, tail]) => {
                    const headNode = document.createElement('span');
                    headNode.style.textAlign = 'right';
                    headNode.classList.add('syntax-attr');
                    headNode.innerText = head;
                    settingsExplanation.appendChild(headNode);
                    settingsExplanation.appendChild(document.createTextNode('-'));
                    const tailNode = document.createElement('span');
                    tailNode.innerText = tail;
                    settingsExplanation.appendChild(tailNode);
                });
                return [
                    `When multiple probes are active, the same editor state will be evaluated multiple times (once for each probe).`,
                    `When this happens, we can re-use the AST multiple to avoid unnecessary re-parses. There are however reasons that you might not want to re-use the AST, or at least not fully.`,
                    '',
                    `While it is technically bad practice, you can use "printf-style" debugging in your attributes (System.out.println(..)).`,
                    `Cached attributes will only output such printf-messages once. With multiple active probes, this makes it uncertain which probe will capture the message.`,
                    `Even worse, if you have any form of mutable state in your AST (please don't!), then reusing an AST can cause unpredictable behavior when parsing.`,
                    `There are a few strategies you can use:`,
                    '',
                    settingsExplanation,
                    '',
                    `Performance is best with 'Full', and worst with 'Purge'.`,
                    `"Debuggability" is best with 'Purge', and worst with 'Full'.`,
                    `If you are unsure of what to use, 'Partial' is usually a pretty good option.`,
                ];
            }
            case 'syntax-highlighting': return [
                `This setting controls which style of highlighting is used in the editor.`,
                `This also affects the suffix used for temporary files, unless 'Custom file suffix' is checked.`,
            ];
            case 'main-args-override': return [
                `When your underlying tool is invoked, the path to a temporary file is sent as an arg to the main method.`,
                `Optionally, some extra args are also included.`,
                `By default, the extra args are defined when you start the CodeProber server.`,
                `For example, running 'java -jar code-prober-jar path/to/your/tool.jar foo bar baz', will set the extra args array to [foo, bar, baz].`,
                `By checking 'Override main args' and clicking "Edit", you can override those extra args.`,
                ``,
                `Args are separated by spaces and/or newlines.`,
                `To include a space in an arg, wrap the arg in quotes (e.g "foo bar").`,
                `To include a newline, quote or backslash in an arg, prefix the char with \\ (e.g \\n, \\" and \\\\).`,
            ];
            case 'customize-file-suffix': return [
                `By default, the editor state is written to a temporary file with a file suffix that matches the chosen syntax highlighting.`,
                `For example, if the highlighting is set to 'Python', then the temp file will end with '.py'.`,
                ``,
                `If you work on a language not represented in the syntax highlighting list, then this might result in your compiler/analyzer rejecting the temporary file due to it having an unknown suffix.`,
                `By checking 'Custom file suffix' you can change the default suffix to something else.`,
                `Note that custom suffixes are used as-is. If you want temp files to end with '.txt', then you must set the custom suffix to exactly '.txt' (including the dot).`,
            ];
            case 'property-list-usage': return [
                `This is the list of available properties on the node you selected.`,
                `The list is filtered according to the 'cpr_propertyListShow' logic (see general help window for more on this).`,
                `When no filter is added, the properties are sorted by two criteria in order:`,
                `1) Properties representing AST child accessors. This corresponds to field declarations in ast files. If you write 'MyNode ::= MyChild:TheType;', then the property 'getMyChild()' will appear high up in this list.`,
                `2) Alphabetical ordering.`,
                ``,
                `When a filter is added, this list is instead is sorted by:`,
                `1) Properties that match the filter. The filter is case insensitive and allows arbitrary characters to appear in between the filter characters. For example, 'gl' matches 'getLorem' but not 'getIpsum'.`,
                `2) Alphabetical ordering`,
            ];
            case 'show-all-properties': return [
                `By default, the property list shown while creating a probe is filtered according to the 'cpr_propertyListShow' logic (see general help window for more on this).`,
                `The last criteria of that filter is that the function must follow one of a few predicates to be shown.`,
                `This checkbox basically adds a '|| true' to the end of that predicate list. I.e any function that is public and has serializable argument types will be shown.`,
                `There can potentially be a very large amount of functions shown is you check this box, which can be annoying.`,
                `In addition, some of the non-standard functions might cause mutations (like 'setChild(int, ..)'), which can cause undefined behavior when used in this tool.`,
                `In general, we recommend you keep this box unchecked, and only occasionally re-check it.`,
            ];
        }
    };
    const displayHelp = (type, setHelpButtonDisabled) => {
        setHelpButtonDisabled(true);
        // TODO prevent this if help window already open
        // Maybe disable the help button, re-enable on close?
        const helpWindow = (0, showWindow_3.default)({
            rootStyle: `
      width: 32rem;
      min-height: 8rem;
    `,
            resizable: true,
            render: (root) => {
                root.appendChild((0, createModalTitle_2.default)({
                    renderLeft: (container) => {
                        const header = document.createElement('span');
                        header.innerText = getHelpTitle(type);
                        container.appendChild(header);
                    },
                    onClose: () => {
                        setHelpButtonDisabled(false);
                        helpWindow.remove();
                    },
                }).element);
                const textHolder = document.createElement('div');
                textHolder.style.padding = '0.5rem';
                const paragraphs = getHelpContents(type);
                paragraphs.forEach(p => {
                    if (!p) {
                        textHolder.appendChild(document.createElement('br'));
                        return;
                    }
                    if (typeof p !== 'string') {
                        textHolder.appendChild(p);
                        return;
                    }
                    const node = document.createElement('p');
                    // node.style.whiteSpace = 'pre';
                    // node.style.maxWidth = '31rem';
                    // const leadingWhitespace = p.length - p.trimStart().length;
                    // if (leadingWhitespace) {
                    //   let ws = '';
                    //   for (let i = 0; i < leadingWhitespace; i++) {
                    //     ws = ws + ' ';
                    //   }
                    //   const wsnode = document.createElement('span');
                    //   wsnode.style.whiteSpace = 'pre';
                    //   wsnode.innerText = ws;
                    //   node.appendChild(wsnode);
                    // }
                    node.appendChild(document.createTextNode(p));
                    node.style.marginTop = '0';
                    node.style.marginBottom = '0';
                    textHolder.appendChild(node);
                });
                root.appendChild(textHolder);
            }
        });
    };
    exports.default = displayHelp;
});
define("model/adjustTypeAtLoc", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const adjustTypeAtLoc = (adjuster, tal) => {
        const span = startEndToSpan(tal.start, tal.end);
        let [ls, cs] = adjuster(span.lineStart, span.colStart);
        let [le, ce] = adjuster(span.lineEnd, span.colEnd);
        if (ls == le && cs == ce) {
            if (span.lineStart === span.lineEnd && span.colStart === span.colEnd) {
                // Accept it, despite it being strange
            }
            else {
                // Instead of accepting change to zero-width span, take same line/col diff as before
                le = ls + (span.lineEnd - span.lineStart);
                ce = cs + (span.colEnd - span.colStart);
                // console.log('Ignoring adjustmpent from', span, 'to', { lineStart: ls, colStart: cs, lineEnd: le, colEnd: ce }, 'because it looks very improbable');
                // return;
            }
        }
        tal.start = (ls << 12) + Math.max(0, cs);
        tal.end = (le << 12) + ce;
    };
    exports.default = adjustTypeAtLoc;
});
define("model/adjustLocator", ["require", "exports", "model/adjustTypeAtLoc"], function (require, exports, adjustTypeAtLoc_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    adjustTypeAtLoc_1 = __importDefault(adjustTypeAtLoc_1);
    const adjustLocator = (adj, loc) => {
        (0, adjustTypeAtLoc_1.default)(adj, loc.result);
        const adjustStep = (step) => {
            switch (step.type) {
                case 'tal': {
                    (0, adjustTypeAtLoc_1.default)(adj, step.value);
                    break;
                }
                case 'nta': {
                    step.value.args.forEach(({ args }) => {
                        if (args) {
                            args.forEach(({ value }) => {
                                if (value && typeof value === 'object') {
                                    adjustLocator(adj, value);
                                }
                            });
                        }
                    });
                    break;
                }
            }
        };
        loc.steps.forEach(adjustStep);
    };
    exports.default = adjustLocator;
});
define("model/syntaxHighlighting", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.getAvailableLanguages = exports.getAppropriateFileSuffix = void 0;
    const langstoSuffixes = {
        plaintext: ['txt', 'Plain Text'],
        abap: ['abap', 'abap'],
        apex: ['cls', 'Apex'],
        azcli: ['azcli', 'Azure CLI'],
        bat: ['bat', 'Batch'],
        bicep: ['bicep', 'Bicep'],
        cameligo: ['mligo', 'Cameligo'],
        clojure: ['clj', 'clojure'],
        coffeescript: ['coffee', 'CoffeeScript'],
        c: ['c', 'C'],
        cpp: ['cpp', 'C++'],
        csharp: ['cs', 'C#'],
        csp: ['csp', 'CSP'],
        css: ['css', 'CSS'],
        dart: ['dart', 'Dart'],
        dockerfile: ['dockerfile', 'Dockerfile'],
        ecl: ['ecl', 'ECL'],
        elixir: ['ex', 'Elixir'],
        flow9: ['flow', 'Flow9'],
        fsharp: ['fs', 'F#'],
        go: ['go', 'Go'],
        graphql: ['graphql', 'GraphQL'],
        handlebars: ['handlebars', 'Handlebars'],
        hcl: ['tf', 'Terraform'],
        html: ['html', 'HTML'],
        ini: ['ini', 'Ini'],
        java: ['java', 'Java'],
        javascript: ['js', 'JavaScript'],
        julia: ['jl', 'Julia'],
        kotlin: ['kt', 'Kotlin'],
        less: ['less', 'Less'],
        lexon: ['lex', 'Lexon'],
        lua: ['lua', 'Lua'],
        liquid: ['liquid', 'Liquid'],
        m3: ['m3', 'Modula-3'],
        markdown: ['md', 'Markdown'],
        mips: ['s', 'MIPS'],
        msdax: ['dax', 'DAX'],
        mysql: ['mysql', 'MySQL'],
        'objective-c': ['m', 'Objective-C'],
        pascal: ['pas', 'Pascal'],
        pascaligo: ['ligo', 'Pascaligo'],
        perl: ['pl', 'Perl'],
        pgsql: ['pgsql', 'PostgreSQL'],
        php: ['php', 'PHP'],
        postiats: ['dats', 'ATS'],
        powerquery: ['pq', 'PQ'],
        powershell: ['ps1', 'PowerShell'],
        proto: ['proto', 'protobuf'],
        pug: ['jade', 'Pug'],
        python: ['py', 'Python'],
        qsharp: ['qs', 'Q#'],
        r: ['r', 'R'],
        razor: ['cshtml', 'Razor'],
        redis: ['redis', 'redis'],
        redshift: ['redshift', 'Redshift'],
        restructuredtext: ['rst', 'reStructuredText'],
        ruby: ['rb', 'Ruby'],
        rust: ['rs', 'Rust'],
        sb: ['sb', 'Small Basic'],
        scala: ['scala', 'Scala'],
        scheme: ['scm', 'scheme'],
        scss: ['scss', 'Sass'],
        shell: ['sh', 'Shell'],
        sol: ['sol', 'sol'],
        aes: ['aes', 'aes'],
        sparql: ['rq', 'sparql'],
        sql: ['sql', 'SQL'],
        st: ['st', 'StructuredText'],
        swift: ['swift', 'Swift'],
        systemverilog: ['sv', 'SV'],
        verilog: ['v', 'V'],
        tcl: ['tcl', 'tcl'],
        twig: ['twig', 'Twig'],
        typescript: ['ts', 'TypeScript'],
        vb: ['vb', 'Visual Basic'],
        xml: ['xml', 'XML'],
        yaml: ['yaml', 'YAML'],
        json: ['json', 'JSON']
    };
    const getAppropriateFileSuffix = (lang) => {
        var _a, _b;
        return (_b = (_a = langstoSuffixes[lang]) === null || _a === void 0 ? void 0 : _a[0]) !== null && _b !== void 0 ? _b : `.${lang.toLowerCase()}`;
    };
    exports.getAppropriateFileSuffix = getAppropriateFileSuffix;
    const getAvailableLanguages = () => Object.assign(Object.entries(langstoSuffixes).map(([k, v]) => ({ id: k, alias: v[1] })));
    exports.getAvailableLanguages = getAvailableLanguages;
});
define("settings", ["require", "exports", "model/syntaxHighlighting"], function (require, exports, syntaxHighlighting_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    let settingsObj = null;
    const settings = {
        get: () => {
            if (!settingsObj) {
                try {
                    // TODO remove 'pasta-settings' fallback after an appropriate amount of time
                    settingsObj = JSON.parse(localStorage.getItem('codeprober-settings') || localStorage.getItem('pasta-settings') || '{}');
                }
                catch (e) {
                    console.warn('Bad data in localStorage, resetting settings', e);
                    settingsObj = {};
                }
            }
            return settingsObj || {};
        },
        set: (newSettings) => {
            settingsObj = newSettings;
            localStorage.setItem('codeprober-settings', JSON.stringify(settingsObj));
        },
        getEditorContents: () => settings.get().editorContents,
        setEditorContents: (editorContents) => settings.set({ ...settings.get(), editorContents }),
        isLightTheme: () => { var _a; return (_a = settings.get().lightTheme) !== null && _a !== void 0 ? _a : false; },
        setLightTheme: (lightTheme) => settings.set({ ...settings.get(), lightTheme }),
        shouldDuplicateProbeOnAttrClick: () => { var _a; return (_a = settings.get().duplicateProbeOnAttrClick) !== null && _a !== void 0 ? _a : true; },
        setShouldDuplicateProbeOnAttrClick: (duplicateProbeOnAttrClick) => settings.set({ ...settings.get(), duplicateProbeOnAttrClick }),
        shouldCaptureStdio: () => { var _a; return (_a = settings.get().captureStdio) !== null && _a !== void 0 ? _a : true; },
        setShouldCaptureStdio: (captureStdio) => settings.set({ ...settings.get(), captureStdio }),
        getPositionRecoveryStrategy: () => { var _a; return (_a = settings.get().positionRecoveryStrategy) !== null && _a !== void 0 ? _a : 'ALTERNATE_PARENT_CHILD'; },
        setPositionRecoveryStrategy: (positionRecoveryStrategy) => settings.set({ ...settings.get(), positionRecoveryStrategy }),
        getAstCacheStrategy: () => { var _a; return (_a = settings.get().astCacheStrategy) !== null && _a !== void 0 ? _a : 'PARTIAL'; },
        setAstCacheStrategy: (astCacheStrategy) => settings.set({ ...settings.get(), astCacheStrategy }),
        getProbeWindowStates: () => { var _a; return (_a = settings.get().probeWindowStates) !== null && _a !== void 0 ? _a : []; },
        setProbeWindowStates: (probeWindowStates) => settings.set({ ...settings.get(), probeWindowStates }),
        getSyntaxHighlighting: () => { var _a; return (_a = settings.get().syntaxHighlighting) !== null && _a !== void 0 ? _a : 'java'; },
        setSyntaxHighlighting: (syntaxHighlighting) => settings.set({ ...settings.get(), syntaxHighlighting }),
        getMainArgsOverride: () => { var _a; return (_a = settings.get().mainArgsOverride) !== null && _a !== void 0 ? _a : null; },
        setMainArgsOverride: (mainArgsOverride) => settings.set({ ...settings.get(), mainArgsOverride }),
        getCustomFileSuffix: () => { var _a; return (_a = settings.get().customFileSuffix) !== null && _a !== void 0 ? _a : null; },
        setCustomFileSuffix: (customFileSuffix) => settings.set({ ...settings.get(), customFileSuffix }),
        getCurrentFileSuffix: () => { var _a; return (_a = settings.getCustomFileSuffix()) !== null && _a !== void 0 ? _a : `.${(0, syntaxHighlighting_1.getAppropriateFileSuffix)(settings.getSyntaxHighlighting())}`; },
        shouldShowAllProperties: () => { var _a; return (_a = settings.get().showAllProperties) !== null && _a !== void 0 ? _a : false; },
        setShouldShowAllProperties: (showAllProperties) => settings.set({ ...settings.get(), showAllProperties }),
    };
    exports.default = settings;
});
define("ui/popup/displayAttributeModal", ["require", "exports", "ui/create/createLoadingSpinner", "ui/create/createModalTitle", "ui/popup/displayProbeModal", "ui/create/showWindow", "ui/popup/displayArgModal", "ui/popup/formatAttr", "ui/create/createTextSpanIndicator", "ui/popup/displayHelp", "model/adjustLocator", "settings"], function (require, exports, createLoadingSpinner_1, createModalTitle_3, displayProbeModal_2, showWindow_4, displayArgModal_1, formatAttr_2, createTextSpanIndicator_2, displayHelp_1, adjustLocator_1, settings_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createLoadingSpinner_1 = __importDefault(createLoadingSpinner_1);
    createModalTitle_3 = __importDefault(createModalTitle_3);
    displayProbeModal_2 = __importDefault(displayProbeModal_2);
    showWindow_4 = __importDefault(showWindow_4);
    displayArgModal_1 = __importDefault(displayArgModal_1);
    formatAttr_2 = __importDefault(formatAttr_2);
    createTextSpanIndicator_2 = __importDefault(createTextSpanIndicator_2);
    displayHelp_1 = __importDefault(displayHelp_1);
    adjustLocator_1 = __importDefault(adjustLocator_1);
    settings_1 = __importDefault(settings_1);
    const displayAttributeModal = (env, modalPos, locator) => {
        const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
        let filter = '';
        let attrs = null;
        let showErr = false;
        const cleanup = () => {
            delete env.onChangeListeners[queryId];
            popup.remove();
        };
        let isFirstRender = true;
        const popup = (0, showWindow_4.default)({
            pos: modalPos,
            rootStyle: `
      min-width: 16rem;
      min-height: 8rem;
      80vh;
    `,
            render: (root, cancelToken) => {
                while (root.firstChild)
                    root.firstChild.remove();
                // root.innerText = 'Loading..';
                root.appendChild((0, createModalTitle_3.default)({
                    renderLeft: (container) => {
                        const headType = document.createElement('span');
                        headType.classList.add('syntax-type');
                        headType.innerText = `${locator.result.type}`;
                        const headAttr = document.createElement('span');
                        headAttr.classList.add('syntax-attr');
                        headAttr.innerText = `.?`;
                        // headAttr.style.fontStyle= 'italic';
                        container.appendChild(headType);
                        container.appendChild(headAttr);
                        container.appendChild((0, createTextSpanIndicator_2.default)({
                            span: startEndToSpan(locator.result.start, locator.result.end),
                            marginLeft: true,
                            onHover: on => env.updateSpanHighlight(on ? startEndToSpan(locator.result.start, locator.result.end) : null),
                        }));
                    },
                    onClose: () => {
                        cleanup();
                    },
                    extraActions: [
                        {
                            title: 'Help',
                            invoke: () => {
                                (0, displayHelp_1.default)('property-list-usage', () => { });
                            }
                        },
                    ],
                }).element);
                if (!attrs && !showErr) {
                    const spinner = (0, createLoadingSpinner_1.default)();
                    spinner.classList.add('absoluteCenter');
                    const spinnerWrapper = document.createElement('div');
                    spinnerWrapper.style.height = '7rem';
                    spinnerWrapper.style.display = 'block';
                    spinnerWrapper.style.position = 'relative';
                    spinnerWrapper.appendChild(spinner);
                    root.appendChild(spinnerWrapper);
                    return;
                }
                if (attrs) {
                    let resortList = () => { };
                    let submit = () => { };
                    const nodesList = [];
                    const filterInput = document.createElement('input');
                    filterInput.placeholder = 'Filter';
                    filterInput.classList.add('attr-modal-filter');
                    if (!filter) {
                        filterInput.classList.add('empty');
                    }
                    filterInput.type = 'text';
                    filterInput.value = filter;
                    filterInput.oninput = (e) => {
                        filter = filterInput.value.trim();
                        resortList();
                    };
                    filterInput.onkeydown = (e) => {
                        var _a;
                        // filterInput.scrollIntoView();
                        if (e.key === 'Enter') {
                            submit();
                        }
                        else if (e.key === 'ArrowDown') {
                            if (nodesList.length > 0) {
                                (_a = nodesList[0]) === null || _a === void 0 ? void 0 : _a.focus();
                                e.preventDefault();
                            }
                        }
                    };
                    if (isFirstRender) {
                        isFirstRender = false;
                        setTimeout(() => filterInput.focus(), 50);
                    }
                    root.appendChild(filterInput);
                    root.style.minHeight = '4rem';
                    const sortedAttrs = document.createElement('div');
                    resortList = () => {
                        nodesList.length = 0;
                        submit = () => { };
                        while (sortedAttrs.firstChild)
                            sortedAttrs.firstChild.remove();
                        if (!attrs) {
                            console.log('attrs disappeared after a successful load??');
                            return;
                        }
                        function escapeRegex(string) {
                            return string.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
                        }
                        const reg = filter ? new RegExp(`.*${[...filter].map(part => part.trim()).filter(Boolean).map(part => escapeRegex(part)).join('.*')}.*`, 'i') : null;
                        const match = (attr) => {
                            if (!reg) {
                                return !!attr.astChildName;
                            }
                            // const formatted =
                            return reg.test((0, formatAttr_2.default)(attr)) || (attr.astChildName && reg.test(attr.astChildName));
                        };
                        const matches = attrs.filter(match);
                        const misses = attrs.filter(a => !match(a));
                        const showProbe = (attr) => {
                            cleanup();
                            if (!attr.args || attr.args.length === 0) {
                                (0, displayProbeModal_2.default)(env, popup.getPos(), locator, { name: attr.name });
                            }
                            else {
                                (0, displayArgModal_1.default)(env, popup.getPos(), locator, {
                                    name: attr.name,
                                    args: attr.args.map(arg => ({
                                        ...arg,
                                        value: ''
                                    })),
                                });
                            }
                        };
                        const buildNode = (attr, borderTop, highlight) => {
                            const node = document.createElement('div');
                            const ourNodeIndex = nodesList.length;
                            nodesList.push(node);
                            node.tabIndex = 0;
                            node.onmousedown = (e) => { e.stopPropagation(); };
                            node.classList.add('syntax-attr-dim-focus');
                            node.classList.add('clickHighlightOnHover');
                            node.style.padding = '0 0.25rem';
                            if (borderTop) {
                                node.style.borderTop = `1px solid gray`;
                            }
                            if (highlight) {
                                node.classList.add('bg-syntax-attr-dim');
                            }
                            node.appendChild(document.createTextNode((0, formatAttr_2.default)(attr)));
                            node.onclick = () => showProbe(attr);
                            node.onkeydown = (e) => {
                                var _a, _b;
                                if (e.key === 'Enter') {
                                    showProbe(attr);
                                }
                                else if (e.key === 'ArrowDown' && ourNodeIndex !== (nodesList.length - 1)) {
                                    e.preventDefault();
                                    (_a = nodesList[ourNodeIndex + 1]) === null || _a === void 0 ? void 0 : _a.focus();
                                }
                                else if (e.key === 'ArrowUp') {
                                    e.preventDefault();
                                    if (ourNodeIndex > 0) {
                                        (_b = nodesList[ourNodeIndex - 1]) === null || _b === void 0 ? void 0 : _b.focus();
                                    }
                                    else {
                                        filterInput.focus();
                                    }
                                }
                            };
                            sortedAttrs.appendChild(node);
                        };
                        matches.forEach((attr, idx) => buildNode(attr, idx > 0, matches.length === 1));
                        if (matches.length && misses.length) {
                            if (matches.length === 1) {
                                const submitExpl = document.createElement('p');
                                submitExpl.classList.add('syntax-attr');
                                submitExpl.style.textAlign = 'center';
                                submitExpl.innerText = 'Press enter to select';
                                sortedAttrs.appendChild(submitExpl);
                                submit = () => showProbe(matches[0]);
                            }
                            const sep = document.createElement('div');
                            sep.classList.add('search-list-separator');
                            sortedAttrs.appendChild(sep);
                            // sortedAttrs.appendChild(document.createElement('hr'));
                            // sortedAttrs.appendChild(document.createElement('hr'));
                            // sortedAttrs.appendChild(document.createElement('hr'));
                        }
                        misses.forEach((attr, idx) => buildNode(attr, idx > 0, !matches.length && misses.length === 1));
                    };
                    resortList();
                    root.appendChild(sortedAttrs);
                }
                else {
                    // showErr
                    // if (cancelToken.cancelled) { return; }
                    while (root.firstChild)
                        root.removeChild(root.firstChild);
                    root.style.display = 'flex';
                    root.style.justifyContent = 'center';
                    root.style.padding = 'auto';
                    root.style.textAlign = 'center';
                    root.style.color = '#F88';
                    root.innerText = 'Error while\nloading attributes..';
                    setTimeout(() => cleanup(), 1000);
                }
            },
        });
        env.onChangeListeners[queryId] = (adjusters) => {
            if (adjusters) {
                adjusters.forEach(adj => (0, adjustLocator_1.default)(adj, locator));
            }
            attrs = null;
            fetchAttrs();
            popup.refresh();
        };
        /*
        const foo = 'bar';
      
        const obj = { foo }
        const obj = { foo: foo }
        const obj = { "foo": foo }
        const obj = { 'foo': foo }
        const obj = { ['foo']: foo }
        */
        let fetchState = 'idle';
        const fetchAttrs = () => {
            switch (fetchState) {
                case 'idle': {
                    fetchState = 'fetching';
                    break;
                }
                case 'fetching': {
                    fetchState = 'queued';
                    return;
                }
                case 'queued': return;
            }
            env.performRpcQuery({
                attr: {
                    name: settings_1.default.shouldShowAllProperties() ? 'meta:listAllProperties' : 'meta:listProperties'
                },
                locator,
            })
                .then((result) => {
                const refetch = fetchState == 'queued';
                fetchState = 'idle';
                if (refetch)
                    fetchAttrs();
                const parsed = result.properties;
                if (!parsed) {
                    throw new Error('Unexpected response body "' + JSON.stringify(result) + '"');
                }
                attrs = [];
                const deudplicator = new Set();
                parsed.forEach(attr => {
                    const uniqId = JSON.stringify(attr);
                    if (deudplicator.has(uniqId)) {
                        return;
                    }
                    deudplicator.add(uniqId);
                    attrs === null || attrs === void 0 ? void 0 : attrs.push(attr);
                });
                popup.refresh();
            })
                .catch(err => {
                console.warn('Error when loading attributes', err);
                showErr = true;
                popup.refresh();
            });
        };
        fetchAttrs();
    };
    exports.default = displayAttributeModal;
});
define("ui/popup/displayProbeModal", ["require", "exports", "ui/create/createLoadingSpinner", "ui/create/createModalTitle", "ui/create/createTextSpanIndicator", "ui/popup/displayAttributeModal", "ui/create/showWindow", "ui/create/registerOnHover", "ui/popup/formatAttr", "ui/popup/displayArgModal", "ui/create/registerNodeSelector", "model/adjustLocator", "ui/popup/displayHelp"], function (require, exports, createLoadingSpinner_2, createModalTitle_4, createTextSpanIndicator_3, displayAttributeModal_2, showWindow_5, registerOnHover_3, formatAttr_3, displayArgModal_2, registerNodeSelector_2, adjustLocator_2, displayHelp_2) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createLoadingSpinner_2 = __importDefault(createLoadingSpinner_2);
    createModalTitle_4 = __importDefault(createModalTitle_4);
    createTextSpanIndicator_3 = __importDefault(createTextSpanIndicator_3);
    displayAttributeModal_2 = __importDefault(displayAttributeModal_2);
    showWindow_5 = __importDefault(showWindow_5);
    registerOnHover_3 = __importDefault(registerOnHover_3);
    formatAttr_3 = __importDefault(formatAttr_3);
    displayArgModal_2 = __importDefault(displayArgModal_2);
    registerNodeSelector_2 = __importDefault(registerNodeSelector_2);
    adjustLocator_2 = __importDefault(adjustLocator_2);
    displayHelp_2 = __importDefault(displayHelp_2);
    const displayProbeModal = (env, modalPos, locator, attr) => {
        const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
        const localErrors = [];
        env.probeMarkers[queryId] = localErrors;
        // const stickyMarker = env.registerStickyMarker(span)
        const cleanup = () => {
            delete env.onChangeListeners[queryId];
            delete env.probeMarkers[queryId];
            delete env.probeWindowStateSavers[queryId];
            env.currentlyLoadingModals.delete(queryId);
            env.triggerWindowSave();
            if (localErrors.length > 0) {
                env.updateMarkers();
            }
            // stickyMarker.remove();
        };
        let copyBody = [];
        const createTitle = () => {
            return (0, createModalTitle_4.default)({
                extraActions: [
                    {
                        title: 'Duplicate window',
                        invoke: () => {
                            const pos = queryWindow.getPos();
                            displayProbeModal(env, { x: pos.x + 10, y: pos.y + 10 }, JSON.parse(JSON.stringify(locator)), attr);
                        },
                    },
                    {
                        title: 'Copy input to clipboard',
                        invoke: () => {
                            navigator.clipboard.writeText(JSON.stringify({ locator, attr }, null, 2));
                        }
                    },
                    {
                        title: 'Copy output to clipboard',
                        invoke: () => {
                            navigator.clipboard.writeText(copyBody.map(line => typeof line === 'string' ? line : JSON.stringify(line, null, 2)).join('\n'));
                        }
                    },
                    {
                        title: 'General probe help',
                        invoke: () => {
                            (0, displayHelp_2.default)('probe-window', () => { });
                        }
                    },
                    {
                        title: 'Magic output messages help',
                        invoke: () => {
                            (0, displayHelp_2.default)('magic-stdout-messages', () => { });
                        }
                    },
                ],
                // onDuplicate: () => {
                //   const pos = queryWindow.getPos();
                //   displayProbeModal(env, { x: pos.x + 10, y: pos.y + 10 }, JSON.parse(JSON.stringify(locator)), attr);
                // },
                renderLeft: (container) => {
                    var _a;
                    const headType = document.createElement('span');
                    headType.classList.add('syntax-type');
                    headType.innerText = `${locator.result.type}`;
                    const headAttr = document.createElement('span');
                    headAttr.classList.add('syntax-attr');
                    headAttr.classList.add('clickHighlightOnHover');
                    if (!attr.args || attr.args.length === 0) {
                        headAttr.innerText = `.${(0, formatAttr_3.default)(attr)}`;
                    }
                    else {
                        headAttr.appendChild(document.createTextNode(`.${attr.name}(`));
                        attr.args.forEach((arg, argIdx) => {
                            if (argIdx > 0) {
                                headAttr.appendChild(document.createTextNode(`,`));
                            }
                            switch (arg.type) {
                                case 'java.lang.String': {
                                    const node = document.createElement('span');
                                    node.classList.add('syntax-string');
                                    node.innerText = `"${arg.value}"`;
                                    headAttr.appendChild(node);
                                    break;
                                }
                                case 'int': {
                                    const node = document.createElement('span');
                                    node.classList.add('syntax-int');
                                    node.innerText = `${arg.value}`;
                                    headAttr.appendChild(node);
                                    break;
                                }
                                case 'boolean': {
                                    const node = document.createElement('span');
                                    node.classList.add('syntax-modifier');
                                    node.innerText = `${arg.value}`;
                                    headAttr.appendChild(node);
                                    break;
                                }
                                default: {
                                    if (arg.isNodeType) {
                                        const node = document.createElement('span');
                                        node.classList.add('syntax-type');
                                        if (!arg.value || (typeof arg.value !== 'object')) {
                                            // Probably null
                                            node.innerText = `${arg.value}`;
                                        }
                                        else {
                                            node.innerText = arg.value.result.type;
                                        }
                                        headAttr.appendChild(node);
                                    }
                                    else {
                                        console.warn('Unsure of how to render', arg.type);
                                        headAttr.appendChild(document.createTextNode(`${arg.value}`));
                                    }
                                    break;
                                }
                            }
                        });
                        headAttr.appendChild(document.createTextNode(`)`));
                    }
                    headAttr.onmousedown = (e) => { e.stopPropagation(); };
                    headAttr.onclick = (e) => {
                        if (env.duplicateOnAttr() != e.shiftKey) {
                            (0, displayAttributeModal_2.default)(env, null, JSON.parse(JSON.stringify(locator)));
                        }
                        else {
                            queryWindow.remove();
                            cleanup();
                            (0, displayAttributeModal_2.default)(env, queryWindow.getPos(), locator);
                        }
                        e.stopPropagation();
                    };
                    container.appendChild(headType);
                    container.appendChild(headAttr);
                    // TODO add edit pen here?
                    if ((_a = attr.args) === null || _a === void 0 ? void 0 : _a.length) {
                        const editButton = document.createElement('img');
                        editButton.src = '/icons/edit_white_24dp.svg';
                        editButton.classList.add('modalEditButton');
                        editButton.classList.add('clickHighlightOnHover');
                        editButton.onmousedown = (e) => { e.stopPropagation(); };
                        editButton.onclick = () => {
                            queryWindow.remove();
                            cleanup();
                            (0, displayArgModal_2.default)(env, queryWindow.getPos(), locator, attr);
                        };
                        // const editHolder = document.createElement('div');
                        // editHolder.style.display = 'flex';
                        // editHolder.style.flexDirection = 'column';
                        // editHolder.style.justifyContent = 'space-around';
                        // editHolder.appendChild(editButton);
                        container.appendChild(editButton);
                    }
                    const spanIndicator = (0, createTextSpanIndicator_3.default)({
                        span: startEndToSpan(locator.result.start, locator.result.end),
                        marginLeft: true,
                        onHover: on => env.updateSpanHighlight(on ? startEndToSpan(locator.result.start, locator.result.end) : null),
                    });
                    (0, registerNodeSelector_2.default)(spanIndicator, () => locator);
                    container.appendChild(spanIndicator);
                },
                onClose: () => {
                    queryWindow.remove();
                    cleanup();
                },
            });
        };
        let lastSpinner = null;
        let isFirstRender = true;
        let loading = false;
        let refreshOnDone = false;
        const queryWindow = (0, showWindow_5.default)({
            pos: modalPos,
            rootStyle: `
      min-width: 16rem;
      min-height: fit-content;
    `,
            resizable: true,
            onFinishedMove: () => env.triggerWindowSave(),
            render: (root, cancelToken) => {
                if (lastSpinner != null) {
                    lastSpinner.style.display = 'inline-block';
                    lastSpinner = null;
                }
                else if (isFirstRender) {
                    isFirstRender = false;
                    while (root.firstChild)
                        root.removeChild(root.firstChild);
                    root.appendChild(createTitle().element);
                    const spinner = (0, createLoadingSpinner_2.default)();
                    spinner.classList.add('absoluteCenter');
                    const spinnerWrapper = document.createElement('div');
                    spinnerWrapper.style.height = '7rem';
                    spinnerWrapper.style.display = 'block';
                    spinnerWrapper.style.position = 'relative';
                    spinnerWrapper.appendChild(spinner);
                    root.appendChild(spinnerWrapper);
                }
                loading = true;
                if (!locator) {
                    console.error('no locator??');
                }
                // console.log('req attr:', JSON.stringify(attr, null, 2));
                env.currentlyLoadingModals.add(queryId);
                const rpcQueryStart = performance.now();
                env.performRpcQuery({
                    attr,
                    locator,
                })
                    .then((parsed) => {
                    var _a;
                    const body = parsed.body;
                    copyBody = body;
                    loading = false;
                    if (refreshOnDone) {
                        refreshOnDone = false;
                        queryWindow.refresh();
                    }
                    if (typeof parsed.totalTime === 'number'
                        && typeof parsed.parseTime === 'number'
                        && typeof parsed.createLocatorTime === 'number'
                        && typeof parsed.applyLocatorTime === 'number'
                        && typeof parsed.attrEvalTime === 'number') {
                        env.statisticsCollector.addProbeEvaluationTime({
                            attrEvalMs: parsed.attrEvalTime / 1000000,
                            fullRpcMs: Math.max(performance.now() - rpcQueryStart),
                            serverApplyLocatorMs: parsed.applyLocatorTime / 1000000,
                            serverCreateLocatorMs: parsed.createLocatorTime / 1000000,
                            serverParseOnlyMs: parsed.parseTime / 1000000,
                            serverSideMs: parsed.totalTime / 1000000,
                        });
                    }
                    if (cancelToken.cancelled) {
                        return;
                    }
                    if (!refreshOnDone) {
                        env.currentlyLoadingModals.delete(queryId);
                    }
                    while (root.firstChild)
                        root.removeChild(root.firstChild);
                    let refreshMarkers = localErrors.length > 0;
                    localErrors.length = 0;
                    // console.log('probe errors:', parsed.errors);
                    parsed.errors.forEach(({ severity, start: errStart, end: errEnd, msg }) => {
                        localErrors.push({ severity, errStart, errEnd, msg });
                    });
                    const updatedArgs = parsed.args;
                    if (updatedArgs) {
                        refreshMarkers = true;
                        (_a = attr.args) === null || _a === void 0 ? void 0 : _a.forEach((arg, argIdx) => {
                            arg.type = updatedArgs[argIdx].type;
                            arg.isNodeType = updatedArgs[argIdx].isNodeType;
                            arg.value = updatedArgs[argIdx].value;
                        });
                    }
                    if (parsed.locator) {
                        refreshMarkers = true;
                        locator = parsed.locator;
                    }
                    if (refreshMarkers || localErrors.length > 0) {
                        env.updateMarkers();
                    }
                    const titleRow = createTitle();
                    root.append(titleRow.element);
                    const encodeLine = (target, line, respectIndent = false) => {
                        if (typeof line === 'string') {
                            const trimmed = line.trimStart();
                            if (trimmed.length !== line.length) {
                                target.appendChild(document.createTextNode(' '.repeat(line.length - trimmed.length)));
                            }
                            if (line.trim()) {
                                target.appendChild(document.createTextNode(line.trim()));
                            }
                            target.appendChild(document.createElement('br'));
                        }
                        else if (Array.isArray(line)) {
                            if (!respectIndent) {
                                // First level indent, 'inline' it
                                line.forEach(sub => encodeLine(target, sub, true));
                            }
                            else {
                                // >=2 level indent, respect it
                                const deeper = document.createElement('pre');
                                // deeper.style.borderLeft = '1px solid #88888877';
                                deeper.style.marginLeft = '1rem';
                                deeper.style.marginTop = '0.125rem';
                                line.forEach(sub => encodeLine(deeper, sub, true));
                                target.appendChild(deeper);
                            }
                        }
                        else {
                            switch (line.type) {
                                case "stdout": {
                                    const span = document.createElement('span');
                                    span.classList.add('captured-stdout');
                                    span.innerText = `> ${line.value}`;
                                    target.appendChild(span);
                                    target.appendChild(document.createElement('br'));
                                    break;
                                }
                                case "stderr": {
                                    const span = document.createElement('span');
                                    span.classList.add('captured-stderr');
                                    span.innerText = `> ${line.value}`;
                                    target.appendChild(span);
                                    target.appendChild(document.createElement('br'));
                                    break;
                                }
                                case "node": {
                                    const { start, end, type } = line.value.result;
                                    const container = document.createElement('div');
                                    const span = {
                                        lineStart: (start >>> 12), colStart: (start & 0xFFF),
                                        lineEnd: (end >>> 12), colEnd: (end & 0xFFF),
                                    };
                                    container.appendChild((0, createTextSpanIndicator_3.default)({
                                        span,
                                        marginLeft: false,
                                    }));
                                    const typeNode = document.createElement('span');
                                    typeNode.classList.add('syntax-type');
                                    typeNode.innerText = type;
                                    container.appendChild(typeNode);
                                    container.classList.add('clickHighlightOnHover');
                                    container.style.width = 'fit-content';
                                    container.style.display = 'inline';
                                    (0, registerOnHover_3.default)(container, on => {
                                        env.updateSpanHighlight(on ? span : null);
                                    });
                                    container.onmousedown = (e) => {
                                        e.stopPropagation();
                                    };
                                    (0, registerNodeSelector_2.default)(container, () => line.value);
                                    container.addEventListener('click', () => {
                                        (0, displayAttributeModal_2.default)(env, null, line.value);
                                    });
                                    target.appendChild(container);
                                    break;
                                }
                                default: {
                                    console.warn('Unknown body line type', line);
                                    break;
                                }
                            }
                        }
                    };
                    const pre = document.createElement('pre');
                    pre.style.margin = '0px';
                    pre.style.padding = '0.5rem';
                    pre.style.fontSize = '0.75rem';
                    // pre.innerHtml = lines.slice(outputStart + 1).join('\n').trim();
                    body
                        .filter((line, lineIdx, arr) => {
                        // Keep empty lines only if they are followed by a non-empty line
                        // Removes two empty lines in a row, and removes trailing empty lines
                        if (!line && !arr[lineIdx + 1]) {
                            return false;
                        }
                        return true;
                    })
                        .forEach((line) => {
                        encodeLine(pre, line);
                        // '\n'
                    });
                    root.appendChild(pre);
                    const spinner = (0, createLoadingSpinner_2.default)();
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
                    if (cancelToken.cancelled) {
                        return;
                    }
                    env.currentlyLoadingModals.delete(queryId);
                    console.log('ProbeModal RPC catch', err);
                    root.innerHTML = '';
                    root.innerText = 'Failed refreshing query..';
                    setTimeout(() => {
                        queryWindow.remove();
                        cleanup();
                    }, 1000);
                });
            },
        });
        env.onChangeListeners[queryId] = (adjusters) => {
            var _a;
            if (adjusters) {
                adjusters.forEach(adj => (0, adjustLocator_2.default)(adj, locator));
                (_a = attr.args) === null || _a === void 0 ? void 0 : _a.forEach(({ value }) => {
                    if (value && typeof value === 'object') {
                        adjusters.forEach(adj => (0, adjustLocator_2.default)(adj, value));
                    }
                });
                // console.log('Adjusted span to:', span);
            }
            if (loading) {
                refreshOnDone = true;
            }
            else {
                queryWindow.refresh();
            }
        };
        env.probeWindowStateSavers[queryId] = (target) => target.push({ locator, attr, modalPos: queryWindow.getPos() });
        env.triggerWindowSave();
    };
    exports.default = displayProbeModal;
});
define("ui/popup/displayRagModal", ["require", "exports", "ui/create/createLoadingSpinner", "ui/create/createModalTitle", "ui/popup/displayAttributeModal", "ui/create/registerOnHover", "ui/create/showWindow", "ui/create/registerNodeSelector"], function (require, exports, createLoadingSpinner_3, createModalTitle_5, displayAttributeModal_3, registerOnHover_4, showWindow_6, registerNodeSelector_3) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createLoadingSpinner_3 = __importDefault(createLoadingSpinner_3);
    createModalTitle_5 = __importDefault(createModalTitle_5);
    displayAttributeModal_3 = __importDefault(displayAttributeModal_3);
    registerOnHover_4 = __importDefault(registerOnHover_4);
    showWindow_6 = __importDefault(showWindow_6);
    registerNodeSelector_3 = __importDefault(registerNodeSelector_3);
    const displayRagModal = (env, line, col) => {
        const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
        const cleanup = () => {
            delete env.onChangeListeners[queryId];
            popup.remove();
        };
        const popup = (0, showWindow_6.default)({
            rootStyle: `
        min-width: 12rem;
        min-height: 4rem;
      `,
            render: (root, cancelToken) => {
                while (root.firstChild) {
                    root.firstChild.remove();
                }
                // root.innerText = 'Loading..';
                root.style.display = 'contents';
                const spinner = (0, createLoadingSpinner_3.default)();
                // spinner.style.width = '16rem';
                // spinner.style.height = '4rem';
                spinner.classList.add('absoluteCenter');
                root.appendChild(spinner);
                const rootProgramLocator = {
                    type: '?',
                    start: (line << 12) + col,
                    end: (line << 12) + col,
                    depth: 0,
                };
                env.performRpcQuery({
                    attr: {
                        name: 'meta:listNodes',
                    },
                    locator: {
                        // root: rootProgramLocator,
                        result: rootProgramLocator,
                        steps: []
                    },
                })
                    .then((parsed) => {
                    if (cancelToken.cancelled) {
                        return;
                    }
                    while (root.firstChild)
                        root.removeChild(root.firstChild);
                    root.style.minHeight = '4rem';
                    root.appendChild((0, createModalTitle_5.default)({
                        renderLeft: (container) => {
                            const headType = document.createElement('span');
                            headType.classList.add('syntax-stype');
                            headType.innerText = `Select node..`;
                            container.appendChild(headType);
                        },
                        onClose: () => {
                            cleanup();
                        },
                    }).element);
                    // const needle = 'SpansAndNodeTypes :: ';
                    if (!parsed.nodes) {
                        throw new Error(`Couldn't find expected line in output '${JSON.stringify(parsed)}'`);
                    }
                    const rowsContainer = document.createElement('div');
                    rowsContainer.style.padding = '2px';
                    root.appendChild(rowsContainer);
                    parsed.nodes.forEach((locator, entIdx) => {
                        const { start, end, type } = locator.result;
                        const span = { lineStart: (start >>> 12), colStart: (start & 0xFFF), lineEnd: (end >>> 12), colEnd: (end & 0xFFF) };
                        const node = document.createElement('div');
                        node.classList.add('clickHighlightOnHover');
                        node.style.padding = `0 0.25rem`;
                        if (entIdx !== 0) {
                            node.style.borderTop = '1px solid gray';
                        }
                        node.innerText = `${type}${start === 0 && end === 0 ? ` ⚠️<No position>` : ''}`;
                        (0, registerOnHover_4.default)(node, on => env.updateSpanHighlight(on ? span : null));
                        node.onmousedown = (e) => { e.stopPropagation(); };
                        (0, registerNodeSelector_3.default)(node, () => locator);
                        node.onclick = () => {
                            cleanup();
                            env.updateSpanHighlight(null);
                            (0, displayAttributeModal_3.default)(env, popup.getPos(), locator);
                        };
                        rowsContainer.appendChild(node);
                    });
                })
                    .catch(err => {
                    if (cancelToken.cancelled) {
                        return;
                    }
                    console.warn('query failed', err);
                    while (root.firstChild)
                        root.removeChild(root.firstChild);
                    root.style.display = 'flex';
                    root.style.justifyContent = 'center';
                    root.style.padding = 'auto';
                    root.style.textAlign = 'center';
                    root.style.color = '#F88';
                    root.innerText = 'No AST node\nat this location..';
                    setTimeout(() => cleanup(), 1000);
                });
            }
        });
        env.onChangeListeners[queryId] = (adjusters) => {
            if (adjusters) {
                adjusters.forEach((adj) => {
                    const [l, c] = adj(line, col);
                    line = l;
                    col = c;
                });
            }
            popup.refresh();
        };
    };
    exports.default = displayRagModal;
});
define("model/StatisticsCollectorImpl", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    class StatisticsCollectorImpl {
        constructor() {
            this.lastMeasurement = null;
            this.mergedMeasurements = { fullRpcMs: 0, serverSideMs: 0, serverParseOnlyMs: 0, serverCreateLocatorMs: 0, serverApplyLocatorMs: 0, attrEvalMs: 0 };
            this.numberOfMeasurements = 0;
            this.onChange = null;
        }
        ;
        addProbeEvaluationTime(measure) {
            var _a;
            this.lastMeasurement = measure;
            this.mergedMeasurements.fullRpcMs += measure.fullRpcMs;
            this.mergedMeasurements.serverSideMs += measure.serverSideMs;
            this.mergedMeasurements.serverParseOnlyMs += measure.serverParseOnlyMs;
            this.mergedMeasurements.serverCreateLocatorMs += measure.serverCreateLocatorMs;
            this.mergedMeasurements.serverApplyLocatorMs += measure.serverApplyLocatorMs;
            this.mergedMeasurements.attrEvalMs += measure.attrEvalMs;
            this.numberOfMeasurements++;
            (_a = this.onChange) === null || _a === void 0 ? void 0 : _a.call(this);
        }
        setOnChange(callback) {
            this.onChange = callback;
        }
        getLastMeasurement() { return this.lastMeasurement; }
        getMergedMeasurements() { return this.mergedMeasurements; }
        getNumberOfMeasurements() { return this.numberOfMeasurements; }
        reset() {
            var _a;
            this.lastMeasurement = null;
            this.mergedMeasurements = { fullRpcMs: 0, serverSideMs: 0, serverParseOnlyMs: 0, serverCreateLocatorMs: 0, serverApplyLocatorMs: 0, attrEvalMs: 0 };
            this.numberOfMeasurements = 0;
            (_a = this.onChange) === null || _a === void 0 ? void 0 : _a.call(this);
        }
    }
    exports.default = StatisticsCollectorImpl;
});
define("ui/popup/displayStatistics", ["require", "exports", "ui/create/createModalTitle", "ui/create/showWindow"], function (require, exports, createModalTitle_6, showWindow_7) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createModalTitle_6 = __importDefault(createModalTitle_6);
    showWindow_7 = __importDefault(showWindow_7);
    ;
    const displayStatistics = (collector, setStatisticsButtonDisabled, setEditorContentsAndUpdateProbes, anyModalIsLoading) => {
        let simulateTimer = -1;
        const onClose = () => {
            collector.setOnChange(null);
            helpWindow.remove();
            setStatisticsButtonDisabled(false);
            clearInterval(simulateTimer);
        };
        setStatisticsButtonDisabled(true);
        let statLabels = null;
        let controlButtons = null;
        const generateMethodGenerator = (cycles) => {
            return () => {
                const alreadyGeneratedIds = new Set();
                const genId = (prefix) => {
                    let newId;
                    do {
                        newId = `${Math.max(10000, Math.floor(Math.random() * 1000000))}`;
                    } while (alreadyGeneratedIds.has(newId));
                    alreadyGeneratedIds.add(newId);
                    return `${prefix}${newId}`;
                };
                const pickRandom = (...options) => options[Math.floor(Math.random() * options.length)];
                return [
                    // ...[...Array(cycles)].map(() => `${['interface', 'abstract class', 'enum'][Math.floor(Math.random() * 3)]} ${genId('Other')} { /* Empty */ }`),
                    `class ${genId('Benchmark')} {`,
                    ...[...Array(cycles)].map(() => [
                        `  ${pickRandom('interface', 'abstract class', 'enum')} ${genId('Other')} { /* Empty */ }`,
                        `  static void ${genId('f')}(String[] ${genId('arg')}, ${Math.random() > 0.5 ? 'int' : 'byte'} ${genId('arg')}) {`,
                        `    final long local = System.currentTimeMillis() % ${genId('')}L;`,
                        `    if (local ${pickRandom('<', '>', '==', '!=', '>=', '<=')} ${genId('')}L) { System.out.println(local); }`,
                        `    else { System.out.println(${genId('')}); }`,
                        `  }`,
                    ].join('\n')),
                    '}',
                ].join('\n');
            };
        };
        const tests = [
            {
                title: 'Java - Tiny',
                contents: generateMethodGenerator(1),
                // contents: () =>  [
                //   `class Benchmark${Math.floor(Math.random() * 1_000_000)} {`,
                //   ` // ${Math.random()}`,
                //   '}',
                // ].join('\n')
            },
            {
                title: 'Java - Medium',
                contents: generateMethodGenerator(5)
                // contents: () => {
                //   const genId = (prefix: string) => `${prefix}${Math.floor(Math.random() * 1_000_000)}`;
                //   return [
                //     `${['interface', 'abstract class', 'enum'][Math.floor(Math.random() * 3)]} ${genId('Other')} { /* Empty */ }`,
                //     `class ${genId('Benchmark')} {`,
                //     ` static void main(String[] ${genId('args')}) {`,
                //     `  final long foo = System.currentTimeMillis() % ${genId('')}L;`,
                //     `  if (foo > ${genId('')}L) { System.out.println(foo); }`,
                //     `  else { System.out.println(${genId('')}); }`,
                //     ` }`,
                //     '}',
                //   ].join('\n');
                // }
            },
            {
                title: 'Java - Large',
                contents: generateMethodGenerator(50),
            },
            {
                title: 'Java - Enormous',
                contents: generateMethodGenerator(500),
            },
        ];
        let activeTest = tests[0].title;
        const helpWindow = (0, showWindow_7.default)({
            rootStyle: `
      width: 32rem;
      min-height: 12rem;
    `,
            resizable: true,
            render: (root) => {
                const merged = collector.getMergedMeasurements();
                const numMeasurement = collector.getNumberOfMeasurements();
                const formatNumber = (num) => num.toFixed(1);
                const computeAverage = () => {
                    const computeOfTotal = (num) => `${formatNumber(num * 100 / merged.serverSideMs)}%`;
                    const serverTotalMs = `${formatNumber(merged.serverSideMs / numMeasurement)}ms`;
                    const parseMs = `${formatNumber(merged.serverParseOnlyMs / numMeasurement)}ms (${computeOfTotal(merged.serverParseOnlyMs)})`;
                    const rpcMs = `${formatNumber((merged.fullRpcMs - merged.serverSideMs) / numMeasurement)}ms`;
                    const createLocatorMs = `${formatNumber(merged.serverCreateLocatorMs / numMeasurement)}ms (${computeOfTotal(merged.serverCreateLocatorMs)})`;
                    const applyLocatorMs = `${formatNumber(merged.serverApplyLocatorMs / numMeasurement)}ms (${computeOfTotal(merged.serverApplyLocatorMs)})`;
                    const attributeEvalMs = `${formatNumber(merged.attrEvalMs / numMeasurement)}ms (${computeOfTotal(merged.attrEvalMs)})`;
                    return { serverTotalMs, parseMs, rpcMs, createLocatorMs, applyLocatorMs, attributeEvalMs };
                };
                const computeIndividual = (last) => {
                    const computeOfTotal = (num) => `${formatNumber(num * 100 / last.serverSideMs)}%`;
                    const serverTotalMs = `${formatNumber(last.serverSideMs)}ms`;
                    const parseMs = `${formatNumber(last.serverParseOnlyMs)}ms (${computeOfTotal(last.serverParseOnlyMs)})`;
                    const rpcMs = `${formatNumber(last.fullRpcMs - last.serverSideMs)}ms`;
                    const createLocatorMs = `${formatNumber(last.serverCreateLocatorMs)}ms (${computeOfTotal(last.serverCreateLocatorMs)})`;
                    const applyLocatorMs = `${formatNumber(last.serverApplyLocatorMs)}ms (${computeOfTotal(last.serverApplyLocatorMs)})`;
                    const attributeEvalMs = `${formatNumber(last.attrEvalMs)}ms (${computeOfTotal(last.attrEvalMs)})`;
                    return { serverTotalMs, parseMs, rpcMs, createLocatorMs, applyLocatorMs, attributeEvalMs };
                };
                const last = collector.getLastMeasurement();
                if (!statLabels || !last) {
                    statLabels = null;
                    while (root.firstChild)
                        root.firstChild.remove();
                    root.appendChild((0, createModalTitle_6.default)({
                        renderLeft: (container) => {
                            const header = document.createElement('span');
                            header.innerText = 'Statistics';
                            container.appendChild(header);
                        },
                        onClose,
                    }).element);
                    const grid = document.createElement('div');
                    grid.style.display = 'grid';
                    grid.style.padding = '0.25rem';
                    // grid.style.justifyItems = 'center';
                    grid.style.gridGap = '4px';
                    grid.style.gridTemplateColumns = 'auto 1fr';
                    const addRow = (title, measurement, boldTitle = false) => {
                        const titleNode = document.createElement('span');
                        titleNode.innerText = title;
                        titleNode.style.textAlign = 'right';
                        titleNode.style.textAlign = 'right';
                        if (boldTitle) {
                            titleNode.style.fontWeight = 'bold';
                        }
                        const measurementNode = document.createElement('span');
                        measurementNode.innerText = measurement;
                        grid.appendChild(titleNode);
                        grid.appendChild(measurementNode);
                        return measurementNode;
                    };
                    const addDivider = () => {
                        const divider = document.createElement('div');
                        divider.style.borderTop = '1px solid gray';
                        grid.appendChild(divider);
                        grid.appendChild(divider.cloneNode(true));
                    };
                    const numEvalsLbl = addRow('Num evaluations', `${numMeasurement}`);
                    addRow('', '');
                    const addGroup = (measurements) => {
                        const serverTotalLbl = addRow('Server side total', measurements.serverTotalMs);
                        const parseLbl = addRow('..of which AST parse', measurements.parseMs);
                        const createLocatorLbl = addRow('..of which locator create', measurements.createLocatorMs);
                        const applyLocatorLbl = addRow('..of which locator apply', measurements.applyLocatorMs);
                        const attrEvalLbl = addRow('..of which attribute eval', measurements.attributeEvalMs);
                        addRow('', '');
                        const rpcLbl = addRow('RPC overhead:', measurements.rpcMs);
                        addDivider();
                        return { serverTotalLbl, parseLbl, rpcLbl, createLocatorLbl, applyLocatorLbl, attrEvalLbl };
                    };
                    let averageLabels = null;
                    if (numMeasurement > 0) {
                        addRow('Average', '', true);
                        averageLabels = addGroup(computeAverage());
                    }
                    let mostRecentLabels = null;
                    if (last) {
                        addRow('Most recent', '', true);
                        mostRecentLabels = addGroup(computeIndividual(last));
                    }
                    root.appendChild(grid);
                    if (controlButtons) {
                        root.appendChild(controlButtons);
                    }
                    else {
                        controlButtons = document.createElement('div');
                        controlButtons.style.padding = '0.5rem';
                        root.appendChild(controlButtons);
                        const addButton = (title, onClick) => {
                            const btn = document.createElement('button');
                            btn.innerText = title;
                            btn.onclick = () => onClick(btn);
                            btn.style.marginRight = '0.25rem';
                            controlButtons.appendChild(btn);
                            return btn;
                        };
                        const stopSimulation = () => {
                            clearInterval(simulateTimer);
                            simulateTimer = -1;
                            resetBtn.innerText = 'Reset';
                            measurementBtn.disabled = false;
                        };
                        let resetBtn = addButton('Reset', () => {
                            if (simulateTimer !== -1) {
                                stopSimulation();
                            }
                            else {
                                collector.reset();
                            }
                        });
                        const measurementBtn = addButton('Run benchmark', (btn) => {
                            btn.disabled = true;
                            collector.reset();
                            clearInterval(simulateTimer);
                            resetBtn.innerText = 'Stop';
                            // let expectMeasurements = 0;
                            const triggerChange = () => {
                                setEditorContentsAndUpdateProbes(tests.find(({ title }) => title === activeTest).contents());
                            };
                            let prevChangeCounter = collector.getNumberOfMeasurements();
                            triggerChange();
                            simulateTimer = setInterval(() => {
                                const newChangeCounter = collector.getNumberOfMeasurements();
                                if (anyModalIsLoading() || newChangeCounter == prevChangeCounter) {
                                    return;
                                }
                                prevChangeCounter = newChangeCounter;
                                // const newMeasurements = collector.getNumberOfMeasurements();
                                // if (newMeasurements === expectMeasurements) {
                                //   return;
                                // }
                                // expectMeasurements = newMeasurements;
                                if (newChangeCounter >= 10000) {
                                    stopSimulation();
                                }
                                else {
                                    triggerChange();
                                }
                            }, 30);
                        });
                    }
                    root.appendChild(document.createElement('hr'));
                    const testSuiteHolder = document.createElement('div');
                    testSuiteHolder.style.padding = '0.25rem';
                    const testSuiteSelector = document.createElement('select');
                    testSuiteSelector.style.marginRight = '0.5rem';
                    testSuiteSelector.id = 'test-type-selector';
                    tests.forEach(({ title }) => {
                        const option = document.createElement('option');
                        option.value = title;
                        option.innerText = title;
                        testSuiteSelector.appendChild(option);
                    });
                    testSuiteSelector.selectedIndex = tests.findIndex(({ title }) => title === activeTest);
                    testSuiteHolder.appendChild(testSuiteSelector);
                    testSuiteSelector.oninput = () => {
                        activeTest = testSuiteSelector.value;
                    };
                    const testSuiteLabel = document.createElement('label');
                    testSuiteLabel.innerText = 'Benchmark type';
                    testSuiteHolder.setAttribute('for', 'test-type-selector');
                    testSuiteHolder.appendChild(testSuiteLabel);
                    root.appendChild(testSuiteHolder);
                    if (averageLabels && mostRecentLabels) {
                        statLabels = {
                            numEvaluations: numEvalsLbl,
                            average: averageLabels,
                            mostRecent: mostRecentLabels,
                        };
                    }
                }
                else {
                    const apply = (group, val) => {
                        group.serverTotalLbl.innerText = val.serverTotalMs;
                        group.createLocatorLbl.innerText = val.createLocatorMs;
                        group.applyLocatorLbl.innerText = val.applyLocatorMs;
                        group.parseLbl.innerText = val.parseMs;
                        group.rpcLbl.innerText = val.rpcMs;
                        group.attrEvalLbl.innerText = val.attributeEvalMs;
                    };
                    statLabels.numEvaluations.innerText = `${collector.getNumberOfMeasurements()}`;
                    apply(statLabels.average, computeAverage());
                    apply(statLabels.mostRecent, computeIndividual(last));
                }
                // grid.appendChild(document.createTextNode(''));
                // grid.appendChild(document.createTextNode('' + collector.getNumberOfMeasurements()));
                // root.appendChild()
            },
        });
        collector.setOnChange(() => helpWindow.refresh());
    };
    exports.default = displayStatistics;
});
define("ui/popup/displayMainArgsOverrideModal", ["require", "exports", "settings", "ui/create/createModalTitle", "ui/create/showWindow"], function (require, exports, settings_2, createModalTitle_7, showWindow_8) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    settings_2 = __importDefault(settings_2);
    createModalTitle_7 = __importDefault(createModalTitle_7);
    showWindow_8 = __importDefault(showWindow_8);
    const getArgs = () => {
        const re = settings_2.default.getMainArgsOverride();
        if (!re) {
            return re;
        }
        return re.map(item => {
            let str = '';
            let surround = false;
            for (let i = 0; i < item.length; ++i) {
                const ch = item[i];
                switch (ch) {
                    case '"': {
                        surround = true;
                        str = `${str}\\"`;
                        break;
                    }
                    case '\\': {
                        str = `${str}\\\\`;
                        break;
                    }
                    case '\n': {
                        str = `${str}\\n`;
                        break;
                    }
                    case ' ': {
                        surround = true;
                        // Fall through
                    }
                    default: {
                        str = `${str}${ch}`;
                        break;
                    }
                }
            }
            if (surround) {
                return `"${str}"`;
            }
            return str;
        }).join(' ');
    };
    const setArgs = (raw, onError) => {
        let args = [];
        let buf = null;
        let parsePos = 0;
        const commit = () => {
            if (buf !== null) {
                args.push(buf);
            }
            buf = null;
        };
        const getLineColFromStartToPos = (pos) => {
            let line = 1;
            let col = 0;
            for (let i = 0; i < pos; ++i) {
                if (raw[i] == '\n') {
                    ++line;
                    col = 0;
                }
                else {
                    // This is slightly incorrect for multibyte characters, TODO support properly (if worth the effort)
                    ++col;
                }
            }
            return { line, col };
        };
        const parseEscaped = () => {
            const next = raw[parsePos++];
            switch (next) {
                case '\\': {
                    buf = `${buf !== null && buf !== void 0 ? buf : ''}\\`;
                    break;
                }
                case '"': {
                    buf = `${buf !== null && buf !== void 0 ? buf : ''}"`;
                    break;
                }
                case 'n': {
                    buf = `${buf !== null && buf !== void 0 ? buf : ''}\n`;
                    break;
                }
                default: {
                    const loc = getLineColFromStartToPos(parsePos - 1);
                    onError(loc.line, loc.col, `Unexpected escape character, expected '"', '\\' or 'n' after this backslash`);
                    throw new Error(`Unexpected escape character`);
                }
            }
        };
        const parseQuoted = () => {
            const start = parsePos;
            buf = '';
            while (parsePos < raw.length) {
                const ch = raw[parsePos++];
                switch (ch) {
                    case '"': {
                        commit();
                        return;
                    }
                    case '\\': {
                        parseEscaped();
                        break;
                    }
                    case '\n': {
                        const loc = getLineColFromStartToPos(start);
                        onError(loc.line, loc.col, `Unterminated string, if you want newlines in the string then write '\\n'`);
                        throw new Error('Unterminated string');
                    }
                    default: {
                        buf = `${buf}${ch}`;
                        break;
                    }
                }
            }
            const loc = getLineColFromStartToPos(start);
            onError(loc.line, loc.col, `Unterminated string`);
            throw new Error('Unterminated string');
        };
        const parseOuter = () => {
            while (parsePos < raw.length) {
                const ch = raw[parsePos++];
                switch (ch) {
                    case '"': {
                        commit();
                        parseQuoted();
                        break;
                    }
                    case '\\': {
                        parseEscaped();
                        break;
                    }
                    case ' ': // Fall through
                    case '\n': {
                        commit();
                        break;
                    }
                    default: {
                        buf = `${buf !== null && buf !== void 0 ? buf : ''}${ch}`;
                        break;
                    }
                }
            }
        };
        parseOuter();
        commit();
        // console.log('done parsing @', parsePos)
        settings_2.default.setMainArgsOverride(args);
    };
    const displayMainArgsOverrideModal = (onClose, onChange) => {
        const windowInstance = (0, showWindow_8.default)({
            render: (root) => {
                while (root.firstChild) {
                    root.firstChild.remove();
                }
                root.appendChild((0, createModalTitle_7.default)({
                    renderLeft: (container) => {
                        const headType = document.createElement('span');
                        headType.classList.add('syntax-stype');
                        headType.innerText = `Main arg override editor`;
                        container.appendChild(headType);
                    },
                    onClose: () => close(),
                }).element);
                const elem = document.createElement('div');
                // elem.style.minHeight = '16rem';
                elem.style = `
      width: 100%;
      height: 100%;
      `;
                const liveView = document.createElement('div');
                liveView.style.display = 'flex';
                liveView.style.padding = '0.25rem';
                liveView.style.flexDirection = 'row';
                liveView.style.maxWidth = '100%';
                liveView.style.flexWrap = 'wrap';
                // liveView.style.maxHeight = '7.8rem';
                liveView.style.overflow = 'scroll';
                liveView.style.rowGap = '4px';
                const refreshLiveView = () => {
                    var _a;
                    liveView.innerHTML = '';
                    const addTn = (str) => {
                        const tn = document.createElement('span');
                        tn.innerText = str;
                        // tn.style.margin = '1px';
                        liveView.appendChild(tn);
                    };
                    // liveView.appendChild(document.createTextNode('tool.main(\n'));
                    addTn('yourtool.main(new String[]{');
                    [...((_a = settings_2.default.getMainArgsOverride()) !== null && _a !== void 0 ? _a : []), '/path/to/file.tmp'].forEach((part, partIdx) => {
                        if (partIdx > 0) {
                            liveView.appendChild(document.createTextNode(', '));
                        }
                        const span = document.createElement('span');
                        span.innerText = part;
                        span.style.whiteSpace = 'pre';
                        span.style.border = '1px solid #888';
                        span.style.marginLeft = '0.125rem';
                        span.style.marginRight = '0.125rem';
                        liveView.appendChild(span);
                    });
                    addTn('})');
                    // liveView.innerText = `tool.main(\n  ${[...(settings.getMainArgsOverride() ?? []), '/path/to/tmp-file'].filter(Boolean).join(',\n  ')})`
                };
                liveView.classList.add('override-main-args-live-view');
                refreshLiveView();
                // elem.classList.add('input-Monaco');
                const editor = window.monaco.editor.create(elem, {
                    value: getArgs(),
                    language: 'plaintext',
                    // theme: 'dark',
                    scrollBeyondLastLine: false,
                    automaticLayout: true,
                    minimap: {
                        enabled: false,
                    },
                    wordWrap: true,
                });
                editor.onDidChangeModelContent(() => {
                    const errs = [];
                    try {
                        setArgs(editor.getValue(), (line, col, msg) => errs.push({ line, col, msg }));
                        refreshLiveView();
                        onChange();
                    }
                    catch (e) {
                        console.warn('Error when parsing user input', e);
                    }
                    window.monaco.editor.setModelMarkers(editor.getModel(), 'override-problems', errs.map(({ line, col, msg }) => ({
                        startLineNumber: line,
                        startColumn: col,
                        endLineNumber: line,
                        endColumn: col + 1,
                        message: msg,
                        severity: 8, // default to 'error' (8)
                    })));
                });
                const wrapper = document.createElement('div');
                wrapper.style = `
      display: flex;
      width: 100%;
      height: 8rem;
      `;
                wrapper.appendChild(elem);
                root.appendChild(wrapper);
                const explanation = document.createElement('p');
                explanation.style.margin = '0';
                explanation.style.padding = '0.25rem';
                explanation.innerText = [
                    'Example invocation with current override value:'
                ].join('\n');
                root.appendChild(explanation);
                root.appendChild(liveView);
            },
            rootStyle: `
    min-width: 12rem;
    min-height: 4rem;
    max-width: 80vw;
    max-height: 80vh;
    overflow: auto;
    `,
            resizable: true,
        });
        const close = () => {
            onClose();
            windowInstance.remove();
        };
        return {
            forceClose: () => close(),
        };
    };
    exports.default = displayMainArgsOverrideModal;
});
define("ui/configureCheckboxWithHiddenButton", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const configureCheckboxWithHiddenButton = (checkbox, button, onCheckboxChange, displayEditor, getButtonDecoration) => {
        checkbox.checked = getButtonDecoration() !== null;
        let overrideEditorCloser = null;
        const refreshButton = () => {
            const decoration = getButtonDecoration();
            if (decoration === null) {
                button.style.display = 'none';
            }
            else {
                button.style.display = 'inline-block';
                button.innerText = decoration;
            }
        };
        refreshButton();
        button.onclick = () => {
            button.disabled = true;
            const { forceClose } = displayEditor(() => {
                button.disabled = false;
                overrideEditorCloser = null;
            });
            overrideEditorCloser = () => forceClose();
        };
        checkbox.oninput = (e) => {
            overrideEditorCloser === null || overrideEditorCloser === void 0 ? void 0 : overrideEditorCloser();
            onCheckboxChange(checkbox.checked);
        };
        return { refreshButton };
    };
    exports.default = configureCheckboxWithHiddenButton;
});
define("ui/UIElements", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    class UIElements {
        // Use lazy getters since the dom elements haven't been loaded
        // by the time this script initially runs.
        get positionRecoverySelector() { return document.getElementById('control-position-recovery-strategy'); }
        get positionRecoveryHelpButton() { return document.getElementById('control-position-recovery-strategy-help'); }
        get astCacheStrategySelector() { return document.getElementById('ast-cache-strategy'); }
        get astCacheStrategyHelpButton() { return document.getElementById('control-ast-cache-strategy-help'); }
        get syntaxHighlightingSelector() { return document.getElementById('syntax-highlighting'); }
        get syntaxHighlightingHelpButton() { return document.getElementById('control-syntax-highlighting-help'); }
        get shouldOverrideMainArgsCheckbox() { return document.getElementById('control-should-override-main-args'); }
        get configureMainArgsOverrideButton() { return document.getElementById('configure-main-args'); }
        get mainArgsOverrideHelpButton() { return document.getElementById('main-args-override-help'); }
        get shouldCustomizeFileSuffixCheckbox() { return document.getElementById('control-customize-file-suffix'); }
        get configureCustomFileSuffixButton() { return document.getElementById('customize-file-suffix'); }
        get customFileSuffixHelpButton() { return document.getElementById('customize-file-suffix-help'); }
        get showAllPropertiesCheckbox() { return document.getElementById('control-show-all-properties'); }
        get showAllPropertiesHelpButton() { return document.getElementById('show-all-properties-help'); }
        get generalHelpButton() { return document.getElementById('display-help'); }
        get captureStdoutCheckbox() { return document.getElementById('control-capture-stdout'); }
        get duplicateProbeCheckbox() { return document.getElementById('control-duplicate-probe-on-attr'); }
        get darkModeCheckbox() { return document.getElementById('control-dark-mode'); }
        get displayStatisticsButton() { return document.getElementById('display-statistics'); }
        get versionInfo() { return document.getElementById('version'); }
    }
    exports.default = UIElements;
});
define("ui/showVersionInfo", ["require", "exports", "model/repositoryUrl"], function (require, exports, repositoryUrl_2) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    repositoryUrl_2 = __importDefault(repositoryUrl_2);
    const showVersionInfo = (elem, ourHash, ourClean, ourBuildTime, wsHandler) => {
        const innerPrefix = `Version: ${ourHash}${ourClean ? '' : ' [DEV]'}`;
        if (ourBuildTime !== undefined) {
            const d = new Date(ourBuildTime * 1000);
            elem.innerText = `${innerPrefix}, ${d.toLocaleDateString()}`;
        }
        else {
            elem.innerText = innerPrefix;
        }
        if ('false' === localStorage.getItem('enable-version-checker')) {
            // In case somebody wants to stay on an old version for a long time,
            // then the "new version available" popup can become annoying.
            // This flag allows you to disable version checking.
            // Don't tell anybody about it though! 🤫 We want people staying updated.
            return;
        }
        if (!ourClean) {
            // No need to poll for new versions, 'DEV' label already shown
            return;
        }
        const pollNewVersion = async () => {
            var _a;
            let fetched;
            try {
                fetched = (_a = (await wsHandler.sendRpc({
                    type: 'fetch',
                    url: `${repositoryUrl_2.default}/-/raw/master/VERSION`
                }))) === null || _a === void 0 ? void 0 : _a.result;
            }
            catch (e) {
                console.warn('Error when fetching version', e);
                return 'done';
            }
            if (!fetched) {
                console.warn('Unexpected response:', fetched);
                return 'done';
            }
            const hash = fetched.trim().split('\n').slice(-1)[0];
            console.log('Newest version hash:', hash);
            if (ourHash === hash) {
                // Status is clean.. for now.
                // Check again (much) later
                return 'again';
            }
            const a = document.createElement('a');
            a.href = `${repositoryUrl_2.default}/-/blob/master/code-prober.jar`;
            a.target = '_blank';
            a.text = 'New version available';
            elem.appendChild(document.createElement('br'));
            elem.appendChild(a);
            return 'done';
        };
        (async () => {
            while (true) {
                const status = await pollNewVersion();
                if (status === 'done') {
                    return;
                }
                // Sleep for 12 hours..
                // In the unlikely (but flattering!) scenario that somebody keeps the tool
                // active on their computer for several days in a row, we will re-check version
                // info periodically so they don't miss new releases.
                await (new Promise((res) => setTimeout(res, 12 * 60 * 60 * 1000)));
            }
        })()
            .catch(err => console.warn('Error when polling for new versions', err));
    };
    exports.default = showVersionInfo;
});
define("main", ["require", "exports", "ui/addConnectionCloseNotice", "ui/popup/displayProbeModal", "ui/popup/displayRagModal", "ui/popup/displayHelp", "ui/popup/displayAttributeModal", "settings", "model/StatisticsCollectorImpl", "ui/popup/displayStatistics", "ui/popup/displayMainArgsOverrideModal", "model/syntaxHighlighting", "createWebsocketHandler", "ui/configureCheckboxWithHiddenButton", "ui/UIElements", "ui/showVersionInfo"], function (require, exports, addConnectionCloseNotice_1, displayProbeModal_3, displayRagModal_1, displayHelp_3, displayAttributeModal_4, settings_3, StatisticsCollectorImpl_1, displayStatistics_1, displayMainArgsOverrideModal_1, syntaxHighlighting_2, createWebsocketHandler_1, configureCheckboxWithHiddenButton_1, UIElements_1, showVersionInfo_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    addConnectionCloseNotice_1 = __importDefault(addConnectionCloseNotice_1);
    displayProbeModal_3 = __importDefault(displayProbeModal_3);
    displayRagModal_1 = __importDefault(displayRagModal_1);
    displayHelp_3 = __importDefault(displayHelp_3);
    displayAttributeModal_4 = __importDefault(displayAttributeModal_4);
    settings_3 = __importDefault(settings_3);
    StatisticsCollectorImpl_1 = __importDefault(StatisticsCollectorImpl_1);
    displayStatistics_1 = __importDefault(displayStatistics_1);
    displayMainArgsOverrideModal_1 = __importDefault(displayMainArgsOverrideModal_1);
    createWebsocketHandler_1 = __importDefault(createWebsocketHandler_1);
    configureCheckboxWithHiddenButton_1 = __importDefault(configureCheckboxWithHiddenButton_1);
    UIElements_1 = __importDefault(UIElements_1);
    showVersionInfo_1 = __importDefault(showVersionInfo_1);
    window.clearUserSettings = () => {
        settings_3.default.set({});
        location.reload();
    };
    const uiElements = new UIElements_1.default();
    const doMain = (wsPort) => {
        let getLocalState = () => '';
        let updateSpanHighlight = (span) => { };
        const performRpcQuery = (handler, props) => handler.sendRpc({
            posRecovery: uiElements.positionRecoverySelector.value,
            cache: uiElements.astCacheStrategySelector.value,
            type: 'query',
            text: getLocalState(),
            stdout: settings_3.default.shouldCaptureStdio(),
            query: props,
            mainArgs: settings_3.default.getMainArgsOverride(),
            tmpSuffix: settings_3.default.getCurrentFileSuffix(),
        });
        const onChangeListeners = {};
        const probeWindowStateSavers = {};
        const triggerWindowSave = () => {
            const states = [];
            Object.values(probeWindowStateSavers).forEach(v => v(states));
            settings_3.default.setProbeWindowStates(states);
        };
        const notifyLocalChangeListeners = (adjusters) => {
            // Short timeout to easier see changes happening. Remove in prod
            // setTimeout(() => {
            Object.values(onChangeListeners).forEach(l => l(adjusters));
            triggerWindowSave();
            // }, 500);
        };
        function initEditor(editorType) {
            if (!location.search) {
                location.search = "editor=" + editorType;
                return;
            }
            document.body.setAttribute('data-theme-light', `${settings_3.default.isLightTheme()}`);
            const wsHandler = (0, createWebsocketHandler_1.default)(new WebSocket(`ws://${location.hostname}:${wsPort}`), addConnectionCloseNotice_1.default);
            const rootElem = document.getElementById('root');
            wsHandler.on('init', ({ version: { clean, hash, buildTimeSeconds } }) => {
                console.log('got version:', clean, hash, buildTimeSeconds);
                rootElem.style.display = "grid";
                const onChange = (newValue, adjusters) => {
                    settings_3.default.setEditorContents(newValue);
                    notifyLocalChangeListeners(adjusters);
                };
                let setLocalState = (value) => { };
                let markText = () => ({});
                let registerStickyMarker = (initialSpan) => ({
                    getSpan: () => initialSpan,
                    remove: () => { },
                });
                const darkModeCheckbox = uiElements.darkModeCheckbox;
                darkModeCheckbox.checked = !settings_3.default.isLightTheme();
                const defineThemeToggler = (cb) => {
                    darkModeCheckbox.oninput = (e) => {
                        let lightTheme = !darkModeCheckbox.checked;
                        settings_3.default.setLightTheme(lightTheme);
                        document.body.setAttribute('data-theme-light', `${lightTheme}`);
                        cb(lightTheme);
                    };
                    cb(settings_3.default.isLightTheme());
                };
                let syntaxHighlightingToggler;
                if (window.definedEditors[editorType]) {
                    const { preload, init, } = window.definedEditors[editorType];
                    window.loadPreload(preload, () => {
                        var _a;
                        const res = init((_a = settings_3.default.getEditorContents()) !== null && _a !== void 0 ? _a : `// Hello World!\n// Write some code in this field, then right click and select 'Create Probe' to get started\n\n`, onChange, settings_3.default.getSyntaxHighlighting());
                        setLocalState = res.setLocalState || setLocalState;
                        getLocalState = res.getLocalState || getLocalState;
                        updateSpanHighlight = res.updateSpanHighlight || updateSpanHighlight;
                        registerStickyMarker = res.registerStickyMarker || registerStickyMarker;
                        markText = res.markText || markText;
                        if (res.themeToggler) {
                            defineThemeToggler(res.themeToggler);
                        }
                        syntaxHighlightingToggler = res.syntaxHighlightingToggler;
                    });
                }
                else {
                    // Throw out to editor selection screen
                    location.search = '';
                }
                const activeMarkers = [];
                const probeMarkers = {};
                const updateMarkers = () => {
                    activeMarkers.forEach(m => { var _a; return (_a = m === null || m === void 0 ? void 0 : m.clear) === null || _a === void 0 ? void 0 : _a.call(m); });
                    activeMarkers.length = 0;
                    const deduplicator = new Set();
                    const filteredAddMarker = (severity, start, end, msg) => {
                        const uniqId = [severity, start, end, msg].join(' | ');
                        ;
                        if (deduplicator.has(uniqId)) {
                            return;
                        }
                        deduplicator.add(uniqId);
                        const lineStart = (start >>> 12);
                        const colStart = start & 0xFFF;
                        const lineEnd = (end >>> 12);
                        const colEnd = end & 0xFFF;
                        activeMarkers.push(markText({ severity, lineStart, colStart, lineEnd, colEnd, message: msg }));
                    };
                    Object.values(probeMarkers).forEach(arr => arr.forEach(({ severity, errStart, errEnd, msg }) => filteredAddMarker(severity, errStart, errEnd, msg)));
                };
                const setupSimpleCheckbox = (input, initial, update) => {
                    input.checked = initial;
                    input.oninput = () => { update(input.checked); notifyLocalChangeListeners(); };
                };
                setupSimpleCheckbox(uiElements.captureStdoutCheckbox, settings_3.default.shouldCaptureStdio(), cb => settings_3.default.setShouldCaptureStdio(cb));
                setupSimpleCheckbox(uiElements.duplicateProbeCheckbox, settings_3.default.shouldDuplicateProbeOnAttrClick(), cb => settings_3.default.setShouldDuplicateProbeOnAttrClick(cb));
                setupSimpleCheckbox(uiElements.showAllPropertiesCheckbox, settings_3.default.shouldShowAllProperties(), cb => settings_3.default.setShouldShowAllProperties(cb));
                const setupSimpleSelector = (input, initial, update) => {
                    input.value = initial;
                    input.oninput = () => { update(input.value); notifyLocalChangeListeners(); };
                };
                setupSimpleSelector(uiElements.astCacheStrategySelector, settings_3.default.getAstCacheStrategy(), cb => settings_3.default.setAstCacheStrategy(cb));
                setupSimpleSelector(uiElements.positionRecoverySelector, settings_3.default.getPositionRecoveryStrategy(), cb => settings_3.default.setPositionRecoveryStrategy(cb));
                const syntaxHighlightingSelector = uiElements.syntaxHighlightingSelector;
                syntaxHighlightingSelector.innerHTML = '';
                (0, syntaxHighlighting_2.getAvailableLanguages)().forEach(({ id, alias }) => {
                    const option = document.createElement('option');
                    option.value = id;
                    option.innerText = alias;
                    syntaxHighlightingSelector.appendChild(option);
                });
                setupSimpleSelector(syntaxHighlightingSelector, settings_3.default.getSyntaxHighlighting(), cb => {
                    settings_3.default.setSyntaxHighlighting(syntaxHighlightingSelector.value);
                    syntaxHighlightingToggler === null || syntaxHighlightingToggler === void 0 ? void 0 : syntaxHighlightingToggler(settings_3.default.getSyntaxHighlighting());
                });
                const overrideCfg = (0, configureCheckboxWithHiddenButton_1.default)(uiElements.shouldOverrideMainArgsCheckbox, uiElements.configureMainArgsOverrideButton, (checked) => {
                    settings_3.default.setMainArgsOverride(checked ? [] : null);
                    overrideCfg.refreshButton();
                    notifyLocalChangeListeners();
                }, onClose => (0, displayMainArgsOverrideModal_1.default)(onClose, () => {
                    overrideCfg.refreshButton();
                    notifyLocalChangeListeners();
                }), () => {
                    const overrides = settings_3.default.getMainArgsOverride();
                    return overrides === null ? null : `Edit (${overrides.length})`;
                });
                const suffixCfg = (0, configureCheckboxWithHiddenButton_1.default)(uiElements.shouldCustomizeFileSuffixCheckbox, uiElements.configureCustomFileSuffixButton, (checked) => {
                    settings_3.default.setCustomFileSuffix(checked ? settings_3.default.getCurrentFileSuffix() : null);
                    suffixCfg.refreshButton();
                    notifyLocalChangeListeners();
                }, onClose => {
                    const newVal = prompt('Enter new suffix', settings_3.default.getCurrentFileSuffix());
                    if (newVal !== null) {
                        settings_3.default.setCustomFileSuffix(newVal);
                        suffixCfg.refreshButton();
                        notifyLocalChangeListeners();
                    }
                    onClose();
                    return { forceClose: () => { }, };
                }, () => {
                    const overrides = settings_3.default.getCustomFileSuffix();
                    return overrides === null ? null : `Edit (${settings_3.default.getCurrentFileSuffix()})`;
                });
                const statCollectorImpl = new StatisticsCollectorImpl_1.default();
                if (location.search.includes('debug=true')) {
                    document.getElementById('secret-debug-panel').style.display = 'block';
                }
                const modalEnv = {
                    performRpcQuery: (args) => performRpcQuery(wsHandler, args),
                    probeMarkers, onChangeListeners, updateMarkers,
                    getLocalState: () => getLocalState(),
                    captureStdout: () => uiElements.captureStdoutCheckbox.checked,
                    duplicateOnAttr: () => uiElements.duplicateProbeCheckbox.checked,
                    registerStickyMarker: (...args) => registerStickyMarker(...args),
                    updateSpanHighlight: (hl) => updateSpanHighlight(hl),
                    probeWindowStateSavers,
                    triggerWindowSave,
                    statisticsCollector: statCollectorImpl,
                    currentlyLoadingModals: new Set(),
                };
                (0, showVersionInfo_1.default)(uiElements.versionInfo, hash, clean, buildTimeSeconds, wsHandler);
                window.displayHelp = (type) => {
                    const common = (type, button) => (0, displayHelp_3.default)(type, disabled => button.disabled = disabled);
                    switch (type) {
                        case "general": return common('general', uiElements.generalHelpButton);
                        case 'recovery-strategy': return common('recovery-strategy', uiElements.positionRecoveryHelpButton);
                        case "ast-cache-strategy": return common('ast-cache-strategy', uiElements.astCacheStrategyHelpButton);
                        case "probe-statistics": return (0, displayStatistics_1.default)(statCollectorImpl, disabled => uiElements.displayStatisticsButton.disabled = disabled, newContents => setLocalState(newContents), () => modalEnv.currentlyLoadingModals.size > 0);
                        case 'syntax-highlighting': return common('syntax-highlighting', uiElements.syntaxHighlightingHelpButton);
                        case 'main-args-override': return common('main-args-override', uiElements.mainArgsOverrideHelpButton);
                        case 'customize-file-suffix': return common('customize-file-suffix', uiElements.customFileSuffixHelpButton);
                        case 'show-all-properties': return common('show-all-properties', uiElements.showAllPropertiesHelpButton);
                        default: return console.error('Unknown help type', type);
                    }
                };
                setTimeout(() => {
                    try {
                        settings_3.default.getProbeWindowStates().forEach((state) => {
                            (0, displayProbeModal_3.default)(modalEnv, state.modalPos, state.locator, state.attr);
                        });
                    }
                    catch (e) {
                        console.warn('Invalid probe window state?', e);
                    }
                }, 300); // JUUUUUUUST in case the stored window state causes issues, this 300ms timeout allows people to click the 'clear state' button
                window.RagQuery = (line, col, autoSelectRoot) => {
                    if (autoSelectRoot) {
                        const node = { type: '<ROOT>', start: (line << 12) + col - 1, end: (line << 12) + col + 1, depth: 0 };
                        (0, displayAttributeModal_4.default)(modalEnv, null, { result: node, steps: [] });
                    }
                    else {
                        (0, displayRagModal_1.default)(modalEnv, line, col);
                    }
                };
            });
            wsHandler.on('refresh', () => {
                notifyLocalChangeListeners();
            });
        }
        initEditor('Monaco');
    };
    window.initCodeProber = () => {
        (async () => {
            const socketRes = await fetch('/WS_PORT');
            if (socketRes.status !== 200) {
                throw new Error(`Unexpected status code when fetch websocket port ${socketRes.status}`);
            }
            const txt = await socketRes.text();
            const port = Number.parseInt(txt, 10);
            if (Number.isNaN(port)) {
                throw new Error(`Bad websocket response text ${txt}`);
            }
            return doMain(port);
        })().catch(err => {
            console.warn('Failed fetching websocket port, falling back to 8080', err);
            doMain(8080);
        });
    };
});
// const isLightTheme = () => localStorage.getItem('editor-theme-light') === 'true';
// const setIsLightTheme = (light: boolean) => {
//   localStorage.setItem('editor-theme-light', `${light}`);
//   document.body.setAttribute('data-theme-light', `${light}`);
// }
// export { isLightTheme, setIsLightTheme };
const startEndToSpan = (start, end) => ({
    lineStart: (start >>> 12),
    colStart: start & 0xFFF,
    lineEnd: (end >>> 12),
    colEnd: end & 0xFFF,
});
