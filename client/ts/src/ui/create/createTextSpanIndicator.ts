import registerOnHover from "./registerOnHover";

interface TextSpanIndicatorArgs {
  span: Span;
  marginLeft?: boolean;
  onHover?: (isHovered: boolean) => void;
}
const createTextSpanIndicator = (args: TextSpanIndicatorArgs) => {
  const { span, marginLeft, onHover } = args;
  const indicator = document.createElement('span');
  indicator.style.fontSize = '0.75rem';
  indicator.style.color = 'gray';
  if (marginLeft) {
    indicator.style.marginLeft = '0.25rem';
  }
  indicator.style.marginRight = '0.25rem';
  indicator.innerText = `[${span.lineStart}:${span.colStart}→${span.lineEnd}:${span.colEnd}]${span.lineStart === 0 && span.colStart === 0 && span.lineEnd === 0 && span.colEnd === 0 ? '⚠️' : ''}`;
  if (onHover) {
    indicator.classList.add('highlightOnHover');
    registerOnHover(indicator, onHover);
  }
  return indicator;
}

export default createTextSpanIndicator;
