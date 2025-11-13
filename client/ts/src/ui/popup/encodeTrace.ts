import ModalEnv from '../../model/ModalEnv';
import { createMutableLocator } from '../../model/UpdatableNodeLocator';
import deepEqual from '../../model/util/deepEqual';
import { NodeLocator, RpcBodyLine, Tracing } from '../../protocol';
import { installLazyHoverDialog } from '../create/installLazyHoverDialog';
import registerOnHover from '../create/registerOnHover';
import startEndToSpan from '../startEndToSpan';
import displayAttributeModal from './displayAttributeModal';
import { ExtraEncodingArgs, LineEncodingArgs } from './encodeRpcBodyLines';


interface EncodeTraceArgs {
  env: ModalEnv;
  trace: Tracing;
  path: number[];
  nestingLevel: number;
  createNodeNode: (target: HTMLElement, locator: NodeLocator, nestingLevel: number, bodyPath: number[], includePositionIndicator?: boolean) => void;
  encodeLine: (target: HTMLElement, line: RpcBodyLine, nestingLevel: number, bodyPath: number[], extraEncodingArgs?: LineEncodingArgs) => void;
  dst: HTMLElement;
  extras: Pick<ExtraEncodingArgs, 'tracingExpansionTracker'>;
}

interface TraceTreeNode {
  data: Tracing;
  dependencies: TraceTreeNode[];
  parent: TraceTreeNode | null;
  indexInParent: number;
  // A reference to the "same" attribute evaluation in a fixed-point evaluation
  // cycle just before the cycle that this trace belongs to. Can be a distant cousin.
  circularCousin?: TraceTreeNode;
  isCousinDiff?: boolean;
  isOrContainsCousinDiff?: boolean;
  mostRecentSummaryDiv?: HTMLElement;
  maybeRunBodyBuilder?: () => void;
}

const prepareTraceTree = (rootTrace: Tracing): TraceTreeNode => {
  const mergeCircularEvents = (trace: Tracing, parent: TraceTreeNode | null, indexInParent: number): TraceTreeNode => {
    const ret: TraceTreeNode = { data: trace, dependencies: [], parent, indexInParent, };
    let sourceDependencies: Tracing[];
    if (!trace.isCircular) {
      // Simple case, just take deps as-is
      sourceDependencies = trace.dependencies;
    } else {
      // Else: circular!
      // A circular trace can contain unexpected data, for example generated after ASTState.startLastCycle in JastAdd.
      // If we use the dependencies as-is, then it can look very strange in the trace. Therefore, group all unexpected
      // events into an "other" trace event
      sourceDependencies = [];
      trace.dependencies.forEach((dep, depId) => {
        if (dep.prop.name === trace.prop.name) {
          // Not fool-proof check but this is likely correct, keep it
          sourceDependencies.push(dep);
        } else {
          // Other event
          if (sourceDependencies.length) {
            // Merge into previous "real" event
            sourceDependencies[sourceDependencies.length - 1].dependencies.push(dep);
          } else {
            // Insert synthetic event
            sourceDependencies.push({
              node: dep.node,
              prop: { name: MAGIC_ATTR_NAME_CIRCLE_UNEXPECTED_EVENT, },
              dependencies: [dep],
              result: { type: 'plain', value: '' },
            });
          }
        }
      });
    }
    sourceDependencies.forEach((dep, depIdx) => {
      ret.dependencies.push(mergeCircularEvents(dep, ret, depIdx));
    })
    return ret;
  }

  const updateCousinPointer = (trace: TraceTreeNode) => {
    if (trace.parent) {
      const parent = trace.parent;
      if (parent.data.isCircular) {
        // Our "circular cousin" is simply the nearest previous neighbor
        if (trace.indexInParent > 0) {
          trace.circularCousin = parent.dependencies[trace.indexInParent - 1];
        }
      } else if (parent.circularCousin) {
        // We are inside a circular evaluation
        const cousin = parent.circularCousin.dependencies[trace.indexInParent];
        if (cousin) {
          const our = trace.data;
          const their = cousin.data;
          if (deepEqual(our.prop, their.prop) && deepEqual(our.node.result, their.node.result)) {
            trace.circularCousin = cousin;
            trace.isCousinDiff = !deepEqual(our.result, their.result);
          }
        }
      }
    }
    trace.dependencies.forEach(updateCousinPointer);
  };
  const updateDiffValues = (trace: TraceTreeNode) => {
    trace.dependencies.forEach(updateDiffValues);
    if (trace.isCousinDiff || trace.dependencies.some(x => x.isOrContainsCousinDiff)) {
      trace.isOrContainsCousinDiff = true;
    }
  }
  const tree = mergeCircularEvents(rootTrace, null, 0);
  // Now the tree structure is fixed, time to assign cousin references
  updateCousinPointer(tree);
  // Finally, propagate "isOrContainsCousinDiff" up in the tree
  updateDiffValues(tree);
  return tree;
}

