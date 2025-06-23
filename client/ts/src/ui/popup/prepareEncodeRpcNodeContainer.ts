import ModalEnv from '../../model/ModalEnv';
import { createMutableLocator } from '../../model/UpdatableNodeLocator';
import { NodeContainer, NodeLocator, RpcBodyLine } from '../../protocol';
import { installLazyHoverDialog } from '../create/installLazyHoverDialog';
import registerOnHover from '../create/registerOnHover';
import trimTypeName from '../trimTypeName';
import displayAttributeModal from './displayAttributeModal';

const prepareEncodeRpcNodeContainer = (args: {
  env: ModalEnv, encodeLine:  (target: HTMLElement, line: RpcBodyLine, nestingLevel: number, bodyPath: number[]) => void
}) => {

  const nodeContainerNodeHoverStack: Span[] = [];
  const createNodeContainerNode = (target: HTMLElement, ncon: NodeContainer, nestingLevel: number, bodyPath: number[], includePositionIndicator = true) => {

    const corner = document.createElement('div');
    corner.classList.add('node-container-corner');

    const meaningfullnessCacheSymbol = Symbol('meaningfullComputationCache')
    const isMeaningfulContainer = (query: NodeContainer): boolean => {
      let cache = (query as any)[meaningfullnessCacheSymbol];
      if (cache !== undefined) {
        return cache;
      }

      let ret = false;
      if (query.body) {
        const bod = query.body;
        if (bod.type === 'arr' && bod.value.length === 1 && bod.value[0].type === 'nodeContainer') {
          ret = isMeaningfulContainer(bod.value[0].value);
        } else {
          ret = true;
        }
      }
      (query as any)[meaningfullnessCacheSymbol] = ret;
      return ret;
    }

    const registerNodeHoverHighlight = (node: HTMLElement, locator: NodeLocator) => {
      const { start, end } = locator.result;
      const span: Span = {
        lineStart: (start >>> 12), colStart: (start & 0xFFF),
        lineEnd: (end >>> 12), colEnd: (end & 0xFFF),
      };

      registerOnHover(node, on => {
        if (on) {
          nodeContainerNodeHoverStack.push(span);
        } else {
          nodeContainerNodeHoverStack.pop();
        }
        args.env.updateSpanHighlight(nodeContainerNodeHoverStack[nodeContainerNodeHoverStack.length - 1] ?? null)

        node.style.cursor = 'default';
        node.classList.add('clickHighlightOnHover');
      });
      node.onmousedown = (e) => {
        e.stopPropagation();
      };
      node.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        displayAttributeModal(args.env.getGlobalModalEnv(), null, createMutableLocator(locator));
      });
      node.classList.add('lazy-hover-diag-trigger'); // Add to hide lazy dialogs when this is hovered
    }

    let lastAddedTypeToCorner: NodeLocator | null = null;
    const addTypeToCorner = (locator: NodeLocator) => {
      lastAddedTypeToCorner = locator;
      const { type, label } = locator.result;

      const typeNode = document.createElement('span');
      typeNode.classList.add('syntax-type');
      typeNode.innerText = label ?? trimTypeName(type);
      // typeNode.style.margin = 'auto 0';
      corner.appendChild(typeNode);
      typeNode.classList.add('clickHighlightOnHover');

      registerNodeHoverHighlight(typeNode, locator);
    }

    addTypeToCorner(ncon.node);
    // container.style.width = 'fit-content';
    // container.style.display = 'inline';

    const container = document.createElement('div');
    container.classList.add('node-container')
    // container.classList.add('node-container-vertical')
    // if (!isMeaningfulContainer(ncon)) {
    //   container.classList.add('node-container-small')
    //   container.classList.add('lazy-hover-diag-trigger'); // Add to hide lazy dialogs when this is hovered
    // }

    let inliner = ncon.body;
    while (inliner
        && inliner.type === 'arr'
        && inliner.value.length === 1
        && inliner.value[0].type === 'nodeContainer'
      ) {
      // Inline multiple steps into one
      const joiner = document.createElement('span');
      joiner.innerText = `>`;
      corner.appendChild(joiner);
      const inner = inliner.value[0].value;
      addTypeToCorner(inner.node);
      inliner = inner.body!;
    }

    if (!isMeaningfulContainer(ncon)) {
      const body = document.createElement('div');
      body.classList.add('node-container-body');
      body.classList.add('node-container-body-placeholder');
      body.appendChild(document.createTextNode('..'));
      container.appendChild(body);
      installLazyHoverDialog({
        elem: body,
        init: (dialog) => {
          if (corner.parentElement) {
            corner.remove();
          }
          dialog.classList.add('modalWindowColoring');
          dialog.style.padding = '0.25rem';
          dialog.appendChild(corner);
        }
      });
      if (lastAddedTypeToCorner) {
        registerNodeHoverHighlight(body, lastAddedTypeToCorner);
      }
    } else if (inliner) {

      const addBodyRows = (body: HTMLElement, content: RpcBodyLine) => {
        if (content.type !== 'arr' || content.value.length <= 1) {
          // Weird type or just single element, just add it
          args.encodeLine(body, content, 0, [...bodyPath, 0]);
          return;
        }
        const rowList = document.createElement('div');
        rowList.classList.add('node-container-body-row-list');
        body.appendChild(rowList);

        let lastLine = 0;
        const addRow = () => {
          const ret = document.createElement('div');
          ret.classList.add('node-container-body-row');
          rowList.appendChild(ret);
          return ret;
        }
        let row = addRow();
        const maybeRefreshRow = (newNode: NodeLocator) => {
          const newLine = newNode.result.start >>> 12;
          if (lastLine && newLine && newLine != lastLine) {
            // Time for a new row!
            row = addRow();
          }
          lastLine = newLine;
        }

        const parts = content.value;
        for (let i = 0; i < parts.length; ++i) {
          let elem = parts[i];
          switch (elem.type) {
            case 'node': maybeRefreshRow(elem.value); break
            case 'nodeContainer': maybeRefreshRow(elem.value.node); break
            case 'plain': {
              if (elem.value.startsWith('\n')) {
                row = addRow();
                elem = { type: 'plain', value: elem.value.substring(1) };
              }
              break;
            }
          }
          if (elem.type === 'plain') {
            row.appendChild(document.createTextNode(elem.value));
          } else {
            args.encodeLine(row, elem, 0, [...bodyPath, 0, i]);
          }
          if (i < parts.length - 1) {
            switch (elem.type) {
              case 'plain': {
                if (elem.value.endsWith('\n')) {
                  row = addRow();
                }
                break;
              }
            }
          }
        }
      }

      if (inliner.type === 'arr' && inliner.value.some(x => x.type === 'plain')) {
        // At least one nonempty string which the user can hover, no need to always display the corner
        corner.classList.add('node-container-corner-nonempty');
        const body = document.createElement('div');
        body.classList.add('node-container-body');
        addBodyRows(body, inliner);
        // encodeLine(body, inliner, 0, [...bodyPath, 0]);
        container.classList.add('node-container-cornerhide');
        container.appendChild(body);

        installLazyHoverDialog({
          elem: body,
          init: (dialog) => {
            if (corner.parentElement) {
              corner.remove();
            }
            dialog.classList.add('modalWindowColoring');
            dialog.style.padding = '0.25rem';
            dialog.appendChild(corner);
          }
        });
        if (lastAddedTypeToCorner) {
          registerNodeHoverHighlight(body, lastAddedTypeToCorner);
        }
      } else {
        // Multiple children, no string literals
        container.appendChild(corner);
        corner.classList.add('node-container-corner-nonempty');
        const body = document.createElement('div');
        body.classList.add('node-container-body');
        addBodyRows(body, inliner);
        // encodeLine(body, inliner, 0, [...bodyPath, 0]);
        container.appendChild(body);
      }
    } else {
      // Just the "corner", no inner content
      container.appendChild(corner);
    }
    target.appendChild(container);
  };

  return { createNodeContainerNode };
}

export default prepareEncodeRpcNodeContainer;
