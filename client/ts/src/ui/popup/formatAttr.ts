import { Property, PropertyArg } from '../../protocol';
import settings from '../../settings';

const formatAttrType = (orig: PropertyArg) => {
  switch (orig.type) {
    case 'nodeLocator': return  orig.value.type;
    case 'integer': return 'int';
    case 'any': return 'any';
    default: return orig.type;
  }
};

const formatAttrBaseName = (name: string, allowShortening = true) => {
  let prefix = name.startsWith('l:') ? name.slice(2) : name;
  if (allowShortening && settings.shouldAutoShortenPropertyNames()) {
    const reg = /^.*?([\w\d\$_]+)$/;
    const match = prefix.match(reg);
    if (match) {
      prefix = match[1];
    }
  }
  return prefix;
};
const formatAttrArgStr = (args: PropertyArg[] | undefined) => {
  return args?.length
    ? `(${args.map(a => formatAttrType(a)).join(', ')})`
    : ''
    ;
}

const formatAttr = (attr: Property) => {
  const prefix = formatAttrBaseName(attr.name);
  const suffix = formatAttrArgStr(attr.args);
  return `${prefix}${suffix}`;
}

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
      case 'any': {
        const node = document.createElement('span');
        node.classList.add('syntax-string');
        node.innerText = `"${arg.value?.value ?? '<any>'}"`;
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

export { formatAttrBaseName, formatAttrType, formatAttrArgList, formatAttrArgStr };
export default formatAttr;