const MAGIC_ATTR_NAME_CIRCLE_UNEXPECTED_EVENT = '$circular_unexpected_event$';

const encodeTrace = (args: EncodeTraceArgs) => {
  const doEncodeTrace = (tr: TraceTreeNode, path: number[], parent: TraceTreeNode | null, indexInParent: number, dst: HTMLElement) => {
    const locToShortStr = (locator: NodeLocator) => locator.result.label ?? locator.result.type.split('.').slice(-1)[0];

    const summaryHolder = document.createElement('div');
    summaryHolder.style.display = 'inline';
    const isTopTrace = !parent;
    let addResult = !isTopTrace;
    if (isTopTrace) {
      const countTraces = (tr: TraceTreeNode): number => {
        return 1 + tr.dependencies.reduce((a, b) => a + countTraces(b), 0);
      };
      const count = countTraces(tr);
      summaryHolder.classList.add('stream-arg-msg');
      if (count <= 1) {
        summaryHolder.innerText = `No traces available`;
      } else {
        summaryHolder.innerText = `${count - 1} trace${count == 2 ? '' : 's'} available, click to expand`;
      }
    } else if (tr.data.prop.name === MAGIC_ATTR_NAME_CIRCLE_UNEXPECTED_EVENT) {
      summaryHolder.classList.add('syntax-modifier');
      summaryHolder.innerText = 'Unrelated Attributes'
      summaryHolder.style.fontStyle = 'italic'
      addResult = false;
    } else {
      const summaryPartNode = document.createElement('span');
      summaryPartNode.classList.add('syntax-type');
      summaryPartNode.innerText = locToShortStr(tr.data.node);
      summaryPartNode.classList.add('clickHighlightOnHover');
      summaryPartNode.onmousedown = (e) => {
        e.stopPropagation();
        e.stopImmediatePropagation();
      };
      const span = startEndToSpan(tr.data.node.result.start, tr.data.node.result.end);
      registerOnHover(summaryPartNode, isHovering => {
        args.env.updateSpanHighlight(isHovering ? span : null);
      })
      summaryPartNode.onclick = (e) => {
        e.preventDefault();
        displayAttributeModal(args.env.getGlobalModalEnv(), null, createMutableLocator(tr.data.node));
      }


      const summaryPartAttr = document.createElement('span');
      summaryPartAttr.classList.add('syntax-attr');
      summaryPartAttr.innerText = `.${tr.data.prop.name}`;

      let summaryPartResult: HTMLElement | null = null;
      const buildSummaryResult = (builder: (dst: HTMLDivElement) => void) => {
        let div = document.createElement('div');
        div.style.display = 'inline';
        if (tr.data.isCircular) {
          const circNode = div.appendChild(document.createElement('span'));
          circNode.classList.add('syntax-modifier');
          circNode.innerText = 'circular';
          circNode.style.marginLeft = '0.25rem';
          installLazyHoverDialog({
            elem: circNode,
            init: (dialog) => {
              dialog.classList.add('modalWindowColoring');
              dialog.style.padding = '0.25rem';
              dialog.style.maxWidth = '30vw';

              const addPart = (msg: string) => {
                const txt = dialog.appendChild(document.createElement('span'));
                txt.innerText = msg;
                txt.style.whiteSpace = 'normal';
                return txt;
              }
              addPart(`This was evaluated in a cycle until a fixed point was achieved. When expanded, you can see the cycle iterations prefixed with `)
              addPart(`[1]`).classList.add('syntax-modifier');
              addPart(`, `);
              addPart(`[2]`).classList.add('syntax-modifier');
              addPart(` etc. Values after the first cycle are `);
              addPart(`highlighted`).classList.add('trace-value-change');
              addPart(` if they are different than the corresponding value in the previous cycle. This way you can track what triggered the subsequent cycle to run. `);
              addPart(`Similarly, trace subtrees are slightly `);
              addPart(`dimmed`).classList.add('unchanged-circle-value');
              addPart(` if nothing in that subtree is different.`);
            },
          })
        }
        builder(div);
        summaryPartResult = div;
      }
      const addPlainSummary = (raw: string) => {
        const val = raw.trim().replace(/\n/g, '\\n');
        const trimmed = val.length < 16 ? val : `${val.slice(0, 14)}..`;
        buildSummaryResult((tgt) => {
          if (!trimmed) {
            // Empty string, treat specially
            tgt.appendChild(document.createTextNode(' = '));
            const empty = document.createElement('span');
            empty.classList.add('dimmed');
            empty.innerText = `(empty str)`;
            tgt.appendChild(empty);
          } else {
            tgt.appendChild(document.createTextNode(` = ${trimmed}`));
          }
        });
        if (val.length < 16) {
          addResult = false;
        }
      };

      switch (tr.data.result.type) {
        case 'arr': {
          const arr = tr.data.result.value;
          if (arr.length == 1 && arr[0].type == 'plain') {
            addPlainSummary(arr[0].value);
          }
          if (arr.length == 2 && arr[0].type == 'node' && arr[1].type == 'plain' && !arr[1].value.trim()) {
            const locator = arr[0].value;
            buildSummaryResult(tgt => {
              tgt.appendChild(document.createTextNode(' = '));
              args.createNodeNode(tgt, locator, args.nestingLevel + 1, path, false);
              addResult = false;
            });
          }
          break;
        }
        case 'plain': {
          addPlainSummary(tr.data.result.value);
          break;
        }
      }
      summaryHolder.appendChild(summaryPartNode);
      summaryHolder.appendChild(summaryPartAttr);
      if (summaryPartResult) {
        summaryHolder.appendChild(summaryPartResult);
      }

      if (addResult) {
        switch (tr.data.result.type) {
          case 'arr': {
            addResult = !!tr.data.result.value.length;
            break;
          }
          case 'plain': {
            addResult = !!tr.data.result.value.length;
            break;
          }
        }
      }
    }

    const body = document.createElement('div');
    body.style.paddingLeft = '1rem';
    let autoOpenChildrenOnClick = false;
    let bodyBuilder = () => {
      if (addResult) {
        args.encodeLine(body, tr.data.result, args.nestingLevel + 1, path, { omitArrMarginLeft: true });
      }
      if (isTopTrace && tr.data.isCircular) {
        // Embed ourselves inside the 'N traces available' text so that the "circular" text has a chance to appear.
        autoOpenChildrenOnClick = true;
        doEncodeTrace(tr, path, tr, 0, body);
      } else {
        tr.dependencies.forEach((dep, depIdx) => {
          doEncodeTrace(dep, [...path, depIdx + 1], tr, depIdx, body);
        });
      }
    };

    const summary = document.createElement('summary');

    if (parent?.data?.isCircular && tr !== parent && tr.data.prop.name !== MAGIC_ATTR_NAME_CIRCLE_UNEXPECTED_EVENT) {
      const meta = summary.appendChild(document.createElement('span'));
      meta.innerText = `[${indexInParent + 1}] `;
      meta.classList.add('syntax-modifier')
    }
    summary.appendChild(summaryHolder);

    tr.mostRecentSummaryDiv = summary;
    if (tr.circularCousin && !tr.parent?.data?.isCircular) {
      registerOnHover(summary, (isHovering) => {
        if (tr.circularCousin?.mostRecentSummaryDiv) {
          const list = tr.circularCousin.mostRecentSummaryDiv.classList;
          const cls = 'trace-value-cousin-hovered';
          if (isHovering) {
            list.add(cls);
          } else {
            list.remove(cls);
          }
        }
      });

      const cousin = tr.circularCousin;
      summary.addEventListener('contextmenu', (e) => {

        const menu = document.body.appendChild(document.createElement('div'));
        const outsideClickListener = (e: MouseEvent) => {
          if (menu.contains(e.target as any)) {
            // Inside the menu
            return;
          }
          // Clicking somewhere else, hide the menu
          menu.remove();
          document.removeEventListener('mousedown', outsideClickListener);
        };
        document.addEventListener('mousedown', outsideClickListener);


        menu.classList.add('codemirror_context_menu');
        const rows = [
          { lbl: 'Show Previous Cycle',
            click: () => {
              const expand = (trace: TraceTreeNode) => {
                menu.remove();
                if (!trace.mostRecentSummaryDiv) {
                  if (trace.parent) {
                    expand(trace.parent);
                    trace.parent.maybeRunBodyBuilder?.();
                  }
                }
              }
              expand(cousin);
              if (!cousin.mostRecentSummaryDiv) {
                console.warn('Failed to expand trace', cousin);
                return;
              }
              const expandDetails = (elem: HTMLElement | null) => {
                if (!elem || elem == args.dst) {
                  return;
                }
                if (elem.nodeName === 'DETAILS') {
                  (elem as HTMLDetailsElement).open = true;
                }
                expandDetails(elem.parentElement);
              }
              expandDetails(cousin.mostRecentSummaryDiv);
              const cousinDiv = cousin.mostRecentSummaryDiv;
              cousinDiv.scrollIntoView();
              const anim = 'trace-previous-cycle-blink';
              cousinDiv.classList.add(anim);
              setTimeout(() => cousinDiv.classList.remove(anim), 500);
            },
          }
        ];
        rows.forEach(cfg => {
          const row = document.createElement('div');
          row.classList.add('row');
          const lbl = document.createElement('span');
          lbl.innerText = cfg.lbl;
          row.appendChild(lbl);
          row.onclick = cfg.click;
          menu.appendChild(row);
        });

        menu.style.top = `${e.pageY + 2}px`;
        menu.style.left = `${e.pageX + 2}px`;
        menu.classList.add('show');
        setTimeout(() => menu.focus(), 100);

        e.preventDefault();
        return false;
      });
    }

    if (!tr.data.isCircular && tr.circularCousin && !tr.isOrContainsCousinDiff) {
      summary.classList.add('unchanged-circle-value');
    }

    let bodyNeedsBuilding = true;
    tr.maybeRunBodyBuilder = () => {
      if (bodyNeedsBuilding) {
        bodyNeedsBuilding = false;
        bodyBuilder();
      }
    }
    if (tr.isCousinDiff) {
      summary.classList.add('trace-value-change')
    }
    if (!addResult && !tr.dependencies.length) {
      dst.append(summary);
    } else {
      summary.onmousedown = (e) => {
        e.stopPropagation();
      };
      summary.classList.add('clickHighlightOnHover');

      const det = document.createElement('details');
      const trKey = `${JSON.stringify(path)}:${tr === parent ? '[self-embed]:' : ''}${tr.data.prop.name}`;
      det.open = args.extras.tracingExpansionTracker?.[trKey] ?? false;

      det.addEventListener('toggle', e => {
        const tracker = args.extras.tracingExpansionTracker;
        if (tracker) {
          tracker[trKey] = det.open;
        }
        if (det.open) {
          tr.maybeRunBodyBuilder?.()
        }
        console.log('should open next?', det.open, autoOpenChildrenOnClick);
        if (det.open && autoOpenChildrenOnClick) {
          autoOpenChildrenOnClick = false;
          body.childNodes.forEach(child => {
            console.log('here..', child)
            if (child instanceof HTMLDetailsElement) {
              child.open = true;
            } else {
              console.log('different child type', child);
            }
          })
        }
      });
      det.appendChild(summary);
      det.appendChild(body);
      dst.appendChild(det);
    }
  };

  doEncodeTrace(prepareTraceTree(args.trace), args.path, null, 0, args.dst);
}

export default encodeTrace;
