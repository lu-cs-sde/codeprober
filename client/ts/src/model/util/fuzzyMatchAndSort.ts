interface Match {
  start: number;
  gaps: number;
  isStartOfWord: boolean;
}

const matchScore = (text: string, chars: string[]) => {
  const t = text.toLowerCase();
  let best: null | Match = null;

  // Try every position where the first filter char occurs
  for (let start = t.indexOf(chars[0]); start !== -1; start = t.indexOf(chars[0], start + 1)) {
    let pos = start;
    for (let i = 1; i < chars.length && pos !== -1; i++) {
      pos = t.indexOf(chars[i], pos + 1);
    }
    if (pos === -1) {
      // if a greedy match fails here, later starts fail too
      break;
    }
    const isStartOfWord = start === 0 || !t.charAt(start - 1).trim();
    const gaps = (pos - start + 1) - chars.length;
    if (!best
        || gaps < best.gaps
        || (gaps === best.gaps && (
          !best.isStartOfWord && isStartOfWord
        ))) best = { gaps, start, isStartOfWord };
    if (gaps === 0) {
      // Can't do better than a contiguous match
      break;
    }
  }
  return best; // null = no match
}


const fuzzyMatchAndSort = <T extends { name: string }>(items: T[], filter: string): { item: T, matched: boolean }[] => {
  const chars = [...filter].map(c => c.trim().toLowerCase()).filter(Boolean);
  if (!chars.length) {
    // Everything matches an empty filter
    return items.map(item => ({ item, matched: true }));
  }
  return items
    .map(item => ({ item, score: matchScore(item.name, chars) }))
    .sort((a, b) => {
      if (!a.score || !b.score) {
        return (a.score ? 0 : 1) - (b.score ? 0 : 1);
      }
      // (Ab)using '||' and 0:s falsyness to create a precedence list of sorting criteria.
      return a.score.gaps - b.score.gaps // Fewest gaps
          || (+b.score.isStartOfWord) - (+a.score.isStartOfWord)  // Match began at the start of a word
          || a.score.start - b.score.start  // Matching earlier in the string
          || a.item.name.length - b.item.name.length // Matching on shorter words
          || 0; // Retain original index
    })
    .map(x => ({ item: x.item, matched: !!x.score }));
}

export default fuzzyMatchAndSort;
