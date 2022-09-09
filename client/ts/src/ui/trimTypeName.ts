

const trimTypeName = (typeName: string) => {
  const lastDot = typeName.lastIndexOf(".");
  return lastDot === -1 ? typeName : typeName.slice(lastDot + 1);
}

export default trimTypeName;
