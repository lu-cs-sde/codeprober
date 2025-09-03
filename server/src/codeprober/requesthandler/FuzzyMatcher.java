package codeprober.requesthandler;

public class FuzzyMatcher {

	public static class ScoredMatch {
		public final String path;
		public final int score;

		ScoredMatch(String path, int score) {
			this.path = path;
			this.score = score;
		}
	}

	/**
	 * Score how well a query matches the given target. The scoring heuristics
	 * assumes that the target is a file path, and gives bonuses for example for
	 * matching after a delimiter. For example, for the target "ab/cd", a query of
	 * "a" or "c" has a higher score than "b" or "d".
	 * <p>
	 * The comparison is case-insensitive according to the default locale on the
	 * machine.
	 *
	 * @param query  the query that should be fuzzy matched against the target.
	 * @param target a target string, assumed to be formatted as a file path (but
	 *               doesn't have to be).
	 * @return the score, or <code>null</code> if the entire query couldn't be
	 *         matched.
	 */
	public static Integer score(String query, String target) {
		String queryLower = query.toLowerCase();
		String targetLower = target.toLowerCase();

		int score = 0;
		int queryIdx = 0;
		int targetIdx = 0;
		int consecutiveBonus = 0;

		while (queryIdx < query.length() && targetIdx < target.length()) {
			if (queryLower.charAt(queryIdx) == targetLower.charAt(targetIdx)) {
				// Base score
				score += 10;

				// Bonus for match at boundary (after / or . or other separators)
				if (targetIdx == 0 || isBoundary(target.charAt(targetIdx - 1))) {
					score += 15;
				}

				// Bonus for consecutive matches
				score += consecutiveBonus * 5;
				consecutiveBonus += 1;

				queryIdx++;
			} else {
				consecutiveBonus = 0;
			}
			targetIdx++;
		}

		// Return null if not all chars matched
		if (queryIdx != query.length()) {
			return null;
		}

		// Penalty for long targets (prefer shorter matches)
		score -= target.length();

		return score;
	}

	private static boolean isBoundary(char c) {
		switch (c) {
		case '/':
		case '\\':
		case '-':
		case '_':
		case '.':
		case ' ':
			return true;
		}
		return false;
	}
}
