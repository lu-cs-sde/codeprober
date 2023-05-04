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
define("protocol", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
});
define("createWebsocketHandler", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.createWebsocketOverHttpHandler = void 0;
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
                const topReq = { type: 'rpc', id, data: msg, };
                socket.send(JSON.stringify(topReq));
                const cleanup = () => delete pendingCallbacks[id];
                pendingCallbacks[id] = (callback) => {
                    cleanup();
                    switch (callback.data.type) {
                        case 'success': {
                            res(callback.data.value);
                            break;
                        }
                        case 'failureMsg': {
                            console.warn('RPC request failed', callback.data.value);
                            rej(callback.data.value);
                            break;
                        }
                        default: {
                            console.error('Unexpected RPC response');
                            rej(JSON.stringify(callback));
                            break;
                        }
                    }
                };
                setTimeout(() => {
                    cleanup();
                    rej('Timeout');
                }, 30000);
            }),
        };
    };
    const createWebsocketOverHttpHandler = (onClose) => {
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
        const defaultRetryBudget = 3;
        const session = `cpr_${(Number.MAX_SAFE_INTEGER * Math.random()) | 0}`;
        const sendRpc = (msg, extraArgs) => new Promise(async (res, rej) => {
            const id = rpcIdGenerator++;
            // const body = { ...msg, id };
            const topReq = { type: 'rpc', id, data: msg, };
            // {...topReq, session}
            // pendingCallbacks[id] = ({ error, result }) => {
            //   cleanup();
            //   if (error) {
            //     console.warn('RPC request failed', error);
            //     rej(error);
            //   } else {
            //     res(result);
            //   }
            // };
            pendingCallbacks[id] = (callback) => {
                cleanup();
                switch (callback.data.type) {
                    case 'success': {
                        res(callback.data.value);
                        break;
                    }
                    case 'failureMsg': {
                        console.warn('RPC request failed', callback.data.value);
                        rej(callback.data.value);
                        break;
                    }
                    default: {
                        console.error('Unexpected RPC response');
                        rej(JSON.stringify(callback));
                        break;
                    }
                }
            };
            const cleanup = () => delete pendingCallbacks[id];
            const attemptFetch = async (remainingTries) => {
                var _a;
                const cleanupTimer = setTimeout(() => {
                    cleanup();
                    rej('Timeout');
                }, (_a = extraArgs === null || extraArgs === void 0 ? void 0 : extraArgs.timeout) !== null && _a !== void 0 ? _a : 30000);
                try {
                    const rawFetchResult = await fetch('/wsput', { method: 'PUT', body: JSON.stringify(topReq) });
                    if (!rawFetchResult.ok) {
                        if (remainingTries > 0) {
                            console.warn('wsput request failed, trying again in 1 second..');
                            clearTimeout(cleanupTimer);
                            setTimeout(() => attemptFetch(remainingTries - 1), 1000);
                            return;
                        }
                    }
                    const fetchResult = await rawFetchResult.json();
                    didReceiveAtLeastOneMessage = true;
                    if (messageHandlers[fetchResult.type]) {
                        messageHandlers[fetchResult.type](fetchResult);
                        cleanup();
                        res(true);
                    }
                    else {
                        console.log('No handler for message', fetchResult, ', got handlers for', Object.keys(messageHandlers));
                        cleanup();
                        rej('Bad response');
                    }
                }
                catch (e) {
                    console.warn('Error when performing ws-over-http request', e);
                    cleanup();
                    rej('Unknown error');
                }
            };
            attemptFetch(defaultRetryBudget);
        });
        const wsHandler = {
            on: (id, cb) => messageHandlers[id] = cb,
            // sendRpc: (msg) => {
            //   console.log('todo send normal rpc messages, wrapped in rpc something something');
            //   // return Promise.reject('todo');
            // },
            sendRpc: async (msg) => {
                const wrapped = {
                    type: 'wsput:tunnel',
                    session,
                    request: msg,
                };
                const res = await sendRpc(wrapped);
                return res.response;
            },
        };
        const initReq = ({ type: 'wsput:init', session });
        sendRpc(initReq)
            .then((init) => {
            if (messageHandlers['init']) {
                messageHandlers['init'](init.info);
            }
            else {
                console.warn('Got init message, but no handler for it');
            }
        })
            .catch(err => {
            console.warn('Failed to get init message', err);
        });
        let prevEtagValue = -1;
        let longPoller = async (retryBudget) => {
            try {
                const longPollRequest = {
                    type: 'wsput:longpoll',
                    session,
                    etag: prevEtagValue,
                };
                const result = await sendRpc(longPollRequest, { timeout: 10 * 60 * 1000 });
                // const rawFetchResult = await fetch('/wsput', { method: 'PUT', body: JSON.stringify({
                //   id: -1, type: 'longpoll', etag: prevEtagValue, session
                // }) });
                // if (!rawFetchResult.ok) {
                //   throw new Error(`Fetch result: ${rawFetchResult.status}`);
                // }
                // const pollResult = await rawFetchResult.json();
                if (result.data) {
                    switch (result.data.type) {
                        case 'etag': {
                            const etag = result.data.value;
                            if (prevEtagValue !== etag) {
                                if (prevEtagValue !== -1) {
                                    if (messageHandlers.refresh) {
                                        messageHandlers.refresh({});
                                    }
                                }
                                prevEtagValue = etag;
                            }
                            break;
                        }
                        case 'push': {
                            const message = result.data.value;
                            const handler = messageHandlers[message.type];
                            if (handler) {
                                handler(message);
                            }
                            else {
                                console.warn('Got /wsput push message of unknown type', message);
                            }
                            break;
                        }
                        default: {
                            console.warn('Unknown longpoll response type:', result.data);
                            break;
                        }
                    }
                }
            }
            catch (e) {
                console.warn('Error during longPoll', e);
                if (retryBudget > 0) {
                    console.log('Retrying longpoll in 1 second');
                    setTimeout(() => {
                        longPoller(retryBudget - 1);
                    }, 1000);
                }
                else {
                    onClose(didReceiveAtLeastOneMessage);
                }
                return;
            }
            setTimeout(() => longPoller(defaultRetryBudget), 1);
        };
        longPoller(defaultRetryBudget);
        return wsHandler;
    };
    exports.createWebsocketOverHttpHandler = createWebsocketOverHttpHandler;
    exports.default = createWebsocketHandler;
});
define("hacks", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.assertUnreachable = void 0;
    const assertUnreachable = (val) => {
        console.warn('Exhaustive switch matched default case - typedefs are out of date');
        console.warn('Related value:', val);
    };
    exports.assertUnreachable = assertUnreachable;
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
            onBegin(e);
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
        const { render, pos: initialPos, size: initialSize, rootStyle, resizable } = args;
        const root = document.createElement('div');
        root.tabIndex = 0;
        root.classList.add('modalWindow');
        root.style = `${rootStyle || ''}`;
        const bringToFront = () => {
            root.style.zIndex = `${(0, attachDragToX_2.modalZIndexGenerator)()}`;
        };
        bringToFront();
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
        render(contentRoot, { cancelToken: lastCancelToken, bringToFront });
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
            resizeButton.style.height = '0.5rem';
            resizeButton.style.width = '0.5rem';
            resizeButton.style.borderRight = '4px solid gray';
            resizeButton.style.borderBottom = '4px solid gray';
            resizePositioner.appendChild(resizeButton);
            root.appendChild(resizePositioner);
            const size = { w: 1, h: 1 };
            reiszeCleanup = (0, attachDragToX_2.default)(resizePositioner, () => {
                size.w = root.clientWidth;
                size.h = root.clientHeight;
            }, (dx, dy) => {
                var _a;
                const newW = Math.max(32, size.w + dx);
                const newH = Math.max(32, size.h + dy);
                // console.log('setting ', `${root.clientWidth + dx}px`);
                root.style.width = `${newW}px`;
                root.style.height = `${newH}px`;
                root.style.maxWidth = 'fit-content';
                root.style.maxHeight = 'fit-content';
                (_a = args.onOngoingResize) === null || _a === void 0 ? void 0 : _a.call(args);
            }, args.onFinishedResize).cleanup;
        }
        if (initialSize) {
            root.style.width = `${initialSize.width}px`;
            root.style.height = `${initialSize.height}px`;
            root.style.maxWidth = 'fit-content';
            root.style.maxHeight = 'fit-content';
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
                render(contentRoot, { cancelToken: lastCancelToken, bringToFront });
            },
            getPos: dragToMove.getPos,
            getSize: () => ({ width: root.clientWidth, height: root.clientHeight }),
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
                    onForceClose: cleanup,
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
        if (onClose) {
            const closeButton = document.createElement('div');
            closeButton.classList.add('modalCloseButton');
            const textHolder = document.createElement('span');
            textHolder.innerText = '𝖷';
            closeButton.appendChild(textHolder);
            closeButton.classList.add('clickHighlightOnHover');
            closeButton.onmousedown = (e) => { e.stopPropagation(); };
            closeButton.onclick = () => onClose();
            buttons.appendChild(closeButton);
        }
        titleRowHolder.appendChild(buttons);
        return {
            element: titleRowHolder
        };
    };
    exports.default = createModalTitle;
});
define("ui/startEndToSpan", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const startEndToSpan = (start, end) => ({
        lineStart: (start >>> 12),
        colStart: start & 0xFFF,
        lineEnd: (end >>> 12),
        colEnd: end & 0xFFF,
    });
    exports.default = startEndToSpan;
});
define("model/adjustTypeAtLoc", ["require", "exports", "ui/startEndToSpan"], function (require, exports, startEndToSpan_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    startEndToSpan_1 = __importDefault(startEndToSpan_1);
    const adjustTypeAtLoc = (adjuster, tal) => {
        if (tal.external) {
            // Refuse to adjust things in external files.
            // CodeProber is only expected to get change events for our own "internal" file.
            return;
        }
        const span = (0, startEndToSpan_1.default)(tal.start, tal.end);
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
    exports.adjustValue = void 0;
    adjustTypeAtLoc_1 = __importDefault(adjustTypeAtLoc_1);
    const adjustValue = (adj, arg) => {
        switch (arg.type) {
            case 'nodeLocator': {
                if (arg.value.value) {
                    adjustLocator(adj, arg.value.value);
                }
                break;
            }
            case 'collection': {
                arg.value.entries.forEach(v => adjustValue(adj, v));
            }
        }
    };
    exports.adjustValue = adjustValue;
    const adjustLocator = (adj, loc) => {
        (0, adjustTypeAtLoc_1.default)(adj, loc.result);
        const adjustStep = (step) => {
            var _a;
            switch (step.type) {
                case 'tal': {
                    (0, adjustTypeAtLoc_1.default)(adj, step.value);
                    break;
                }
                case 'nta': {
                    (_a = step.value.property.args) === null || _a === void 0 ? void 0 : _a.forEach(arg => adjustValue(adj, arg));
                    break;
                }
            }
        };
        loc.steps.forEach(adjustStep);
    };
    exports.default = adjustLocator;
});
define("model/repositoryUrl", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.rawUrl = exports.repositoryUrl = void 0;
    const repositoryUrl = `https://github.com/lu-cs-sde/codeprober`;
    exports.repositoryUrl = repositoryUrl;
    const rawUrl = (resource) => `https://raw.githubusercontent.com/lu-cs-sde/codeprober/master/${resource}`;
    exports.rawUrl = rawUrl;
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
define("model/WindowState", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
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
        getProbeWindowStates: () => {
            var _a;
            const ret = (_a = settings.get().probeWindowStates) !== null && _a !== void 0 ? _a : [];
            return ret.map((item) => {
                if (typeof item.data === 'undefined') {
                    // Older variant of this data, upgrade it
                    return {
                        modalPos: item.modalPos,
                        data: {
                            type: 'probe',
                            locator: item.locator,
                            property: item.property,
                            nested: {},
                        }
                    };
                }
                return item;
            });
        },
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
        getLocationStyle: () => { var _a; return (_a = settings.get().locationStyle) !== null && _a !== void 0 ? _a : 'full'; },
        setLocationStyle: (locationStyle) => settings.set({ ...settings.get(), locationStyle }),
        shouldHideSettingsPanel: () => { var _a, _b; return (_b = (_a = settings.get()) === null || _a === void 0 ? void 0 : _a.hideSettingsPanel) !== null && _b !== void 0 ? _b : false; },
        setShouldHideSettingsPanel: (shouldHide) => settings.set({ ...settings.get(), hideSettingsPanel: shouldHide }),
        shouldEnableTesting: () => window.location.search.includes('enableTesting=true'),
    };
    exports.default = settings;
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
define("ui/create/createTextSpanIndicator", ["require", "exports", "settings", "ui/create/registerOnHover"], function (require, exports, settings_1, registerOnHover_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    settings_1 = __importDefault(settings_1);
    registerOnHover_1 = __importDefault(registerOnHover_1);
    const createTextSpanIndicator = (args) => {
        var _a;
        const { span, marginLeft, onHover, onClick } = args;
        const indicator = document.createElement('span');
        indicator.style.fontSize = '0.75rem';
        indicator.style.color = 'gray';
        if (marginLeft) {
            indicator.style.marginLeft = '0.25rem';
        }
        if (args.autoVerticalMargin) {
            indicator.style.marginTop = 'auto';
            indicator.style.marginBottom = 'auto';
        }
        indicator.style.marginRight = '0.25rem';
        const ext = args.external ? '↰' : '';
        const warn = span.lineStart === 0 && span.colStart === 0 && span.lineEnd === 0 && span.colEnd === 0 ? '⚠️' : '';
        switch ((_a = args.styleOverride) !== null && _a !== void 0 ? _a : settings_1.default.getLocationStyle()) {
            case 'full-compact':
                if (span.lineStart === span.lineEnd) {
                    indicator.innerText = `${ext}[${span.lineStart}:${span.colStart}-${span.colEnd}]${warn}`;
                    break;
                }
            // Else, fall through
            case 'full':
                indicator.innerText = `${ext}[${span.lineStart}:${span.colStart}→${span.lineEnd}:${span.colEnd}]${warn}`;
                break;
            case 'lines-compact':
                if (span.lineStart === span.lineEnd) {
                    indicator.innerText = `${ext}[${span.lineStart}]${warn}`;
                    break;
                }
            // Else, fall through
            case 'lines':
                indicator.innerText = `${ext}[${span.lineStart}→${span.lineEnd}]${warn}`;
                break;
            case 'start':
                indicator.innerText = `${ext}[${span.lineStart}:${span.colStart}]${warn}`;
                break;
            case 'start-line':
                indicator.innerText = `${ext}[${span.lineStart}]${warn}`;
                break;
        }
        if (!args.external) {
            if (onHover) {
                indicator.classList.add('highlightOnHover');
                (0, registerOnHover_1.default)(indicator, onHover);
            }
            if (onClick) {
                indicator.onclick = () => onClick();
            }
        }
        return indicator;
    };
    exports.default = createTextSpanIndicator;
});
define("ui/popup/displayHelp", ["require", "exports", "model/repositoryUrl", "ui/create/createModalTitle", "ui/create/createTextSpanIndicator", "ui/create/showWindow"], function (require, exports, repositoryUrl_1, createModalTitle_1, createTextSpanIndicator_1, showWindow_2) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createModalTitle_1 = __importDefault(createModalTitle_1);
    createTextSpanIndicator_1 = __importDefault(createTextSpanIndicator_1);
    showWindow_2 = __importDefault(showWindow_2);
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
        'duplicate-probe-on-attr': 'Duplicate probe',
        'capture-stdout': 'Capture stdout',
        'location-style': 'Location styles',
        'ast': 'AST',
        'test-code-vs-codeprober-code': 'Test code vs CodeProber code',
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
                    `If you get the message 'Node listing failed', then it likely means that something went wrong during parsing.`,
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
                    `- The argument types are 'String', 'int', 'boolean', 'java.io.OutputStream', 'java.io.PrintStream' or a subtype of the top AST Node type.`,
                    `- One of the following is true:`,
                    `-- The function is an attribute (originates from a jrag file, e.g 'z' in 'syn X Y.z() = ...')`,
                    `-- The function is an AST child accessor (used to get members declared in an .ast file).`,
                    `-- The function name is either 'toString', 'getChild', 'getNumChild', 'getParent' or 'dumpTree'`,
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
                        a.href = repositoryUrl_1.repositoryUrl;
                        a.innerText = repositoryUrl_1.repositoryUrl;
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
                const sampleAttr = document.createElement('pre');
                sampleAttr.style.marginTop = '6px';
                sampleAttr.style.marginLeft = '2px';
                sampleAttr.style.fontSize = '0.875rem';
                sampleAttr.innerText = `
aspect DumpSubtree {

  // Bypass/inline Opt/List
  void ASTNode.bypassDumpSubtree(java.util.List<Object> dst, int budget) {
    if (budget <= 0) return;
    for (ASTNode child : astChildren()) child.dumpSubtree(dst, budget);
  }
  void Opt.dumpSubtree(java.util.List<Object> dst, int budget) { bypassDumpSubtree(dst, budget); }
  void List.dumpSubtree(java.util.List<Object> dst, int budget) { bypassDumpSubtree(dst, budget); }

  void ASTNode.dumpSubtree(java.util.List<Object> dst, int budget) {
    dst.add(this);
    if (getNumChild() == 0 || budget <= 0) { return; }

    final java.util.List<Object> ret = new java.util.ArrayList<>();
    for (ASTNode child : astChildren()) child.dumpSubtree(ret, budget - 1);
    dst.add(ret);
  }

  syn Object ASTNode.dumpSubtree(int budget) {
    java.util.List<Object> dst = new java.util.ArrayList<>();
    for (ASTNode child : astChildren()) child.dumpSubtree(dst, budget);
    if (dst.size() == 1 && dst.get(0) instanceof java.util.List<?>) {
      return dst.get(0);
    }
    return dst;
  }
  syn Object ASTNode.dumpSubtree() { return dumpSubtree(999); }

}
`.trim();
                const copyButton = document.createElement('button');
                copyButton.innerText = 'Copy to clipboard';
                copyButton.onclick = () => {
                    navigator.clipboard.writeText(sampleAttr.innerText);
                };
                return [
                    'Some nodes in your AST might be missing location information.',
                    'CodeProber is heavily built around the idea that all AST nodes have positions, and the experience is worsened for nodes where this isn\'t true.',
                    '',
                    'There are two solutions:',
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
                    `An efficient way to root out parser problems is to pick 'Fail', then dump the entire tree using the following 'dumpSubtree' attribute (JastAdd syntax):`,
                    sampleAttr,
                    copyButton,
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
                    `If you check 'Capture stdout' on the top right, you'll also see any messages printed to System.out and System.err in the window.`,
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
                    `Whenever probes are evaluated, these messages are intercepted (even if 'Capture stdout' isn't checked!).`,
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
                    `When this happens, we can re-use the AST multiple times to avoid unnecessary re-parses. There are however reasons that you might not want to re-use the AST, or at least not fully.`,
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
                `There is potentially a very large amount of functions shown is you check this box, which can be annoying.`,
                `In addition, some of the non-standard functions might cause mutations (like 'setChild(int, ..)'), which can cause undefined behavior when used in this tool.`,
                `In general, we recommend you keep this box unchecked, and only occasionally re-check it.`,
            ];
            case 'duplicate-probe-on-attr': return [
                `When you have created a probe, you can click the property name to create a new probe on the same node, but with a different property.`,
                `This click can either create a new probe window, or replace the old one.`,
                `If this box is checked, then a new window will be created.`,
                `If this box is unchecked, then it will replace the old window.`,
                `By holding 'Shift' while clicking the property name, you can access the 'reverse' functionality.`,
                `I.e if the box is checked and you hold shift, then the window will be replaced, and vice versa.`,
            ];
            case 'capture-stdout': {
                const styled = (text, cls) => {
                    const span = document.createElement('span');
                    span.classList.add(cls);
                    span.innerText = text;
                    return span;
                };
                return [
                    `Check this if you want messages to stdout and stderr to be shown in probe outputs.`,
                    `'printf-debugging' should generally be avoided if possible, but if you feel it is strictly needed then you can use this checkbox to access it.`,
                    joinElements(`Captured messages are displayed with a `, styled('blue', 'captured-stdout'), ` color if they were printed to stdout, and a `, styled('red', 'captured-stderr'), ` color if they were printed to stderr.`),
                    ``,
                    `Note that only messages printed during property evaluation are captured.`,
                    `Messages printed during parsing are not shown here, but can still be seen in the terminal where you started code-prober.jar.`,
                    `An exception to this is when parsing fails, in which case messages during parsing are displayed (even if this checkbox is unchecked).`,
                ];
            }
            case 'location-style': {
                const sp = { lineStart: 1, colStart: 2, lineEnd: 3, colEnd: 4 };
                const createExplanationPanel = (entries) => {
                    const settingsExplanation = document.createElement('div');
                    settingsExplanation.style.paddingLeft = '1rem';
                    settingsExplanation.style.display = 'grid';
                    settingsExplanation.style.gridTemplateColumns = 'auto auto 1fr';
                    settingsExplanation.style.gridColumnGap = '0.5rem';
                    entries.forEach(([head, tail, span]) => {
                        const headNode = document.createElement('span');
                        headNode.style.textAlign = 'right';
                        headNode.classList.add('syntax-attr');
                        headNode.innerText = head;
                        settingsExplanation.appendChild(headNode);
                        settingsExplanation.appendChild(document.createTextNode('-'));
                        settingsExplanation.appendChild((0, createTextSpanIndicator_1.default)({
                            span,
                            styleOverride: tail,
                        }));
                        // const tailNode = document.createElement('span');
                        // tailNode.innerText = tail;
                        // settingsExplanation.appendChild(tailNode);
                    });
                    return settingsExplanation;
                };
                return [
                    `In several locations in CodeProber you can see location indicators.`,
                    `This setting control how the location indicators are presented. Example values can be seen below for a location that starts at line 1, column 2 and ends at line 3, column 4.`,
                    ``,
                    createExplanationPanel([
                        [`Full`, 'full', sp],
                        [`Lines`, 'lines', sp],
                        [`Start`, 'start', sp],
                        [`Start line`, `start-line`, sp],
                    ]),
                    ``,
                    `The 'compact' options look like the non-compact options if the start and end lines are different. If start and end lines are equal, then it looks like this:`,
                    createExplanationPanel([
                        [`Full compact`, 'full-compact', { ...sp, lineEnd: 1 }],
                        [`Lines compact`, 'lines-compact', { ...sp, lineEnd: 1 }],
                    ]),
                    ``,
                    `Note that this setting doesn't affect the hover highlighting. The exact line/column is highlighted, even if the indicator only shows the start line for example.`,
                ];
            }
            case 'ast': {
                return [
                    `This window displays the abstract syntax tree (AST) around a node in the tree.`,
                    `Nodes can be hovered and interacted with, just like when the output of a normal probe is an AST node.`,
                    `When you see '᠁', the AST has been truncated due to performance reasons.`,
                    `You can click the '᠁' to continue exploring the AST from that point`,
                ];
            }
            case 'test-code-vs-codeprober-code': {
                return [
                    `When a test is created it saves the current state of CodeProber. This includes the code in the main CodeProber text editor, as well as some of the settings (cache settings, main args, file suffix, etc.).`,
                    ``,
                    `When tests are executed, they do so with their saved state, *not* the current CodeProber state. This lets you have multiple tests at the same time, each with their on unique configuration.`,
                    ``,
                    `When a test fails, you may want to open probes to inspect why. The first step is to change the current CodeProber code to the test code. Open the test in question and click the 'Source Code' tab. There will be a button labeled 'Load Source' or 'Open Probe'.`,
                    `• Clicking 'Load Source' will replace the code inside the main CodeProber editor with the saved code from the test.`,
                    `• Clicking 'Open Probe' will open the probe corresponding to the test.`,
                    `'Open Probe' is only available if the CodeProber code matches the test code.`,
                ];
            }
        }
    };
    const displayHelp = (type, setHelpButtonDisabled) => {
        setHelpButtonDisabled === null || setHelpButtonDisabled === void 0 ? void 0 : setHelpButtonDisabled(true);
        const cleanup = () => {
            helpWindow.remove();
            setHelpButtonDisabled === null || setHelpButtonDisabled === void 0 ? void 0 : setHelpButtonDisabled(false);
        };
        const helpWindow = (0, showWindow_2.default)({
            rootStyle: `
      width: 32rem;
      min-height: 8rem;
    `,
            onForceClose: cleanup,
            resizable: true,
            render: (root) => {
                root.appendChild((0, createModalTitle_1.default)({
                    renderLeft: (container) => {
                        const header = document.createElement('span');
                        header.innerText = getHelpTitle(type);
                        container.appendChild(header);
                    },
                    onClose: cleanup,
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
define("model/findLocatorWithNestingPath", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const findLocatorWithNestingPath = (path, rootLines) => {
        const step = (index, from) => {
            const line = rootLines[path[index]];
            if (!line) {
                return null;
            }
            if (index >= path.length - 1) {
                switch (line.type) {
                    case 'node':
                        return line.value;
                    default:
                        return null;
                }
            }
            switch (line.type) {
                case 'arr':
                    return step(index + 1, line.value);
                default:
                    return null;
            }
        };
        return step(0, rootLines);
    };
    exports.default = findLocatorWithNestingPath;
});
define("dependencies/onp/data", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.position = exports.SES_ADD = exports.SES_COMMON = exports.SES_DELETE = void 0;
    exports.SES_DELETE = -1;
    exports.SES_COMMON = 0;
    exports.SES_ADD = 1;
    function position(x, y, k) {
        return { x, y, k };
    }
    exports.position = position;
});
define("dependencies/onp/results", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.createTextResults = exports.createResultItem = void 0;
    function createResultItem(left, right, state) {
        return { left, right, state };
    }
    exports.createResultItem = createResultItem;
    function createTextResults(results) {
        if (results.length === 0) {
            return [];
        }
        let last = createResultItem(results[0].left, results[0].right, results[0].state);
        let shrink = [last];
        results.slice(1).forEach((item) => {
            if (item.state !== last.state) {
                last = createResultItem(item.left, item.right, item.state);
                shrink.push(last);
            }
            else {
                last.left += item.left;
                last.right += item.right;
            }
        });
        return shrink;
    }
    exports.createTextResults = createTextResults;
});
define("dependencies/onp/onp", ["require", "exports", "dependencies/onp/data", "dependencies/onp/results"], function (require, exports, data_1, results_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.onp = void 0;
    function createInfo(a, b) {
        //switch sides
        if (a.length >= b.length) {
            return {
                a: b,
                b: a,
                m: b.length,
                n: a.length,
                reverse: true,
                offset: b.length + 1
            };
        }
        return {
            a: a,
            b: b,
            m: a.length,
            n: b.length,
            reverse: false,
            offset: a.length + 1
        };
    }
    function onp(textA, textB) {
        const [epc, ed] = positions(textA, textB);
        const [result, lcs] = sequence(textA, textB, epc);
        return [result, ed, lcs];
    }
    exports.onp = onp;
    function positions(textA, textB) {
        const { n, m, offset } = createInfo(textA, textB);
        const path = [];
        const pos = [];
        const delta = n - m;
        const size = m + n + 3;
        const fp = {};
        for (let i = 0; i < size; i++) {
            fp[i] = data_1.SES_DELETE;
            path[i] = data_1.SES_DELETE;
        }
        let p = data_1.SES_DELETE;
        do {
            ++p;
            for (let k = -p; k <= delta - 1; k++) {
                fp[k + offset] = snake(textA, textB, path, pos, k, fp[k - 1 + offset] + 1, fp[k + 1 + offset]);
            }
            for (let k = delta + p; k >= delta + 1; k--) {
                fp[k + offset] = snake(textA, textB, path, pos, k, fp[k - 1 + offset] + 1, fp[k + 1 + offset]);
            }
            fp[delta + offset] = snake(textA, textB, path, pos, delta, fp[delta - 1 + offset] + 1, fp[delta + 1 + offset]);
        } while (fp[delta + offset] !== n);
        let ed = delta + 2 * p;
        let epc = [];
        let r = path[delta + offset];
        while (r !== data_1.SES_DELETE) {
            epc[epc.length] = (0, data_1.position)(pos[r].x, pos[r].y, null);
            r = pos[r].k;
        }
        return [epc, ed];
    }
    function sequence(textA, textB, epc) {
        const { a, b, reverse } = createInfo(textA, textB);
        const changes = [];
        let y_idx = 1;
        let x_idx = 1;
        let py_idx = 0;
        let px_idx = 0;
        let lcs = "";
        for (let i = epc.length - 1; i >= 0; i--) {
            while (px_idx < epc[i].x || py_idx < epc[i].y) {
                if (epc[i].y - epc[i].x > py_idx - px_idx) {
                    if (reverse) {
                        changes[changes.length] = (0, results_1.createResultItem)(b[py_idx], b[py_idx], data_1.SES_DELETE);
                    }
                    else {
                        changes[changes.length] = (0, results_1.createResultItem)(b[py_idx], b[py_idx], data_1.SES_ADD);
                    }
                    ++y_idx;
                    ++py_idx;
                }
                else if (epc[i].y - epc[i].x < py_idx - px_idx) {
                    if (reverse) {
                        changes[changes.length] = (0, results_1.createResultItem)(a[px_idx], a[px_idx], data_1.SES_ADD);
                    }
                    else {
                        changes[changes.length] = (0, results_1.createResultItem)(a[px_idx], a[px_idx], data_1.SES_DELETE);
                    }
                    ++x_idx;
                    ++px_idx;
                }
                else {
                    changes[changes.length] = (0, results_1.createResultItem)(a[px_idx], b[py_idx], data_1.SES_COMMON);
                    lcs += a[px_idx];
                    ++x_idx;
                    ++y_idx;
                    ++px_idx;
                    ++py_idx;
                }
            }
        }
        return [changes, lcs];
    }
    function snake(textA, textB, path, pos, k, p, pp) {
        const { a, b, n, m, offset } = createInfo(textA, textB);
        const r = p > pp ? path[k - 1 + offset] : path[k + 1 + offset];
        let y = Math.max(p, pp);
        let x = y - k;
        while (x < m && y < n && a[x] === b[y]) {
            ++x;
            ++y;
        }
        path[k + offset] = pos.length;
        pos[pos.length] = (0, data_1.position)(x, y, r);
        return y;
    }
});
define("dependencies/onp/array", ["require", "exports", "dependencies/onp/results", "dependencies/onp/data"], function (require, exports, results_2, data_2) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.objectifyLcs = exports.objectifyArray = exports.stringifyArray = void 0;
    function stringifyArray(a, b) {
        const map = { forward: {}, backward: {}, pointer: 1 };
        const textA = a.map((item) => determineCode(map, item)).join("");
        const textB = b.map((item) => determineCode(map, item)).join("");
        return [textA, textB, map];
    }
    exports.stringifyArray = stringifyArray;
    function objectifyArray(arrayA, arrayB, res, map) {
        const results = res.map((r) => (0, results_2.createResultItem)(map.backward[r.left], map.backward[r.right], r.state));
        results
            .filter(filter([data_2.SES_COMMON, data_2.SES_DELETE]))
            .forEach((item, index) => setData(item, arrayA[index], -1));
        results
            .filter(filter([data_2.SES_COMMON, data_2.SES_ADD]))
            .forEach((item, index) => setData(item, arrayB[index], 1));
        return results;
    }
    exports.objectifyArray = objectifyArray;
    function objectifyLcs(map, res) {
        return res.filter((item) => {
            return item.state === data_2.SES_COMMON;
        }).map((item) => {
            return item.right;
        });
    }
    exports.objectifyLcs = objectifyLcs;
    function determineCode(map, item) {
        let id = item.toString();
        let code = map.forward[id];
        if (!code) {
            code = String.fromCharCode(map.pointer);
            map.forward[id] = code;
            map.backward[code] = id;
            map.pointer++;
        }
        return code;
    }
    function filter(what) {
        return (item) => {
            return what.indexOf(item.state) >= 0;
        };
    }
    function setData(item, data, side) {
        switch (true) {
            case item.state === data_2.SES_DELETE:
            case item.state === data_2.SES_ADD:
                item.left = item.right = data;
                break;
            case item.state === data_2.SES_COMMON && side === -1:
                item.left = data;
                break;
            case item.state === data_2.SES_COMMON && side === 1:
                item.right = data;
                break;
            default:
                break;
        }
    }
});
define("dependencies/onp/index", ["require", "exports", "dependencies/onp/data", "dependencies/onp/results", "dependencies/onp/onp", "dependencies/onp/array"], function (require, exports, data_3, results_3, onp_1, array_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.diffArray = exports.diffText = exports.SES_ADD = exports.SES_COMMON = exports.SES_DELETE = void 0;
    Object.defineProperty(exports, "SES_DELETE", { enumerable: true, get: function () { return data_3.SES_DELETE; } });
    Object.defineProperty(exports, "SES_COMMON", { enumerable: true, get: function () { return data_3.SES_COMMON; } });
    Object.defineProperty(exports, "SES_ADD", { enumerable: true, get: function () { return data_3.SES_ADD; } });
    function diffText(a, b) {
        const [results, ed, lcs] = (0, onp_1.onp)(a, b);
        return {
            distance: ed,
            lcs: lcs,
            results: (0, results_3.createTextResults)(results)
        };
    }
    exports.diffText = diffText;
    function diffArray(arrayA, arrayB) {
        const [a, b, map] = (0, array_1.stringifyArray)(arrayA, arrayB);
        const [res, ed] = (0, onp_1.onp)(a, b);
        const results = (0, array_1.objectifyArray)(arrayA, arrayB, res, map);
        const lcs = (0, array_1.objectifyLcs)(map, results);
        return {
            distance: ed,
            lcs: lcs,
            results: results
        };
    }
    exports.diffArray = diffArray;
});
define("model/test/rpcBodyToAssertionLine", ["require", "exports", "hacks"], function (require, exports, hacks_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.rpcLinesToAssertionLines = void 0;
    const rpcBodyToTestBody = (line) => {
        switch (line.type) {
            case 'plain':
            case 'streamArg':
            case 'node':
                return line;
            case 'stdout':
            case 'stderr':
                // Do not keep these
                return null;
            case 'arr':
                return { type: 'arr', value: line.value.map(rpcBodyToTestBody).filter(Boolean) };
            default: {
                (0, hacks_1.assertUnreachable)(line);
                return line;
            }
        }
    };
    const rpcLinesToAssertionLines = (lines) => {
        const mapped = lines.map(rpcBodyToTestBody);
        return mapped.filter(Boolean);
    };
    exports.rpcLinesToAssertionLines = rpcLinesToAssertionLines;
    exports.default = rpcBodyToTestBody;
});
define("model/test/compareTestResult", ["require", "exports", "dependencies/onp/index", "model/test/rpcBodyToAssertionLine"], function (require, exports, index_1, rpcBodyToAssertionLine_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const flattenLines = (lines, callback) => {
        lines = (0, rpcBodyToAssertionLine_1.rpcLinesToAssertionLines)(lines);
        lines.forEach((line, idx) => {
            switch (line.type) {
                case 'arr': {
                    flattenLines(line.value, (path, line) => callback([idx, ...path], line));
                    break;
                }
                default: {
                    callback([idx], line);
                    break;
                }
            }
        });
    };
    const createEncounterCounter = () => {
        const encounterCounter = {};
        return (path) => {
            var _a;
            const encoded = JSON.stringify(path);
            const prevEncounters = (_a = encounterCounter[encoded]) !== null && _a !== void 0 ? _a : 0;
            encounterCounter[encoded] = prevEncounters + 1;
            return prevEncounters;
        };
    };
    const flattenNestedTestResponses = (tests, callback) => {
        const encounterCounter = createEncounterCounter();
        tests.forEach(test => {
            if (test.result === 'could-not-find-node') {
                return;
            }
            const encIndex = encounterCounter(test.path);
            flattenLines(test.result.body, (path, line) => callback([...test.path, encIndex, ...path], line));
            flattenNestedTestResponses(test.result.nested, (path, line) => callback([...test.path, encIndex, ...path], line));
        });
    };
    const createFlattenedLine = (path, line, side) => {
        return {
            path, line, side,
            toString: () => `${JSON.stringify(path.slice(0, path.length - 1))} - ${JSON.stringify(line)}`,
        };
    };
    const compareTestResult = (assertionType, tcase, evalRes) => {
        // const flattenedExpected: { [id: string]: FlattenedLine } = {};
        const flattenedExpected = [];
        const addExpected = (path, line) => flattenedExpected.push(createFlattenedLine(path, line, 'left'));
        if (tcase.result !== 'could-not-find-node') {
            flattenLines(tcase.result.body, addExpected);
            flattenNestedTestResponses(tcase.result.nested, addExpected);
        }
        // const flattenedActual: { [id: string]: RpcBodyLine } = {};
        const flattenedActual = [];
        const addActual = (path, line) => flattenedActual.push(createFlattenedLine(path, line, 'right'));
        // const addActual = (path: number[], line: RpcBodyLine) => flattenedActual[JSON.stringify(path)] = line;
        if (evalRes.result !== 'could-not-find-node') {
            flattenLines(evalRes.result.body, addActual);
            flattenNestedTestResponses(evalRes.result.nested, addActual);
        }
        let someErr = false;
        if (assertionType === 'SET') {
            const sorter = (a, b) => {
                const lhs = a.toString();
                const rhs = b.toString();
                if (lhs == rhs) {
                    return 0;
                }
                return lhs < rhs ? -1 : 1;
            };
            flattenedExpected.sort(sorter);
            flattenedActual.sort(sorter);
        }
        const retExp = {};
        const retAct = {};
        const diffResult = (0, index_1.diffArray)(flattenedExpected, flattenedActual);
        diffResult.results.forEach(result => {
            if (!result.state) {
                return; // No diff, no marker to add
            }
            someErr = true;
            if (result.state === -1) {
                // Expected line not matched
                retExp[JSON.stringify(result.left.path)] = 'error';
            }
            else {
                // Unexpected actual line
                retAct[JSON.stringify(result.right.path)] = 'error';
            }
        });
        return {
            overall: someErr ? 'error' : 'ok',
            expectedMarkers: retExp,
            actualMarkers: retAct,
        };
    };
    // export { TestComparisonReport }
    exports.default = compareTestResult;
});
define("model/test/TestManager", ["require", "exports", "model/findLocatorWithNestingPath", "model/test/compareTestResult"], function (require, exports, findLocatorWithNestingPath_1, compareTestResult_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.nestedTestResponseToTest = exports.createTestManager = void 0;
    findLocatorWithNestingPath_1 = __importDefault(findLocatorWithNestingPath_1);
    compareTestResult_1 = __importDefault(compareTestResult_1);
    ;
    const createTestManager = (getEnv, createJobId) => {
        const suiteListRepo = {};
        const saveCategoryState = async (category, cases) => {
            console.log('save', category, '-->', cases);
            console.log('expected bytelen:', JSON.stringify(cases).length);
            const prev = suiteListRepo[category];
            suiteListRepo[category] = cases;
            let resp;
            try {
                resp = await getEnv().performTypedRpc({
                    type: "Test:PutTestSuite",
                    suite: `${category}.json`,
                    contents: {
                        v: 1,
                        cases
                    },
                });
            }
            catch (e) {
                suiteListRepo[category] = prev;
                throw e;
            }
            if (!resp.err)
                return true;
            console.warn(`Failed saving cases for ${category}, error:`, resp.err);
            return false;
        };
        const listeners = {};
        const notifyListeners = (type) => Object.values(listeners).forEach(cb => cb(type));
        let categoryInvalidationCount = 0;
        const addTest = async (category, test, overwriteIfExisting) => {
            const categories = await listTestSuiteCategories();
            if (categories == 'failed-listing') {
                return 'failed-fetching';
            }
            // console.log('todo add', category, '/', test.name);
            const existing = await doGetTestSuite(category);
            let alreadyExisted = false;
            if (existing == 'failed-fetching') {
                // Doesn't exist yet, this is OK
            }
            else if (existing.some(tc => tc.name == test.name)) {
                if (!overwriteIfExisting) {
                    return 'already-exists-with-that-name';
                }
                alreadyExisted = true;
            }
            await saveCategoryState(category, [...(suiteListRepo[category] || []).filter(tc => tc.name !== test.name), test]);
            ++categoryInvalidationCount;
            if (overwriteIfExisting && testStatusRepo[category]) {
                delete testStatusRepo[category][test.name];
            }
            notifyListeners(alreadyExisted ? 'updated-test' : 'added-test');
            return 'ok';
        };
        const removeTest = async (category, name) => {
            const existing = await doGetTestSuite(category);
            if (existing == 'failed-fetching') {
                return 'failed-fetching';
            }
            if (!existing.some(cat => cat.name === name)) {
                return 'no-such-test';
            }
            await saveCategoryState(category, existing.filter(cat => cat.name !== name));
            ++categoryInvalidationCount;
            delete testStatusRepo[category];
            notifyListeners('removed-test');
            return 'ok';
        };
        const listTestSuiteCategories = (() => {
            let categoryLister = null;
            let categoryListingVersion = -1;
            return () => {
                if (!categoryLister || categoryInvalidationCount !== categoryListingVersion) {
                    categoryListingVersion = categoryInvalidationCount;
                    categoryLister = getEnv().performTypedRpc({
                        type: 'Test:ListTestSuites',
                    })
                        .then(({ result }) => {
                        if (result.type == 'err') {
                            console.warn('Failed listing test suites. Error code:', result.value);
                            return 'failed-listing';
                        }
                        return result.value
                            .map((suite) => {
                            if (!suite.endsWith('.json')) {
                                console.warn(`Unexpected suite file name ${suite}`);
                                return '';
                            }
                            return suite.slice(0, suite.lastIndexOf('.'));
                        })
                            .filter(Boolean);
                    });
                }
                return categoryLister;
            };
        })();
        const doGetTestSuite = async (id) => {
            if (suiteListRepo[id]) {
                return suiteListRepo[id];
            }
            const { result } = await getEnv().performTypedRpc({
                type: 'Test:GetTestSuite',
                suite: `${id}.json`,
            });
            if (result.type === 'contents') {
                const cases = result.value.cases;
                suiteListRepo[id] = cases;
                return cases;
            }
            console.warn(`Failed to get test suite '${id}'. Error code: ${result.value}`);
            return 'failed-fetching';
        };
        const testStatusRepo = {};
        const evaluateTest = (category, name) => {
            const categoryStatus = testStatusRepo[category] || {};
            testStatusRepo[category] = categoryStatus;
            const existing = categoryStatus[name];
            if (!!existing) {
                return existing;
            }
            const fresh = (async () => {
                const suite = await doGetTestSuite(category);
                if (suite === 'failed-fetching') {
                    return 'failed-fetching';
                }
                const tcase = suite.find(ent => ent.name === name);
                if (!tcase) {
                    console.warn(`No such test case '${name}' in ${category}`);
                    return 'failed-fetching';
                }
                const nestedTestToRequest = (test) => ({
                    path: test.path,
                    property: test.property,
                    nested: test.nestedProperties.map(nestedTestToRequest),
                });
                const res = await fullyEvaluate(tcase.src, tcase.property, tcase.locator, tcase.nestedProperties.map(nestedTestToRequest), `${tcase.name}`);
                if (tcase.assertType === 'SMOKE') {
                    return {
                        test: tcase,
                        output: res,
                        status: {
                            overall: 'ok',
                            expectedMarkers: {},
                            actualMarkers: {},
                        },
                    };
                }
                return {
                    test: tcase,
                    output: res,
                    status: (0, compareTestResult_1.default)(tcase.assertType, testToNestedTestResponse({
                        expectedOutput: tcase.expectedOutput,
                        nestedProperties: tcase.nestedProperties,
                        path: [],
                        property: tcase.property,
                    }), res),
                };
            })();
            categoryStatus[name] = fresh;
            notifyListeners('test-status-update');
            return fresh;
        };
        const addListener = (uid, callback) => {
            listeners[uid] = callback;
        };
        const removeListener = (uid) => {
            delete listeners[uid];
        };
        const fullyEvaluate = async (src, property, locator, nested, debugLabel) => {
            // let ret: NestedTestResponse = {
            //   path: '',
            //   property,
            //   body: [],
            //   nested: [],
            // };
            const rootRes = await new Promise(async (resolve, reject) => {
                const handleUpdate = (data) => {
                    switch (data.value.type) {
                        case 'workerTaskDone': {
                            const res = data.value.value;
                            if (res.type === 'normal') {
                                const cast = res.value;
                                if (cast.response.type == 'job') {
                                    throw new Error(`Unexpected 'job' result in async test update`);
                                }
                                resolve(cast.response.value);
                            }
                            else {
                                reject(res.value);
                            }
                        }
                    }
                    // if (data.status === 'done') {
                    //   resolve(data.result.response.value);
                    // }
                };
                const jobId = createJobId(handleUpdate);
                try {
                    const env = getEnv();
                    const res = await env.performTypedRpc({
                        type: 'EvaluateProperty',
                        property,
                        locator,
                        src,
                        captureStdout: true,
                        job: jobId,
                        jobLabel: `Test > ${debugLabel}`,
                    });
                    if (res.response.type === 'sync') {
                        // Non-concurrent server, handle request synchronously
                        resolve(res.response.value);
                    }
                }
                catch (e) {
                    reject(e);
                }
            });
            const nestedTestResponses = await Promise.all(nested.map(async (nest) => {
                const nestPathParts = nest.path;
                const nestLocator = (0, findLocatorWithNestingPath_1.default)(nestPathParts, rootRes.body);
                if (!nestLocator) {
                    return {
                        path: nest.path,
                        property: nest.property,
                        result: 'could-not-find-node',
                    };
                }
                const nestRes = await fullyEvaluate(src, nest.property, nestLocator, nest.nested, debugLabel);
                return {
                    ...nestRes,
                    path: nest.path,
                };
            }));
            return {
                path: [],
                property,
                result: {
                    body: rootRes.body,
                    nested: nestedTestResponses,
                },
            };
        };
        const convertTestResponseToTest = (tcase, response) => {
            if (response.result === 'could-not-find-node') {
                return null;
            }
            const convertResToTest = (res) => {
                if (res.result === 'could-not-find-node') {
                    return { path: res.path, property: res.property, expectedOutput: [{
                                type: 'plain', value: 'Could not find',
                            }], nestedProperties: [] };
                }
                return {
                    path: res.path,
                    property: res.property,
                    expectedOutput: res.result.body,
                    nestedProperties: res.result.nested.map(convertResToTest),
                };
            };
            return {
                src: tcase.src,
                assertType: tcase.assertType,
                expectedOutput: response.result.body,
                nestedProperties: response.result.nested.map(convertResToTest),
                property: tcase.property,
                locator: tcase.locator,
                name: tcase.name,
            };
        };
        return {
            addTest,
            removeTest,
            listTestSuiteCategories,
            getTestSuite: doGetTestSuite,
            evaluateTest,
            addListener,
            removeListener,
            flushTestCaseData: () => {
                Object.keys(testStatusRepo).forEach(id => delete testStatusRepo[id]);
                notifyListeners('test-status-update');
            },
            fullyEvaluate,
            convertTestResponseToTest,
        };
    };
    exports.createTestManager = createTestManager;
    const testToNestedTestResponse = (src) => ({
        path: src.path,
        property: src.property,
        result: {
            body: src.expectedOutput,
            nested: src.nestedProperties.map(testToNestedTestResponse),
        },
    });
    const nestedTestResponseToTest = (src) => {
        if (src.result === 'could-not-find-node') {
            return null;
        }
        const nestedProperties = [];
        src.result.nested.forEach(nest => {
            const res = nestedTestResponseToTest(nest);
            if (res) {
                nestedProperties.push(res);
            }
        });
        return {
            path: src.path,
            property: src.property,
            expectedOutput: src.result.body,
            nestedProperties,
        };
    };
    exports.nestedTestResponseToTest = nestedTestResponseToTest;
});
define("model/ModalEnv", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
});
define("model/UpdatableNodeLocator", ["require", "exports", "model/adjustLocator"], function (require, exports, adjustLocator_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.createMutableLocator = exports.createImmutableLocator = void 0;
    adjustLocator_1 = __importDefault(adjustLocator_1);
    const createImmutableLocator = (source) => {
        // const callbacks: { [id: string]: () => void } = {};
        // const ourOwnListenerId = `immut-${(Math.random() * Number.MAX_SAFE_INTEGER)|0}`;
        // source.setUpdateCallback()
        // HMM How would this be cleaned up? Does it need to be?
        return {
            get: () => source.get(),
            set: () => { },
            adjust: () => { },
            isMutable: () => false,
            createMutableClone: () => createMutableLocator(JSON.parse(JSON.stringify(source.get()))),
            // setUpdateCallback: (id, callback) => {
            //   if (callback) {
            //     callbacks[id] = callback;
            //   } else {
            //     delete callbacks[id];
            //   }
            // },
        };
    };
    exports.createImmutableLocator = createImmutableLocator;
    const createMutableLocator = (locator) => {
        // const callbacks: { [id: string]: () => void } = {};
        return {
            get: () => locator,
            set: (val) => {
                locator = val;
                // Object.values(callbacks).forEach(cb => cb());
            },
            adjust: (adjusters) => {
                adjusters.forEach(adj => (0, adjustLocator_1.default)(adj, locator));
            },
            isMutable: () => true,
            createMutableClone: () => createMutableLocator(JSON.parse(JSON.stringify(locator))),
            // setUpdateCallback: (id, callback) => {
            //   if (callback) {
            //     callbacks[id] = callback;
            //   } else {
            //     delete callbacks[id];
            //   }
            // }
        };
    };
    exports.createMutableLocator = createMutableLocator;
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
define("ui/trimTypeName", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const trimTypeName = (typeName) => {
        const lastDot = typeName.lastIndexOf(".");
        return lastDot === -1 ? typeName : typeName.slice(lastDot + 1);
    };
    exports.default = trimTypeName;
});
define("ui/popup/formatAttr", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.formatAttrArgList = exports.formatAttrType = void 0;
    const formatAttrType = (orig) => {
        switch (orig.type) {
            case 'nodeLocator': return orig.value.type;
            case 'integer': return 'int';
            // case ''
            // case 'java.lang.String': return 'String';
            default: return orig.type;
        }
    };
    exports.formatAttrType = formatAttrType;
    const formatAttr = (attr) => `${attr.name.startsWith('l:') ? attr.name.slice(2) : attr.name}${(attr.args
        ? `(${attr.args.map(a => formatAttrType(a)).join(', ')})`
        : '')}`;
    const formatAttrArgList = (target, attr) => {
        var _a;
        (_a = attr.args) === null || _a === void 0 ? void 0 : _a.forEach((arg, argIdx) => {
            var _a, _b, _c;
            if (argIdx > 0) {
                target.appendChild(document.createTextNode(`,`));
            }
            switch (arg.type) {
                case 'string': {
                    const node = document.createElement('span');
                    node.classList.add('syntax-string');
                    node.innerText = `"${arg.value}"`;
                    target.appendChild(node);
                    break;
                }
                case 'integer': {
                    const node = document.createElement('span');
                    node.classList.add('syntax-int');
                    node.innerText = `${arg.value}`;
                    target.appendChild(node);
                    break;
                }
                case 'bool': {
                    const node = document.createElement('span');
                    node.classList.add('syntax-modifier');
                    node.innerText = `${arg.value}`;
                    target.appendChild(node);
                    break;
                }
                case 'nodeLocator': {
                    const node = document.createElement('span');
                    node.classList.add('syntax-type');
                    if (!arg.value || (typeof arg.value !== 'object')) {
                        node.innerText = `null`;
                    }
                    else {
                        node.innerText = (_c = (_b = (_a = arg.value.value) === null || _a === void 0 ? void 0 : _a.result) === null || _b === void 0 ? void 0 : _b.type) !== null && _c !== void 0 ? _c : arg.value.type; // .split('.')[0];
                    }
                    target.appendChild(node);
                    break;
                }
                case 'outputstream': {
                    const node = document.createElement('span');
                    node.classList.add('stream-arg-msg');
                    node.innerText = '<stream>';
                    target.appendChild(node);
                    break;
                }
                default: {
                    console.warn('Unsure of how to render', arg.type);
                    target.appendChild(document.createTextNode(`${arg.value}`));
                }
            }
        });
    };
    exports.formatAttrArgList = formatAttrArgList;
    exports.default = formatAttr;
});
define("ui/popup/displayArgModal", ["require", "exports", "hacks", "model/UpdatableNodeLocator", "ui/create/createModalTitle", "ui/create/createTextSpanIndicator", "ui/create/registerNodeSelector", "ui/create/registerOnHover", "ui/startEndToSpan", "ui/trimTypeName", "ui/popup/displayAttributeModal", "ui/popup/displayProbeModal", "ui/popup/formatAttr"], function (require, exports, hacks_2, UpdatableNodeLocator_1, createModalTitle_2, createTextSpanIndicator_2, registerNodeSelector_1, registerOnHover_2, startEndToSpan_2, trimTypeName_1, displayAttributeModal_1, displayProbeModal_1, formatAttr_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createModalTitle_2 = __importDefault(createModalTitle_2);
    createTextSpanIndicator_2 = __importDefault(createTextSpanIndicator_2);
    registerNodeSelector_1 = __importDefault(registerNodeSelector_1);
    registerOnHover_2 = __importDefault(registerOnHover_2);
    startEndToSpan_2 = __importDefault(startEndToSpan_2);
    trimTypeName_1 = __importDefault(trimTypeName_1);
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
    const displayArgModal = (env, modalPos, locator, attr, nested) => {
        const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
        let lastLocatorRequest = null;
        const cleanup = () => {
            if (window.ActiveLocatorRequest === lastLocatorRequest) {
                cancelLocatorRequest();
            }
            delete env.onChangeListeners[queryId];
            popup.remove();
        };
        const args = attr.args;
        if (!args || !args.length) {
            throw new Error('Created arg modal for attribute without arguments - create probe modal instead');
        }
        env.onChangeListeners[queryId] = (adjusters) => {
            if (adjusters) {
                locator.adjust(adjusters);
            }
        };
        const createTitle = () => {
            return (0, createModalTitle_2.default)({
                extraActions: [
                    {
                        title: 'Duplicate window',
                        invoke: () => {
                            const pos = popup.getPos();
                            displayArgModal(env, { x: pos.x + 10, y: pos.y + 10 }, locator, attr, nested);
                        },
                    }
                ],
                renderLeft: (container) => {
                    var _a;
                    const headType = document.createElement('span');
                    headType.classList.add('syntax-type');
                    headType.innerText = `${(_a = locator.get().result.label) !== null && _a !== void 0 ? _a : (0, trimTypeName_1.default)(locator.get().result.type)}`;
                    const headAttr = document.createElement('span');
                    headAttr.classList.add('syntax-attr');
                    headAttr.innerText = `.${(0, formatAttr_1.default)(attr)}`;
                    container.appendChild(headType);
                    container.appendChild(headAttr);
                },
                onClose: () => {
                    cleanup();
                },
            });
        };
        const popup = env.showWindow({
            pos: modalPos,
            rootStyle: `
      min-width: 16rem;
      min-height: 4rem;
    `,
            onForceClose: cleanup,
            render: (root) => {
                root.appendChild(createTitle().element);
                console.log('Show arg modal : ', JSON.stringify(attr, null, 2));
                const attrList = document.createElement('div');
                attrList.classList.add('attr-arg-list');
                const argValues = [];
                const proceed = () => {
                    var _a;
                    cleanup();
                    (0, displayProbeModal_1.default)(env, popup.getPos(), locator, {
                        name: attr.name,
                        args: (_a = attr.args) === null || _a === void 0 ? void 0 : _a.map((arg, argIdx) => argValues[argIdx]),
                    }, nested);
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
                    argType.innerText = (0, formatAttr_1.formatAttrType)(arg);
                    argHeader.appendChild(argType);
                    // argHeader.appendChild(document.createTextNode(` ${arg.name}`));
                    attrList.appendChild(argHeader);
                    const setupTextInput = (init, cleanupValue) => {
                        const inp = document.createElement('input');
                        inp.classList.add('attr-arg-input-text');
                        init(inp);
                        inp.placeholder = `Arg ${argIdx}`;
                        inp.oninput = () => {
                            // argValues[argIdx] = { type: 'string', value: cleanupValue(inp.value) };
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
                    argValues[argIdx] = arg;
                    switch (arg.type) {
                        case 'integer': {
                            // argValues[argIdx] = arg.value || '0';
                            attrList.appendChild(setupTextInput((elem) => {
                                elem.type = 'number';
                                elem.value = `${typeof argValues[argIdx].value === 'number' ? argValues[argIdx].value : ''}`;
                            }, (val) => ({ type: 'integer', value: parseInt(val, 10) || 0 })));
                            break;
                        }
                        case 'bool': {
                            argValues[argIdx] = { type: 'bool', value: arg.value === true };
                            attrList.appendChild(setupTwoPillInput((parent, left, right) => {
                                left.innerText = 'true';
                                right.innerText = 'false';
                            }, () => argValues[argIdx].value === true ? 'left' : 'right', (node, updateActive) => {
                                argValues[argIdx] = { type: 'bool', value: node === 'left' };
                                updateActive();
                            }));
                            break;
                        }
                        case 'collection': {
                            console.warn('todo should we support collections?');
                            attrList.appendChild(setupTextInput((elem) => {
                                elem.type = 'text';
                                elem.value = `${argValues[argIdx]}`;
                            }, id => ({ type: 'string', value: id })));
                            break;
                        }
                        case 'nodeLocator': {
                            const origLocator = arg.value;
                            let pickedNodePanel = document.createElement('div');
                            let pickedNodeHighlighter = () => { };
                            (0, registerOnHover_2.default)(pickedNodePanel, (on) => pickedNodeHighlighter(on));
                            let state = arg.value ? 'node' : 'null';
                            const refreshPickedNode = () => {
                                var _a;
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
                                const pickedNode = argValues[argIdx].value.value;
                                if (!pickedNode || typeof pickedNode !== 'object') {
                                    pickedNodePanel.style.display = 'block';
                                    pickedNodePanel.style.fontStyle = 'italic';
                                    pickedNodePanel.innerText = 'No node picked yet..';
                                    return;
                                }
                                const nodeWrapper = document.createElement('div');
                                (0, registerNodeSelector_1.default)(nodeWrapper, () => pickedNode);
                                nodeWrapper.addEventListener('click', () => {
                                    (0, displayAttributeModal_1.default)(env, null, (0, UpdatableNodeLocator_1.createMutableLocator)(pickedNode));
                                });
                                const span = (0, startEndToSpan_2.default)(pickedNode.result.start, pickedNode.result.end);
                                nodeWrapper.appendChild((0, createTextSpanIndicator_2.default)({
                                    span,
                                }));
                                const typeNode = document.createElement('span');
                                typeNode.classList.add('syntax-type');
                                typeNode.innerText = (_a = pickedNode.result.label) !== null && _a !== void 0 ? _a : (0, trimTypeName_1.default)(pickedNode.result.type);
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
                                    argValues[argIdx] = { type: 'nodeLocator', value: { type: origLocator.type, value: undefined } };
                                    cancelLocatorRequest();
                                }
                                else {
                                    state = 'node';
                                    lastLocatorRequest = startLocatorRequest(locator => {
                                        argValues[argIdx] = { type: 'nodeLocator', value: { type: origLocator.type, value: locator } };
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
                        case 'outputstream': {
                            const node = document.createElement('span');
                            node.innerText = '<captured to probe output>';
                            node.classList.add('stream-arg-msg');
                            attrList.appendChild(node);
                            break;
                        }
                        default: {
                            (0, hacks_2.assertUnreachable)(arg);
                            console.warn('Unknown arg type', arg, ', defaulting to string input');
                            // Fall through
                        }
                        case 'string': {
                            attrList.appendChild(setupTextInput((elem) => {
                                elem.type = 'text';
                                elem.value = `${argValues[argIdx].value}`;
                            }, id => ({ type: 'string', value: id })));
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
define("model/cullingTaskSubmitterFactory", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const createCullingTaskSubmitterFactory = (cullTime) => {
        if (typeof cullTime !== 'number') {
            return () => ({ submit: (cb) => cb(), cancel: () => { }, });
        }
        return () => {
            let localChangeDebounceTimer = -1;
            return {
                submit: (cb) => {
                    clearTimeout(localChangeDebounceTimer);
                    localChangeDebounceTimer = setTimeout(() => cb(), cullTime);
                },
                cancel: () => {
                    clearTimeout(localChangeDebounceTimer);
                },
            };
        };
    };
    exports.default = createCullingTaskSubmitterFactory;
});
define("ui/create/createStickyHighlightController", ["require", "exports", "ui/startEndToSpan"], function (require, exports, startEndToSpan_3) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    startEndToSpan_3 = __importDefault(startEndToSpan_3);
    const createStickyHighlightController = (env) => {
        const stickyId = `sticky-highlight-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
        let activeStickyColorClass = '';
        let currentTarget = null;
        let currentLocator = null;
        const applySticky = () => {
            if (!currentTarget || !currentLocator)
                return;
            env.setStickyHighlight(stickyId, {
                classNames: [
                    `monaco-rag-highlight-sticky`,
                    activeStickyColorClass,
                ],
                span: (0, startEndToSpan_3.default)(currentLocator.get().result.start, currentLocator.get().result.end),
            });
            currentTarget.classList.add(`monaco-rag-highlight-sticky`);
            currentTarget.classList.add(activeStickyColorClass);
        };
        return {
            onClick: () => {
                var _a, _b;
                if (!activeStickyColorClass) {
                    for (let i = 0; i < 10; ++i) {
                        document.querySelector;
                        activeStickyColorClass = `monaco-rag-highlight-sticky-${i}`;
                        if (!!document.querySelector(`.${activeStickyColorClass}`)) {
                            activeStickyColorClass = '';
                        }
                        else {
                            break;
                        }
                    }
                    if (!activeStickyColorClass) {
                        // More than 10 colors active, pick one pseudorandomly instead
                        activeStickyColorClass = `monaco-rag-highlight-sticky-${(Math.random() * 10) | 0}`;
                    }
                    applySticky();
                }
                else {
                    env.clearStickyHighlight(stickyId);
                    if (activeStickyColorClass) {
                        (_a = currentTarget === null || currentTarget === void 0 ? void 0 : currentTarget.classList) === null || _a === void 0 ? void 0 : _a.remove(`monaco-rag-highlight-sticky`);
                        (_b = currentTarget === null || currentTarget === void 0 ? void 0 : currentTarget.classList) === null || _b === void 0 ? void 0 : _b.remove(activeStickyColorClass);
                        activeStickyColorClass = '';
                    }
                }
            },
            cleanup: () => {
                if (activeStickyColorClass) {
                    env.clearStickyHighlight(stickyId);
                }
            },
            configure: (target, locator) => {
                currentTarget = target;
                currentLocator = locator;
                if (activeStickyColorClass) {
                    applySticky();
                }
                else {
                    env.clearStickyHighlight(stickyId);
                }
            }
        };
    };
    exports.default = createStickyHighlightController;
});
define("ui/popup/displayAstModal", ["require", "exports", "ui/create/createLoadingSpinner", "ui/create/createModalTitle", "ui/popup/displayHelp", "ui/popup/encodeRpcBodyLines", "ui/create/attachDragToX", "ui/popup/displayAttributeModal", "ui/create/createTextSpanIndicator", "model/cullingTaskSubmitterFactory", "ui/create/createStickyHighlightController", "ui/startEndToSpan", "model/UpdatableNodeLocator"], function (require, exports, createLoadingSpinner_1, createModalTitle_3, displayHelp_1, encodeRpcBodyLines_1, attachDragToX_3, displayAttributeModal_2, createTextSpanIndicator_3, cullingTaskSubmitterFactory_1, createStickyHighlightController_1, startEndToSpan_4, UpdatableNodeLocator_2) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createLoadingSpinner_1 = __importDefault(createLoadingSpinner_1);
    createModalTitle_3 = __importDefault(createModalTitle_3);
    displayHelp_1 = __importDefault(displayHelp_1);
    encodeRpcBodyLines_1 = __importDefault(encodeRpcBodyLines_1);
    attachDragToX_3 = __importDefault(attachDragToX_3);
    displayAttributeModal_2 = __importDefault(displayAttributeModal_2);
    createTextSpanIndicator_3 = __importDefault(createTextSpanIndicator_3);
    cullingTaskSubmitterFactory_1 = __importDefault(cullingTaskSubmitterFactory_1);
    createStickyHighlightController_1 = __importDefault(createStickyHighlightController_1);
    startEndToSpan_4 = __importDefault(startEndToSpan_4);
    const displayAstModal = (env, modalPos, locator, listDirection, initialTransform) => {
        var _a, _b, _c, _d, _e;
        const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
        let state = null;
        let lightTheme = env.themeIsLight();
        const stickyController = (0, createStickyHighlightController_1.default)(env);
        let fetchState = 'idle';
        const cleanup = () => {
            delete env.onChangeListeners[queryId];
            delete env.probeWindowStateSavers[queryId];
            delete env.themeChangeListeners[queryId];
            popup.remove();
            env.triggerWindowSave();
            stickyController.cleanup();
        };
        const onResizePtr = {
            callback: () => { },
        };
        const bufferingSaver = (0, cullingTaskSubmitterFactory_1.default)(100)();
        const saveAfterTransformChange = () => {
            bufferingSaver.submit(() => { env.triggerWindowSave(); });
        };
        const trn = {
            x: (_a = initialTransform === null || initialTransform === void 0 ? void 0 : initialTransform.x) !== null && _a !== void 0 ? _a : 1920 / 2,
            y: (_b = initialTransform === null || initialTransform === void 0 ? void 0 : initialTransform.y) !== null && _b !== void 0 ? _b : 0,
            scale: (_c = initialTransform === null || initialTransform === void 0 ? void 0 : initialTransform.scale) !== null && _c !== void 0 ? _c : 1,
            width: (_d = initialTransform === null || initialTransform === void 0 ? void 0 : initialTransform.width) !== null && _d !== void 0 ? _d : 0,
            height: (_e = initialTransform === null || initialTransform === void 0 ? void 0 : initialTransform.height) !== null && _e !== void 0 ? _e : 0,
        };
        let resetTranslationOnRender = !initialTransform;
        const popup = env.showWindow({
            pos: modalPos,
            size: (initialTransform === null || initialTransform === void 0 ? void 0 : initialTransform.width) && (initialTransform === null || initialTransform === void 0 ? void 0 : initialTransform.height) ? {
                width: initialTransform.width,
                height: initialTransform.height,
            } : undefined,
            rootStyle: `
      min-width: 24rem;
      min-height: 12rem;
      80vh;
    `,
            onForceClose: cleanup,
            onFinishedMove: () => {
                bufferingSaver.cancel();
                env.triggerWindowSave();
            },
            onOngoingResize: () => onResizePtr.callback(),
            onFinishedResize: () => {
                onResizePtr.callback();
                const size = popup.getSize();
                trn.width = size.width;
                trn.height = size.height;
            },
            resizable: true,
            render: (root, { bringToFront }) => {
                while (root.firstChild)
                    root.firstChild.remove();
                // root.innerText = 'Loading..';
                root.appendChild((0, createModalTitle_3.default)({
                    renderLeft: (container) => {
                        const headType = document.createElement('span');
                        headType.innerText = `AST`;
                        container.appendChild(headType);
                        const spanIndicator = (0, createTextSpanIndicator_3.default)({
                            span: (0, startEndToSpan_4.default)(locator.get().result.start, locator.get().result.end),
                            marginLeft: true,
                            onHover: on => env.updateSpanHighlight(on ? (0, startEndToSpan_4.default)(locator.get().result.start, locator.get().result.end) : null),
                            onClick: stickyController.onClick,
                        });
                        stickyController.configure(spanIndicator, locator);
                        container.appendChild(spanIndicator);
                    },
                    onClose: () => {
                        cleanup();
                    },
                    extraActions: [
                        ...(env.getGlobalModalEnv() === env ? [] : [{
                                title: 'Detatch window',
                                invoke: () => {
                                    cleanup();
                                    displayAstModal(env.getGlobalModalEnv(), null, locator.createMutableClone(), listDirection, trn);
                                }
                            }]),
                        {
                            title: 'Help',
                            invoke: () => {
                                (0, displayHelp_1.default)('ast', () => { });
                            }
                        },
                    ],
                }).element);
                const addSpinner = () => {
                    const spinner = (0, createLoadingSpinner_1.default)();
                    spinner.classList.add('absoluteCenter');
                    const spinnerWrapper = document.createElement('div');
                    spinnerWrapper.style.height = '7rem';
                    spinnerWrapper.style.display = 'block';
                    spinnerWrapper.style.position = 'relative';
                    spinnerWrapper.appendChild(spinner);
                    root.appendChild(spinnerWrapper);
                };
                if (state === null) {
                    addSpinner();
                    return;
                }
                if (state.type === 'err') {
                    if (state.body.length === 0) {
                        const text = document.createElement('span');
                        text.classList.add('captured-stderr');
                        text.innerText = `Failed listing tree`;
                        root.appendChild(text);
                        return;
                    }
                    root.appendChild((0, encodeRpcBodyLines_1.default)(env, state.body));
                }
                else {
                    // Build UI
                    root.style.display = 'flex';
                    root.style.flexDirection = 'column';
                    root.style.flexGrow = '1';
                    root.style.overflow = 'hidden';
                    const cv = document.createElement('canvas');
                    cv.width = 1920;
                    cv.height = 1080;
                    const wrapper = document.createElement('div');
                    wrapper.appendChild(cv);
                    wrapper.style.flexGrow = '1';
                    wrapper.style.minWidth = '4rem';
                    wrapper.style.minHeight = '4rem';
                    // wrapper.style.width = '100vw';
                    // wrapper.style.height = '100vh';
                    root.appendChild(wrapper);
                    const ctx = cv.getContext('2d');
                    if (!ctx) {
                        root.appendChild(document.createTextNode(`You browser doesn't seem to support HTML canvas 2D rendering mode.`));
                        return;
                    }
                    cv.style.width = '100%';
                    cv.style.height = '100%';
                    // cv.width  = cv.offsetWidth;
                    // cv.height = cv.offsetHeight;
                    cv.onmousedown = (e) => {
                        e.stopPropagation();
                    };
                    cv.style.cursor = 'default';
                    // const trn = Array(9).fill(0);
                    const lastClick = { x: 0, y: 0 };
                    const getScaleY = () => trn.scale * (cv.clientWidth / 1920) / (cv.clientHeight / 1080);
                    const clientToWorld = (pt, trnx = trn.x, trny = trn.y, scaleX = trn.scale, scaleY = getScaleY()) => {
                        const csx = 1920 / cv.clientWidth;
                        const x = (pt.x * csx - trnx) / scaleX;
                        const csy = 1080 / cv.clientHeight;
                        const y = (pt.y * csy - trny) / scaleY;
                        // ((pt.x * 1920 / (cv.clientWidth)) - (trnx)) / scaleX == REF
                        //
                        return { x, y };
                    };
                    const dragInfo = { x: trn.x, y: trn.y }; // , sx: 1, sy: 1 };
                    let hoverClick = 'no';
                    (0, attachDragToX_3.default)(cv, (e) => {
                        bringToFront();
                        dragInfo.x = trn.x;
                        dragInfo.y = trn.y;
                        const w = clientToWorld({ x: e.offsetX, y: e.offsetY });
                        lastClick.x = w.x;
                        lastClick.y = w.y;
                        hoverClick = 'maybe';
                        // dragInfo.sx = 1920 / cv.clientWidth;
                        // dragInfo.sy = 1080 / cv.clientHeight;
                    }, (dx, dy) => {
                        hoverClick = 'no';
                        const w = clientToWorld({
                            x: dx,
                            y: dy,
                        }, 0, 0, 1, 1);
                        trn.x = dragInfo.x + w.x;
                        trn.y = dragInfo.y + w.y;
                        // trn.x = dragInfo.x + dx * dragInfo.sx;
                        // trn.y = dragInfo.y + dy * dragInfo.sy;
                        renderFrame();
                        saveAfterTransformChange();
                    }, () => {
                        if (hoverClick == 'maybe') {
                            hoverClick = 'yes';
                            renderFrame();
                        }
                    });
                    cv.addEventListener('wheel', (e) => {
                        const ptx = e.offsetX;
                        const csx = 1920 / cv.clientWidth;
                        const trx1 = trn.x;
                        const z1 = trn.scale;
                        const z2 = Math.max(0.1, Math.min(10, trn.scale + e.deltaY / 100.0));
                        /*
                          -- We want to modify trn.x so that transforming {e.offsetX, e.offsetY} gets the same result before and after zooming.
                          -- For trn.x we want this relation to hold:
                          (ptx*csx - trx1) / z1 = (ptx*csx - trx2) / z2
                          -- All variables are known but trx2. rewrite a bit and we get:
                          trx2 = ptx*csx - (z2/z1)*(ptx*csx - trx1)
                        */
                        trn.x = (ptx * csx - (z2 / z1) * (ptx * csx - trx1));
                        // Same idea for trn.y
                        const csy = 1080 / cv.clientHeight;
                        trn.y = (e.offsetY * csy - (z2 / z1) * (e.offsetY * csy - trn.y));
                        // const w = clientToWorld({ x: e.offsetX, y: e.offsetY });
                        // trn.x += (w.x / trn.scale) * e.deltaX / 1000;
                        trn.scale = z2;
                        renderFrame();
                        saveAfterTransformChange();
                    });
                    let hover = null;
                    let hasActiveSpanHighlight = false;
                    cv.addEventListener('mousemove', e => {
                        hover = clientToWorld({ x: e.offsetX, y: e.offsetY });
                        hoverClick = 'no';
                        renderFrame();
                    });
                    cv.addEventListener('mouseleave', () => {
                        hover = null;
                        if (hasActiveSpanHighlight) {
                            hasActiveSpanHighlight = false;
                            env.updateSpanHighlight(null);
                        }
                        renderFrame();
                    });
                    const rootNode = state.data;
                    const nodew = 256 + 128;
                    const nodeh = 64;
                    const nodepadx = nodew * 0.05;
                    const nodepady = nodeh * 0.75;
                    const measureBoundingBox = (node) => {
                        if (node.boundingBox) {
                            return node.boundingBox;
                        }
                        let bb = { x: nodew, y: nodeh };
                        if (Array.isArray(node.children)) {
                            let childW = 0;
                            node.children.forEach((child, childIdx) => {
                                const childBox = measureBoundingBox(child);
                                if (childIdx >= 1) {
                                    childW += nodepadx;
                                }
                                childW += childBox.x;
                                bb.y = Math.max(bb.y, nodeh + nodepady + childBox.y);
                            });
                            bb.x = Math.max(bb.x, childW);
                        }
                        node.boundingBox = bb;
                        return bb;
                    };
                    const rootBox = measureBoundingBox(rootNode);
                    if (resetTranslationOnRender) {
                        resetTranslationOnRender = false;
                        trn.scale = 1;
                        trn.x = (1920 - rootBox.x) / 2;
                        trn.y = 0;
                    }
                    const renderFrame = () => {
                        const w = cv.width;
                        const h = cv.height;
                        ctx.resetTransform();
                        ctx.fillStyle = getThemedColor(lightTheme, 'probe-result-area');
                        ctx.fillRect(0, 0, w, h);
                        ctx.translate(trn.x, trn.y);
                        ctx.scale(trn.scale, getScaleY());
                        cv.style.cursor = 'default';
                        let didHighlightSomething = false;
                        const renderNode = (node, ox, oy) => {
                            var _a, _b;
                            const nodeBox = measureBoundingBox(node);
                            const renderx = ox + (nodeBox.x - nodew) / 2;
                            const rendery = oy;
                            if (hover && hover.x >= renderx && hover.x <= (renderx + nodew) && hover.y >= rendery && (hover.y < rendery + nodeh)) {
                                ctx.fillStyle = getThemedColor(lightTheme, 'ast-node-bg-hover');
                                cv.style.cursor = 'pointer';
                                const { start, end, external } = node.locator.result;
                                if (start && end && !external) {
                                    didHighlightSomething = true;
                                    hasActiveSpanHighlight = true;
                                    env.updateSpanHighlight({
                                        lineStart: (start >>> 12), colStart: (start & 0xFFF),
                                        lineEnd: (end >>> 12), colEnd: (end & 0xFFF),
                                    });
                                }
                                if (hoverClick === 'yes') {
                                    hoverClick = 'no';
                                    (0, displayAttributeModal_2.default)(env.getGlobalModalEnv(), null, (0, UpdatableNodeLocator_2.createMutableLocator)(node.locator));
                                }
                            }
                            else {
                                ctx.fillStyle = getThemedColor(lightTheme, 'ast-node-bg');
                            }
                            ctx.fillRect(renderx, rendery, nodew, nodeh);
                            ctx.strokeStyle = getThemedColor(lightTheme, 'separator');
                            if (node.locator.steps.length > 0 && node.locator.steps[node.locator.steps.length - 1].type === 'nta') {
                                ctx.setLineDash([5, 5]);
                                ctx.strokeRect(renderx, rendery, nodew, nodeh);
                                ctx.setLineDash([]);
                            }
                            else {
                                ctx.strokeRect(renderx, rendery, nodew, nodeh);
                            }
                            // ctx.fillStyle = `black`;
                            let fonth = (nodeh * 0.5) | 0;
                            renderText: while (true) {
                                ctx.font = `${fonth}px sans`;
                                const typeTail = ((_a = node.locator.result.label) !== null && _a !== void 0 ? _a : node.locator.result.type).split('\.').slice(-1)[0];
                                const txty = rendery + (nodeh - (nodeh - fonth) * 0.5);
                                if (node.name) {
                                    const typeTailMeasure = ctx.measureText(`: ${typeTail}`);
                                    const nameMeasure = ctx.measureText(node.name);
                                    const totalW = nameMeasure.width + typeTailMeasure.width;
                                    if (totalW > nodew && fonth > 16) {
                                        fonth = Math.max(16, fonth * 0.9 | 0);
                                        continue renderText;
                                    }
                                    const txtx = renderx + (nodew - totalW) / 2;
                                    ctx.fillStyle = getThemedColor(lightTheme, 'syntax-variable');
                                    ctx.fillText(node.name, txtx, txty);
                                    ctx.fillStyle = getThemedColor(lightTheme, 'syntax-type');
                                    // dark: 4EC9B0
                                    ctx.fillText(`: ${typeTail}`, txtx + nameMeasure.width, txty);
                                }
                                else {
                                    ctx.fillStyle = getThemedColor(lightTheme, 'syntax-type');
                                    const typeTailMeasure = ctx.measureText(typeTail);
                                    if (typeTailMeasure.width > nodew && fonth > 16) {
                                        fonth = Math.max(16, fonth * 0.9 | 0);
                                        continue renderText;
                                    }
                                    ctx.fillText(typeTail, renderx + (nodew - typeTailMeasure.width) / 2, txty);
                                }
                                break;
                            }
                            if (!Array.isArray(node.children)) {
                                if (((_b = node.children) === null || _b === void 0 ? void 0 : _b.type) == 'placeholder') {
                                    // More children available
                                    // console.log('placeholder:', node.children);
                                    const msg = `᠁`;
                                    const fonth = (nodeh * 0.5) | 0;
                                    ctx.font = `${fonth}px sans`;
                                    ctx.fillStyle = getThemedColor(lightTheme, 'separator');
                                    const cx = renderx + nodew / 2;
                                    const cy = rendery + nodeh + nodepady + fonth;
                                    ctx.strokeStyle = getThemedColor(lightTheme, 'separator');
                                    ctx.beginPath();
                                    ctx.moveTo(cx, rendery + nodeh);
                                    ctx.lineTo(cx, cy - fonth);
                                    ctx.stroke();
                                    if (hover && Math.hypot(cx - hover.x, cy - hover.y) < fonth) {
                                        ctx.strokeStyle = 'cyan';
                                        cv.style.cursor = 'pointer';
                                        if (hoverClick == 'yes') {
                                            hoverClick = 'no';
                                            displayAstModal(env.getGlobalModalEnv(), null, (0, UpdatableNodeLocator_2.createMutableLocator)(node.locator), 'downwards');
                                        }
                                    }
                                    const msgMeasure = ctx.measureText(msg);
                                    ctx.fillText(msg, renderx + (nodew - msgMeasure.width) / 2, cy + fonth * 0.33);
                                    ctx.beginPath();
                                    ctx.arc(cx, cy, fonth, 0, Math.PI * 2);
                                    ctx.stroke();
                                }
                                return;
                            }
                            let childOffX = 0;
                            const childOffY = nodeh + nodepady;
                            node.children.forEach((child, childIdx) => {
                                const chbb = measureBoundingBox(child);
                                if (childIdx >= 1) {
                                    childOffX += nodepadx;
                                }
                                renderNode(child, ox + childOffX, oy + childOffY);
                                ctx.strokeStyle = getThemedColor(lightTheme, 'separator');
                                ctx.lineWidth = 2;
                                ctx.beginPath(); // Start a new path
                                ctx.moveTo(renderx + nodew / 2, rendery + nodeh);
                                const paddedBottomY = rendery + nodeh + nodepady * 0.5;
                                ctx.lineTo(renderx + nodew / 2, paddedBottomY);
                                // ctx.lineTo(ox + childOffX + chbb.x / 2, oy + childOffY); // Draw a line to (150, 100)
                                const chx = ox + childOffX + chbb.x / 2;
                                // ctx.bezierCurveTo()
                                ctx.arcTo(chx, paddedBottomY, chx, oy + childOffY, nodepady / 2);
                                ctx.lineTo(chx, oy + childOffY);
                                ctx.stroke(); // Render the path
                                ctx.lineWidth = 1;
                                childOffX += chbb.x;
                            });
                        };
                        renderNode(rootNode, 0, 32);
                        if (!didHighlightSomething) {
                            if (hasActiveSpanHighlight) {
                                hasActiveSpanHighlight = false;
                                env.updateSpanHighlight(null);
                            }
                        }
                    };
                    renderFrame();
                    onResizePtr.callback = () => {
                        renderFrame();
                    };
                }
                if (fetchState !== 'idle') {
                    const spinner = (0, createLoadingSpinner_1.default)();
                    spinner.classList.add('absoluteCenter');
                    root.appendChild(spinner);
                }
            },
        });
        const refresher = env.createCullingTaskSubmitter();
        env.onChangeListeners[queryId] = (adjusters) => {
            if (adjusters) {
                locator.adjust(adjusters);
            }
            refresher.submit(() => {
                fetchAttrs();
                popup.refresh();
            });
        };
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
            env.performTypedRpc({
                locator: locator.get(),
                src: env.createParsingRequestData(),
                type: listDirection === 'upwards' ? 'ListTreeUpwards' : 'ListTreeDownwards'
            })
                .then((result) => {
                var _a;
                const refetch = fetchState == 'queued';
                fetchState = 'idle';
                if (refetch)
                    fetchAttrs();
                const parsed = result.node;
                if (!parsed) {
                    // root.appendChild(createTitle('err'));
                    if ((_a = result.body) === null || _a === void 0 ? void 0 : _a.length) {
                        state = { type: 'err', body: result.body };
                        popup.refresh();
                        // root.appendChild(encodeRpcBodyLines(env, parsed.body));
                        return;
                    }
                    throw new Error('Unexpected response body "' + JSON.stringify(result) + '"');
                }
                // Handle resp
                if (result.locator) {
                    locator.set(result.locator);
                }
                const mapNode = (src) => ({
                    type: src.type,
                    locator: src.locator,
                    name: src.name,
                    children: src.children.type === 'children'
                        ? src.children.value.map(mapNode)
                        : { type: 'placeholder', num: src.children.value },
                });
                state = { type: 'ok', data: mapNode(parsed) };
                popup.refresh();
            })
                .catch(err => {
                console.warn('Error when loading attributes', err);
                state = { type: 'err', body: [] };
                popup.refresh();
            });
        };
        fetchAttrs();
        env.probeWindowStateSavers[queryId] = (target) => {
            target.push({
                modalPos: popup.getPos(),
                data: {
                    type: 'ast',
                    locator: locator.get(),
                    direction: listDirection,
                    transform: { ...trn, },
                },
            });
        };
        env.themeChangeListeners[queryId] = (light) => {
            lightTheme = light;
            onResizePtr.callback();
        };
        env.triggerWindowSave();
    };
    exports.default = displayAstModal;
});
define("ui/popup/displayAttributeModal", ["require", "exports", "ui/create/createLoadingSpinner", "ui/create/createModalTitle", "ui/popup/displayProbeModal", "ui/popup/displayArgModal", "ui/popup/formatAttr", "ui/create/createTextSpanIndicator", "ui/popup/displayHelp", "settings", "ui/popup/encodeRpcBodyLines", "ui/trimTypeName", "ui/popup/displayAstModal", "ui/startEndToSpan"], function (require, exports, createLoadingSpinner_2, createModalTitle_4, displayProbeModal_2, displayArgModal_1, formatAttr_2, createTextSpanIndicator_4, displayHelp_2, settings_2, encodeRpcBodyLines_2, trimTypeName_2, displayAstModal_1, startEndToSpan_5) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createLoadingSpinner_2 = __importDefault(createLoadingSpinner_2);
    createModalTitle_4 = __importDefault(createModalTitle_4);
    displayProbeModal_2 = __importDefault(displayProbeModal_2);
    displayArgModal_1 = __importDefault(displayArgModal_1);
    formatAttr_2 = __importDefault(formatAttr_2);
    createTextSpanIndicator_4 = __importDefault(createTextSpanIndicator_4);
    displayHelp_2 = __importDefault(displayHelp_2);
    settings_2 = __importDefault(settings_2);
    encodeRpcBodyLines_2 = __importDefault(encodeRpcBodyLines_2);
    trimTypeName_2 = __importDefault(trimTypeName_2);
    displayAstModal_1 = __importDefault(displayAstModal_1);
    startEndToSpan_5 = __importDefault(startEndToSpan_5);
    const displayAttributeModal = (env, modalPos, locator) => {
        const queryId = `attr-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
        console.log('dAM');
        let filter = '';
        let state = null;
        let fetchState = 'idle';
        const cleanup = () => {
            console.log('DAM, cleanup');
            delete env.onChangeListeners[queryId];
            popup.remove();
        };
        let isFirstRender = true;
        const popup = env.showWindow({
            pos: modalPos,
            rootStyle: `
      min-width: 16rem;
      min-height: 8rem;
      80vh;
    `,
            onForceClose: cleanup,
            render: (root) => {
                while (root.firstChild)
                    root.firstChild.remove();
                root.appendChild((0, createModalTitle_4.default)({
                    renderLeft: (container) => {
                        var _a;
                        if (env === env.getGlobalModalEnv()) {
                            const headType = document.createElement('span');
                            headType.classList.add('syntax-type');
                            headType.innerText = `${(_a = locator.get().result.label) !== null && _a !== void 0 ? _a : (0, trimTypeName_2.default)(locator.get().result.type)}`;
                            container.appendChild(headType);
                        }
                        const headAttr = document.createElement('span');
                        headAttr.classList.add('syntax-attr');
                        headAttr.innerText = `.?`;
                        container.appendChild(headAttr);
                        container.appendChild((0, createTextSpanIndicator_4.default)({
                            span: (0, startEndToSpan_5.default)(locator.get().result.start, locator.get().result.end),
                            marginLeft: true,
                            onHover: on => env.updateSpanHighlight(on ? (0, startEndToSpan_5.default)(locator.get().result.start, locator.get().result.end) : null),
                        }));
                    },
                    onClose: () => {
                        cleanup();
                    },
                    extraActions: [
                        {
                            title: 'Help',
                            invoke: () => {
                                (0, displayHelp_2.default)('property-list-usage', () => { });
                            }
                        },
                        {
                            title: 'Render AST downwards',
                            invoke: () => {
                                cleanup();
                                (0, displayAstModal_1.default)(env, popup.getPos(), locator, 'downwards');
                            }
                        },
                        {
                            title: 'Render AST upwards',
                            invoke: () => {
                                cleanup();
                                (0, displayAstModal_1.default)(env, popup.getPos(), locator, 'upwards');
                            }
                        },
                    ],
                }).element);
                const addSpinner = () => {
                    const spinner = (0, createLoadingSpinner_2.default)();
                    spinner.classList.add('absoluteCenter');
                    const spinnerWrapper = document.createElement('div');
                    spinnerWrapper.style.height = '7rem';
                    spinnerWrapper.style.display = 'block';
                    spinnerWrapper.style.position = 'relative';
                    spinnerWrapper.appendChild(spinner);
                    root.appendChild(spinnerWrapper);
                };
                if (state === null) {
                    addSpinner();
                    return;
                }
                if (state.type === 'err') {
                    if (state.body.length === 0) {
                        const text = document.createElement('span');
                        text.classList.add('captured-stderr');
                        // text.style.color = '#F88';
                        text.innerText = `Failed listing properties`;
                        root.appendChild(text);
                        return;
                    }
                    root.appendChild((0, encodeRpcBodyLines_2.default)(env, state.body));
                }
                else {
                    const attrs = state.attrs;
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
                                (0, displayProbeModal_2.default)(env, popup.getPos(), locator, { name: attr.name }, {});
                            }
                            else {
                                if (attr.args.every(arg => arg.type === 'outputstream')) {
                                    // Shortcut directly to probe since there is nothing for user to add in arg modal
                                    (0, displayProbeModal_2.default)(env, popup.getPos(), locator, attr, {});
                                }
                                else {
                                    (0, displayArgModal_1.default)(env, popup.getPos(), locator, attr, {});
                                }
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
                        }
                        misses.forEach((attr, idx) => buildNode(attr, idx > 0, !matches.length && misses.length === 1));
                    };
                    resortList();
                    root.appendChild(sortedAttrs);
                }
                if (fetchState !== 'idle') {
                    const spinner = (0, createLoadingSpinner_2.default)();
                    spinner.classList.add('absoluteCenter');
                    root.appendChild(spinner);
                }
            },
        });
        const refresher = env.createCullingTaskSubmitter();
        console.log('DAM, inserting', queryId);
        env.onChangeListeners[queryId] = (adjusters) => {
            if (adjusters) {
                locator.adjust(adjusters);
            }
            refresher.submit(() => {
                fetchAttrs();
                popup.refresh();
            });
        };
        console.log('changeListeners:', env.onChangeListeners);
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
            env.performTypedRpc({
                locator: locator.get(),
                src: env.createParsingRequestData(),
                type: 'ListProperties',
                all: settings_2.default.shouldShowAllProperties(),
            })
                .then((result) => {
                var _a;
                const refetch = fetchState == 'queued';
                fetchState = 'idle';
                if (refetch)
                    fetchAttrs();
                const parsed = result.properties;
                if (!parsed) {
                    if ((_a = result.body) === null || _a === void 0 ? void 0 : _a.length) {
                        state = { type: 'err', body: result.body };
                        popup.refresh();
                        return;
                    }
                    throw new Error('Unexpected response body "' + JSON.stringify(result) + '"');
                }
                const uniq = [];
                const deudplicator = new Set();
                parsed.forEach(attr => {
                    const uniqId = JSON.stringify(attr);
                    if (deudplicator.has(uniqId)) {
                        return;
                    }
                    deudplicator.add(uniqId);
                    uniq.push(attr);
                });
                state = { type: 'attrs', attrs: uniq };
                popup.refresh();
            })
                .catch(err => {
                console.warn('Error when loading attributes', err);
                state = { type: 'err', body: [] };
                popup.refresh();
            });
        };
        fetchAttrs();
    };
    exports.default = displayAttributeModal;
});
define("ui/popup/encodeRpcBodyLines", ["require", "exports", "model/UpdatableNodeLocator", "ui/create/createTextSpanIndicator", "ui/create/registerNodeSelector", "ui/create/registerOnHover", "ui/trimTypeName", "ui/popup/displayAttributeModal"], function (require, exports, UpdatableNodeLocator_3, createTextSpanIndicator_5, registerNodeSelector_2, registerOnHover_3, trimTypeName_3, displayAttributeModal_3) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createTextSpanIndicator_5 = __importDefault(createTextSpanIndicator_5);
    registerNodeSelector_2 = __importDefault(registerNodeSelector_2);
    registerOnHover_3 = __importDefault(registerOnHover_3);
    trimTypeName_3 = __importDefault(trimTypeName_3);
    displayAttributeModal_3 = __importDefault(displayAttributeModal_3);
    const getCommonStreamArgWhitespacePrefix = (line) => {
        if (Array.isArray(line)) {
            return Math.min(...line.map(getCommonStreamArgWhitespacePrefix));
        }
        if (line && typeof line === 'object') {
            switch (line.type) {
                case 'streamArg': {
                    const v = line.value;
                    return v.length - v.trimStart().length;
                }
            }
        }
        return Number.MAX_SAFE_INTEGER;
    };
    const encodeRpcBodyLines = (env, body, extras = {}) => {
        let needCapturedStreamArgExplanation = false;
        const appliedDecoratorResultsTrackers = [];
        const applyDecoratorClass = (target, applyRoot, decoratorResult) => {
            appliedDecoratorResultsTrackers.forEach(tracker => tracker[decoratorResult] = true);
            switch (decoratorResult) {
                case 'error':
                    target.classList.add('test-diff-error');
                    if (applyRoot) {
                        target.classList.add('test-diff-error-root');
                    }
                    break;
                case 'unmatched':
                    target.classList.add('test-diff-unmatched');
                    if (applyRoot) {
                        target.classList.add('test-diff-unmatched-root');
                    }
                    break;
                default:
                    break;
            }
        };
        const streamArgPrefix = Math.min(...body.map(getCommonStreamArgWhitespacePrefix));
        const encodeLine = (target, line, nestingLevel, bodyPath) => {
            switch (line.type) {
                case 'plain': {
                    const trimmed = line.value.trimStart();
                    if (extras.decorator) {
                        const decoration = extras.decorator(bodyPath);
                        if (decoration !== 'default') {
                            const holder = document.createElement('spawn');
                            if (extras.capWidths) {
                                holder.style.whiteSpace = 'normal';
                            }
                            else {
                                holder.style.whiteSpace = 'pre';
                            }
                            if (trimmed.length !== line.value.length) {
                                holder.appendChild(document.createTextNode(' '.repeat(line.value.length - trimmed.length)));
                            }
                            if (line.value.trim()) {
                                holder.appendChild(document.createTextNode(line.value.trim()));
                            }
                            holder.appendChild(document.createElement('br'));
                            applyDecoratorClass(holder, nestingLevel <= 1, decoration);
                            target.appendChild(holder);
                            break;
                        }
                    }
                    if (trimmed.length !== line.value.length) {
                        target.appendChild(document.createTextNode(' '.repeat(line.value.length - trimmed.length)));
                    }
                    if (line.value.trim()) {
                        target.appendChild(document.createTextNode(line.value.trim()));
                    }
                    target.appendChild(document.createElement('br'));
                    break;
                }
                case 'arr': {
                    const encodeTo = (arrTarget) => {
                        let lineIdxBackshift = 0;
                        line.value.forEach((sub, idx) => {
                            encodeLine(arrTarget, sub, nestingLevel + 1, [...bodyPath, idx - lineIdxBackshift]);
                            if (extras.excludeStdIoFromPaths) {
                                switch (sub.type) {
                                    case 'stdout':
                                    case 'stderr':
                                        ++lineIdxBackshift;
                                }
                            }
                        });
                    };
                    if (nestingLevel === 0) {
                        // First level indent, 'inline' it
                        encodeTo(target);
                    }
                    else {
                        // >=2 level indent, respect it
                        if (nestingLevel === 1) {
                            appliedDecoratorResultsTrackers.push({});
                        }
                        const deeper = document.createElement('pre');
                        deeper.style.marginLeft = '1rem';
                        deeper.style.marginTop = '0.125rem';
                        if (extras.capWidths) {
                            deeper.style.whiteSpace = 'normal';
                        }
                        encodeTo(deeper);
                        if (nestingLevel === 1) {
                            const wrapper = document.createElement('div');
                            wrapper.appendChild(deeper);
                            const tracker = appliedDecoratorResultsTrackers.pop();
                            if (tracker === null || tracker === void 0 ? void 0 : tracker['error']) {
                                wrapper.classList.add('test-diff-error-root');
                            }
                            else if (tracker === null || tracker === void 0 ? void 0 : tracker['unmatched']) {
                                wrapper.classList.add('test-diff-unmatched-root');
                            }
                            target.appendChild(wrapper);
                        }
                        else {
                            target.appendChild(deeper);
                        }
                    }
                    break;
                }
                case 'stdout': {
                    const span = document.createElement('span');
                    span.classList.add('captured-stdout');
                    span.innerText = `> ${line.value}`;
                    target.appendChild(span);
                    target.appendChild(document.createElement('br'));
                    break;
                }
                case 'stderr': {
                    const span = document.createElement('span');
                    span.classList.add('captured-stderr');
                    span.innerText = `> ${line.value}`;
                    target.appendChild(span);
                    target.appendChild(document.createElement('br'));
                    break;
                }
                case 'streamArg': {
                    needCapturedStreamArgExplanation = true;
                    const span = document.createElement('span');
                    span.classList.add('stream-arg-msg');
                    span.innerText = `> ${line.value.slice(streamArgPrefix)}`;
                    target.appendChild(span);
                    target.appendChild(document.createElement('br'));
                    break;
                }
                case "node": {
                    const { start, end, type, label } = line.value.result;
                    const container = document.createElement('div');
                    if (extras.decorator) {
                        applyDecoratorClass(container, nestingLevel <= 1, extras.decorator(bodyPath));
                    }
                    const span = {
                        lineStart: (start >>> 12), colStart: (start & 0xFFF),
                        lineEnd: (end >>> 12), colEnd: (end & 0xFFF),
                    };
                    container.appendChild((0, createTextSpanIndicator_5.default)({
                        span,
                        marginLeft: false,
                        autoVerticalMargin: true,
                    }));
                    const typeNode = document.createElement('span');
                    typeNode.classList.add('syntax-type');
                    typeNode.innerText = label !== null && label !== void 0 ? label : (0, trimTypeName_3.default)(type);
                    typeNode.style.margin = 'auto 0';
                    container.appendChild(typeNode);
                    container.classList.add('clickHighlightOnHover');
                    container.style.width = 'fit-content';
                    container.style.display = 'inline';
                    (0, registerOnHover_3.default)(container, on => {
                        var _a, _b;
                        if (!on || ((_b = (_a = extras.lateInteractivityEnabledChecker) === null || _a === void 0 ? void 0 : _a.call(extras)) !== null && _b !== void 0 ? _b : true)) {
                            env.updateSpanHighlight(on ? span : null);
                            container.style.cursor = 'default';
                            container.classList.add('clickHighlightOnHover');
                        }
                        else {
                            container.style.cursor = 'not-allowed';
                            container.classList.remove('clickHighlightOnHover');
                        }
                    });
                    container.onmousedown = (e) => {
                        e.stopPropagation();
                    };
                    if (!extras.disableNodeSelectors) {
                        (0, registerNodeSelector_2.default)(container, () => line.value);
                    }
                    container.addEventListener('click', () => {
                        var _a, _b;
                        if ((_b = (_a = extras.lateInteractivityEnabledChecker) === null || _a === void 0 ? void 0 : _a.call(extras)) !== null && _b !== void 0 ? _b : true) {
                            (0, displayAttributeModal_3.default)(env.getGlobalModalEnv(), null, (0, UpdatableNodeLocator_3.createMutableLocator)(line.value));
                        }
                    });
                    if (extras.nodeLocatorExpanderHandler) {
                        // if (existing) {
                        //   if (existing.parentElement) existing.parentElement.removeChild(existing);
                        //   target.appendChild(existing);
                        // } else {
                        const middleContainer = document.createElement('div');
                        middleContainer.style.display = 'flex';
                        middleContainer.style.flexDirection = 'row';
                        container.style.display = 'flex';
                        middleContainer.appendChild(container);
                        if (!extras.disableInlineExpansionButton) {
                            const expander = document.createElement('div');
                            expander.innerText = `▼`;
                            expander.style.marginLeft = '0.25rem';
                            expander.classList.add('linkedProbeCreator');
                            expander.onmouseover = e => e.stopPropagation();
                            expander.onmousedown = (e) => {
                                e.stopPropagation();
                            };
                            middleContainer.appendChild(expander);
                            const clickHandler = extras.nodeLocatorExpanderHandler.onClick;
                            expander.onclick = () => clickHandler({
                                locator: line.value,
                                locatorRoot: outerContainer,
                                expansionArea,
                                path: bodyPath,
                            });
                        }
                        const outerContainer = document.createElement('div');
                        outerContainer.style.display = 'flex';
                        outerContainer.style.flexDirection = 'column';
                        outerContainer.appendChild(middleContainer);
                        const existingExpansionArea = extras.nodeLocatorExpanderHandler.getReusableExpansionArea(bodyPath);
                        if (existingExpansionArea) {
                            if (existingExpansionArea.parentElement) {
                                existingExpansionArea.parentElement.removeChild(existingExpansionArea);
                            }
                        }
                        const expansionArea = existingExpansionArea !== null && existingExpansionArea !== void 0 ? existingExpansionArea : document.createElement('div');
                        outerContainer.appendChild(expansionArea);
                        target.appendChild(outerContainer);
                        extras.nodeLocatorExpanderHandler.onCreate({
                            locator: line.value,
                            locatorRoot: outerContainer,
                            expansionArea,
                            path: bodyPath,
                        });
                        // }
                    }
                    else {
                        target.appendChild(container);
                    }
                    break;
                }
                default: {
                    console.warn('Unknown body line type', line);
                    break;
                }
            }
        };
        const pre = document.createElement(extras.decorator ? 'div' : 'pre');
        pre.style.margin = '0px';
        pre.style.padding = '0.5rem';
        pre.style.fontSize = '0.75rem';
        if (extras.capWidths) {
            pre.style.wordBreak = 'break-word';
            pre.style.whiteSpace = 'normal';
        }
        // pre.innerHtml = lines.slice(outputStart + 1).join('\n').trim();
        let lineIdxBackshift = 0;
        body
            .filter((line, lineIdx, arr) => {
            // Keep empty lines only if they are followed by a non-empty line
            // Removes two empty lines in a row, and removes trailing empty lines
            if (!line && !arr[lineIdx + 1]) {
                return false;
            }
            return true;
        })
            .forEach((line, lineIdx) => {
            if (extras.decorator) {
                const holder = document.createElement('pre');
                holder.style.margin = '0px';
                holder.style.padding = '0';
                if (extras.capWidths) {
                    holder.style.wordBreak = 'break-word';
                    holder.style.whiteSpace = 'normal';
                }
                applyDecoratorClass(holder, true, extras.decorator([lineIdx - lineIdxBackshift]));
                encodeLine(holder, line, 0, [lineIdx - lineIdxBackshift]);
                pre.appendChild(holder);
            }
            else {
                encodeLine(pre, line, 0, [lineIdx - lineIdxBackshift]);
            }
            if (extras.excludeStdIoFromPaths) {
                switch (line.type) {
                    case 'stdout':
                    case 'stderr':
                        ++lineIdxBackshift;
                }
            }
            // '\n'
        });
        if (needCapturedStreamArgExplanation) {
            const expl = document.createElement('span');
            expl.style.fontStyle = 'italic';
            expl.style.fontSize = '0.5rem';
            // expl.classList.add('syntax-attr-dim');
            const add = (msg, styled) => {
                const node = document.createElement('span');
                if (styled) {
                    node.classList.add('stream-arg-msg');
                }
                node.innerText = msg;
                expl.appendChild(node);
            };
            add(`Values printed to `, false);
            add(`<stream>`, true);
            add(` shown in `, false);
            add(`green`, true);
            add(` above`, false);
            pre.appendChild(expl);
            pre.appendChild(document.createElement('br'));
        }
        return pre;
    };
    exports.default = encodeRpcBodyLines;
});
define("ui/popup/displayTestAdditionModal", ["require", "exports", "ui/create/createLoadingSpinner", "ui/create/createModalTitle", "ui/create/showWindow"], function (require, exports, createLoadingSpinner_3, createModalTitle_5, showWindow_3) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createLoadingSpinner_3 = __importDefault(createLoadingSpinner_3);
    createModalTitle_5 = __importDefault(createModalTitle_5);
    showWindow_3 = __importDefault(showWindow_3);
    const displayTestAdditionModal = (env, modalPos, locator, request) => {
        const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
        // Capture `baseProps` immediately, in case the user modifies text while this dialog is open
        const baseProps = env.createParsingRequestData();
        let categories = 'loading';
        const fullEvaluate = () => env.testManager.fullyEvaluate(baseProps, request.property, locator, request.nested, 'Get reference output');
        const cleanup = () => {
            popup.remove();
        };
        const popup = (0, showWindow_3.default)({
            rootStyle: `
      width: 32rem;
      min-height: 8rem;
    `,
            pos: modalPos,
            onForceClose: cleanup,
            resizable: true,
            render: (root) => {
                while (root.firstChild)
                    root.removeChild(root.firstChild);
                root.appendChild((0, createModalTitle_5.default)({
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
                        const spinner = (0, createLoadingSpinner_3.default)();
                        spinner.classList.add('absoluteCenter');
                        root.appendChild(spinner);
                    }
                    else {
                        const textHolder = document.createElement('div');
                        textHolder.style.padding = '0.5rem';
                        textHolder.innerText = `Failed listing test categories, have you set '-Dcpr.testDir=<a valid directory>'?`;
                    }
                }
                else {
                    const addRow = (title, cb) => {
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
                    const state = {
                        category: '',
                        name: '',
                        assertionType: 'Exact Match'
                    };
                    let update = () => { };
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
                        const category = row.querySelector(`#cat-${queryId}`);
                        // row.appendChild(category);
                        // category.outerHTML = `<input list=${datalistId}></input>`;
                        category.type = 'text';
                        const datalist = document.createElement('datalist');
                        datalist.id = datalistId;
                        categories.forEach(cat => {
                            const opt = document.createElement('option');
                            opt.value = cat.lastIndexOf('.') > 0 ? cat.slice(0, cat.lastIndexOf('.')) : cat;
                            datalist.appendChild(opt);
                        });
                        row.appendChild(datalist);
                        console.log('cat:', category);
                        category.oninput = () => {
                            console.log('change???');
                            state.category = category.value;
                            console.log('cat: ', state.category);
                            update();
                        };
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
                        const assertionTypeList = ['Exact Match', 'Set Comparison', 'Smoke Test'];
                        assertionTypeList.forEach((option) => {
                            const val = document.createElement('option');
                            val.value = option;
                            val.innerText = option;
                            opts.appendChild(val);
                        });
                        opts.oninput = () => {
                            state.assertionType = opts.value;
                            update();
                        };
                    });
                    let errMsg;
                    let commitButton = document.createElement('button');
                    addRow('', (row) => {
                        commitButton.innerText = 'Add';
                        row.appendChild(commitButton);
                        commitButton.onclick = () => {
                            commitButton.disabled = true;
                            fullEvaluate()
                                .then((evalRes) => {
                                const convTest = env.testManager.convertTestResponseToTest({
                                    src: baseProps,
                                    assertType: state.assertionType === 'Exact Match'
                                        ? 'IDENTITY'
                                        : (state.assertionType === 'Set Comparison'
                                            ? 'SET'
                                            : 'SMOKE'),
                                    expectedOutput: [],
                                    nestedProperties: [],
                                    property: evalRes.property,
                                    locator,
                                    name: state.name,
                                }, evalRes);
                                if (convTest === null) {
                                    throw new Error(`Couldn't locate root test node`);
                                }
                                env.testManager.addTest(state.category, convTest, false).then((result) => {
                                    if (result === 'ok') {
                                        cleanup();
                                        return;
                                    }
                                    else {
                                        console.warn('Error when adding test:', result);
                                        commitButton.disabled = false;
                                        errMsg.innerText = `Error when adding test. Please try again or check server log for more information.`;
                                    }
                                }).catch(err => {
                                    console.warn('Failed adding test:', err);
                                    commitButton.disabled = false;
                                    errMsg.innerText = `Failed adding test. Please try again`;
                                });
                            })
                                .catch((err) => {
                                commitButton.disabled = false;
                                console.warn('Failed getting test reference data:', err);
                                errMsg.innerText = `Failed creating test. Please try again`;
                            });
                            // typeof line === 'string' ? line : { naive: line, robust: line }
                        };
                    });
                    addRow('', (row) => {
                        errMsg = document.createElement('p');
                        errMsg.style.textAlign = 'center';
                        errMsg.classList.add('captured-stderr');
                        row.appendChild(errMsg);
                        update = () => {
                            commitButton.disabled = true;
                            if (!state.category) {
                                errMsg.innerText = `Missing category. The test will be saved in a file called '<category>.json'`;
                            }
                            else if (!/^[a-zA-Z0-9-_]+$/.test(state.category)) {
                                errMsg.innerText = `Invalid category, please only use '[a-zA-Z0-9-_]' in the name, i.e A-Z, numbers, dash (-) and underscore (_).'`;
                            }
                            else if (!state.name) {
                                errMsg.innerText = `Missing test name`;
                            }
                            else {
                                commitButton.disabled = false;
                                errMsg.innerText = '';
                            }
                        };
                    });
                    update();
                }
            },
        });
        env.testManager.listTestSuiteCategories()
            .then((result) => {
            categories = result;
            popup.refresh();
        });
    };
    exports.default = displayTestAdditionModal;
});
define("ui/renderProbeModalTitleLeft", ["require", "exports", "ui/create/createTextSpanIndicator", "ui/create/registerNodeSelector", "ui/popup/displayArgModal", "ui/popup/displayAttributeModal", "ui/popup/formatAttr", "ui/startEndToSpan", "ui/trimTypeName"], function (require, exports, createTextSpanIndicator_6, registerNodeSelector_3, displayArgModal_2, displayAttributeModal_4, formatAttr_3, startEndToSpan_6, trimTypeName_4) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createTextSpanIndicator_6 = __importDefault(createTextSpanIndicator_6);
    registerNodeSelector_3 = __importDefault(registerNodeSelector_3);
    displayArgModal_2 = __importDefault(displayArgModal_2);
    displayAttributeModal_4 = __importDefault(displayAttributeModal_4);
    formatAttr_3 = __importStar(formatAttr_3);
    startEndToSpan_6 = __importDefault(startEndToSpan_6);
    trimTypeName_4 = __importDefault(trimTypeName_4);
    const renderProbeModalTitleLeft = (env, container, close, getWindowPos, stickyController, locator, attr, nested, typeRenderingStyle) => {
        var _a, _b, _c;
        if (typeRenderingStyle !== 'minimal-nested') {
            const headType = document.createElement('span');
            headType.classList.add('syntax-type');
            headType.innerText = `${(_a = locator.get().result.label) !== null && _a !== void 0 ? _a : (0, trimTypeName_4.default)(locator.get().result.type)}`;
            container.appendChild(headType);
        }
        const headAttr = document.createElement('span');
        headAttr.classList.add('syntax-attr');
        if (!attr.args || attr.args.length === 0) {
            headAttr.innerText = `.${(0, formatAttr_3.default)(attr)}`;
        }
        else {
            headAttr.appendChild(document.createTextNode(`.${attr.name}(`));
            (0, formatAttr_3.formatAttrArgList)(headAttr, attr);
            headAttr.appendChild(document.createTextNode(`)`));
        }
        if (env) {
            headAttr.onmousedown = (e) => { e.stopPropagation(); };
            headAttr.classList.add('clickHighlightOnHover');
            headAttr.onclick = (e) => {
                if (env.duplicateOnAttr() != e.shiftKey) {
                    (0, displayAttributeModal_4.default)(env, null, locator.isMutable() ? locator.createMutableClone() : locator);
                }
                else {
                    close === null || close === void 0 ? void 0 : close();
                    (0, displayAttributeModal_4.default)(env, getWindowPos(), locator);
                }
                e.stopPropagation();
            };
        }
        container.appendChild(headAttr);
        if (((_b = attr.args) === null || _b === void 0 ? void 0 : _b.length) && env) {
            const editButton = document.createElement('img');
            editButton.src = '/icons/edit_white_24dp.svg';
            editButton.classList.add('modalEditButton');
            editButton.classList.add('clickHighlightOnHover');
            editButton.onmousedown = (e) => { e.stopPropagation(); };
            editButton.onclick = () => {
                close === null || close === void 0 ? void 0 : close();
                (0, displayArgModal_2.default)(env, getWindowPos(), locator, attr, nested);
            };
            container.appendChild(editButton);
        }
        if (env) {
            const spanIndicator = (0, createTextSpanIndicator_6.default)({
                span: (0, startEndToSpan_6.default)(locator.get().result.start, locator.get().result.end),
                marginLeft: true,
                onHover: on => env.updateSpanHighlight(on ? (0, startEndToSpan_6.default)(locator.get().result.start, locator.get().result.end) : null),
                onClick: (_c = stickyController === null || stickyController === void 0 ? void 0 : stickyController.onClick) !== null && _c !== void 0 ? _c : undefined,
                external: locator.get().result.external,
            });
            stickyController === null || stickyController === void 0 ? void 0 : stickyController.configure(spanIndicator, locator);
            (0, registerNodeSelector_3.default)(spanIndicator, () => locator.get());
            container.appendChild(spanIndicator);
        }
    };
    exports.default = renderProbeModalTitleLeft;
});
define("ui/create/createInlineWindowManager", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const createInlineArea = (args) => {
        let { inlineRoot, expansionAreaInsideTheRoot } = args;
        const applyActiveRootStyling = () => {
            inlineRoot.style.border = '1px solid black';
            inlineRoot.style.paddingTop = '0.25rem';
        };
        const activeWindowClosers = [];
        const localChangeListeners = {};
        // const activeWindowRefreshers: (() => void)[] = [];
        let activeSubWindowCount = 0;
        const area = {
            getNestedModalEnv: (parentEnv) => ({
                ...parentEnv,
                showWindow: (args) => area.add(args),
                onChangeListeners: localChangeListeners,
                probeWindowStateSavers: args.localWindowStateSaves,
            }),
            add: (args) => {
                if (activeSubWindowCount === 0) {
                    applyActiveRootStyling();
                    expansionAreaInsideTheRoot.style.marginTop = '0.25rem';
                }
                ++activeSubWindowCount;
                const closer = () => args.onForceClose();
                activeWindowClosers.push(closer);
                // const refresher = () => renderFn();
                // activeWindowRefreshers.push(refresher);
                const localDiv = document.createElement('div');
                if (args.rootStyle) {
                    localDiv.style = args.rootStyle;
                }
                localDiv.style.overflow = 'scroll';
                localDiv.classList.add('subWindow');
                expansionAreaInsideTheRoot.prepend(localDiv);
                let lastCancelToken = {};
                const renderFn = () => {
                    lastCancelToken.cancelled = true;
                    lastCancelToken = {};
                    args.render(localDiv, {
                        cancelToken: lastCancelToken,
                        bringToFront: () => { }, // TODO bring this from somewhere
                    });
                };
                renderFn();
                return {
                    getPos: () => ({ x: 0, y: 0 }),
                    getSize: () => ({ width: localDiv.clientWidth, height: localDiv.clientHeight }),
                    refresh: () => renderFn(),
                    remove: () => {
                        console.log('TODO remove me');
                        const parent = localDiv.parentElement;
                        if (parent)
                            parent.removeChild(localDiv);
                        --activeSubWindowCount;
                        if (activeSubWindowCount === 0) {
                            inlineRoot.style.border = 'none';
                        }
                        const idx = activeWindowClosers.findIndex(x => x === closer);
                        if (idx !== -1) {
                            activeWindowClosers.splice(idx, 1);
                            // activeWindowRefreshers.splice(idx, 1);
                        }
                    },
                };
            },
            notifyListenersOfChange: () => {
                Object.values(localChangeListeners).forEach(chl => chl());
            }
        };
        return {
            area,
            destroyer: () => {
                activeWindowClosers.forEach(closer => closer());
            },
            updateRoot: (newRoot) => {
                inlineRoot = newRoot;
                if (activeSubWindowCount > 0) {
                    applyActiveRootStyling();
                }
            },
            // refresh: () => {
            //   activeWindowRefreshers.forEach(closer => closer());
            // },
        };
    };
    const createInlineWindowManager = () => {
        const areas = {};
        const encodeAreaId = (raw) => JSON.stringify(raw);
        const decodeAreaId = (raw) => JSON.parse(raw);
        return {
            getPreviousExpansionArea: (areaId) => { var _a, _b; return (_b = (_a = areas[encodeAreaId(areaId)]) === null || _a === void 0 ? void 0 : _a.expansionArea) !== null && _b !== void 0 ? _b : null; },
            getPreviouslyAssociatedLocator: (areaId) => { var _a, _b; return (_b = (_a = areas[encodeAreaId(areaId)]) === null || _a === void 0 ? void 0 : _a.locator) !== null && _b !== void 0 ? _b : null; },
            getArea: (areaId, inlineRoot, expansionAreaInsideTheRoot, locator) => {
                const encodedId = encodeAreaId(areaId);
                if (!areas[encodedId]) {
                    const localWindowStateSaves = {};
                    // areaToLocalWindowStateSaves[id] =
                    areas[encodedId] = {
                        localWindowStateSaves,
                        expansionArea: expansionAreaInsideTheRoot,
                        locator,
                        area: createInlineArea({
                            inlineRoot,
                            expansionAreaInsideTheRoot,
                            localWindowStateSaves,
                        })
                    };
                }
                else {
                    areas[encodedId].area.updateRoot(inlineRoot);
                }
                return areas[encodedId].area.area;
            },
            getWindowStates: () => {
                const ret = {};
                Object.entries(areas).forEach(([key, val]) => {
                    const states = [];
                    Object.values(val.localWindowStateSaves).forEach(saver => saver(states));
                    ret[key] = states;
                });
                return ret;
            },
            destroy: () => Object.values(areas).forEach(area => area.area.destroyer()),
            conditiionallyDestroyAreas: (predicate) => {
                Object.entries(areas).forEach(([key, val]) => {
                    if (predicate(decodeAreaId(key))) {
                        delete areas[key];
                        val.area.destroyer();
                    }
                });
            },
            notifyListenersOfChange: () => Object.values(areas).forEach(area => area.area.area.notifyListenersOfChange()),
            // refresh: () => Object.values(areas).forEach(area => area.area.refresh()),
        };
    };
    exports.default = createInlineWindowManager;
});
define("ui/popup/displayProbeModal", ["require", "exports", "ui/create/createLoadingSpinner", "ui/create/createModalTitle", "model/adjustLocator", "ui/popup/displayHelp", "ui/popup/encodeRpcBodyLines", "ui/create/createStickyHighlightController", "ui/popup/displayTestAdditionModal", "ui/renderProbeModalTitleLeft", "settings", "ui/popup/displayAttributeModal", "ui/popup/displayAstModal", "ui/create/createInlineWindowManager", "model/UpdatableNodeLocator"], function (require, exports, createLoadingSpinner_4, createModalTitle_6, adjustLocator_2, displayHelp_3, encodeRpcBodyLines_3, createStickyHighlightController_2, displayTestAdditionModal_1, renderProbeModalTitleLeft_1, settings_3, displayAttributeModal_5, displayAstModal_2, createInlineWindowManager_1, UpdatableNodeLocator_4) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createLoadingSpinner_4 = __importDefault(createLoadingSpinner_4);
    createModalTitle_6 = __importDefault(createModalTitle_6);
    displayHelp_3 = __importDefault(displayHelp_3);
    encodeRpcBodyLines_3 = __importDefault(encodeRpcBodyLines_3);
    createStickyHighlightController_2 = __importDefault(createStickyHighlightController_2);
    displayTestAdditionModal_1 = __importDefault(displayTestAdditionModal_1);
    renderProbeModalTitleLeft_1 = __importDefault(renderProbeModalTitleLeft_1);
    settings_3 = __importDefault(settings_3);
    displayAttributeModal_5 = __importDefault(displayAttributeModal_5);
    displayAstModal_2 = __importDefault(displayAstModal_2);
    createInlineWindowManager_1 = __importDefault(createInlineWindowManager_1);
    const displayProbeModal = (env, modalPos, locator, property, nestedWindows) => {
        const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
        // console.log('displayProbeModal, nested:', nestedWindows, 'attr:', property.name, ', query:', queryId);
        const localErrors = [];
        env.probeMarkers[queryId] = localErrors;
        const stickyController = (0, createStickyHighlightController_2.default)(env);
        let lastOutput = [];
        let activelyLoadingJob = null;
        let loading = false;
        let isCleanedUp = false;
        nestedWindows = { ...nestedWindows }; // Make copy so we can locally modify it
        // const activeNests: {
        //   [id: string]: {
        //     getWindowStates: () => WindowState[];
        //     actives: ActiveNesting[]
        //   }
        // } = {};
        const inlineWindowManager = (0, createInlineWindowManager_1.default)();
        // const inline
        const doStopJob = (jobId) => env.performTypedRpc({
            type: 'Concurrent:StopJob',
            job: jobId,
        }).then(res => {
            if (res.err) {
                console.warn('Error when stopping job:', res.err);
                return false;
            }
            return true;
        });
        const cleanup = () => {
            queryWindow.remove();
            isCleanedUp = true;
            delete env.onChangeListeners[queryId];
            delete env.probeMarkers[queryId];
            delete env.probeWindowStateSavers[queryId];
            env.currentlyLoadingModals.delete(queryId);
            env.triggerWindowSave();
            if (localErrors.length > 0) {
                env.updateMarkers();
            }
            stickyController.cleanup();
            console.log('cleanup: ', { loading, activelyLoadingJob });
            if (loading && activelyLoadingJob !== null) {
                doStopJob(activelyLoadingJob);
            }
            inlineWindowManager.destroy();
        };
        let copyBody = [];
        const getNestedTestRequests = (path, state) => {
            const nested = [];
            Object.entries(state.nested).forEach(([path, val]) => {
                const pathIndexes = JSON.parse(path);
                val.forEach(v => {
                    if (v.data.type !== 'probe') {
                        return;
                    }
                    nested.push(getNestedTestRequests(pathIndexes, v.data));
                });
            });
            return { path, property: state.property, nested, };
        };
        const createTitle = () => {
            return (0, createModalTitle_6.default)({
                extraActions: [
                    ...(env.getGlobalModalEnv() === env
                        ? [{
                                title: 'Duplicate window',
                                invoke: () => {
                                    const pos = queryWindow.getPos();
                                    displayProbeModal(env, { x: pos.x + 10, y: pos.y + 10 }, locator.createMutableClone(), property, nestedWindows);
                                },
                            }]
                        : [{
                                title: 'Detatch window',
                                invoke: () => {
                                    const pos = queryWindow.getPos();
                                    cleanup();
                                    displayProbeModal(env.getGlobalModalEnv(), null, locator.createMutableClone(), property, nestedWindows);
                                },
                            }]),
                    {
                        title: 'Copy input to clipboard',
                        invoke: () => {
                            navigator.clipboard.writeText(JSON.stringify({ locator: locator.get(), property }, null, 2));
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
                            (0, displayHelp_3.default)('probe-window', () => { });
                        }
                    },
                    {
                        title: 'Magic output messages help',
                        invoke: () => {
                            (0, displayHelp_3.default)('magic-stdout-messages', () => { });
                        }
                    },
                    ...[
                        settings_3.default.shouldEnableTesting() && (env === env.getGlobalModalEnv()) && {
                            title: 'Save as test',
                            invoke: () => {
                                const nestedReq = getNestedTestRequests([], getWindowStateData());
                                (0, displayTestAdditionModal_1.default)(env, queryWindow.getPos(), locator.get(), {
                                    property: nestedReq.property,
                                    nested: nestedReq.nested,
                                });
                            },
                        }
                    ].filter(Boolean),
                ],
                // onDuplicate: () => {
                //   const pos = queryWindow.getPos();
                //   displayProbeModal(env, { x: pos.x + 10, y: pos.y + 10 }, JSON.parse(JSON.stringify(locator)), attr);
                // },
                renderLeft: (container) => (0, renderProbeModalTitleLeft_1.default)(env, container, () => {
                    cleanup();
                }, () => queryWindow.getPos(), stickyController, locator, property, nestedWindows, env === env.getGlobalModalEnv() ? 'default' : 'minimal-nested'),
                onClose: () => {
                    cleanup();
                },
            });
        };
        let lastSpinner = null;
        let isFirstRender = true;
        let refreshOnDone = false;
        const queryWindow = env.showWindow({
            pos: modalPos,
            rootStyle: `
      min-width: 16rem;
      min-height: fit-content;
    `,
            resizable: true,
            onFinishedMove: () => env.triggerWindowSave(),
            onForceClose: cleanup,
            render: (root, { cancelToken, bringToFront }) => {
                if (lastSpinner != null) {
                    lastSpinner.style.display = 'inline-block';
                    lastSpinner = null;
                }
                else if (isFirstRender) {
                    isFirstRender = false;
                    while (root.firstChild)
                        root.removeChild(root.firstChild);
                    root.appendChild(createTitle().element);
                    const spinner = (0, createLoadingSpinner_4.default)();
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
                const doFetch = () => new Promise(async (resolve, reject) => {
                    var _a;
                    let isDone = false;
                    let isConnectedToConcurrentCapableServer = false;
                    let statusPre = null;
                    let localConcurrentCleanup = () => { };
                    const initialPollDelayTimer = setTimeout(() => {
                        if (isDone || isCleanedUp || !isConnectedToConcurrentCapableServer) {
                            return;
                        }
                        const stop = document.createElement('button');
                        stop.innerText = 'Stop';
                        stop.onclick = () => {
                            doStopJob(jobId).then(stopped => {
                                if (stopped) {
                                    isDone = true;
                                    resolve('stopped');
                                }
                                // Else, job might have finished just as the user clicked stop
                            });
                        };
                        root.appendChild(stop);
                        statusPre = document.createElement('p');
                        statusPre.style.whiteSpace = 'pre';
                        statusPre.style.fontFamily = 'monospace';
                        root.appendChild(statusPre);
                        statusPre.innerText = `Request takes a while, polling status..\nIf you see message for longer than a few milliseconds then the job hasn't started running yet, or the server is severely overloaded.`;
                        localConcurrentCleanup = () => {
                            root.removeChild(stop);
                            if (statusPre) {
                                root.removeChild(statusPre);
                            }
                        };
                        const poll = () => {
                            if (isDone || isCleanedUp) {
                                return;
                            }
                            env.performTypedRpc({
                                type: 'Concurrent:PollWorkerStatus',
                                job: jobId,
                            })
                                .then(res => {
                                if (res.ok) {
                                    // Polled OK, async update will be delivered to job monitor below
                                    // Queue future poll.
                                    setTimeout(poll, 1000);
                                }
                                else {
                                    console.warn('Error when polling for job status');
                                    // Don't queue more polling, very unlikely to work anyway.
                                }
                            });
                        };
                        poll();
                    }, 5000);
                    const jobId = env.createJobId(data => {
                        isConnectedToConcurrentCapableServer = true;
                        let knownStatus = 'Unknown';
                        let knownStackTrace = null;
                        const refreshStatusPre = () => {
                            if (!statusPre) {
                                return;
                            }
                            const lines = [];
                            lines.push(`Property evaluation is taking a while, status below:`);
                            lines.push(`Status: ${knownStackTrace}`);
                            if (knownStackTrace) {
                                lines.push("Stack trace:");
                                knownStackTrace.forEach((ste) => lines.push(`> ${ste}`));
                            }
                            statusPre.innerText = lines.join('\n');
                        };
                        switch (data.value.type) {
                            case 'status': {
                                knownStatus = data.value.value;
                                refreshStatusPre();
                                break;
                            }
                            case 'workerStackTrace': {
                                knownStackTrace = data.value.value;
                                refreshStatusPre();
                                break;
                            }
                            case 'workerTaskDone': {
                                const res = data.value.value;
                                isDone = true;
                                localConcurrentCleanup();
                                if (res.type === 'normal') {
                                    const cast = res.value;
                                    if (cast.response.type == 'job') {
                                        throw new Error(`Unexpected 'job' result in async update`);
                                    }
                                    resolve(cast.response.value);
                                }
                                else {
                                    console.log('Worker task failed. This is likely an internal CodeProber issue. Error message:');
                                    res.value.forEach(line => console.log(line));
                                    reject('Worker failed');
                                }
                                break;
                            }
                            default: {
                                // Ignore
                            }
                        }
                    });
                    activelyLoadingJob = jobId;
                    env.performTypedRpc({
                        type: 'EvaluateProperty',
                        property,
                        locator: locator.get(),
                        src: env.createParsingRequestData(),
                        captureStdout: settings_3.default.shouldCaptureStdio(),
                        job: jobId,
                        jobLabel: `Probe: '${`${(_a = locator.get().result.label) !== null && _a !== void 0 ? _a : locator.get().result.type}`.split('.').slice(-1)[0]}.${property.name}'`,
                    })
                        .then(data => {
                        if (data.response.type === 'job') {
                            // Async work queued, not done.
                            isConnectedToConcurrentCapableServer = true;
                        }
                        else {
                            // Sync work executed, done.
                            clearTimeout(initialPollDelayTimer);
                            isDone = true;
                            resolve(data.response.value);
                        }
                    })
                        .catch(err => {
                        isDone = true;
                        reject(err);
                    });
                });
                doFetch()
                    .then((parsed) => {
                    var _a, _b;
                    loading = false;
                    if (parsed === 'stopped') {
                        refreshOnDone = false;
                    }
                    if (refreshOnDone) {
                        refreshOnDone = false;
                        queryWindow.refresh();
                    }
                    if (cancelToken.cancelled) {
                        return;
                    }
                    if (parsed === 'stopped') {
                        while (root.firstChild)
                            root.removeChild(root.firstChild);
                        root.append(createTitle().element);
                        const p = document.createElement('p');
                        p.innerText = `Property evaluation stopped. If any update happens (e.g text or setting changed within CodeProber), evaluation will be attempted again.`;
                        root.appendChild(p);
                    }
                    else {
                        const body = parsed.body;
                        copyBody = body;
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
                        if (!refreshOnDone) {
                            env.currentlyLoadingModals.delete(queryId);
                        }
                        while (root.firstChild)
                            root.removeChild(root.firstChild);
                        let refreshMarkers = localErrors.length > 0;
                        localErrors.length = 0;
                        localErrors.push(...((_a = parsed.errors) !== null && _a !== void 0 ? _a : []));
                        // parsed.errors?.forEach(({severity, start: errStart, end: errEnd, msg }) => {
                        //   localErrors.push({ severity, errStart, errEnd, msg });
                        // })
                        const updatedArgs = parsed.args;
                        if (updatedArgs) {
                            refreshMarkers = true;
                            (_b = property.args) === null || _b === void 0 ? void 0 : _b.forEach((arg, argIdx) => {
                                arg.type = updatedArgs[argIdx].type;
                                // arg.detail = updatedArgs[argIdx].detail;
                                arg.value = updatedArgs[argIdx].value;
                            });
                        }
                        if (parsed.locator) {
                            refreshMarkers = true;
                            locator.set(parsed.locator);
                            // locator = parsed.locator;
                        }
                        if (refreshMarkers || localErrors.length > 0) {
                            env.updateMarkers();
                        }
                        const titleRow = createTitle();
                        root.append(titleRow.element);
                        lastOutput = body;
                        // TODO, record which lines are used. Clear inline windows that are no longer usable.
                        const enableExpander = body.length >= 1 && (body[0].type === 'node' || (body[0].type === 'arr' && body[0].value.length >= 1 && body[0].value[0].type === 'node'));
                        const areasToKeep = new Set();
                        // const enableExpander = true;
                        root.appendChild((0, encodeRpcBodyLines_3.default)(env, body, {
                            nodeLocatorExpanderHandler: enableExpander ? ({
                                getReusableExpansionArea: (path) => {
                                    return inlineWindowManager.getPreviousExpansionArea(path);
                                },
                                onCreate: ({ locator, locatorRoot, expansionArea, path: nestId }) => {
                                    var _a;
                                    areasToKeep.add(JSON.stringify(nestId));
                                    const updLocator = (_a = inlineWindowManager.getPreviouslyAssociatedLocator(nestId)) !== null && _a !== void 0 ? _a : (0, UpdatableNodeLocator_4.createMutableLocator)(locator);
                                    updLocator.set(locator);
                                    const area = inlineWindowManager.getArea(nestId, locatorRoot, expansionArea, updLocator);
                                    const nestedEnv = area.getNestedModalEnv(env);
                                    const encodedId = JSON.stringify(nestId);
                                    const nests = nestedWindows[encodedId];
                                    if (!nests) {
                                        return;
                                    }
                                    delete nestedWindows[encodedId];
                                    nests.forEach(nest => {
                                        // nest.ty
                                        switch (nest.data.type) {
                                            case 'probe': {
                                                const dat = nest.data;
                                                displayProbeModal(nestedEnv, null, (0, UpdatableNodeLocator_4.createImmutableLocator)(updLocator), dat.property, dat.nested);
                                                break;
                                            }
                                            case 'ast': {
                                                const dat = nest.data;
                                                (0, displayAstModal_2.default)(nestedEnv, null, (0, UpdatableNodeLocator_4.createImmutableLocator)(updLocator), dat.direction, dat.transform);
                                                break;
                                            }
                                        }
                                    });
                                },
                                onClick: ({ locator, locatorRoot, expansionArea, path: nestId }) => {
                                    const prevLocator = inlineWindowManager.getPreviouslyAssociatedLocator(nestId);
                                    if (!prevLocator) {
                                        console.warn('OnClick on unknown area:', nestId);
                                        return;
                                    }
                                    prevLocator.set(locator);
                                    const area = inlineWindowManager.getArea(nestId, locatorRoot, expansionArea, prevLocator);
                                    const nestedEnv = area.getNestedModalEnv(env);
                                    (0, displayAttributeModal_5.default)(nestedEnv, null, (0, UpdatableNodeLocator_4.createImmutableLocator)(prevLocator));
                                    env.triggerWindowSave();
                                },
                            }) : undefined,
                            // nodeLocatorExpanderHandler: () => {},
                        }));
                        inlineWindowManager.conditiionallyDestroyAreas((areaId) => {
                            return !areasToKeep.has(JSON.stringify(areaId));
                        });
                        inlineWindowManager.notifyListenersOfChange();
                    }
                    const spinner = (0, createLoadingSpinner_4.default)();
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
                    root.innerText = 'Failed refreshing probe..';
                    console.warn('Failed refreshing probe w/ args:', JSON.stringify({ locator, property }, null, 2));
                    setTimeout(() => {
                        queryWindow.remove();
                        cleanup();
                    }, 2000);
                });
            },
        });
        const refresher = env.createCullingTaskSubmitter();
        env.onChangeListeners[queryId] = (adjusters) => {
            var _a;
            if (adjusters) {
                locator.adjust(adjusters);
                (_a = property.args) === null || _a === void 0 ? void 0 : _a.forEach((arg) => {
                    adjusters.forEach(adj => (0, adjustLocator_2.adjustValue)(adj, arg));
                });
            }
            if (loading) {
                refreshOnDone = true;
            }
            else {
                refresher.submit(() => queryWindow.refresh());
            }
        };
        // locator.setUpdateCallback(queryId, () => {
        //   if (loading) {
        //     refreshOnDone = true;
        //   } else {
        //     refresher.submit(() => queryWindow.refresh());
        //   }
        // });
        const getWindowStateData = () => {
            return {
                type: 'probe',
                locator: locator.get(),
                property,
                nested: inlineWindowManager.getWindowStates(),
            };
        };
        env.probeWindowStateSavers[queryId] = (target) => {
            // const nested: NestedWindows = {};
            // if (env === env.getGlobalModalEnv()) console.log('saving, activeNests:', activeNests);
            // Object.entries(activeNests).forEach(([key, vals]) => {
            //   nested[key] = vals.getWindowStates();
            // });
            // if (env === env.getGlobalModalEnv()) console.log('...resulting nested:', nested);
            target.push({
                modalPos: queryWindow.getPos(),
                data: getWindowStateData(),
            });
        };
        env.triggerWindowSave();
    };
    exports.default = displayProbeModal;
});
define("ui/popup/displayRagModal", ["require", "exports", "ui/create/createLoadingSpinner", "ui/create/createModalTitle", "ui/popup/displayAttributeModal", "ui/create/registerOnHover", "ui/create/showWindow", "ui/create/registerNodeSelector", "ui/popup/encodeRpcBodyLines", "ui/trimTypeName", "model/UpdatableNodeLocator"], function (require, exports, createLoadingSpinner_5, createModalTitle_7, displayAttributeModal_6, registerOnHover_4, showWindow_4, registerNodeSelector_4, encodeRpcBodyLines_4, trimTypeName_5, UpdatableNodeLocator_5) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createLoadingSpinner_5 = __importDefault(createLoadingSpinner_5);
    createModalTitle_7 = __importDefault(createModalTitle_7);
    displayAttributeModal_6 = __importDefault(displayAttributeModal_6);
    registerOnHover_4 = __importDefault(registerOnHover_4);
    showWindow_4 = __importDefault(showWindow_4);
    registerNodeSelector_4 = __importDefault(registerNodeSelector_4);
    encodeRpcBodyLines_4 = __importDefault(encodeRpcBodyLines_4);
    trimTypeName_5 = __importDefault(trimTypeName_5);
    const displayRagModal = (env, line, col) => {
        const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
        const cleanup = () => {
            delete env.onChangeListeners[queryId];
            popup.remove();
        };
        const popup = (0, showWindow_4.default)({
            rootStyle: `
        min-width: 12rem;
        min-height: 4rem;
      `,
            onForceClose: cleanup,
            render: (root, { cancelToken }) => {
                root.style.display = 'contents';
                const spinner = (0, createLoadingSpinner_5.default)();
                spinner.classList.add('absoluteCenter');
                root.appendChild(spinner);
                const createTitle = (status) => (0, createModalTitle_7.default)({
                    renderLeft: (container) => {
                        const headType = document.createElement('span');
                        headType.classList.add('syntax-stype');
                        headType.innerText = status === 'ok' ? 'Select node..' : `⚠️ Node listing failed..`;
                        container.appendChild(headType);
                    },
                    onClose: () => {
                        cleanup();
                    },
                }).element;
                env.performTypedRpc({
                    src: env.createParsingRequestData(),
                    pos: (line << 12) + col,
                    type: 'ListNodes',
                })
                    .then((parsed) => {
                    var _a;
                    if (cancelToken.cancelled) {
                        return;
                    }
                    while (root.firstChild)
                        root.removeChild(root.firstChild);
                    root.style.minHeight = '4rem';
                    if (!parsed.nodes) {
                        root.appendChild(createTitle('err'));
                        if ((_a = parsed.body) === null || _a === void 0 ? void 0 : _a.length) {
                            root.appendChild((0, encodeRpcBodyLines_4.default)(env, parsed.body));
                            return;
                        }
                        throw new Error(`Couldn't find expected line or body in output '${JSON.stringify(parsed)}'`);
                    }
                    root.appendChild(createTitle('ok'));
                    const rowsContainer = document.createElement('div');
                    rowsContainer.style.padding = '2px';
                    root.appendChild(rowsContainer);
                    parsed.nodes.forEach((locator, entIdx) => {
                        const { start, end, type, label } = locator.result;
                        const span = { lineStart: (start >>> 12), colStart: (start & 0xFFF), lineEnd: (end >>> 12), colEnd: (end & 0xFFF) };
                        const node = document.createElement('div');
                        node.classList.add('clickHighlightOnHover');
                        node.style.padding = `0 0.25rem`;
                        if (entIdx !== 0) {
                            node.style.borderTop = '1px solid gray';
                        }
                        node.innerText = `${label !== null && label !== void 0 ? label : (0, trimTypeName_5.default)(type)}${start === 0 && end === 0 ? ` ⚠️<No position>` : ''}`;
                        (0, registerOnHover_4.default)(node, on => env.updateSpanHighlight(on ? span : null));
                        node.onmousedown = (e) => { e.stopPropagation(); };
                        (0, registerNodeSelector_4.default)(node, () => locator);
                        node.onclick = () => {
                            cleanup();
                            env.updateSpanHighlight(null);
                            (0, displayAttributeModal_6.default)(env, popup.getPos(), (0, UpdatableNodeLocator_5.createMutableLocator)(locator));
                        };
                        rowsContainer.appendChild(node);
                    });
                })
                    .catch(err => {
                    if (cancelToken.cancelled) {
                        return;
                    }
                    // TODO handle this better, show an informative, refresh-aware modal that doesn't autoclose
                    // When starting it might be nice to open a modal and then tinker with settings until it refreshes successfully
                    console.warn('query failed', err);
                    while (root.firstChild)
                        root.removeChild(root.firstChild);
                    root.appendChild(createTitle('err'));
                    const errMsg = document.createElement('div');
                    errMsg.style.display = 'flex';
                    errMsg.style.justifyContent = 'center';
                    errMsg.style.padding = 'auto';
                    errMsg.style.textAlign = 'center';
                    errMsg.style.color = '#F88';
                    errMsg.style.padding = '0.25rem';
                    errMsg.innerText = 'Parsing failed..\nPerhaps a custom file suffix\nor main args override would help?\nLook at your terminal for more information.';
                    root.appendChild(errMsg);
                    // setTimeout(() => cleanup(), 1000);
                });
            }
        });
        const refresher = env.createCullingTaskSubmitter();
        env.onChangeListeners[queryId] = (adjusters) => {
            if (adjusters) {
                adjusters.forEach((adj) => {
                    const [l, c] = adj(line, col);
                    line = l;
                    col = c;
                });
            }
            refresher.submit(() => popup.refresh());
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
define("ui/popup/displayStatistics", ["require", "exports", "ui/create/createModalTitle", "ui/create/showWindow"], function (require, exports, createModalTitle_8, showWindow_5) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createModalTitle_8 = __importDefault(createModalTitle_8);
    showWindow_5 = __importDefault(showWindow_5);
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
            },
            {
                title: 'Java - Medium',
                contents: generateMethodGenerator(5)
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
        const helpWindow = (0, showWindow_5.default)({
            rootStyle: `
      width: 32rem;
      min-height: 12rem;
    `,
            onForceClose: onClose,
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
                    root.appendChild((0, createModalTitle_8.default)({
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
            },
        });
        collector.setOnChange(() => helpWindow.refresh());
    };
    exports.default = displayStatistics;
});
define("ui/popup/displayMainArgsOverrideModal", ["require", "exports", "settings", "ui/create/createModalTitle", "ui/create/showWindow"], function (require, exports, settings_4, createModalTitle_9, showWindow_6) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    settings_4 = __importDefault(settings_4);
    createModalTitle_9 = __importDefault(createModalTitle_9);
    showWindow_6 = __importDefault(showWindow_6);
    const getArgs = () => {
        const re = settings_4.default.getMainArgsOverride();
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
        settings_4.default.setMainArgsOverride(args);
    };
    const displayMainArgsOverrideModal = (onClose, onChange) => {
        const windowInstance = (0, showWindow_6.default)({
            onForceClose: () => close(),
            render: (root) => {
                while (root.firstChild) {
                    root.firstChild.remove();
                }
                root.appendChild((0, createModalTitle_9.default)({
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
                    [...((_a = settings_4.default.getMainArgsOverride()) !== null && _a !== void 0 ? _a : []), '/path/to/file.tmp'].forEach((part, partIdx) => {
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
        get duplicateProbeCheckbox() { return document.getElementById('control-duplicate-probe-on-attr'); }
        get duplicateProbeHelpButton() { return document.getElementById('duplicate-probe-on-attr-help'); }
        get captureStdoutCheckbox() { return document.getElementById('control-capture-stdout'); }
        get captureStdoutHelpButton() { return document.getElementById('capture-stdout-help'); }
        get locationStyleSelector() { return document.getElementById('location-style'); }
        get locationStyleHelpButton() { return document.getElementById('control-location-style-help'); }
        get generalHelpButton() { return document.getElementById('display-help'); }
        get saveAsUrlButton() { return document.getElementById('saveAsUrl'); }
        get darkModeCheckbox() { return document.getElementById('control-dark-mode'); }
        get displayStatisticsButton() { return document.getElementById('display-statistics'); }
        get displayWorkerStatusButton() { return document.getElementById('display-worker-status'); }
        get versionInfo() { return document.getElementById('version'); }
        get settingsHider() { return document.getElementById('settings-hider'); }
        get settingsRevealer() { return document.getElementById('settings-revealer'); }
        get showTests() { return document.getElementById('show-tests'); }
    }
    exports.default = UIElements;
});
define("ui/showVersionInfo", ["require", "exports", "model/repositoryUrl"], function (require, exports, repositoryUrl_2) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const showVersionInfo = (elem, ourHash, ourClean, ourBuildTime, sendRequest) => {
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
                fetched = (_a = (await sendRequest({
                    type: 'Fetch',
                    url: (0, repositoryUrl_2.rawUrl)('VERSION'),
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
            if (ourHash === hash) {
                // Status is clean.. for now.
                // Check again (much) later
                return 'again';
            }
            const a = document.createElement('a');
            a.href = `${repositoryUrl_2.repositoryUrl}/blob/master/code-prober.jar`;
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
define("model/runBgProbe", ["require", "exports", "settings"], function (require, exports, settings_5) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    settings_5 = __importDefault(settings_5);
    const runInvisibleProbe = (env, locator, property) => {
        const id = `invisible-probe-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
        const localErrors = [];
        env.probeMarkers[id] = localErrors;
        let state = 'idle';
        let reloadOnDone = false;
        const onRpcDone = () => {
            state = 'idle';
            if (reloadOnDone) {
                reloadOnDone = false;
                performRpc();
            }
        };
        const performRpc = () => {
            state = 'loading';
            env.performTypedRpc({
                type: 'EvaluateProperty',
                property,
                locator,
                src: env.createParsingRequestData(),
                captureStdout: settings_5.default.shouldCaptureStdio(),
            })
                .then((rawResp) => {
                var _a;
                if (rawResp.response.type === 'job') {
                    throw new Error(`Got concurrent response for non-concurrent request`);
                }
                const res = rawResp.response.value;
                const prevLen = localErrors.length;
                localErrors.length = 0;
                localErrors.push(...((_a = res.errors) !== null && _a !== void 0 ? _a : []));
                if (prevLen !== 0 || localErrors.length !== 0) {
                    env.updateMarkers();
                }
                onRpcDone();
            })
                .catch((err) => {
                console.warn('Failed refreshing invisible probe', err);
                onRpcDone();
            });
        };
        const refresh = () => {
            if (state === 'loading') {
                reloadOnDone = true;
            }
            else {
                performRpc();
            }
        };
        env.onChangeListeners[id] = refresh;
        refresh();
    };
    exports.default = runInvisibleProbe;
});
define("ui/popup/displayTestDiffModal", ["require", "exports", "model/test/rpcBodyToAssertionLine", "model/UpdatableNodeLocator", "settings", "ui/create/createInlineWindowManager", "ui/create/createLoadingSpinner", "ui/create/createModalTitle", "ui/create/showWindow", "ui/renderProbeModalTitleLeft", "ui/UIElements", "ui/popup/displayHelp", "ui/popup/displayProbeModal", "ui/popup/encodeRpcBodyLines"], function (require, exports, rpcBodyToAssertionLine_2, UpdatableNodeLocator_6, settings_6, createInlineWindowManager_2, createLoadingSpinner_6, createModalTitle_10, showWindow_7, renderProbeModalTitleLeft_2, UIElements_1, displayHelp_4, displayProbeModal_3, encodeRpcBodyLines_5) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    settings_6 = __importDefault(settings_6);
    createInlineWindowManager_2 = __importDefault(createInlineWindowManager_2);
    createLoadingSpinner_6 = __importDefault(createLoadingSpinner_6);
    createModalTitle_10 = __importDefault(createModalTitle_10);
    showWindow_7 = __importDefault(showWindow_7);
    renderProbeModalTitleLeft_2 = __importDefault(renderProbeModalTitleLeft_2);
    UIElements_1 = __importDefault(UIElements_1);
    displayHelp_4 = __importDefault(displayHelp_4);
    displayProbeModal_3 = __importDefault(displayProbeModal_3);
    encodeRpcBodyLines_5 = __importDefault(encodeRpcBodyLines_5);
    const preventDragOnClick = (elem) => {
        elem.onmousedown = (e) => {
            e.stopPropagation();
            e.preventDefault();
        };
    };
    const displayTestDiffModal = (env, modalPos, locator, property, testCategory, testCaseName) => {
        const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
        let activeTab = 'output';
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
        let lastLoadedTestCase = null;
        const createTitle = () => {
            const onClose = () => {
                queryWindow.remove();
                cleanup();
            };
            return (0, createModalTitle_10.default)({
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
                    ...(lastLoadedTestCase != null ? (() => {
                        const tc = lastLoadedTestCase;
                        return [
                            {
                                title: 'Load state into editor',
                                invoke: () => {
                                    var _a;
                                    settings_6.default.setAstCacheStrategy(tc.src.cache);
                                    settings_6.default.setMainArgsOverride((_a = tc.src.mainArgs) !== null && _a !== void 0 ? _a : null);
                                    settings_6.default.setPositionRecoveryStrategy(tc.src.posRecovery);
                                    if (tc.src.tmpSuffix && tc.src.tmpSuffix !== settings_6.default.getCurrentFileSuffix()) {
                                        settings_6.default.setCustomFileSuffix(tc.src.tmpSuffix);
                                    }
                                    settings_6.default.setEditorContents(tc.src.text);
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
                        ];
                    })() : []),
                ]
            });
        };
        let lastSpinner = null;
        let isFirstRender = true;
        let refreshOnDone = false;
        const queryWindow = (0, showWindow_7.default)({
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
                }
                else if (isFirstRender) {
                    isFirstRender = false;
                    while (root.firstChild)
                        root.removeChild(root.firstChild);
                    root.appendChild(createTitle().element);
                    const spinner = (0, createLoadingSpinner_6.default)();
                    spinner.classList.add('absoluteCenter');
                    const spinnerWrapper = document.createElement('div');
                    spinnerWrapper.style.height = '7rem';
                    spinnerWrapper.style.display = 'block';
                    spinnerWrapper.style.position = 'relative';
                    spinnerWrapper.appendChild(spinner);
                    root.appendChild(spinnerWrapper);
                }
                env.testManager.evaluateTest(testCategory, testCaseName)
                    .then((evaluationResult) => {
                    if (isCleanedUp)
                        return;
                    if (refreshOnDone) {
                        refreshOnDone = false;
                        queryWindow.refresh();
                    }
                    if (cancelToken.cancelled) {
                        return;
                    }
                    while (root.firstChild)
                        root.removeChild(root.firstChild);
                    const titleRow = createTitle();
                    root.append(titleRow.element);
                    if (evaluationResult === 'failed-fetching') {
                        root.appendChild(document.createTextNode(`Failed running test, please check the server log for more information`));
                        lastLoadedTestCase = null;
                        return;
                    }
                    const testStatus = evaluationResult.status;
                    const testCase = evaluationResult.test;
                    lastLoadedTestCase = testCase;
                    let contentUpdater = (tab) => { };
                    const localRefreshListeners = [];
                    env.onChangeListeners[queryId] = () => localRefreshListeners.forEach(lrl => lrl());
                    const someOutputErr = testStatus.overall === 'error';
                    // const someLocatorErr = testReport.overall === 'error' || typeof testReport === 'object' && [
                    //   testReport.sourceLocators, testReport.attrArgLocators, testReport.outputLocators
                    // ].some(loc => loc !== 'pass');
                    const captureStdioSetting = settings_6.default.shouldCaptureStdio();
                    localRefreshListeners.push(() => {
                        if (settings_6.default.shouldCaptureStdio() !== captureStdioSetting) {
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
                        const infos = [
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
                                    var _a, _b, _c, _d;
                                    if (otherIdx !== index) {
                                        (_b = (_a = other.btn) === null || _a === void 0 ? void 0 : _a.classList) === null || _b === void 0 ? void 0 : _b.remove('tab-button-active');
                                        (_d = (_c = other.btn) === null || _c === void 0 ? void 0 : _c.classList) === null || _d === void 0 ? void 0 : _d.add('tab-button-inactive');
                                    }
                                });
                            };
                            infoSelector.appendChild(btn);
                            infos[index].btn = btn;
                        });
                        const refreshSourceButtonText = () => {
                            var _a;
                            const sourceBtn = (_a = infos.find(i => i.type === 'source')) === null || _a === void 0 ? void 0 : _a.btn;
                            if (sourceBtn) {
                                if (testCase.src.text === settings_6.default.getEditorContents()) {
                                    sourceBtn.innerText = `Source Code ✅`;
                                }
                                else {
                                    sourceBtn.innerText = `Source Code ⚠️`;
                                }
                            }
                        };
                        refreshSourceButtonText();
                        localRefreshListeners.push(refreshSourceButtonText);
                        // Add save button
                        (() => {
                            if (testStatus.overall === 'ok') {
                                return;
                            }
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
                                env.testManager.addTest(testCategory, mapped, true);
                            };
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
                        if (tab === activeTab) {
                            return;
                        }
                        activeTab = tab;
                        while (contentRoot.firstChild)
                            contentRoot.removeChild(contentRoot.firstChild);
                        switch (tab) {
                            case 'output':
                            default: {
                                const addSplitTitle = (target, title) => {
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
                                const lhsInlineWindowManager = (0, createInlineWindowManager_2.default)();
                                switch (testCase.assertType) {
                                    case 'IDENTITY':
                                    case 'SET': {
                                        const doEncodeRpcLines = (target, lines, nests, markerPrefix) => {
                                            if (!captureStdioSetting) {
                                                lines = (0, rpcBodyToAssertionLine_2.rpcLinesToAssertionLines)(lines);
                                            }
                                            target.appendChild((0, encodeRpcBodyLines_5.default)(env, lines, {
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
                                                        const wrappedLocator = (0, UpdatableNodeLocator_6.createImmutableLocator)((0, UpdatableNodeLocator_6.createMutableLocator)(locator));
                                                        const area = lhsInlineWindowManager.getArea(path, locatorRoot, expansionArea, wrappedLocator);
                                                        // const env = area.getNestedModalEnv(modal)
                                                        relatedNests.forEach((nest, nestIdx) => {
                                                            const localWindow = area.add({
                                                                onForceClose: () => { },
                                                                render: (root) => {
                                                                    root.style.display = 'flex';
                                                                    root.style.flexDirection = 'column';
                                                                    root.appendChild((0, createModalTitle_10.default)({
                                                                        renderLeft: (target) => (0, renderProbeModalTitleLeft_2.default)(null, target, null, () => localWindow.getPos(), null, wrappedLocator, nest.property, {}, 'minimal-nested'),
                                                                        onClose: null,
                                                                    }).element);
                                                                    doEncodeRpcLines(root, nest.expectedOutput, nest.nestedProperties, [...path, nestIdx]);
                                                                },
                                                            });
                                                        });
                                                    },
                                                    onClick: () => { },
                                                }
                                            }));
                                        };
                                        doEncodeRpcLines(leftPane, testCase.expectedOutput, testCase.nestedProperties, []);
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
                                const rhsInlineWindowManager = (0, createInlineWindowManager_2.default)();
                                const doEncodeRpcLines = (target, lines, nests, markerPrefix) => {
                                    if (!captureStdioSetting) {
                                        lines = (0, rpcBodyToAssertionLine_2.rpcLinesToAssertionLines)(lines);
                                    }
                                    target.appendChild((0, encodeRpcBodyLines_5.default)(env, lines, {
                                        lateInteractivityEnabledChecker: () => testCase.src.text === env.getLocalState(),
                                        excludeStdIoFromPaths: true,
                                        capWidths: true,
                                        decorator: (line) => {
                                            console.log('in prefix:', markerPrefix, ', line:', line);
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
                                            onCreate: ({ path, locatorRoot, expansionArea, locator }) => {
                                                const encodedPath = JSON.stringify(path);
                                                const relatedNests = nests.filter(nest => JSON.stringify(nest.path) === encodedPath);
                                                console.log('onCreate', path, '; nests:', nests, 'matches:', relatedNests);
                                                if (relatedNests.length === 0) {
                                                    return;
                                                }
                                                const wrappedLocator = (0, UpdatableNodeLocator_6.createImmutableLocator)((0, UpdatableNodeLocator_6.createMutableLocator)(locator));
                                                const area = rhsInlineWindowManager.getArea(path, locatorRoot, expansionArea, wrappedLocator);
                                                // const env = area.getNestedModalEnv(modal)
                                                relatedNests.forEach((nest, nestIdx) => {
                                                    const localWindow = area.add({
                                                        onForceClose: () => { },
                                                        render: (root) => {
                                                            root.style.display = 'flex';
                                                            root.style.flexDirection = 'column';
                                                            root.appendChild((0, createModalTitle_10.default)({
                                                                renderLeft: (target) => (0, renderProbeModalTitleLeft_2.default)(null, target, null, () => localWindow.getPos(), null, wrappedLocator, nest.property, {}, 'minimal-nested'),
                                                                onClose: null,
                                                            }).element);
                                                            if (nest.result !== 'could-not-find-node') {
                                                                doEncodeRpcLines(root, nest.result.body, nest.result.nested, [...path, nestIdx]);
                                                            }
                                                        },
                                                    });
                                                });
                                            },
                                            onClick: () => { },
                                        }
                                    }));
                                };
                                if (evaluationResult.output && evaluationResult.output.result !== 'could-not-find-node') {
                                    console.log('actualOutput:', evaluationResult.output.result);
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
                                const addP = (msg) => {
                                    const explanationTail = document.createElement('p');
                                    explanationTail.textContent = msg;
                                    wrapper.appendChild(explanationTail);
                                };
                                const addTail = () => {
                                    addP(`You can inspect the difference by opening a probe from 'Source Code' tab. If you are OK with the differences, click 'Save 💾'.`);
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
                                }
                                else {
                                    wrapper.appendChild(document.createTextNode('All AST node references involved in property execution are identical to expected values.'));
                                }
                                break;
                            }
                            case 'source': {
                                const wrapper = document.createElement('div');
                                wrapper.style.padding = '0.25rem';
                                contentRoot.appendChild(wrapper);
                                const same = testCase.src.text === env.getLocalState();
                                const addText = (msg) => wrapper.appendChild(document.createTextNode(msg));
                                const addHelp = (type) => {
                                    const btn = document.createElement('button');
                                    btn.innerText = `?`;
                                    btn.style.marginLeft = '0.25rem';
                                    btn.style.borderRadius = '50%';
                                    btn.onclick = () => (0, displayHelp_4.default)(type);
                                    wrapper.appendChild(btn);
                                };
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
                                        const nestedTestToProbeData = (test) => {
                                            const inner = {};
                                            test.nestedProperties.forEach((nest) => {
                                                var _a;
                                                const key = JSON.stringify(nest.path);
                                                inner[key] = (_a = inner[key]) !== null && _a !== void 0 ? _a : [];
                                                inner[key].push({
                                                    data: nestedTestToProbeData(nest),
                                                    modalPos: { x: 0, y: 0 },
                                                });
                                            });
                                            return {
                                                type: 'probe',
                                                locator: testCase.locator,
                                                property: test.property,
                                                nested: inner,
                                            };
                                        };
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
                                        (0, displayProbeModal_3.default)(env, null, (0, UpdatableNodeLocator_6.createMutableLocator)(locator), property, nestedTestToProbeData({
                                            path: [],
                                            expectedOutput: testCase.expectedOutput,
                                            nestedProperties: testCase.nestedProperties,
                                            property: testCase.property,
                                        }).nested);
                                    };
                                    wrapper.appendChild(btn);
                                    wrapper.appendChild(document.createTextNode(`to explore the probe that created this test.`));
                                }
                                else {
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
                                        env.setLocalState(testCase.src.text);
                                    };
                                    wrapper.appendChild(btn);
                                    addText(`to replace CodeProber text with the test source.`);
                                }
                                wrapper.appendChild(document.createElement('hr'));
                                const addExplanationLine = (head, value, valueClass = '') => {
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
                                };
                                addExplanationLine('Node type:', (locator.result.label || locator.result.type).split('.').slice(-1)[0], 'syntax-type');
                                addExplanationLine('Property:', property.name, 'syntax-attr');
                                if (locator.result.external) {
                                    addExplanationLine('AST Node:', `${locator.result.label || locator.result.type} in external file`);
                                }
                                else {
                                    // const span = startEndToSpan(locator.result.start, locator.result.end);
                                    // const linesTail = span.lineStart === span.lineEnd
                                    //   ? ` $${span.lineStart}`
                                    //   : `s ${span.lineStart}→${span.lineEnd}`;
                                    // addExplanationLine('AST Node:', `${locator.result.label || locator.result.type} on line${linesTail}`);
                                }
                                addExplanationLine('Source Code', '⬇️, related line(s) in green.');
                                testCase.src.text.split('\n').forEach((line, lineIdx) => {
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
                                const translateSelectorValToHumanLabel = (val, selector) => {
                                    if (!selector) {
                                        console.warn('no selector for', val);
                                        return val;
                                    }
                                    for (let i = 0; i < selector.childElementCount; ++i) {
                                        const opt = selector.children[i];
                                        if (opt.nodeName === 'OPTION') {
                                            if (opt.value === val) {
                                                return opt.innerHTML;
                                            }
                                        }
                                    }
                                    console.warn(`Not sure how to translate ${val} to human-readable value`);
                                    return val;
                                };
                                const ul = document.createElement('ul');
                                ul.style.margin = '0 0 0.25rem';
                                const uiElements = new UIElements_1.default();
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
                    });
                    const spinner = (0, createLoadingSpinner_6.default)();
                    spinner.style.display = 'none';
                    spinner.classList.add('absoluteCenter');
                    lastSpinner = spinner;
                    root.appendChild(spinner);
                })
                    .catch(err => {
                    if (refreshOnDone) {
                        refreshOnDone = false;
                        queryWindow.refresh();
                        return;
                    }
                    if (cancelToken.cancelled) {
                        return;
                    }
                    console.log('TestDiffModal RPC catch', err);
                    root.innerHTML = '';
                    root.innerText = 'Failed refreshing test diff..';
                    console.warn('Failed refreshing test w/ args:', JSON.stringify({ locator, property }, null, 2));
                    setTimeout(() => {
                        queryWindow.remove();
                        cleanup();
                    }, 2000);
                });
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
        };
    };
    exports.default = displayTestDiffModal;
});
define("ui/popup/displayTestSuiteModal", ["require", "exports", "ui/create/createLoadingSpinner", "ui/create/createModalTitle", "ui/create/showWindow", "ui/popup/displayTestDiffModal"], function (require, exports, createLoadingSpinner_7, createModalTitle_11, showWindow_8, displayTestDiffModal_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createLoadingSpinner_7 = __importDefault(createLoadingSpinner_7);
    createModalTitle_11 = __importDefault(createModalTitle_11);
    showWindow_8 = __importDefault(showWindow_8);
    displayTestDiffModal_1 = __importDefault(displayTestDiffModal_1);
    const displayTestSuiteModal = (env, category) => {
        const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
        const cleanup = () => {
            popup.remove();
            env.testManager.removeListener(queryId);
        };
        let contents = 'loading';
        const popup = (0, showWindow_8.default)({
            rootStyle: `
      min-width: 16rem;
      min-height: fit-content;
    `,
            onForceClose: cleanup,
            resizable: true,
            render: (root) => {
                while (root.firstChild)
                    root.removeChild(root.firstChild);
                root.appendChild((0, createModalTitle_11.default)({
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
                        const spinner = (0, createLoadingSpinner_7.default)();
                        spinner.classList.add('absoluteCenter');
                        root.appendChild(spinner);
                    }
                    else {
                        const textHolder = document.createElement('div');
                        textHolder.style.padding = '0.5rem';
                        textHolder.innerText = `Failed fetch test content, have you set '-Dcpr.testDir=<a valid directory>'? If so, check if ${category}.json exists and contains valid JSON.`;
                    }
                }
                else {
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
                        row.style.order = '1';
                        env.testManager.evaluateTest(category, tc.name).then(result => {
                            // console.log('tstatus for', category, '>', tc.name, ':', result);
                            if (result == 'failed-fetching') {
                                status.innerText = `<Evaluation Error>`;
                            }
                            else {
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
                                        row.style.order = '3';
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
                            (0, displayTestDiffModal_1.default)(env, null, tc.locator, tc.property, category, tc.name);
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
    };
    exports.default = displayTestSuiteModal;
});
define("ui/popup/displayTestSuiteListModal", ["require", "exports", "ui/create/createLoadingSpinner", "ui/create/createModalTitle", "ui/create/showWindow", "ui/popup/displayTestSuiteModal"], function (require, exports, createLoadingSpinner_8, createModalTitle_12, showWindow_9, displayTestSuiteModal_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createLoadingSpinner_8 = __importDefault(createLoadingSpinner_8);
    createModalTitle_12 = __importDefault(createModalTitle_12);
    showWindow_9 = __importDefault(showWindow_9);
    displayTestSuiteModal_1 = __importDefault(displayTestSuiteModal_1);
    const displayTestSuiteListModal = (env, onClose, serverSideWorkerProcessCount) => {
        const queryId = `query-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`;
        let isClosed = false;
        const cleanup = () => {
            isClosed = true;
            onClose();
            popup.remove();
            env.testManager.removeListener(queryId);
        };
        let categories = 'loading';
        let changeCallbackCounter = 0;
        let localTestSuiteKnowledge = {};
        const getLocalKnowledge = (suiteId) => {
            return localTestSuiteKnowledge[suiteId] = localTestSuiteKnowledge[suiteId] || { pass: 0, fail: 0 };
        };
        const runAll = async (kind) => {
            localTestSuiteKnowledge = {};
            const suites = await env.testManager.listTestSuiteCategories();
            if (suites === 'failed-listing') {
                throw new Error('Failed listing test suites');
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
                if (cases === 'failed-fetching') {
                    throw new Error(`Failed fetching test suite '${suite}'`);
                }
                ;
                if (isClosed) {
                    return false;
                }
                const sorted = [...cases];
                // For caching purposes, sort so that all equal-text cases are evaluated next to each other
                sorted.sort((a, b) => {
                    if (a.src !== b.src) {
                        return a.src < b.src ? -1 : 1;
                    }
                    if (a.src.cache != b.src.cache) {
                        return a.src.cache < b.src.cache ? -1 : 1;
                    }
                    // Close enough to equal
                    return 0;
                });
                // Eval (process - 1) tests at a time to avoid overlapping with normal CodeProber usage.
                const sliceSize = (serverSideWorkerProcessCount !== undefined) ? Math.max(1, serverSideWorkerProcessCount * 3) : 1;
                const numSlices = Math.ceil(sorted.length / sliceSize);
                const evaluateSlice = async (slice) => {
                    const relatedCases = sorted.slice(slice * sliceSize, Math.min(sorted.length, (slice + 1) * sliceSize));
                    let expectedChangeCallback = changeCallbackCounter + relatedCases.length;
                    ;
                    await Promise.all(relatedCases.map(async (tcase) => {
                        const evaluation = await env.testManager.evaluateTest(suite, tcase.name);
                        // console.log('Ran test', suite, '>', tcase.name, ', status:', status);
                        if (isClosed) {
                            return;
                        }
                        if (evaluation === 'failed-fetching') {
                            ++getLocalKnowledge(suite).fail;
                        }
                        else if (evaluation.status.overall !== 'ok') {
                            if (kind !== 'save-if-error') {
                                ++getLocalKnowledge(suite).fail;
                            }
                            else {
                                const mapped = env.testManager.convertTestResponseToTest(tcase, evaluation.output);
                                if (mapped === null) {
                                    ++getLocalKnowledge(suite).fail;
                                }
                                else {
                                    await env.testManager.addTest(suite, mapped, true);
                                    ++getLocalKnowledge(suite).pass;
                                }
                            }
                        }
                        else {
                            ++getLocalKnowledge(suite).pass;
                        }
                        expectedChangeCallback = changeCallbackCounter + 1;
                    }));
                    if (changeCallbackCounter !== expectedChangeCallback) {
                        // One or more result was cached, force reload
                        popup.refresh();
                    }
                };
                for (let i = 0; i < numSlices; ++i) {
                    if (isClosed) {
                        return false;
                    }
                    await evaluateSlice(i);
                }
            }
            return true;
        };
        const popup = (0, showWindow_9.default)({
            rootStyle: `
      width: 16rem;
      min-width: 4rem;
      min-height: 8rem;
    `,
            onForceClose: cleanup,
            resizable: true,
            render: (root) => {
                while (root.firstChild)
                    root.removeChild(root.firstChild);
                root.appendChild((0, createModalTitle_12.default)({
                    renderLeft: (container) => {
                        const header = document.createElement('span');
                        header.innerText = `Test Suites`;
                        container.appendChild(header);
                        header.style.minHeight = '1.25rem';
                    },
                    onClose: cleanup,
                    extraActions: (typeof categories === 'string' || categories.length === 0) ? [] : [
                        {
                            title: 'Run all',
                            invoke: () => {
                                runAll('just-run').catch((err) => {
                                    console.warn('Error when running all tests:', err);
                                });
                            },
                        },
                        {
                            title: 'Save all tests as expected behavior',
                            invoke: () => {
                                const confirmation = confirm(`Are you sure? This cannot be undone. One or more of the failures may represent meaningful information, which will be lost if you save it as 'expected' behavior`);
                                if (!confirmation) {
                                    return;
                                }
                                runAll('save-if-error').catch((err) => {
                                    console.warn('Error when running all tests:', err);
                                });
                            }
                        }
                    ]
                }).element);
                if (typeof categories === 'string') {
                    if (categories === 'loading') {
                        root.style.display = 'contents';
                        const spinner = (0, createLoadingSpinner_8.default)();
                        spinner.classList.add('absoluteCenter');
                        root.appendChild(spinner);
                    }
                    else {
                        const textHolder = document.createElement('div');
                        textHolder.style.padding = '0.5rem';
                        textHolder.innerText = `Failed listing test categories, have you set '-Dcpr.testDir=<a valid directory>'?`;
                    }
                }
                else {
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
                        title.style.maxWidth = '70%';
                        title.style.wordBreak = 'break-word';
                        row.appendChild(title);
                        const icons = document.createElement('div');
                        row.appendChild(icons);
                        const status = document.createElement('span');
                        env.testManager.getTestSuite(cat).then((res) => {
                            if (res === 'failed-fetching') {
                                status.innerText = `<failed fetching>`;
                            }
                            else if (res.length === 0) {
                                status.innerText = `Empty suite`;
                            }
                            else {
                                if (localTestSuiteKnowledge[cat]) {
                                    const know = localTestSuiteKnowledge[cat];
                                    if ((know.pass + know.fail) === res.length) {
                                        if (know.fail === 0) {
                                            status.innerText = `✅`;
                                        }
                                        else {
                                            status.innerText = `Fail ${know.fail}/${res.length} ❌`;
                                        }
                                    }
                                    else {
                                        // Loading
                                        const pct = ((know.fail + know.pass) * 100 / res.length) | 0;
                                        status.innerText = `${pct}% ⏳`;
                                    }
                                }
                                else {
                                    status.innerText = `${res.length} ${res.length === 1 ? 'test' : 'tests'}`;
                                }
                            }
                        });
                        icons.appendChild(status);
                        row.onclick = () => (0, displayTestSuiteModal_1.default)(env, cat);
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
    };
    exports.default = displayTestSuiteListModal;
});
define("ui/popup/displayWorkerStatus", ["require", "exports", "ui/create/createModalTitle", "ui/create/showWindow"], function (require, exports, createModalTitle_13, showWindow_10) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    createModalTitle_13 = __importDefault(createModalTitle_13);
    showWindow_10 = __importDefault(showWindow_10);
    const displayWorkerStatus = (env, setDisplayButtonDisabled) => {
        let activeSubscription = null;
        const onClose = () => {
            helpWindow.remove();
            setDisplayButtonDisabled(false);
            if (activeSubscription != null) {
                env.performTypedRpc({
                    type: 'Concurrent:UnsubscribeFromWorkerStatus',
                    job: activeSubscription.job,
                    subscriberId: activeSubscription.subscriberId,
                }).then(res => {
                    if (!res.ok) {
                        console.warn('Failed unsubscribing from worker status for', activeSubscription);
                    }
                });
            }
        };
        setDisplayButtonDisabled(true);
        const helpWindow = (0, showWindow_10.default)({
            rootStyle: `
      width: 32rem;
      min-height: 12rem;
    `,
            resizable: true,
            onForceClose: onClose,
            render: (root) => {
                while (root.firstChild)
                    root.removeChild(root.firstChild);
                root.appendChild((0, createModalTitle_13.default)({
                    renderLeft: (container) => {
                        container.appendChild(document.createTextNode('Worker status monitor'));
                    },
                    onClose,
                }).element);
                const pre = document.createElement('pre');
                pre.style.margin = '0.25rem';
                root.appendChild(pre);
                pre.innerText = `Requesting worker status..`;
                const job = env.createJobId(data => {
                    // console.log('worker status data:', data);
                    switch (data.value.type) {
                        case 'workerStatuses': {
                            const workers = data.value.value;
                            pre.innerText = `Status:\n${workers.map((stat, idx) => `${`${idx + 1}`.padStart(3, ' ')}:${stat}`).join('\n')}`;
                            break;
                        }
                    }
                });
                env.performTypedRpc({
                    type: 'Concurrent:SubscribeToWorkerStatus',
                    job,
                }).then(res => {
                    activeSubscription = {
                        job,
                        subscriberId: res.subscriberId,
                    };
                });
            },
        });
    };
    exports.default = displayWorkerStatus;
});
define("main", ["require", "exports", "ui/addConnectionCloseNotice", "ui/popup/displayProbeModal", "ui/popup/displayRagModal", "ui/popup/displayHelp", "ui/popup/displayAttributeModal", "settings", "model/StatisticsCollectorImpl", "ui/popup/displayStatistics", "ui/popup/displayMainArgsOverrideModal", "model/syntaxHighlighting", "createWebsocketHandler", "ui/configureCheckboxWithHiddenButton", "ui/UIElements", "ui/showVersionInfo", "model/runBgProbe", "model/cullingTaskSubmitterFactory", "ui/popup/displayAstModal", "model/test/TestManager", "ui/popup/displayTestSuiteListModal", "ui/popup/displayWorkerStatus", "ui/create/showWindow", "model/UpdatableNodeLocator"], function (require, exports, addConnectionCloseNotice_1, displayProbeModal_4, displayRagModal_1, displayHelp_5, displayAttributeModal_7, settings_7, StatisticsCollectorImpl_1, displayStatistics_1, displayMainArgsOverrideModal_1, syntaxHighlighting_2, createWebsocketHandler_1, configureCheckboxWithHiddenButton_1, UIElements_2, showVersionInfo_1, runBgProbe_1, cullingTaskSubmitterFactory_2, displayAstModal_3, TestManager_1, displayTestSuiteListModal_1, displayWorkerStatus_1, showWindow_11, UpdatableNodeLocator_7) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    addConnectionCloseNotice_1 = __importDefault(addConnectionCloseNotice_1);
    displayProbeModal_4 = __importDefault(displayProbeModal_4);
    displayRagModal_1 = __importDefault(displayRagModal_1);
    displayHelp_5 = __importDefault(displayHelp_5);
    displayAttributeModal_7 = __importDefault(displayAttributeModal_7);
    settings_7 = __importDefault(settings_7);
    StatisticsCollectorImpl_1 = __importDefault(StatisticsCollectorImpl_1);
    displayStatistics_1 = __importDefault(displayStatistics_1);
    displayMainArgsOverrideModal_1 = __importDefault(displayMainArgsOverrideModal_1);
    createWebsocketHandler_1 = __importStar(createWebsocketHandler_1);
    configureCheckboxWithHiddenButton_1 = __importDefault(configureCheckboxWithHiddenButton_1);
    UIElements_2 = __importDefault(UIElements_2);
    showVersionInfo_1 = __importDefault(showVersionInfo_1);
    runBgProbe_1 = __importDefault(runBgProbe_1);
    cullingTaskSubmitterFactory_2 = __importDefault(cullingTaskSubmitterFactory_2);
    displayAstModal_3 = __importDefault(displayAstModal_3);
    displayTestSuiteListModal_1 = __importDefault(displayTestSuiteListModal_1);
    displayWorkerStatus_1 = __importDefault(displayWorkerStatus_1);
    showWindow_11 = __importDefault(showWindow_11);
    const uiElements = new UIElements_2.default();
    window.clearUserSettings = () => {
        settings_7.default.set({});
        location.reload();
    };
    const clearHashFromLocation = () => history.replaceState('', document.title, `${window.location.pathname}${window.location.search}`);
    window.saveStateAsUrl = () => {
        const encoded = encodeURIComponent(JSON.stringify(settings_7.default.getProbeWindowStates()));
        clearHashFromLocation();
        // delete location.hash;'
        // console.log('loc:', location.toString());
        navigator.clipboard.writeText(`${window.location.origin}${window.location.pathname}${window.location.search}${window.location.search.length === 0 ? '?' : '&'}ws=${encoded}`);
        const btn = uiElements.saveAsUrlButton;
        const saveText = btn.textContent;
        setTimeout(() => {
            btn.textContent = saveText;
            delete btn.style.border;
        }, 1000);
        btn.textContent = `Copied to clipboard`;
        btn.style.border = '1px solid green';
    };
    const doMain = (wsPort) => {
        if (settings_7.default.shouldHideSettingsPanel() && !window.location.search.includes('fullscreen=true')) {
            document.body.classList.add('hide-settings');
        }
        if (!settings_7.default.shouldEnableTesting()) {
            uiElements.showTests.style.display = 'none';
        }
        let getLocalState = () => { var _a; return (_a = settings_7.default.getEditorContents()) !== null && _a !== void 0 ? _a : ''; };
        let basicHighlight = null;
        const stickyHighlights = {};
        let updateSpanHighlight = (span, stickies) => { };
        const onChangeListeners = {};
        const probeWindowStateSavers = {};
        const triggerWindowSave = () => {
            const states = [];
            Object.values(probeWindowStateSavers).forEach(v => v(states));
            settings_7.default.setProbeWindowStates(states);
        };
        // setInterval(() => {
        //   console.log('listener ids:', JSON.stringify(Object.keys(onChangeListeners)));
        // }, 1000);
        const notifyLocalChangeListeners = (adjusters, reason) => {
            Object.values(onChangeListeners).forEach(l => l(adjusters, reason));
            triggerWindowSave();
        };
        function initEditor(editorType) {
            if (!location.search) {
                location.search = "editor=" + editorType;
                return;
            }
            document.body.setAttribute('data-theme-light', `${settings_7.default.isLightTheme()}`);
            const wsHandler = (() => {
                if (wsPort == 'ws-over-http') {
                    return (0, createWebsocketHandler_1.createWebsocketOverHttpHandler)(addConnectionCloseNotice_1.default);
                }
                if (typeof wsPort == 'object') {
                    // Codespaces-compat
                    const needle = `-${wsPort.from}.`;
                    if (location.hostname.includes(needle) && !location.port) {
                        return (0, createWebsocketHandler_1.default)(new WebSocket(`wss://${location.hostname.replace(needle, `-${wsPort.to}.`)}`), addConnectionCloseNotice_1.default);
                    }
                    else {
                        // Else, we are running Codespaces locally from a 'native' (non-web) editor.
                        // We only need to do the compat layer if running Codespaces from the web.
                        // Fall down to default impl below.
                    }
                }
                return (0, createWebsocketHandler_1.default)(new WebSocket(`ws://${location.hostname}:${wsPort}`), addConnectionCloseNotice_1.default);
            })();
            const jobUpdateHandlers = {};
            wsHandler.on('asyncUpdate', (rawData) => {
                const data = rawData; // Ideally stricly parse this, but this works
                const { job, isFinalUpdate, value } = data;
                // console.log('jobUpdate for', job, '; result:', result);
                if (!job || value === undefined) {
                    console.warn('Invalid job update', data);
                    return;
                }
                const handler = jobUpdateHandlers[job];
                if (!handler) {
                    console.log('Got no handler for job update:', data);
                    return;
                }
                if (isFinalUpdate) {
                    delete jobUpdateHandlers[data === null || data === void 0 ? void 0 : data.job];
                }
                handler(data);
            });
            const rootElem = document.getElementById('root');
            const initHandler = (info) => {
                const { version: { clean, hash, buildTimeSeconds }, changeBufferTime, workerProcessCount } = info;
                console.log('onInit, buffer:', changeBufferTime, 'workerProcessCount:', workerProcessCount);
                rootElem.style.display = "grid";
                const onChange = (newValue, adjusters) => {
                    settings_7.default.setEditorContents(newValue);
                    notifyLocalChangeListeners(adjusters);
                };
                let setLocalState = (value) => { };
                let markText = () => ({});
                let registerStickyMarker = (initialSpan) => ({
                    getSpan: () => initialSpan,
                    remove: () => { },
                });
                const darkModeCheckbox = uiElements.darkModeCheckbox;
                darkModeCheckbox.checked = !settings_7.default.isLightTheme();
                const themeChangeListeners = {};
                darkModeCheckbox.oninput = (e) => {
                    let lightTheme = !darkModeCheckbox.checked;
                    settings_7.default.setLightTheme(lightTheme);
                    document.body.setAttribute('data-theme-light', `${lightTheme}`);
                    Object.values(themeChangeListeners).forEach(cb => cb(lightTheme));
                };
                let syntaxHighlightingToggler;
                if (window.definedEditors[editorType]) {
                    const { preload, init, } = window.definedEditors[editorType];
                    window.loadPreload(preload, () => {
                        var _a;
                        const res = init((_a = settings_7.default.getEditorContents()) !== null && _a !== void 0 ? _a : `// Hello World!\n// Write some code in this field, then right click and select 'Create Probe' to get started\n\n`, onChange, settings_7.default.getSyntaxHighlighting());
                        setLocalState = res.setLocalState || setLocalState;
                        getLocalState = res.getLocalState || getLocalState;
                        updateSpanHighlight = res.updateSpanHighlight || updateSpanHighlight;
                        registerStickyMarker = res.registerStickyMarker || registerStickyMarker;
                        markText = res.markText || markText;
                        if (res.themeToggler) {
                            themeChangeListeners['main-editor'] = (light) => res.themeToggler(light);
                            res.themeToggler(settings_7.default.isLightTheme());
                            // defineThemeToggler(res.themeToggler);
                        }
                        syntaxHighlightingToggler = res.syntaxHighlightingToggler;
                        location.search.split(/\?|&/g).forEach((kv) => {
                            const needle = `bgProbe=`;
                            if (kv.startsWith(needle)) {
                                (0, runBgProbe_1.default)(modalEnv, { result: { start: 0, end: 0, type: '<ROOT>', depth: 0 }, steps: [] }, { name: kv.slice(needle.length), });
                            }
                        });
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
                    Object.values(probeMarkers).forEach(arr => arr.forEach(({ type, start, end, msg }) => filteredAddMarker(type, start, end, msg)));
                };
                const setupSimpleCheckbox = (input, initial, update) => {
                    input.checked = initial;
                    input.oninput = () => { update(input.checked); notifyLocalChangeListeners(); };
                };
                setupSimpleCheckbox(uiElements.captureStdoutCheckbox, settings_7.default.shouldCaptureStdio(), cb => settings_7.default.setShouldCaptureStdio(cb));
                setupSimpleCheckbox(uiElements.duplicateProbeCheckbox, settings_7.default.shouldDuplicateProbeOnAttrClick(), cb => settings_7.default.setShouldDuplicateProbeOnAttrClick(cb));
                setupSimpleCheckbox(uiElements.showAllPropertiesCheckbox, settings_7.default.shouldShowAllProperties(), cb => settings_7.default.setShouldShowAllProperties(cb));
                const setupSimpleSelector = (input, initial, update) => {
                    input.value = initial;
                    input.oninput = () => { update(input.value); notifyLocalChangeListeners(); };
                };
                setupSimpleSelector(uiElements.astCacheStrategySelector, settings_7.default.getAstCacheStrategy(), cb => settings_7.default.setAstCacheStrategy(cb));
                setupSimpleSelector(uiElements.positionRecoverySelector, settings_7.default.getPositionRecoveryStrategy(), cb => settings_7.default.setPositionRecoveryStrategy(cb));
                setupSimpleSelector(uiElements.locationStyleSelector, `${settings_7.default.getLocationStyle()}`, cb => settings_7.default.setLocationStyle(cb));
                uiElements.settingsHider.onclick = () => {
                    document.body.classList.add('hide-settings');
                    settings_7.default.setShouldHideSettingsPanel(true);
                };
                uiElements.settingsRevealer.onclick = () => {
                    document.body.classList.remove('hide-settings');
                    settings_7.default.setShouldHideSettingsPanel(false);
                };
                const syntaxHighlightingSelector = uiElements.syntaxHighlightingSelector;
                syntaxHighlightingSelector.innerHTML = '';
                (0, syntaxHighlighting_2.getAvailableLanguages)().forEach(({ id, alias }) => {
                    const option = document.createElement('option');
                    option.value = id;
                    option.innerText = alias;
                    syntaxHighlightingSelector.appendChild(option);
                });
                setupSimpleSelector(syntaxHighlightingSelector, settings_7.default.getSyntaxHighlighting(), cb => {
                    settings_7.default.setSyntaxHighlighting(syntaxHighlightingSelector.value);
                    syntaxHighlightingToggler === null || syntaxHighlightingToggler === void 0 ? void 0 : syntaxHighlightingToggler(settings_7.default.getSyntaxHighlighting());
                });
                const overrideCfg = (0, configureCheckboxWithHiddenButton_1.default)(uiElements.shouldOverrideMainArgsCheckbox, uiElements.configureMainArgsOverrideButton, (checked) => {
                    settings_7.default.setMainArgsOverride(checked ? [] : null);
                    overrideCfg.refreshButton();
                    notifyLocalChangeListeners();
                }, onClose => (0, displayMainArgsOverrideModal_1.default)(onClose, () => {
                    overrideCfg.refreshButton();
                    notifyLocalChangeListeners();
                }), () => {
                    const overrides = settings_7.default.getMainArgsOverride();
                    return overrides === null ? null : `Edit (${overrides.length})`;
                });
                const suffixCfg = (0, configureCheckboxWithHiddenButton_1.default)(uiElements.shouldCustomizeFileSuffixCheckbox, uiElements.configureCustomFileSuffixButton, (checked) => {
                    settings_7.default.setCustomFileSuffix(checked ? settings_7.default.getCurrentFileSuffix() : null);
                    suffixCfg.refreshButton();
                    notifyLocalChangeListeners();
                }, onClose => {
                    const newVal = prompt('Enter new suffix', settings_7.default.getCurrentFileSuffix());
                    if (newVal !== null) {
                        settings_7.default.setCustomFileSuffix(newVal);
                        suffixCfg.refreshButton();
                        notifyLocalChangeListeners();
                    }
                    onClose();
                    return { forceClose: () => { }, };
                }, () => {
                    const overrides = settings_7.default.getCustomFileSuffix();
                    return overrides === null ? null : `Edit (${settings_7.default.getCurrentFileSuffix()})`;
                });
                const statCollectorImpl = new StatisticsCollectorImpl_1.default();
                if (location.search.includes('debug=true')) {
                    document.getElementById('secret-debug-panel').style.display = 'block';
                }
                // const testManager = createTestManager(req => performRpcQuery(wsHandler, req));
                const createJobId = (updateHandler) => {
                    const id = ++jobIdGenerator;
                    jobUpdateHandlers[id] = updateHandler;
                    return id;
                };
                const testManager = (0, TestManager_1.createTestManager)(() => modalEnv, createJobId);
                let jobIdGenerator = 0;
                const modalEnv = {
                    showWindow: showWindow_11.default,
                    performTypedRpc: (req) => wsHandler.sendRpc(req),
                    createParsingRequestData: () => {
                        var _a;
                        return ({
                            posRecovery: uiElements.positionRecoverySelector.value,
                            cache: uiElements.astCacheStrategySelector.value,
                            text: getLocalState(),
                            stdout: settings_7.default.shouldCaptureStdio(),
                            mainArgs: (_a = settings_7.default.getMainArgsOverride()) !== null && _a !== void 0 ? _a : undefined,
                            tmpSuffix: settings_7.default.getCurrentFileSuffix(),
                        });
                    },
                    probeMarkers, onChangeListeners, themeChangeListeners, updateMarkers,
                    themeIsLight: () => settings_7.default.isLightTheme(),
                    getLocalState: () => getLocalState(),
                    setLocalState: (newVal) => setLocalState(newVal),
                    captureStdout: () => uiElements.captureStdoutCheckbox.checked,
                    duplicateOnAttr: () => uiElements.duplicateProbeCheckbox.checked,
                    registerStickyMarker: (...args) => registerStickyMarker(...args),
                    updateSpanHighlight: (hl) => {
                        basicHighlight = hl;
                        updateSpanHighlight(basicHighlight, Object.values(stickyHighlights));
                    },
                    probeWindowStateSavers,
                    setStickyHighlight: (pi, hl) => {
                        stickyHighlights[pi] = hl;
                        updateSpanHighlight(basicHighlight, Object.values(stickyHighlights));
                    },
                    clearStickyHighlight: (pi) => {
                        delete stickyHighlights[pi];
                        updateSpanHighlight(basicHighlight, Object.values(stickyHighlights));
                    },
                    triggerWindowSave,
                    statisticsCollector: statCollectorImpl,
                    currentlyLoadingModals: new Set(),
                    createCullingTaskSubmitter: (0, cullingTaskSubmitterFactory_2.default)(changeBufferTime),
                    testManager,
                    createJobId,
                    getGlobalModalEnv: () => modalEnv,
                };
                modalEnv.onChangeListeners['reeval-tests-on-server-refresh'] = (_, reason) => {
                    if (reason === 'refresh-from-server') {
                        testManager.flushTestCaseData();
                    }
                };
                uiElements.showTests.onclick = () => {
                    uiElements.showTests.disabled = true;
                    (0, displayTestSuiteListModal_1.default)(modalEnv, () => { uiElements.showTests.disabled = false; }, workerProcessCount);
                };
                (0, showVersionInfo_1.default)(uiElements.versionInfo, hash, clean, buildTimeSeconds, modalEnv.performTypedRpc);
                window.displayHelp = (type) => {
                    const common = (type, button) => (0, displayHelp_5.default)(type, disabled => button.disabled = disabled);
                    switch (type) {
                        case "general": return common('general', uiElements.generalHelpButton);
                        case 'recovery-strategy': return common('recovery-strategy', uiElements.positionRecoveryHelpButton);
                        case "ast-cache-strategy": return common('ast-cache-strategy', uiElements.astCacheStrategyHelpButton);
                        case "probe-statistics": return (0, displayStatistics_1.default)(statCollectorImpl, disabled => uiElements.displayStatisticsButton.disabled = disabled, newContents => setLocalState(newContents), () => modalEnv.currentlyLoadingModals.size > 0);
                        case 'worker-status': return (0, displayWorkerStatus_1.default)(modalEnv, disabled => uiElements.displayWorkerStatusButton.disabled = disabled);
                        case 'syntax-highlighting': return common('syntax-highlighting', uiElements.syntaxHighlightingHelpButton);
                        case 'main-args-override': return common('main-args-override', uiElements.mainArgsOverrideHelpButton);
                        case 'customize-file-suffix': return common('customize-file-suffix', uiElements.customFileSuffixHelpButton);
                        case 'show-all-properties': return common('show-all-properties', uiElements.showAllPropertiesHelpButton);
                        case 'duplicate-probe-on-attr': return common('duplicate-probe-on-attr', uiElements.duplicateProbeHelpButton);
                        case 'capture-stdout': return common('capture-stdout', uiElements.captureStdoutHelpButton);
                        case 'location-style': return common('location-style', uiElements.locationStyleHelpButton);
                        default: return console.error('Unknown help type', type);
                    }
                };
                setTimeout(() => {
                    try {
                        let windowStates = settings_7.default.getProbeWindowStates();
                        let wsMatch;
                        if ((wsMatch = /[?&]?ws=[^?&]+/.exec(location.search)) != null) {
                            const trimmedSearch = wsMatch.index === 0
                                ? (wsMatch[0].length < location.search.length
                                    ? `?${location.search.slice(wsMatch[0].length + 1)}`
                                    : `${location.search.slice(0, wsMatch.index)}${location.search.slice(wsMatch.index + wsMatch[0].length)}`)
                                : `${location.search.slice(0, wsMatch.index)}${location.search.slice(wsMatch.index + wsMatch[0].length)}`;
                            history.replaceState('', document.title, `${window.location.pathname}${trimmedSearch}`);
                            try {
                                windowStates = JSON.parse(decodeURIComponent(wsMatch[0].slice('?ws='.length)));
                                clearHashFromLocation();
                            }
                            catch (e) {
                                console.warn('Invalid windowState in hash', e);
                            }
                        }
                        windowStates.forEach((state) => {
                            switch (state.data.type) {
                                case 'probe': {
                                    (0, displayProbeModal_4.default)(modalEnv, state.modalPos, (0, UpdatableNodeLocator_7.createMutableLocator)(state.data.locator), state.data.property, state.data.nested);
                                    break;
                                }
                                case 'ast': {
                                    (0, displayAstModal_3.default)(modalEnv, state.modalPos, (0, UpdatableNodeLocator_7.createMutableLocator)(state.data.locator), state.data.direction, state.data.transform);
                                    break;
                                }
                                default: {
                                    console.warn('Unexpected probe window state type:', state.data);
                                }
                            }
                        });
                    }
                    catch (e) {
                        console.warn('Invalid probe window state?', e);
                    }
                }, 300); // JUUUUUUUST in case the stored window state causes issues, this 300ms timeout allows people to click the 'clear state' button
                window.RagQuery = (line, col, autoSelectRoot) => {
                    if (autoSelectRoot) {
                        const node = { type: '<ROOT>', start: (line << 12) + col - 1, end: (line << 12) + col + 1, depth: 0 };
                        (0, displayAttributeModal_7.default)(modalEnv, null, (0, UpdatableNodeLocator_7.createMutableLocator)({ result: node, steps: [] }));
                    }
                    else {
                        (0, displayRagModal_1.default)(modalEnv, line, col);
                    }
                };
            };
            wsHandler.on('init', initHandler);
            wsHandler.on('refresh', () => {
                console.log('notifying of refresh..');
                notifyLocalChangeListeners(undefined, 'refresh-from-server');
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
            if (txt === 'http') {
                return doMain('ws-over-http');
            }
            if (txt.startsWith('codespaces-compat:')) {
                const parts = txt.slice('codespaces-compat:'.length).split(':');
                if (parts.length === 2) {
                    const from = Number.parseInt(parts[0], 10);
                    const to = Number.parseInt(parts[1], 10);
                    if (Number.isNaN(from) || Number.isNaN(to)) {
                        throw new Error(`Bad codespaces compat values: [${from},${to}]`);
                    }
                    return doMain({ type: 'codespaces-compat', from, to });
                }
                else {
                    throw new Error(`Bad codespaces compat values: ${parts.join(", ")}`);
                }
            }
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
const lightColors = {
    'window-border': '#999',
    'probe-result-area': '#F4F4F4',
    'syntax-type': '#267F99',
    'syntax-attr': '#795E26',
    'syntax-modifier': '#0000FF',
    'syntax-variable': '#001080',
    'separator': '#000',
    'ast-node-bg': '#DDD',
    'ast-node-bg-hover': '#AAA',
};
const darkColors = {
    'window-border': '#999',
    'probe-result-area': '#333',
    'syntax-type': '#4EC9B0',
    'syntax-attr': '#DCDCAA',
    'syntax-modifier': '#569CD6',
    'syntax-variable': '#9CDCFE',
    'separator': '#FFF',
    'ast-node-bg': '#1C1C1C',
    'ast-node-bg-hover': '#666',
};
const getThemedColor = (lightTheme, type) => {
    return (lightTheme ? lightColors : darkColors)[type];
};
