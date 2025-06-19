
interface HoverDialogArgs {
  elem: HTMLElement;
  init: (container: HTMLDivElement) => void;
}
const installLazyHoverDialog = (args: HoverDialogArgs) => {
  args.elem.classList.add('lazy-hover-diag-trigger');
  const mouseoverListener = (e: MouseEvent) => {
      const node = document.createElement('div');
      node.classList.add('lazy-hover-diag-root')

      const contents = document.createElement('div');
      args.init(contents);
      node.appendChild(contents);
      if (args.elem.nextSibling) {
          args.elem.parentElement?.insertBefore(node, args.elem.nextSibling);
      } else {
          args.elem.parentElement?.appendChild(node);
      }
      args.elem.removeEventListener('mouseover', mouseoverListener);
  };
  args.elem.addEventListener('mouseover', mouseoverListener)
}

export { installLazyHoverDialog };
