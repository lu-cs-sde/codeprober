"use strict";
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
        const id = Math.floor(Math.random() * Number.MAX_SAFE_INTEGER);
        rpcQuerySocket.send(JSON.stringify({ id, ...props }));
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
    const notifyLocalChangeListeners = () => {
        Object.values(onChangeListeners).forEach(l => l());
    };
    function init(editorType) {
        if (!location.search) {
            location.search = "editor=" + editorType;
            return;
        }
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
            let changeCtr = version;
            let blockOnChange = false;
            const onChange = (newValue) => {
                if (blockOnChange) {
                    return;
                }
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
                notifyLocalChangeListeners();
            };
            // window.foo
            // parserToggler.disabled = false;
            // parserToggler.value = parser;
            let setLocalState = (value) => { };
            let markText;
            const darkModeCheckbox = document.getElementById('control-dark-mode');
            darkModeCheckbox.checked = !window.MiniEditorUtils.getThemeIsLight();
            const defineThemeToggler = (cb) => {
                darkModeCheckbox.oninput = (e) => {
                    let lightTheme = !darkModeCheckbox.checked;
                    window.MiniEditorUtils.setThemeIsLight(lightTheme);
                    cb(lightTheme);
                };
                cb(window.MiniEditorUtils.getThemeIsLight());
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
                const filteredAddMarker = (start, end, msg) => {
                    const uniqId = [start, end, msg].join(' | ');
                    ;
                    if (deduplicator.has(uniqId)) {
                        return;
                    }
                    deduplicator.add(uniqId);
                    const lineStart = (start >>> 12);
                    const colStart = start & 0xFFF;
                    const lineEnd = (end >>> 12);
                    const colEnd = end & 0xFFF;
                    activeMarkers.push(markText({ lineStart, colStart, lineEnd, colEnd, message: msg }));
                };
                console.log('probeMarkers:', JSON.stringify(probeMarkers, null, 2));
                Object.values(probeMarkers).forEach(arr => arr.forEach(({ errStart, errEnd, msg }) => filteredAddMarker(errStart, errEnd, msg)));
            };
            const captureStdoutCheckbox = document.getElementById('control-capture-stdout');
            captureStdoutCheckbox.oninput = () => {
                notifyLocalChangeListeners();
            };
            const duplicateProbeCheckbox = document.getElementById('control-duplicate-probe-on-attr');
            const modalEnv = {
                performRpcQuery, probeMarkers, onChangeListeners, updateMarkers,
                getLocalState: () => getLocalState(),
                captureStdout: () => captureStdoutCheckbox.checked,
                duplicateOnAttr: () => duplicateProbeCheckbox.checked
            };
            window.displayHelp = () => {
                const helpButton = document.getElementById('display-help');
                helpButton.disabled = true;
                // TODO prevent this if help window already open
                // Maybe disable the help button, re-enable on close?
                const helpWindow = window.MiniEditorUtils.showModal({
                    rootStyle: `
            width: 16rem;
            min-height: 12rem;
          `,
                    render: (root) => {
                        root.appendChild(window.MiniEditorUtils.createModalTitle({
                            renderLeft: (container) => {
                                const header = document.createElement('span');
                                header.innerText = `How to use`;
                                container.appendChild(header);
                            },
                            onClose: () => {
                                helpButton.disabled = false;
                                helpWindow.remove();
                            },
                        }).element);
                        const textHolder = document.createElement('div');
                        textHolder.style.padding = '0.5rem';
                        const paragraphs = [
                            `Here is some help text`,
                            `Right click in the editor and debug away!`,
                            `Contributions welcome at [url]`,
                            `Version: abc-123`,
                        ];
                        paragraphs.forEach(p => {
                            const node = document.createElement('p');
                            node.textContent = p.trim();
                            textHolder.appendChild(node);
                        });
                        root.appendChild(textHolder);
                    }
                });
            };
            setTimeout(() => {
                window.QueryModals.displayProbeModal(modalEnv, { x: 320, y: 240 }, { lineStart: 5, colStart: 1, lineEnd: 7, colEnd: 2 }, 'Program', 'prettyPrint');
            }, 500);
            window.RagQuery = (line, col) => {
                console.log('sending query to rag modal..');
                try {
                    window.QueryModals.displayRagModal(modalEnv, line, col);
                }
                catch (e) {
                    console.warn('wtf', e);
                    throw e;
                }
            };
        };
        handlers.init = init;
        handlers['init-pasta'] = () => {
            pastaMode = true;
            delete window.DoAutoComplete;
            rootElem.style.gridTemplateColumns = '3fr 1fr';
            handlers.init({ value: '// Hello World!\n\nint main() {\n  print(123 + 456);\n}\n', parser: 'beaver', version: 1 });
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
        // }
        socket.addEventListener('close', () => {
            // Small reduce risk of it appearing when navigating away
            setTimeout(() => window.MiniEditorUtils.addCloseNotice(didReceiveAtLeastOneMessage), 100);
        });
    }
    function maybeAutoInit() {
        const idx = location.search.indexOf('editor=');
        const editorId = location.search.slice(idx + 'editor='.length).split('&')[0];
        if (editorId) {
            init(editorId);
        }
    }
    return { maybeAutoInit }
};
window.MiniEditorMain = main();
// export default main;
