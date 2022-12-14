
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

export {¬†formatAttrType };
export default formatAttr;
