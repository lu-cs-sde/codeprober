
interface KeyboardListNavigationControllerArgs {
  focusParent: () => void;
  listItems: HTMLElement[];
}
interface KeyboardListNavigationController {
  register: (
    row: HTMLElement,
    onEnter: () => void,
    extras?: {
      onFocus: (active: boolean) => void
    },
  ) => void;
  focusFirst: (e: KeyboardEvent) => void;
}

const createKeyboardListNavigationController = (args: KeyboardListNavigationControllerArgs): KeyboardListNavigationController => {
  const register: KeyboardListNavigationController['register'] = (row, onEnter, extras) => {
    const ourNodeIndex = args.listItems.indexOf(row);
    if (extras?.onFocus) {
      row.addEventListener('focus', () => extras?.onFocus(true));
      row.addEventListener('blur', () => extras?.onFocus(false));
    }
      if (ourNodeIndex === -1) {
        throw new Error("Row is not in array of items");
      }
      row.onkeydown = (e) => {
        if (e.key === 'Enter') {
          onEnter();
        } else if (e.key === 'ArrowDown' && ourNodeIndex !== (args.listItems.length - 1)) {
          e.preventDefault();
          args.listItems[ourNodeIndex+1]?.focus();
        } else if (e.key === 'ArrowUp') {
          e.preventDefault();
          if (ourNodeIndex > 0) {
            args.listItems[ourNodeIndex-1]?.focus();
          } else {
            args.focusParent();
          }
        }
      }
    };
  return {
    register,
    focusFirst: (e) => {
      if (args.listItems.length) {
        args.listItems[0].focus();
        e.preventDefault();
      }
    }
  }
}

export default createKeyboardListNavigationController;
export { KeyboardListNavigationController }
