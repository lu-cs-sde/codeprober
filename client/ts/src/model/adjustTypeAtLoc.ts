import { TALStep } from '../protocol';
import startEndToSpan from '../ui/startEndToSpan';

const adjustTypeAtLoc = (adjuster: LocationAdjuster, tal: TALStep) => {
  if (tal.external) {
    // Refuse to adjust things in external files.
    // CodeProber is only expected to get change events for our own "internal" file.
    return;
  }
  const span = startEndToSpan(tal.start, tal.end);

  let [ls, cs] = adjuster(span.lineStart, span.colStart);
  let [le, ce] = adjuster(span.lineEnd, span.colEnd);
  if (ls == le && cs == ce) {
    if (span.lineStart === span.lineEnd && span.colStart === span.colEnd) {
      // Accept it, despite it being strange
    } else {
      // Instead of accepting change to zero-width span, take same line/col diff as before
      le = ls + (span.lineEnd - span.lineStart);
      ce = cs + (span.colEnd - span.colStart);
    }
  }
  tal.start = (ls << 12) + Math.max(0, cs);
  tal.end = (le << 12) + ce;
}

export default adjustTypeAtLoc;

