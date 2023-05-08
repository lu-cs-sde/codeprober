
let lastKnownMousePos = { x: 0, y: 0 };
window.onmousemove = (e) => {
  lastKnownMousePos.x = e.x;
  lastKnownMousePos.y = e.y;
};
let currentMouseDownTracker: { down: boolean } | null = null;
window.onmouseup = () => {
  if (currentMouseDownTracker) {
    currentMouseDownTracker.down = false;
    currentMouseDownTracker = null;
  }
}
const setCurrentMouseDown = (newTracker: { down: boolean }) => {
  if (currentMouseDownTracker) {
    currentMouseDownTracker.down = false;
  }
  currentMouseDownTracker = newTracker;
}

const modalZIndexGenerator = (() => {
  let counter = 1;
  return () => counter++;
})();
const attachDragToX = (
  element: HTMLElement,
  onBegin: (e: MouseEvent) => void,
  onUpdate: (deltaX: number, deltaY: number) => void,
  onFinishedMove?: () => void
) => {

  const refreshPos = (newX?: number, newY?: number) => onUpdate(newX ?? 0, newY ?? 0);

  let mouse = { down: false, x: 0, y: 0 };
  element.onmousedown = (e) => {
    setCurrentMouseDown(mouse);
    // e.preventDefault();
    e.stopPropagation();
    e.stopImmediatePropagation();
    e.cancelBubble = true;
    mouse.down = true;
    element.style.zIndex = `${modalZIndexGenerator()}`;
    mouse.x = e.pageX;
    mouse.y = e.pageY;
    onBegin(e);
  };
  const onMouseMove = (e: MouseEvent) => {
    if (mouse.down) {
      let dx = e.pageX - mouse.x;
      let dy = e.pageY - mouse.y;
      refreshPos(dx, dy);
      // mouse.x = e.x;
      // mouse.y = e.y;
    }
  };
  document.addEventListener('mousemove', onMouseMove);
  element.onmouseup = (e) => {
    mouse.down = false;
    onFinishedMove?.();
  }
  // refreshPos();
  return {
    cleanup: () => {
      document.removeEventListener('mousemove', onMouseMove);
    },
    hasMouseDown: () => mouse.down,
  };
};

export { modalZIndexGenerator, lastKnownMousePos };
export default attachDragToX;
