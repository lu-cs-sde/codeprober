"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const displayRagModal = (env, line, col) => {
    console.log('hello??');
    const popup = window.MiniEditorUtils.showModal({
        rootStyle: `
        min-width: 12rem;
        min-height: 8rem;
        max-height: 12rem;
      `,
        render: (root, cancelToken) => {
            // root.innerText = 'Loading..';
            const spinner = window.MiniEditorUtils.createLoadingSpinner();
            spinner.classList.add('absoluteCenter');
            root.appendChild(spinner);
            env.performRpcQuery({
                type: 'query',
                lineStart: line,
                colStart: col,
                lineEnd: line,
                colEnd: col,
                text: env.getLocalState(),
                queryText: `Program.containingSpansAndNodeTypes`,
            })
                .then((result) => {
                if (cancelToken.cancelled) {
                    return;
                }
                while (root.firstChild)
                    root.removeChild(root.firstChild);
                root.style.minHeight = '4rem';
                root.appendChild(window.MiniEditorUtils.createModalTitle({
                    renderLeft: (container) => {
                        const headType = document.createElement('span');
                        headType.classList.add('syntax-stype');
                        headType.innerText = `Select node..`;
                        container.appendChild(headType);
                    },
                    onClose: () => {
                        popup.remove();
                    },
                }).element);
                const needle = 'SpansAndNodeTypes :: ';
                const interestingLine = result.split('\n').find(l => l.startsWith(needle));
                if (!interestingLine) {
                    throw new Error("Couldn't find expected line in output");
                }
                const parsed = JSON.parse(interestingLine.slice(needle.length));
                const rows = parsed.map((ent, entIdx) => {
                    let [strStart, strEnd, t] = ent.split(':');
                    const start = +strStart;
                    const end = +strEnd;
                    const span = { lineStart: (start >>> 12), colStart: (start & 0xFFF), lineEnd: (end >>> 12), colEnd: (end & 0xFFF) };
                    const node = document.createElement('div');
                    node.classList.add('clickHighlightOnHover');
                    node.style.padding = `0 0.25rem`;
                    if (entIdx !== 0) {
                        node.style.borderTop = '1px solid gray';
                    }
                    node.innerText = t;
                    window.MiniEditorUtils.registerOnHover(node, on => env.updateSpanHighlight(on ? span : null));
                    node.onmousedown = (e) => { e.stopPropagation(); };
                    node.onclick = () => {
                        popup.remove();
                        env.updateSpanHighlight(null);
                        window.QueryModals.displayAttributeModal(env, popup.getPos(), span, t);
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
                setTimeout(() => popup.remove(), 1000);
            });
        }
    });
};
exports.default = displayRagModal;
