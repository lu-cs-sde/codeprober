import ModalEnv from '../../model/ModalEnv';
import { createMutableLocator } from '../../model/UpdatableNodeLocator';
import { NodeLocator, RpcBodyLine } from '../../protocol';
import createTextSpanIndicator from "../create/createTextSpanIndicator";
import registerNodeSelector from "../create/registerNodeSelector";
import registerOnHover from "../create/registerOnHover";
import trimTypeName from "../trimTypeName";
import displayAttributeModal from "./displayAttributeModal";

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
  const encodeLine = (target: HTMLElement, line: RpcBodyLine, nestingLevel: number, bodyPath: number[]) => {
    switch (line.type) {
      case 'plain': {
        const trimmed = line.value.trimStart();
        if (extras.decorator) {
          const decoration = extras.decorator(bodyPath);
          if (decoration !== 'default') {
            const holder = document.createElement('spawn');
            if (extras.capWidths) {
              holder.style.whiteSpace = 'normal';
            } else {
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
      case "node": {
        const { start, end, type, label } = line.value.result;

        const container = document.createElement('div');
        if (extras.decorator) {
          applyDecoratorClass(container, nestingLevel <= 1, extras.decorator(bodyPath));
        }
        // container.appendChild(document.createTextNode(`area:${JSON.stringify(bodyPath)}`));
        const span: Span = {
          lineStart: (start >>> 12), colStart: (start & 0xFFF),
          lineEnd: (end >>> 12), colEnd: (end & 0xFFF),
        };

        container.appendChild(createTextSpanIndicator({
          span,
          marginLeft: false,
          autoVerticalMargin: true,
        }));
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
          registerNodeSelector(container, () => line.value);
        }
        container.addEventListener('click', () => {
          if (extras.lateInteractivityEnabledChecker?.() ?? true) {
            displayAttributeModal(env.getGlobalModalEnv(), null, createMutableLocator(line.value));
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
            locator: line.value,
            locatorRoot: outerContainer,
            expansionArea,
            path: bodyPath,
            isFresh: !existingExpansionArea,
          });

        } else {
          target.appendChild(container);
        }
        break;
      }

      default: {
        console.warn('Unknown body line type', line);
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
