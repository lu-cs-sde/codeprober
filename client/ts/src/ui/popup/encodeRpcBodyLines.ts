import ModalEnv from '../../model/ModalEnv';
import { createMutableLocator } from '../../model/UpdatableNodeLocator';
import { NodeLocator, RpcBodyLine, Tracing } from '../../protocol';
import createTextSpanIndicator from "../create/createTextSpanIndicator";
import registerNodeSelector from "../create/registerNodeSelector";
import registerOnHover from "../create/registerOnHover";
import trimTypeName from "../trimTypeName";
import displayAttributeModal from "./displayAttributeModal";
import { graphviz as d3 } from 'dependencies/graphviz/graphviz';
import { assertUnreachable } from '../../hacks';
import startEndToSpan from '../startEndToSpan';

const getCommonStreamArgWhitespacePrefix = (line: RpcBodyLine): number => {
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
}

type LineDecorator = (linePath: number[]) => 'default' | 'error' | 'unmatched';

type ExpanderCallback<T = {}> = (args: {
  path: number[];
  locator: NodeLocator;
  locatorRoot: HTMLElement;
  expansionArea: HTMLElement;
} & T) => void;

interface ExtraEncodingArgs {
  decorator?: LineDecorator;
  lateInteractivityEnabledChecker?: () => boolean;
  disableNodeSelectors?: true;
  disableInlineExpansionButton?: true;
  nodeLocatorExpanderHandler?: {
    getReusableExpansionArea: (path: number[]) => HTMLElement | null;
    onCreate: ExpanderCallback<{
      isFresh: boolean;
    }>;
    onClick: ExpanderCallback;
  };
  excludeStdIoFromPaths?: true;
  capWidths?: true;
}
const encodeRpcBodyLines = (env: ModalEnv, body: RpcBodyLine[], extras: ExtraEncodingArgs = {}): HTMLElement => {
  let needCapturedStreamArgExplanation = false;
  let localDisableNodeExpander = false;

  const createNodeNode = (target: HTMLElement, locator: NodeLocator, nestingLevel: number, bodyPath: number[], includePositionIndicator = true) => {
    const { start, end, type, label } = locator.result;

    const container = document.createElement('div');
    if (extras.decorator) {
      applyDecoratorClass(container, nestingLevel <= 1, extras.decorator(bodyPath));
    }
    // container.appendChild(document.createTextNode(`area:${JSON.stringify(bodyPath)}`));
    const span: Span = {
      lineStart: (start >>> 12), colStart: (start & 0xFFF),
      lineEnd: (end >>> 12), colEnd: (end & 0xFFF),
    };

    if (includePositionIndicator) {
      container.appendChild(createTextSpanIndicator({
        span,
        marginLeft: false,
        autoVerticalMargin: true,
      }));
    }
    const typeNode = document.createElement('span');
    typeNode.classList.add('syntax-type');
    typeNode.innerText = label ?? trimTypeName(type);
    typeNode.style.margin = 'auto 0';
    container.appendChild(typeNode);

    container.classList.add('clickHighlightOnHover');
    container.style.width = 'fit-content';
    container.style.display = 'inline';
    registerOnHover(container, on => {
      if (!on || (extras.lateInteractivityEnabledChecker?.() ?? true)) {
        env.updateSpanHighlight(on ? span : null);
        container.style.cursor = 'default';
        container.classList.add('clickHighlightOnHover');
      } else {
        container.style.cursor = 'not-allowed';
        container.classList.remove('clickHighlightOnHover');
      }
    });
    container.onmousedown = (e) => {
      e.stopPropagation();
    };
    if (!extras.disableNodeSelectors) {
      registerNodeSelector(container, () => locator);
    }
    container.addEventListener('click', () => {
      if (extras.lateInteractivityEnabledChecker?.() ?? true) {
        displayAttributeModal(env.getGlobalModalEnv(), null, createMutableLocator(locator));
      }
    });
    if (!localDisableNodeExpander && extras.nodeLocatorExpanderHandler) {
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
          locator,
          locatorRoot: outerContainer,
          expansionArea,
          path: bodyPath,
        });
      }


      const outerContainer = document.createElement('div');
      outerContainer.style.display = 'inline-flex';
      // outerContainer.style.marginBottom = '0.125rem';
      outerContainer.style.flexDirection = 'column';
      outerContainer.appendChild(middleContainer);

      const existingExpansionArea = extras.nodeLocatorExpanderHandler.getReusableExpansionArea(bodyPath);
      if (existingExpansionArea) {
        if (existingExpansionArea.parentElement) {
          existingExpansionArea.parentElement.removeChild(existingExpansionArea);
        }
      }
      const expansionArea = existingExpansionArea ?? document.createElement('div');
      outerContainer.appendChild(expansionArea);

      target.appendChild(outerContainer);

      extras.nodeLocatorExpanderHandler.onCreate({
        locator,
        locatorRoot: outerContainer,
        expansionArea,
        path: bodyPath,
        isFresh: !existingExpansionArea,
      });

    } else {
      target.appendChild(container);
    }
  };


  const appliedDecoratorResultsTrackers: Partial<Record<ReturnType<LineDecorator>, true>>[] = [];
  const applyDecoratorClass = (target: HTMLElement, applyRoot: boolean, decoratorResult: ReturnType<LineDecorator> ) => {
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
  }

  const streamArgPrefix = Math.min(...body.map(getCommonStreamArgWhitespacePrefix));

  const encodeDotVal = (dotVal: string): HTMLDivElement => {
    const holder = document.createElement('div');
    const id = `dot_${(Math.random() * Number.MAX_SAFE_INTEGER) | 0}`
    holder.id = id;
    setTimeout(() => {
      if (!!document.getElementById(id)) {
        try {
          d3(`#${id}`, { zoom: false })
            .dot(dotVal)
            .render()
            ;
        } catch (e) {
          console.warn('Error when d3:ing', e);
        }
      }
    }, 100)
    return holder;
  }
  const encodeLine = (target: HTMLElement, line: RpcBodyLine, nestingLevel: number, bodyPath: number[]) => {
    const addPlain = (msg: string, plainHoverSpan?: { start: number, end: number }) => {
      const trimmed = msg.trimStart();
      if (extras.decorator) {
        const decoration = extras.decorator(bodyPath);
        if (decoration !== 'default') {
          const holder = document.createElement('spawn');
          if (extras.capWidths) {
            holder.style.whiteSpace = 'normal';
          } else {
            holder.style.whiteSpace = 'pre';
          }
          if (trimmed.length !== msg.length) {
            holder.appendChild(document.createTextNode(' '.repeat(msg.length - trimmed.length)));
          }
          if (msg.trim()) {
            holder.appendChild(document.createTextNode(msg.trim()));
          }
          holder.appendChild(document.createElement('br'));
          applyDecoratorClass(holder, nestingLevel <= 1, decoration);
          target.appendChild(holder);
          return;
        }
      }
      if (trimmed.length !== msg.length) {
        target.appendChild(document.createTextNode(' '.repeat(msg.length - trimmed.length)));
      }
      if (msg.trim()) {
        const trimmed = msg.trim();
        if (plainHoverSpan) {
          const node = document.createElement('span');
          const span = startEndToSpan(plainHoverSpan.start, plainHoverSpan.end);
          node.classList.add('highlightOnHover');
          registerOnHover(node, hovering => {
            env.updateSpanHighlight(hovering ? span : null)
          });
          node.innerText = trimmed;
          target.appendChild(node);
        } else {
          target.appendChild(document.createTextNode(trimmed));
        }
      }
      target.appendChild(document.createElement('br'));
    };
    switch (line.type) {
      case 'plain': {
        addPlain(line.value);
        break;
      }
      case 'highlightMsg': {
        addPlain(line.value.msg, line.value);
        break;
      }
      case 'arr': {
        const encodeTo = (arrTarget: HTMLElement) => {
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
        } else {
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
            if (tracker?.['error']) {
              wrapper.classList.add('test-diff-error-root');
            } else if (tracker?.['unmatched']) {
              wrapper.classList.add('test-diff-unmatched-root');
            }
            target.appendChild(wrapper);
          } else {
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
      case 'node': {
        createNodeNode(target, line.value, nestingLevel, bodyPath)
        break;
      }

      case 'dotGraph': {
        target.appendChild(encodeDotVal(line.value));
        target.appendChild(document.createElement('br'));
        break;
      }

      case 'tracing': {
        const encodeTrace = (tr: Tracing, path: number[], isTopTrace: boolean, dst: HTMLElement) => {
          const locToShortStr = (locator: NodeLocator) => locator.result.label ?? locator.result.type.split('.').slice(-1)[0];

          const summaryHolder = document.createElement('div');
          summaryHolder.style.display = 'inline';
          let addResult = !isTopTrace;
          if (isTopTrace) {
            const countTraces = (tr: Tracing): number => {
              return 1 + tr.dependencies.reduce((a, b) => a + countTraces(b), 0);
            };
            const count = countTraces(tr);
            summaryHolder.classList.add('stream-arg-msg');
            if (count <= 1) {
              summaryHolder.innerText = `No traces available`;
            } else {
              summaryHolder.innerText = `${count - 1} trace${count == 2 ? '' : 's'} available, click to expand`;
            }
          } else {
            const summaryPartNode = document.createElement('span');
            summaryPartNode.classList.add('syntax-type');
            summaryPartNode.innerText = locToShortStr(tr.node);
            summaryPartNode.classList.add('clickHighlightOnHover');
            summaryPartNode.onmousedown = (e) => {
              e.stopPropagation();
              e.stopImmediatePropagation();
            };
            const span = startEndToSpan(tr.node.result.start, tr.node.result.end);
            registerOnHover(summaryPartNode, isHovering => {
              env.updateSpanHighlight(isHovering ? span : null);
            })
            summaryPartNode.onclick = (e) => {
              // console.log('click summary node', e)
              e.stopPropagation();
              e.stopImmediatePropagation();
              e.preventDefault();
              displayAttributeModal(env.getGlobalModalEnv(), null, createMutableLocator(tr.node));
            }


            const summaryPartAttr = document.createElement('span');
            summaryPartAttr.classList.add('syntax-attr');
            summaryPartAttr.innerText = `.${tr.prop.name}`;

            let summaryPartResult: HTMLElement | null = null;
            const addSummaryResult = (builder: (dst: HTMLDivElement) => void) => {
              let div = document.createElement('div');
              div.style.display = 'inline';
              builder(div);
              summaryPartResult = div;
            }
            const addPlainSummary = (raw: string) => {
              const val = raw.trim().replace(/\n/g, '\\n');
              const trimmed = val.length < 16 ? val : `${val.slice(0, 14)}..`;
              addSummaryResult((tgt) => {
                if (!trimmed) {
                  // Empty string, treat specially
                  tgt.appendChild(document.createTextNode(' = '));
                  const empty = document.createElement('span');
                  empty.classList.add('dimmed');
                  empty.innerText = `(empty str)`;
                  tgt.appendChild(empty);
                } else {
                  tgt.innerText =` = ${trimmed}`;
                }
              });
              if (val.length < 16) {
                addResult = false;
              }
            };

            // let summaryText = `${locToShortStr(tr.node)}.${tr.prop.name}`;
            switch (tr.result.type) {
              case 'arr': {
                const arr = tr.result.value;
                if (arr.length == 1 && arr[0].type == 'plain') {
                  addPlainSummary(arr[0].value);
                }
                if (arr.length == 2 && arr[0].type == 'node' && arr[1].type == 'plain' && !arr[1].value.trim()) {
                  const locator = arr[0].value;
                  addSummaryResult(tgt => {
                    tgt.appendChild(document.createTextNode(' = '));
                    createNodeNode(tgt, locator, nestingLevel + 1, path, false);
                    addResult = false;
                  });
                }
                break;
              }
              case 'plain': {
                addPlainSummary(tr.result.value);
                break;
              }
            }
            // summary.innerText = summaryText;
            summaryHolder.appendChild(summaryPartNode);
            summaryHolder.appendChild(summaryPartAttr);
            if (!!summaryPartResult) {
              summaryHolder.appendChild(summaryPartResult);
            }
          }

          // encodeLine = (target: HTMLElement, line: RpcBodyLine, nestingLevel: number, bodyPath: number[]) => {

          if (addResult) {
            switch (tr.result.type) {
              case 'arr': {
                addResult = !!tr.result.value.length;
                break;
              }
              case 'plain': {
                addResult = !!tr.result.value.length;
                break;
              }
            }
          }

          const body = document.createElement('div');
          body.style.paddingLeft = '1rem';
          if (addResult) {
            console.log('going encode tracing child of', tr.prop.name, ':', tr.result);
            // if (tr.result.type == 'arr' && tr.result.value[1].type == 'plain' && !tr.result.value[1].value.trim()) {
            //   encodeLine(body, tr.result.value[0], nestingLevel + 1, path);
            // } else {
              encodeLine(body, tr.result, nestingLevel + 1, path);
            // }
          }
          tr.dependencies.forEach((dep, depIdx) => {
            encodeTrace(dep, [...path, depIdx + 1], false, body);
          });

          const summary = document.createElement('summary');
          summary.appendChild(summaryHolder);
          // summary.classList.add('syntax-attr');
          if (!addResult && !tr.dependencies.length) {
            dst.append(summary);
          } else {
            summary.onmousedown = (e) => {
              e.stopPropagation();
            };
            summary.classList.add('clickHighlightOnHover');

            const det = document.createElement('details');
            det.appendChild(summary);
            det.appendChild(body);
            dst.appendChild(det);
          }
        };
        localDisableNodeExpander = true;
        encodeTrace(line.value, bodyPath, true, target);
        localDisableNodeExpander = false;
        break;
      }

      default: {
        console.warn('Unknown body line type', line);
        assertUnreachable(line);
        break;
      }
    }
  }
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
      if (!line && !arr[lineIdx+1]) { return false; }
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

      } else {
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

      const add = (msg: string, styled: boolean) => {
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

export default encodeRpcBodyLines;
