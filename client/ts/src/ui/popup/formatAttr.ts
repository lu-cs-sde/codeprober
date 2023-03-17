
const formatAttrType = (orig: AstAttrArg['type']) => {
  switch (orig) {
    case 'java.lang.String': return 'String';
    default: return orig;
  }
};

const formatAttr = (attr: AstAttr) => `${attr.name.startsWith('l:') ? attr.name.slice(2) : attr.name}${(
  attr.args
    ? `(${attr.args.map(a => formatAttrType(a.type)).join(', ')})`
    : ''
)}`;

const formatAttrArgList = (target: HTMLElement, attr: AstAttrWithValue) => {
  attr.args?.forEach((arg, argIdx) => {
    if (argIdx > 0) {
      target.appendChild(document.createTextNode(`,`));
    }
    switch (arg.type) {
      case 'java.lang.String': {
        const node = document.createElement('span');
        node.classList.add('syntax-string');
        node.innerText = `"${arg.value}"`;
        target.appendChild(node);
        break;
      }
      case 'int': {
        const node = document.createElement('span');
        node.classList.add('syntax-int');
        node.innerText = `${arg.value}`;
        target.appendChild(node);
        break;
      }
      case 'boolean': {
        const node = document.createElement('span');
        node.classList.add('syntax-modifier');
        node.innerText = `${arg.value}`;
        target.appendChild(node);
        break;
      }
      default: {
        switch (arg.detail) {
          case 'AST_NODE': {
            const node = document.createElement('span');
            node.classList.add('syntax-type');
            if (!arg.value || (typeof arg.value !== 'object')) {
              // Probably null
              node.innerText = `${arg.value}`;
            } else {
              node.innerText = arg.value.result.type;
            }
            target.appendChild(node);
            break;
          }
          case 'OUTPUTSTREAM': {
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
        break;
      }
    }
  });
}

export { formatAttrType, formatAttrArgList };
export default formatAttr;
