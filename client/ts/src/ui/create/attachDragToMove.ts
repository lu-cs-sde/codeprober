
import attachDragToX, { lastKnownMousePos } from "./attachDragToX"

const attachDragToMove = (element: HTMLElement, initialPos?: ModalPosition | null, onFinishedMove?: () => void) => {
  const elemPos = { x: initialPos?.x ?? lastKnownMousePos.x, y: initialPos?.y ?? lastKnownMousePos.y };
  const startPos = {...elemPos};
  const onBegin = () => {
    startPos.x = elemPos.x;
    startPos.y = elemPos.y;
  };
  const onUpdate = (dx: number, dy: number) => {
    let newX = startPos.x + dx;
    let newY = startPos.y + dy;
    if (newY < 0) {
      newY = 0;
    } else {
      newY = Math.min(document.body.clientHeight - element.clientHeight, newY);
    }
    if (newX < 0) {
      newX = 0;
    } else {
      newX = Math.min(document.body.clientWidth - element.clientWidth, newX);
    }
    element.style.top = `${newY}px`;
    element.style.left = `${newX}px`;
    elemPos.x = newX;
    elemPos.y = newY;
  };
  onUpdate(0, 0);
  const { cleanup, hasMouseDown } = attachDragToX(element,
    onBegin,
    onUpdate,
    onFinishedMove
  );
  return {
    cleanup,
    getPos: () => elemPos,
    bumpIntoScreen: () => {
      if (!hasMouseDown()) {
        onBegin();
        onUpdate(0, 0);
      }
    },
  }
}

export default attachDragToMove;
