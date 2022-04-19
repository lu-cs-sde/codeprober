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
            mouse.x = e.screenX;
            mouse.y = e.screenY;
            onBegin();
        };
        const onMouseMove = (e) => {
            if (mouse.down) {
                let dx = e.screenX - mouse.x;
                let dy = e.screenY - mouse.y;
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
        console.log('new and shiny showModal..', args);
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
        contentRoot.classList.add('HELLO-FIND-ME');
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
                            console.log('container.onmousedown..');
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
                    console.log('window.onmousedown');
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
define("ui/popup/displayArgModal", ["require", "exports", "ui/create/createModalTitle", "ui/create/showWindow", "ui/popup/displayProbeModal", "ui/popup/formatAttr"], function (require, exports, createModalTitle_1, showWindow_2, displayProbeModal_1, formatAttr_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createModalTitle_1 = __importDefault(createModalTitle_1);
    showWindow_2 = __importDefault(showWindow_2);
    displayProbeModal_1 = __importDefault(displayProbeModal_1);
    formatAttr_1 = __importStar(formatAttr_1);
    const displayArgModal = (env, modalPos, locator, attr) => {
        const cleanup = () => {
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
      min-width: 20rem;
      min-height: 4rem;
    `,
            render: (root) => {
                root.appendChild(createTitle().element);
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
                    argValues[argIdx] = arg.value || '';
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
                    const inp = document.createElement('input');
                    inp.classList.add('attr-arg-input-text');
                    // inp.style.marginLeft = '1rem';
                    inp.type = 'text';
                    inp.value = argValues[argIdx];
                    // inp.id = inpId;
                    inp.placeholder = arg.name;
                    inp.oninput = () => {
                        argValues[argIdx] = inp.value;
                    };
                    inp.onkeydown = (e) => {
                        if (e.key === 'Enter') {
                            proceed();
                        }
                    };
                    attrList.appendChild(inp);
                    if (argIdx === 0) {
                        setTimeout(() => inp.focus(), 100);
                    }
                    // attrList.appendChild(argRow);
                });
                root.appendChild(attrList);
                const submitWrapper = document.createElement('div');
                submitWrapper.style.marginTop = '0.5rem';
                const submit = document.createElement('input');
                submit.type = 'submit';
                submit.classList.add('attr-list-submit');
                submit.style.width = '90%';
                submit.style.display = 'block';
                // submit.style.margin = 'auto';
                submit.style.margin = '0.5rem auto';
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
define("ui/popup/displayAttributeModal", ["require", "exports", "ui/create/createLoadingSpinner", "ui/create/createModalTitle", "ui/popup/displayProbeModal", "ui/create/showWindow", "ui/popup/displayArgModal", "ui/popup/formatAttr", "ui/create/createTextSpanIndicator"], function (require, exports, createLoadingSpinner_1, createModalTitle_2, displayProbeModal_2, showWindow_3, displayArgModal_1, formatAttr_2, createTextSpanIndicator_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createLoadingSpinner_1 = __importDefault(createLoadingSpinner_1);
    createModalTitle_2 = __importDefault(createModalTitle_2);
    displayProbeModal_2 = __importDefault(displayProbeModal_2);
    showWindow_3 = __importDefault(showWindow_3);
    displayArgModal_1 = __importDefault(displayArgModal_1);
    formatAttr_2 = __importDefault(formatAttr_2);
    createTextSpanIndicator_1 = __importDefault(createTextSpanIndicator_1);
    const displayAttributeModal = (env, modalPos, locator) => {
        let filter = '';
        let attrs = null;
        let showErr = false;
        const popup = (0, showWindow_3.default)({
            pos: modalPos,
            rootStyle: `
      min-width: 16rem;
      min-height: 8rem;
      max-height: 32rem;
    `,
            render: (root, cancelToken) => {
                while (root.firstChild)
                    root.firstChild.remove();
                // root.innerText = 'Loading..';
                root.appendChild((0, createModalTitle_2.default)({
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
                        container.appendChild((0, createTextSpanIndicator_1.default)({
                            span: startEndToSpan(locator.result.start, locator.result.end),
                            marginLeft: true,
                            onHover: on => env.updateSpanHighlight(on ? startEndToSpan(locator.result.start, locator.result.end) : null),
                        }));
                    },
                    onClose: () => {
                        popup.remove();
                    },
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
                    setTimeout(() => filterInput.focus(), 50);
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
                        const reg = filter ? new RegExp(`.*${[...filter].map(part => part.trim()).filter(Boolean).join('.*')}.*`, 'i') : null;
                        const match = (attr) => {
                            if (!reg) {
                                return false;
                            }
                            return reg.test(attr.name);
                        };
                        const matches = attrs.filter(match);
                        const misses = attrs.filter(a => !match(a));
                        const showProbe = (attr) => {
                            popup.remove();
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
                            node.innerText = (0, formatAttr_2.default)(attr);
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
                    setTimeout(() => popup.remove(), 1000);
                }
            },
        });
        env.performRpcQuery({
            type: 'query',
            // ...span,
            text: env.getLocalState(),
            query: {
                nodeType: locator.result.type,
                attr: {
                    name: 'pastaAttrs'
                },
                locator: locator,
            },
        })
            .then((result) => {
            // if (cancelToken.cancelled) { return; }
            const parsed = JSON.parse(result).pastaAttrs;
            if (!parsed) {
                throw new Error('Unexpected response body "' + result + '"');
            }
            filter = '';
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
            // attrs = [...new Set(parsed)];
            popup.refresh();
        })
            .catch(err => {
            console.warn('UserPA err:', err);
            showErr = true;
            popup.refresh();
        });
    };
    exports.default = displayAttributeModal;
});
define("ui/popup/displayProbeModal", ["require", "exports", "ui/create/createLoadingSpinner", "ui/create/createModalTitle", "ui/create/createTextSpanIndicator", "ui/popup/displayAttributeModal", "ui/create/showWindow", "ui/create/registerOnHover", "ui/popup/formatAttr", "ui/popup/displayArgModal"], function (require, exports, createLoadingSpinner_2, createModalTitle_3, createTextSpanIndicator_2, displayAttributeModal_1, showWindow_4, registerOnHover_2, formatAttr_3, displayArgModal_2) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createLoadingSpinner_2 = __importDefault(createLoadingSpinner_2);
    createModalTitle_3 = __importDefault(createModalTitle_3);
    createTextSpanIndicator_2 = __importDefault(createTextSpanIndicator_2);
    displayAttributeModal_1 = __importDefault(displayAttributeModal_1);
    showWindow_4 = __importDefault(showWindow_4);
    registerOnHover_2 = __importDefault(registerOnHover_2);
    formatAttr_3 = __importDefault(formatAttr_3);
    displayArgModal_2 = __importDefault(displayArgModal_2);
    const displayProbeModal = (env, modalPos, locator, attr) => {
        console.log('dPM, env:', env);
        const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
        const localErrors = [];
        env.probeMarkers[queryId] = localErrors;
        // const stickyMarker = env.registerStickyMarker(span)
        const cleanup = () => {
            delete env.onChangeListeners[queryId];
            delete env.probeMarkers[queryId];
            delete env.probeWindowStateSavers[queryId];
            env.triggerWindowSave();
            if (localErrors.length > 0) {
                env.updateMarkers();
            }
            // stickyMarker.remove();
        };
        let copyBody = [];
        const createTitle = () => {
            return (0, createModalTitle_3.default)({
                extraActions: [
                    {
                        title: 'Duplicate window',
                        invoke: () => {
                            const pos = queryWindow.getPos();
                            displayProbeModal(env, { x: pos.x + 10, y: pos.y + 10 }, JSON.parse(JSON.stringify(locator)), attr);
                        },
                    },
                    {
                        title: 'Copy contents to clipboard',
                        invoke: () => {
                            console.log('todo');
                            navigator.clipboard.writeText(copyBody.map(line => typeof line === 'string' ? line : JSON.stringify(line, null, 2)).join('\n'));
                        }
                    }
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
                                default: {
                                    console.warn('Unsure of how to render', arg.type);
                                    headAttr.appendChild(document.createTextNode(arg.value));
                                    break;
                                }
                            }
                        });
                        headAttr.appendChild(document.createTextNode(`)`));
                    }
                    headAttr.onmousedown = (e) => { e.stopPropagation(); };
                    headAttr.onclick = (e) => {
                        if (env.duplicateOnAttr()) {
                            (0, displayAttributeModal_1.default)(env, null, JSON.parse(JSON.stringify(locator)));
                        }
                        else {
                            queryWindow.remove();
                            cleanup();
                            (0, displayAttributeModal_1.default)(env, queryWindow.getPos(), locator);
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
                    container.appendChild((0, createTextSpanIndicator_2.default)({
                        span: startEndToSpan(locator.result.start, locator.result.end),
                        marginLeft: true,
                        onHover: on => env.updateSpanHighlight(on ? startEndToSpan(locator.result.start, locator.result.end) : null),
                    }));
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
        const queryWindow = (0, showWindow_4.default)({
            pos: modalPos,
            rootStyle: `
      min-width: 20rem;
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
                env.performRpcQuery({
                    type: 'query',
                    // ...span,
                    text: env.getLocalState(),
                    stdout: env.captureStdout(),
                    query: {
                        nodeType: locator.result.type,
                        attr,
                        locator,
                        // id: {
                        //   root: {
                        //     type: type,
                        //     start: (span.lineStart << 12) + span.colStart,
                        //     end: (span.lineEnd << 12) + span.colEnd,
                        //   },
                        //   steps: [],
                        // },
                    },
                })
                    .then((res) => {
                    const parsed = JSON.parse(res);
                    const body = parsed.body;
                    copyBody = body;
                    loading = false;
                    if (refreshOnDone) {
                        refreshOnDone = false;
                        queryWindow.refresh();
                    }
                    if (cancelToken.cancelled) {
                        return;
                    }
                    while (root.firstChild)
                        root.removeChild(root.firstChild);
                    // const info = {
                    //   matchPos: { lineStart, colStart, lineEnd, colEnd }
                    // };
                    let refreshMarkers = localErrors.length > 0;
                    localErrors.length = 0;
                    console.log('probe errors:', parsed.errors);
                    parsed.errors.forEach(({ severity, start: errStart, end: errEnd, msg }) => {
                        localErrors.push({ severity, errStart, errEnd, msg });
                    });
                    if (parsed.locator) {
                        refreshMarkers = true;
                        locator = parsed.locator;
                    }
                    // if (parsed.matchStart !== undefined) {
                    //   const num = +parsed.matchStart;
                    //   span.lineStart = num >>> 12;
                    //   span.colStart = num & 0xFFF;
                    // }
                    // if (parsed.matchEnd !== undefined) {
                    //   const num = +parsed.matchEnd;
                    //   span.lineEnd = num >>> 12;
                    //   span.colEnd = num & 0xFFF;
                    // }
                    if (refreshMarkers) {
                        console.log('refresh markers!! local:', localErrors);
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
                                    console.log('node line:', JSON.stringify(line, null, 2));
                                    const container = document.createElement('div');
                                    const span = {
                                        lineStart: (start >>> 12), colStart: (start & 0xFFF),
                                        lineEnd: (end >>> 12), colEnd: (end & 0xFFF),
                                    };
                                    container.appendChild((0, createTextSpanIndicator_2.default)({
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
                                    (0, registerOnHover_2.default)(container, on => {
                                        env.updateSpanHighlight(on ? span : null);
                                    });
                                    container.onmousedown = (e) => {
                                        e.stopPropagation();
                                    };
                                    container.onclick = () => {
                                        (0, displayAttributeModal_1.default)(env, null, line.value);
                                    };
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
                    console.log('ProbeModal RPC catch', err);
                    root.innerHTML = '';
                    root.innerText = 'Failed refreshing query..';
                    setTimeout(() => {
                        queryWindow.remove();
                    }, 1000);
                });
            },
        });
        env.onChangeListeners[queryId] = (adjusters) => {
            if (adjusters) {
                // console.log('onChange', stickyMarker.getSpan());
                // console.log('Adjusted span from:', span);
                adjusters.forEach((adj) => {
                    const adjust = (tal) => {
                        const span = startEndToSpan(tal.start, tal.end);
                        let [ls, cs] = adj(span.lineStart, span.colStart);
                        let [le, ce] = adj(span.lineEnd, span.colEnd);
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
                    adjust(locator.root);
                    adjust(locator.result);
                    locator.steps.forEach((step) => {
                        // todo if step is TaL, adjust it
                        // step.
                    });
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
define("ui/popup/displayRagModal", ["require", "exports", "ui/create/createLoadingSpinner", "ui/create/createModalTitle", "ui/popup/displayAttributeModal", "ui/create/registerOnHover", "ui/create/showWindow"], function (require, exports, createLoadingSpinner_3, createModalTitle_4, displayAttributeModal_2, registerOnHover_3, showWindow_5) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createLoadingSpinner_3 = __importDefault(createLoadingSpinner_3);
    createModalTitle_4 = __importDefault(createModalTitle_4);
    displayAttributeModal_2 = __importDefault(displayAttributeModal_2);
    registerOnHover_3 = __importDefault(registerOnHover_3);
    showWindow_5 = __importDefault(showWindow_5);
    const displayRagModal = (env, line, col) => {
        const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
        const cleanup = () => {
            delete env.onChangeListeners[queryId];
            popup.remove();
        };
        const popup = (0, showWindow_5.default)({
            rootStyle: `
        min-width: 12rem;
        min-height: 4rem;
        max-height: 32rem;
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
                    type: 'Program',
                    start: (line << 12) + col,
                    end: (line << 12) + col
                };
                env.performRpcQuery({
                    type: 'query',
                    lineStart: line,
                    colStart: col,
                    lineEnd: line,
                    colEnd: col,
                    text: env.getLocalState(),
                    query: {
                        nodeType: 'Program',
                        attr: {
                            name: 'pasta_containingSpansAndNodeTypes',
                        },
                        locator: {
                            root: rootProgramLocator,
                            result: rootProgramLocator,
                            steps: []
                        },
                    },
                })
                    .then((result) => {
                    if (cancelToken.cancelled) {
                        return;
                    }
                    while (root.firstChild)
                        root.removeChild(root.firstChild);
                    root.style.minHeight = '4rem';
                    root.appendChild((0, createModalTitle_4.default)({
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
                    const parsed = JSON.parse(result);
                    if (!parsed.spansAndNodeTypes) {
                        throw new Error("Couldn't find expected line in output");
                    }
                    // const parsed = JSON.parse(interestingLine.slice(needle.length)) as string[];
                    const rows = parsed.spansAndNodeTypes.map(({ start, end, type }, entIdx) => {
                        const span = { lineStart: (start >>> 12), colStart: (start & 0xFFF), lineEnd: (end >>> 12), colEnd: (end & 0xFFF) };
                        const node = document.createElement('div');
                        node.classList.add('clickHighlightOnHover');
                        node.style.padding = `0 0.25rem`;
                        if (entIdx !== 0) {
                            node.style.borderTop = '1px solid gray';
                        }
                        node.innerText = `${type}${start === 0 && end === 0 ? ` ⚠️<No position>` : ''}`;
                        (0, registerOnHover_3.default)(node, on => env.updateSpanHighlight(on ? span : null));
                        node.onmousedown = (e) => { e.stopPropagation(); };
                        node.onclick = () => {
                            cleanup();
                            env.updateSpanHighlight(null);
                            const node = {
                                start: (span.lineStart << 12) + span.colStart,
                                end: (span.lineEnd << 12) + span.colEnd,
                                type,
                            };
                            (0, displayAttributeModal_2.default)(env, popup.getPos(), { root: node, result: node, steps: [] });
                        };
                        root.appendChild(node);
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
define("ui/popup/displayHelp", ["require", "exports", "ui/create/createModalTitle", "ui/create/showWindow"], function (require, exports, createModalTitle_5, showWindow_6) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createModalTitle_5 = __importDefault(createModalTitle_5);
    showWindow_6 = __importDefault(showWindow_6);
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
        'general': 'How to use',
        'recovery-strategy': 'Position recovery',
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
                    exampleVisible.appendChild(document.createTextNode('Example: '));
                    add('syntax-modifier', 'syn', 'right');
                    add('syntax-type', 'boolean List');
                    add('syntax-attr', '.pastaVisible', '');
                    add('', '() =', 'right');
                    add('syntax-modifier', 'false', 'false');
                    add('', ';', '');
                }
                const exampleAttrs = document.createElement('div');
                {
                    const add = (...args) => exampleAttrs.appendChild(createSyntaxNode(args[0], args[1], args[2]));
                    exampleAttrs.appendChild(document.createTextNode('Example: '));
                    // syn  Function.pastaAttrs() = Arrays.asList("eval", "references");
                    add('syntax-modifier', 'syn', 'right');
                    add('syntax-type', 'java.util.List<String> Function');
                    add('syntax-attr', '.pastaAttrs', '');
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
                    exampleView.appendChild(document.createTextNode('Example: '));
                    // syn  Function.pastaAttrs() = Arrays.asList("eval", "references");
                    add('syntax-modifier', 'syn', 'right');
                    add('syntax-type', 'Object IntType');
                    add('syntax-attr', '.pastaView', '');
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
    if (value has 'pastaView'): encode(value.pastaView())
    else: output(value.location, value.type)

  if (value is Iterator or Iterable):
    for (entry in value): encode(entry)

  if no case above matched: output(value.toString())
`.trim();
                return [
                    `PASTA : Probe-AST-Attributes 🍝 🤌`,
                    `Right click on some text in the editor to get started`,
                    `There are three magic attributes you may want to add:`,
                    ``,
                    joinElements(`1) '`, createHeader('pastaVisible'), `'. This controls whether or not a node will appear in the "RAG Query" menu.`),
                    `Default: `,
                    `--    false: for 'List' and 'Opt'. Note: this is only default, you can override it.`,
                    `--     true: for all other types`,
                    // `Example: syn boolean List.pastaVisible() = false;`,
                    exampleVisible,
                    ``,
                    joinElements(`2) '`, createHeader('pastaAttrs'), `'. A filter that can be used to specify which attributes should be visible.`),
                    `Default: all public functions with "simple" argument types (String, int, boolean) visible.`,
                    // `Example: syn java.util.List<String> Function.pastaAttrs() = Arrays.asList("eval", "references");`,
                    exampleAttrs,
                    ``,
                    joinElements(`3) '`, createHeader('pastaView'), `'. This controls how a value is printed`),
                    `Default: encodes one or more options in order. In pseudocode:`,
                    viewDefault,
                    exampleView,
                    ``,
                    `Contributions welcome at [url]`,
                    `Version: abc-123`,
                ];
            }
            case 'recovery-strategy': {
                const settingsExplanation = document.createElement('div');
                settingsExplanation.style.display = 'grid';
                settingsExplanation.style.gridTemplateColumns = 'auto auto 1fr';
                settingsExplanation.style.gridColumnGap = '0.5rem';
                [
                    ['Fail', 'don\'t try to recover information'],
                    ['Parent', 'search recursively upwards through parent nodes, using the equivalent of "node.getParent()"'],
                    ['Child', 'search recursively downwards through child nodes, using the equivalent of "node.getChild(0)"'],
                    ['Parent->Child', 'Try "Parent". If no position is found, try "Child".'],
                    ['Child->Parent', 'Try "Child". If no position is found, try "Parent".'],
                    ['Zigzag', 'Similar to "Parent->Child", but only search one step in one direction, then try the other direction, then another step in the first direction, etc. Initially searches one step upwards.'],
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
                    'This editor is built around the idea that all AST nodes have positions, and it is very hard to use for nodes where this isn\'t true',
                    '',
                    'There are two solutions',
                    '',
                    '1) Fix your parser',
                    'Usually position information is missing because of how you structured your parser.',
                    'Maybe you do some sort of desugaring in the parser, and create multiple AST nodes in a single production rule.',
                    'Beaver, for example, will only give a single node position information per production rule, so try to ony create a single node per rule.',
                    '',
                    '2) Use a recovery strategy',
                    'If a node is missing location information, then we can sometimes get it from nearby nodes.',
                    'This setting controls just how we search for information. Which option fits best for you depends on how you built your AST.',
                    'Settings:',
                    settingsExplanation,
                    '',
                    'No strategy guarantees success. If position is missing, it will be marked with "⚠️", and you\'ll likely run into problems when using it',
                    'If you are unsure of what to use, "Zigzag" is usually a pretty good option.',
                ];
            }
        }
    };
    const displayHelp = (type, setHelpButtonDisabled) => {
        setHelpButtonDisabled(true);
        // TODO prevent this if help window already open
        // Maybe disable the help button, re-enable on close?
        const helpWindow = (0, showWindow_6.default)({
            rootStyle: `
      width: 32rem;
      min-height: 12rem;
    `,
            resizable: true,
            render: (root) => {
                root.appendChild((0, createModalTitle_5.default)({
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
define("settings", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    let settingsObj = null;
    const settings = {
        get: () => {
            if (!settingsObj) {
                try {
                    settingsObj = JSON.parse(localStorage.getItem('pasta-settings') || '{}');
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
            localStorage.setItem('pasta-settings', JSON.stringify(settingsObj));
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
        getProbeWindowStates: () => { var _a; return (_a = settings.get().probeWindowStates) !== null && _a !== void 0 ? _a : []; },
        setProbeWindowStates: (probeWindowStates) => settings.set({ ...settings.get(), probeWindowStates }),
    };
    exports.default = settings;
});
define("main", ["require", "exports", "ui/addConnectionCloseNotice", "ui/popup/displayProbeModal", "ui/popup/displayRagModal", "ui/popup/displayHelp", "ui/popup/displayAttributeModal", "settings"], function (require, exports, addConnectionCloseNotice_1, displayProbeModal_3, displayRagModal_1, displayHelp_1, displayAttributeModal_3, settings_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    addConnectionCloseNotice_1 = __importDefault(addConnectionCloseNotice_1);
    displayProbeModal_3 = __importDefault(displayProbeModal_3);
    displayRagModal_1 = __importDefault(displayRagModal_1);
    displayHelp_1 = __importDefault(displayHelp_1);
    displayAttributeModal_3 = __importDefault(displayAttributeModal_3);
    settings_1 = __importDefault(settings_1);
    window.clearPastaSettings = () => {
        settings_1.default.set({});
        location.reload();
    };
    const main = () => {
        function toggleTheme() {
            console.log('Theme not defined for current editor');
        }
        let getLocalState = () => '';
        let updateSpanHighlight = (span) => { };
        let rpcQuerySocket = null;
        let pastaMode = false;
        const rpcHandlers = {};
        const performRpcQuery = (props) => new Promise(async (res, rej) => {
            // await new Promise(w => setTimeout(w, 2000)); // Debug slow connection
            const posRecoverySelect = document.getElementById('control-position-recovery-strategy');
            console.log('sending rpc', props.query);
            const id = Math.floor(Math.random() * Number.MAX_SAFE_INTEGER);
            rpcQuerySocket.send(JSON.stringify({ id, posRecovery: posRecoverySelect.value, ...props }));
            const cleanup = () => delete rpcHandlers[id];
            rpcHandlers[id] = ({ result }) => {
                cleanup();
                res(result);
            };
            setTimeout(() => {
                cleanup();
                rej('Timeout');
            }, 10000);
        });
        const onChangeListeners = {};
        const notifyLocalChangeListeners = (adjusters) => {
            // Short timeout to easier see changes happening. Remove in prod
            // setTimeout(() => {
            Object.values(onChangeListeners).forEach(l => l(adjusters));
            // }, 500);
        };
        function init(editorType) {
            if (!location.search) {
                location.search = "editor=" + editorType;
                return;
            }
            document.body.setAttribute('data-theme-light', `${settings_1.default.isLightTheme()}`);
            document.getElementById('connections').style.display = 'none';
            const socket = new WebSocket(`ws://${location.hostname}:8080`);
            rpcQuerySocket = socket;
            let handlers = {
                rpc: ({ id, ...res }) => {
                    const handler = rpcHandlers[id];
                    if (handler) {
                        delete rpcHandlers[id];
                        handler(res);
                    }
                    else {
                        console.warn('Received RPC response for', id, ', expected one of', Object.keys(rpcHandlers));
                    }
                },
            };
            const rootElem = document.getElementById('root');
            const init = ({ value, parser, version }) => {
                rootElem.style.display = "grid";
                const onChange = (newValue, adjusters) => {
                    settings_1.default.setEditorContents(newValue);
                    if (!pastaMode) {
                        // ++changeCtr;
                        // socket.send(JSON.stringify({
                        //   type: 'change',
                        //   version: changeCtr,
                        //   parser: parserToggler.value,
                        //   str: newValue,
                        //   mode: 'pretty', // [outputToggler.value, modeTail].filter(Boolean).join(':'),
                        // }));
                    }
                    notifyLocalChangeListeners(adjusters);
                };
                // window.foo
                // parserToggler.disabled = false;
                // parserToggler.value = parser;
                let setLocalState = (value) => { };
                let markText = () => ({});
                let registerStickyMarker = (initialSpan) => ({
                    getSpan: () => initialSpan,
                    remove: () => { },
                });
                const darkModeCheckbox = document.getElementById('control-dark-mode');
                darkModeCheckbox.checked = !settings_1.default.isLightTheme();
                const defineThemeToggler = (cb) => {
                    darkModeCheckbox.oninput = (e) => {
                        let lightTheme = !darkModeCheckbox.checked;
                        settings_1.default.setLightTheme(lightTheme);
                        document.body.setAttribute('data-theme-light', `${lightTheme}`);
                        cb(lightTheme);
                    };
                    cb(settings_1.default.isLightTheme());
                    // let lightTheme = MiniEditorUtils.getThemeIsLight();
                    // window.toggleTheme = () => {
                    //   lightTheme = !lightTheme;
                    //   MiniEditorUtils.setThemeIsLight(lightTheme);
                    //   setThemeButton(lightTheme ? '☀️' : '🌛');
                    //   cb(lightTheme);
                    // }
                    // lightTheme = !lightTheme;
                    // window.toggleTheme();
                };
                console.log('definedEditors:', Object.keys(window.definedEditors), '; tag:', editorType);
                if (window.definedEditors[editorType]) {
                    const { preload, init, } = window.definedEditors[editorType];
                    window.loadPreload(preload, () => {
                        const res = init(value, onChange);
                        setLocalState = res.setLocalState || setLocalState;
                        getLocalState = res.getLocalState || getLocalState;
                        updateSpanHighlight = res.updateSpanHighlight || updateSpanHighlight;
                        registerStickyMarker = res.registerStickyMarker || registerStickyMarker;
                        markText = res.markText || markText;
                        if (res.themeToggler) {
                            defineThemeToggler(res.themeToggler);
                        }
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
                    console.log('probeMarkers:', JSON.stringify(probeMarkers, null, 2));
                    Object.values(probeMarkers).forEach(arr => arr.forEach(({ severity, errStart, errEnd, msg }) => filteredAddMarker(severity, errStart, errEnd, msg)));
                };
                const captureStdoutCheckbox = document.getElementById('control-capture-stdout');
                captureStdoutCheckbox.checked = settings_1.default.shouldCaptureStdio();
                captureStdoutCheckbox.oninput = () => {
                    notifyLocalChangeListeners();
                };
                const recoveryStrategySelector = document.getElementById('control-position-recovery-strategy');
                recoveryStrategySelector.value = settings_1.default.getPositionRecoveryStrategy();
                recoveryStrategySelector.oninput = () => {
                    settings_1.default.setPositionRecoveryStrategy(recoveryStrategySelector.value);
                    notifyLocalChangeListeners();
                };
                const duplicateProbeCheckbox = document.getElementById('control-duplicate-probe-on-attr');
                duplicateProbeCheckbox.oninput = () => {
                    settings_1.default.setShouldDuplicateProbeOnAttrClick(duplicateProbeCheckbox.checked);
                };
                duplicateProbeCheckbox.checked = settings_1.default.shouldDuplicateProbeOnAttrClick();
                const probeWindowStateSavers = {};
                const triggerWindowSave = () => {
                    console.log('triggerWindowsSave...');
                    const states = [];
                    Object.values(probeWindowStateSavers).forEach(v => v(states));
                    console.log('triggerWindowsSave -->', states);
                    settings_1.default.setProbeWindowStates(states);
                };
                const modalEnv = {
                    performRpcQuery, probeMarkers, onChangeListeners, updateMarkers,
                    getLocalState: () => getLocalState(),
                    captureStdout: () => captureStdoutCheckbox.checked,
                    duplicateOnAttr: () => duplicateProbeCheckbox.checked,
                    registerStickyMarker: (...args) => registerStickyMarker(...args),
                    updateSpanHighlight: (hl) => updateSpanHighlight(hl),
                    probeWindowStateSavers,
                    triggerWindowSave,
                };
                // const inputHeader = document.getElementById('input-header');
                // onChangeListeners['passive-bg-listener'] = () => {
                //   // If you have more than 100k lines in this editor and the program node starts after that,
                //   // then I don't know what to tell you.
                //   const rootLocator: TypeAtLoc = {
                //     start: 0,
                //     end: (100000 << 12) + 100,
                //     type: 'Program',
                //   };
                //   performRpcQuery({
                //     type: 'query',
                //     // ...span,
                //     text: modalEnv.getLocalState(),
                //     query: {
                //       nodeType: 'Program',
                //       attr: {
                //         name: 'pasta_containingSpansAndNodeTypes',
                //       },
                //       locator: {
                //         root: rootLocator,
                //         result: rootLocator,
                //         steps: []
                //       },
                //     },
                //   }).then((res) => {
                //     console.log('PASSIVE:', res);
                //   });
                // };
                window.displayGeneralHelp = () => (0, displayHelp_1.default)('general', disabled => document.getElementById('display-help').disabled = disabled);
                window.displayRecoveryStrategyHelp = () => (0, displayHelp_1.default)('recovery-strategy', disabled => document.getElementById('control-position-recovery-strategy-help').disabled = disabled);
                setTimeout(() => {
                    // displayProbeModal(modalEnv, { x: 320, y: 240 }, { lineStart: 5, colStart: 1, lineEnd: 7, colEnd: 2 }, 'Program', 'prettyPrint');
                }, 500);
                setTimeout(() => {
                    // displayProbeModal(modalEnv, { x: 320, y: 140 }, { lineStart: 4, colStart: 1, lineEnd: 4, colEnd: 5 }, 'Call', 'prettyPrint');
                    // displayAttributeModal(modalEnv, { x: 320, y: 140 }, { lineStart: 4, colStart: 1, lineEnd: 4, colEnd: 5 }, 'Call');
                    // displayArgModal(modalEnv, { x: 320, y: 140 }, { lineStart: 4, colStart: 1, lineEnd: 4, colEnd: 5 },
                    //   'Call',  { name: 'lookup', args: [{ type: 'java.lang.String', name: 'name', value: '' }]}
                    // const call = { type: 'Call', start: (4 << 12) + 1, end: (4 << 12) + 5};
                    // displayProbeModal(modalEnv, { x: 320, y: 140 }, { root: call, result: call, steps: [] },
                    //   { name: 'lookup', args: [{ type: 'java.lang.String', name: 'name', value: 'Foo' }]}
                    // );
                    try {
                        settings_1.default.getProbeWindowStates().forEach((state) => {
                            (0, displayProbeModal_3.default)(modalEnv, state.modalPos, state.locator, state.attr);
                        });
                    }
                    catch (e) {
                        console.warn('Invalid probe window state?', e);
                    }
                }, 300); // JUUUUUUUST in case the stored window state causes issues, this 300ms timeout allows people to click the 'clear state' button
                window.RagQuery = (line, col, preselectedType) => {
                    if (preselectedType) {
                        const node = { type: preselectedType, start: (line << 12) + col - 1, end: (line << 12) + col + 1 };
                        (0, displayAttributeModal_3.default)(modalEnv, null, { root: node, result: node, steps: [] });
                    }
                    else {
                        (0, displayRagModal_1.default)(modalEnv, line, col);
                    }
                };
            };
            handlers.init = init;
            handlers['init-pasta'] = () => {
                var _a;
                pastaMode = true;
                delete window.DoAutoComplete;
                rootElem.style.gridTemplateColumns = '3fr 1fr';
                // handlers.init({ value: '// Hello World!\n\nint main() {\n  print(123);\n  print(456);\n}\n', parser: 'beaver', version: 1 });
                handlers.init({ value: (_a = settings_1.default.getEditorContents()) !== null && _a !== void 0 ? _a : '// Hello World!\n\class Foo {\n  static void main(String[] args) {\n    System.out.println("Hello World!");\n  }\n}\n', parser: 'beaver', version: 1 });
            };
            handlers.refresh = () => {
                notifyLocalChangeListeners();
            };
            let didReceiveAtLeastOneMessage = false;
            // Listen for messages
            socket.addEventListener('message', function (event) {
                didReceiveAtLeastOneMessage = true;
                console.log('Message from server ', event.data);
                const parsed = JSON.parse(event.data);
                if (handlers[parsed.type]) {
                    handlers[parsed.type](parsed);
                }
                else {
                    console.log('No handler for message', parsed, ', got handlers for', Object.keys(handlers));
                }
            });
            window.DoAutoComplete = (line, col) => {
                return performRpcQuery({
                    type: 'complete',
                    line,
                    col,
                    text: getLocalState(),
                    // parser: parserToggler.value,
                });
            };
            socket.addEventListener('close', () => {
                // Small timeout to reduce risk of it appearing when navigating away
                setTimeout(() => (0, addConnectionCloseNotice_1.default)(didReceiveAtLeastOneMessage), 100);
            });
        }
        window.maybeAutoInit = () => {
            const idx = location.search.indexOf('editor=');
            const editorId = location.search.slice(idx + 'editor='.length).split('&')[0];
            if (editorId) {
                init(editorId);
            }
        };
        window.init = init;
    };
    window.MiniEditorMain = main;
});
// export default main;
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
