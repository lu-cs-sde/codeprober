
interface KeyboardListNavigationControllerArgs {
  focusParent: () => void;
  listItems: HTMLElement[];
}
interface KeyboardListNavigationController {
  register: (row: HTMLElement, onEnter: () => void) => void;
  focusFirst: (e: KeyboardEvent) => void;
}

const createKeyboardListNavigationController = (args: KeyboardListNavigationControllerArgs): KeyboardListNavigationController => {
  return {
    register: (row, onEnter) => {
      const ourNodeIndex = args.listItems.indexOf(row);
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
    },
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
