
const registerOnHover = (element: HTMLElement, onHover: (isHovering: boolean) => void) => {
  element.onmouseenter = () => onHover(true);
  element.onmouseleave = () => onHover(false);
};

export default registerOnHover;
