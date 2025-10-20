import { TALStep } from '../protocol';
import startEndToSpan from '../ui/startEndToSpan';

const adjustSpan = (adjuster: LocationAdjuster, span: Span): { start: number, end: number } => {
  let [ls, cs] = adjuster(span.lineStart, span.colStart);
  let [le, ce] = adjuster(span.lineEnd, span.colEnd);
  if (ls == le && cs == ce) {
    if (span.lineStart === span.lineEnd && span.colStart === span.colEnd) {
      // Accept it, despite it being strange
    } else {
      // One of the sides of the span moved into the other side
      // Or, possibly both sides moved a bit and ended up on the same spot.
      // Instead of accepting change to zero-width span, make sure we have the same line/col diff as before
      // Whether we move left or right depends on which side moved "most"
      const startMove = Math.abs(span.lineStart - ls) + Math.abs(span.colStart - cs);
      const endMove = Math.abs(span.lineEnd - le) + Math.abs(span.colEnd - ce);
      if (endMove > startMove) {
        // console.log('inner case 1')
        // The end moved more, force move start
        ls = ls + (span.lineStart - span.lineEnd);
        cs = cs + (span.colStart - span.colEnd);
      } else {
        // console.log('inner case 2')
        // The start moved more, force move end
        le = ls + (span.lineEnd - span.lineStart);
        ce = cs + (span.colEnd - span.colStart);
      }
    }
  }
  return {
    start: (ls << 12) + Math.max(0, cs),
    end: (le << 12) + ce,
  }
}

const adjustStartEnd = (adjuster: LocationAdjuster, start: number, end: number): { start: number, end: number } => {
  return adjustSpan(adjuster, startEndToSpan(start, end));
};

const adjustTypeAtLoc = (adjuster: LocationAdjuster, tal: TALStep) => {
  if (tal.external) {
    // Refuse to adjust things in external files.
    // CodeProber is only expected to get change events for our own "internal" file.
    return;
  }
  const adj = adjustStartEnd(adjuster, tal.start, tal.end);
  tal.start = adj.start;
  tal.end = adj.end;
}

export { adjustStartEnd, adjustSpan }
export default adjustTypeAtLoc;

