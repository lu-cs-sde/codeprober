import { Property, PropertyArg } from '../../protocol';

const formatAttrType = (orig: PropertyArg) => {
  switch (orig.type) {
    case 'nodeLocator': return  orig.value.type;
    case 'integer': return 'int';
    // case ''
    // case 'java.lang.String': return 'String';
    default: return orig.type;
  }
};

const formatAttr = (attr: Property) => `${attr.name.startsWith('l:') ? attr.name.slice(2) : attr.name}${(
  attr.args
    ? `(${attr.args.map(a => formatAttrType(a)).join(', ')})`
    : ''
)}`;

const formatAttrArgList = (target: HTMLElement, attr: Property) => {
  attr.args?.forEach((arg, argIdx) => {
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
        } else {
          node.innerText = arg.value.value?.result?.type ?? arg.value.type; // .split('.')[0];
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
}

export { formatAttrType, formatAttrArgList };
export default formatAttr;
