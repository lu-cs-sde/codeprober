import ModalEnv from '../../model/ModalEnv';
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
      case 'stream-arg': {
        const v = line.value;
        return v.length - v.trimStart().length;
      }
    }
  }
  return Number.MAX_SAFE_INTEGER;
}

type LineDecorator = (line: number) => 'default' | 'error' | 'unmatched';

interface ExtraEncodingArgs {
  decorator?: LineDecorator;
  lateInteractivityEnabledChecker?: () => boolean;
}

const encodeRpcBodyLines = (env: ModalEnv, body: RpcBodyLine[], extras: ExtraEncodingArgs = {}): HTMLElement => {
  let needCapturedStreamArgExplanation = false;

  const streamArgPrefix = Math.min(...body.map(getCommonStreamArgWhitespacePrefix));
  const encodeLine = (target: HTMLElement, line: RpcBodyLine, respectIndent = false) => {
    if (typeof line === 'string') {
      const trimmed = line.trimStart();
      if (trimmed.length !== line.length) {
        target.appendChild(document.createTextNode(' '.repeat(line.length - trimmed.length)));
      }
      if (line.trim()) {
        target.appendChild(document.createTextNode(line.trim()));
      }
      target.appendChild(document.createElement('br'));
    } else if (Array.isArray(line)) {
      if (!respectIndent) {
        // First level indent, 'inline' it
        line.forEach(sub => encodeLine(target, sub, true));
      } else {
        // >=2 level indent, respect it
        const deeper = document.createElement('pre');
        // deeper.style.borderLeft = '1px solid #88888877';
        deeper.style.marginLeft = '1rem';
        deeper.style.marginTop = '0.125rem';
        line.forEach(sub => encodeLine(deeper, sub, true));
        target.appendChild(deeper);
      }
    } else {
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
        case 'stream-arg': {
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
          const span: Span = {
            lineStart: (start >>> 12), colStart: (start & 0xFFF),
            lineEnd: (end >>> 12), colEnd: (end & 0xFFF),
          };

          container.appendChild(createTextSpanIndicator({
            span,
            marginLeft: false,
          }));
          const typeNode = document.createElement('span');
          typeNode.classList.add('syntax-type');
          typeNode.innerText = label ?? trimTypeName(type);
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
          registerNodeSelector(container, () => line.value);
          container.addEventListener('click', () => {
            if (extras.lateInteractivityEnabledChecker?.() ?? true) {
              displayAttributeModal(env, null, line.value);
            }
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
  }
  const pre = document.createElement(extras.decorator ? 'div' : 'pre');
  pre.style.margin = '0px';
  pre.style.padding = '0.5rem';
  pre.style.fontSize = '0.75rem';
  // pre.innerHtml = lines.slice(outputStart + 1).join('\n').trim();
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

        switch (extras.decorator(lineIdx)) {
          case 'error': {
            holder.classList.add('test-diff-error');
            break;
          }
          case 'unmatched': {
            holder.classList.add('test-diff-unmatched');
            break;
          }
          default: break;
        }
        encodeLine(holder, line);
        pre.appendChild(holder);

      } else {

        encodeLine(pre, line);
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


const mapRpcBodyLineDepthFirst = (line: RpcBodyLine, mapper: (line: RpcBodyLine) => RpcBodyLine): RpcBodyLine => {
  if (typeof line === 'string') {
    return mapper(line);
  }
  if (Array.isArray(line)) {
    return mapper(line.map(sub => mapRpcBodyLineDepthFirst(sub, mapper)));
  }
  return mapper(line);
}

export { mapRpcBodyLineDepthFirst };
export default encodeRpcBodyLines;
