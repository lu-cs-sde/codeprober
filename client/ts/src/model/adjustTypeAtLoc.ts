

const adjustTypeAtLoc = (adjuster: LocationAdjuster, tal: TypeAtLoc) => {
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
      // console.log('Ignoring adjustmpent from', span, 'to', {┬álineStart: ls, colStart: cs, lineEnd: le, colEnd: ce }, 'because it looks very improbable');
      // return;
    }
  }
  tal.start = (ls << 12) + Math.max(0, cs);
  tal.end = (le << 12) + ce;
}

export default adjustTypeAtLoc;

