

const startEndToSpan = (start: number, end: number): Span => ({
  lineStart: (start >>> 12),
  colStart: start & 0xFFF,
  lineEnd: (end >>> 12),
  colEnd: end & 0xFFF,
})
