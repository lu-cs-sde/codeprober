import ModalEnv from '../model/ModalEnv';
import { StickyHighlightController } from './create/createStickyHighlightController';
import createTextSpanIndicator from './create/createTextSpanIndicator';
import registerNodeSelector from './create/registerNodeSelector';
import displayArgModal from './popup/displayArgModal';
import displayAttributeModal from './popup/displayAttributeModal';
import formatAttr, { formatAttrArgList } from './popup/formatAttr';
import trimTypeName from './trimTypeName';


const renderProbeModalTitleLeft = (
  env: ModalEnv,
  container: HTMLElement,
  close: () => void,
  getWindowPos: () => ModalPosition,
  stickyController: StickyHighlightController | null,
  locator: NodeLocator,
  attr: AstAttrWithValue
) => {
  const headType = document.createElement('span');
  headType.classList.add('syntax-type');
  headType.innerText = `${locator.result.label ?? trimTypeName(locator.result.type)}`;

  const headAttr = document.createElement('span');
  headAttr.classList.add('syntax-attr');
  headAttr.classList.add('clickHighlightOnHover');
  if (!attr.args || attr.args.length === 0) {
    headAttr.innerText = `.${formatAttr(attr)}`;
  } else {
    headAttr.appendChild(document.createTextNode(`.${attr.name}(`));
    formatAttrArgList(headAttr, attr);
    headAttr.appendChild(document.createTextNode(`)`));
  }
  headAttr.onmousedown = (e) => { e.stopPropagation(); }
  headAttr.onclick = (e) => {
    if (env.duplicateOnAttr() != e.shiftKey) {
      displayAttributeModal(env, null, JSON.parse(JSON.stringify(locator)));
    } else {
      close();
      displayAttributeModal(env, getWindowPos(), locator);
    }
    e.stopPropagation();
  };

  container.appendChild(headType);
  container.appendChild(headAttr);

  if (attr.args?.length) {
    const editButton = document.createElement('img');
    editButton.src = '/icons/edit_white_24dp.svg';
    editButton.classList.add('modalEditButton');
    editButton.classList.add('clickHighlightOnHover');
    editButton.onmousedown = (e) => { e.stopPropagation(); }
    editButton.onclick = () => {
      close();
      displayArgModal(env, getWindowPos(), locator, attr);
    };

    container.appendChild(editButton);
  }

  const spanIndicator = createTextSpanIndicator({
    span: startEndToSpan(locator.result.start, locator.result.end),
    marginLeft: true,
    onHover: on => env.updateSpanHighlight(on ? startEndToSpan(locator.result.start, locator.result.end) : null),
    onClick: stickyController?.onClick ?? undefined,
    external: locator.result.external,
  });
  stickyController?.configure(spanIndicator, locator);
  registerNodeSelector(spanIndicator, () => locator);
  container.appendChild(spanIndicator);
}

export default renderProbeModalTitleLeft;
