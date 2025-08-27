import { lastKnownMousePos } from '../ui/create/attachDragToX';
import ModalEnv from './ModalEnv';

interface SpanFlasher {
  quickFlash: (spans: Span[]) => void;
  flash: (spans: Span[], removeHighlightsOnMove?: boolean) => void;
  clear: () => void;
}
const createSpanFlasher = (env: ModalEnv): SpanFlasher => {
  let activeFlashCleanup = () => { };
  let activeFlashSpan: Span[] | null = null;
  const activeSpanReferenceHoverPos: typeof lastKnownMousePos = { ...lastKnownMousePos };
  const flash: SpanFlasher['flash'] = (spans, removeHighlightsOnMove) => {
    // Remove invalid spans
    spans = spans.filter(x => x.lineStart && x.lineEnd);
    const act = activeFlashSpan;
    if (act && JSON.stringify(act) === JSON.stringify(spans) /* bit hacky comparison, but it works */) {
      // Already flashing this, update the hover pos reference and exit
      Object.assign(activeSpanReferenceHoverPos, lastKnownMousePos);
      console.log('already flashing this')
      return;
    }
    activeFlashSpan = spans;
    const flashes: { id: string, sticky: StickyHighlight }[] = spans.map(span => ({
      id: `flash-${Math.floor(Number.MAX_SAFE_INTEGER * Math.random())}`,
      sticky: { span, classNames: [span.lineStart === span.lineEnd ? 'elp-flash' : 'elp-flash-multiline'] }
    }));
    flashes.forEach(flash => env.setStickyHighlight(flash.id, flash.sticky));
    let numCleanups = 0;
    let mouseMoveListener: null | ((e: MouseEvent) => void) = null;
    const cleanup = () => {
      ++numCleanups;
      flashes.forEach(flash => env.clearStickyHighlight(flash.id));
      activeFlashSpan = null;
      if (mouseMoveListener) {
        window.removeEventListener('mousemove', mouseMoveListener);
      }
    };
    Object.assign(activeSpanReferenceHoverPos, lastKnownMousePos);
    if (removeHighlightsOnMove) {
      mouseMoveListener = (e: MouseEvent) => {
        const dx = e.x - activeSpanReferenceHoverPos.x;
        const dy = e.y - activeSpanReferenceHoverPos.y;
        if (Math.hypot(dx, dy) > 24 /* arbitrary distance */) {
          cleanup();
        }
      };
      window.addEventListener('mousemove', mouseMoveListener)
    }
    activeFlashCleanup();
    activeFlashCleanup = cleanup;
  }

  const quickFlash: SpanFlasher['quickFlash'] = (spans) => {
    flash(spans);
    const cleaner = activeFlashCleanup;
    setTimeout(() => {
      if (activeFlashCleanup === cleaner) {
        activeFlashCleanup();
      } {
        // Else, we have been replaced by another highlight
      }
    }, 500);
  };
  const clear: SpanFlasher['clear'] = () => {
    activeFlashCleanup();
  }
  return { flash, clear, quickFlash };

}

export { createSpanFlasher }
export default SpanFlasher;
