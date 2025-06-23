import ModalEnv from '../model/ModalEnv';
import UpdatableNodeLocator from '../model/UpdatableNodeLocator';
import { NestedWindows } from '../model/WindowState';
import { Property } from '../protocol';
import settings from '../settings';
import { StickyHighlightController } from './create/createStickyHighlightController';
import createTextSpanIndicator from './create/createTextSpanIndicator';
import { installLazyHoverDialog } from './create/installLazyHoverDialog';
import registerNodeSelector from './create/registerNodeSelector';
import displayArgModal from './popup/displayArgModal';
import displayAttributeModal from './popup/displayAttributeModal';
import { prettyPrintProbePropertyName, searchProbePropertyName } from './popup/displayProbeModal';
import formatAttr, { formatAttrArgList, formatAttrBaseName } from './popup/formatAttr';
import startEndToSpan from './startEndToSpan';
import trimTypeName from './trimTypeName';

const renderProbeModalTitleLeft = (
  env: ModalEnv | null,
  container: HTMLElement,
  close: (() => void) | null,
  getWindowPos: () => ModalPosition,
  stickyController: StickyHighlightController | null,
  locator: UpdatableNodeLocator,
  attr: Property,
  nested: NestedWindows,
  typeRenderingStyle: 'default' | 'minimal-nested',
) => {
  if (typeRenderingStyle !== 'minimal-nested') {
    const headType = document.createElement('span');
    headType.classList.add('syntax-type');
    headType.innerText = `${locator.get().result.label ?? trimTypeName(locator.get().result.type)}`;
    container.appendChild(headType);
  }

  const headAttr = document.createElement('span');
  headAttr.classList.add('syntax-attr');
  let maybeInstallUnshorteningPopup = false;
  if (attr.name === searchProbePropertyName) {
    const propName = attr.args?.[0]?.value ?? '';
    headAttr.innerText = `.*.${propName}`;
  } else if (attr.name === prettyPrintProbePropertyName) {
    headAttr.innerText = `->PrettyPrint`;
  } else if (!attr.args?.length) {
    headAttr.innerText = `.${formatAttr(attr)}`;
    maybeInstallUnshorteningPopup = true;
  } else {
    headAttr.appendChild(document.createTextNode(`.${attr.name}(`));
    formatAttrArgList(headAttr, attr);
    headAttr.appendChild(document.createTextNode(`)`));
    maybeInstallUnshorteningPopup = true;
  }
  if (maybeInstallUnshorteningPopup && settings.shouldAutoShortenPropertyNames()) {
    const short = formatAttrBaseName(attr.name, true);
    const long = formatAttrBaseName(attr.name, false);
    if (short != long) {
      installLazyHoverDialog({
        elem: headAttr,
        init: (dialog) => {
          dialog.classList.add('modalWindowColoring');
          dialog.style.padding = '0.25rem';
          dialog.innerText = long;
        },
      });
    }
  }

  if (env) {
    headAttr.onmousedown = (e) => { e.stopPropagation(); }
    headAttr.classList.add('clickHighlightOnHover');
    headAttr.onclick = (e) => {
      let initialFilter = '';
      if (attr.name == searchProbePropertyName) {
        const propName = attr.args?.[0]?.value ?? '';
        if (propName) {
          console.log('initialFilter:', propName)
          initialFilter = `*.${propName}`;
        }
        if ((attr.args?.length ?? 0) >= 2) {
          initialFilter = `${initialFilter}?${attr.args?.[1]?.value}`;
        }
      }
      if (env.duplicateOnAttr() != e.shiftKey) {
        displayAttributeModal(env, null, locator.isMutable() ? locator.createMutableClone() : locator, { initialFilter });
      } else {
        close?.();
        displayAttributeModal(env, getWindowPos(), locator, { initialFilter });
      }
      e.stopPropagation();
    };
  }

  container.appendChild(headAttr);

  if (attr.name === searchProbePropertyName && (attr.args?.length ?? 0) >= 2) {
    const pred = document.createElement('span');
    pred.classList.add('syntax-int');
    pred.innerText = `?${attr.args?.[1]?.value}`;
    container.appendChild(pred);
  }

  if (attr.args?.length && env && attr.name !== searchProbePropertyName) {
    const editButton = document.createElement('img');
    editButton.src = 'icons/edit_white_24dp.svg';
    editButton.classList.add('modalEditButton');
    editButton.classList.add('clickHighlightOnHover');
    editButton.onmousedown = (e) => { e.stopPropagation(); }
    editButton.onclick = () => {
      close?.();
      displayArgModal(env, getWindowPos(), locator, attr, nested);
    };

    container.appendChild(editButton);
  }

  if (env) {
    const spanIndicator = createTextSpanIndicator({
      span: startEndToSpan(locator.get().result.start, locator.get().result.end),
      marginLeft: true,
      onHover: on => env.updateSpanHighlight(on ? startEndToSpan(locator.get().result.start, locator.get().result.end) : null),
      onClick: stickyController?.onClick ?? undefined,
      external: locator.get().result.external,
      styleOverride: typeRenderingStyle === 'minimal-nested' ? 'lines-compact' : undefined,
    });
    stickyController?.configure(spanIndicator, locator);
    registerNodeSelector(spanIndicator, () => locator.get());
    container.appendChild(spanIndicator);
  }
}

export default renderProbeModalTitleLeft;
