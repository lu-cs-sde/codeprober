package codeprober.requesthandler;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TestFuzzyMatcher {

	private int score(String query, String target) {
		Integer actual = FuzzyMatcher.score(query, target);
		assertNotNull(actual);
		return actual;
	}

	@Test
	public void testExactMatch() {
		score("foo", "foo");
	}

	@Test
	public void testMismatch() {
		assertNull(FuzzyMatcher.score("foo", "bar"));
	}

	@Test
	public void testInexactMatchScore() {
		int exactScore = score("foo", "foo");
		int inexactScore = score("foo", "fo/oo/bar");

		assertTrue(exactScore > inexactScore);
	}

	@Test
	public void testBoundaryBonus() {
		// Match at start gets boundary bonus
		int startScore = score("f", "foo");

		// Match in middle (no boundary) should have lower score
		int middleScore = score("o", "foo");

		// Match after separator gets boundary bonus
		// All boundary matches should score higher than middle match
		assertTrue(startScore > middleScore);
		assertTrue(score("b", "foo/bar") > middleScore);
		assertTrue(score("j", "foo.java") > middleScore);
		assertTrue(score("t", "foo_test") > middleScore);
		assertTrue(score("v", "foo-version") > middleScore);
		assertTrue(score("b", "foo\\bar") > middleScore);
		assertTrue(score("w", "foo world") > middleScore);
	}

	@Test
	public void testConsecutiveBonus() {
		// Consecutive matches should score higher than scattered matches (without
		// boundary interference)
		int consecutive = score("abc", "abcdef");
		int scattered = score("abc", "axbxcx");

		assertTrue(consecutive > scattered);

		// Longer consecutive sequences should score higher than shorter ones
		int longConsecutive = score("test", "testfile");
		int shortConsecutive = score("te", "testfile");

		assertTrue(longConsecutive > shortConsecutive);
	}

	@Test
	public void testLengthPenalty() {
		// Shorter targets should score higher than longer ones for same match
		int shortTarget = score("foo", "foo");
		int longTarget = score("foo", "foobar");

		assertTrue(shortTarget > longTarget);

		// Even longer target should score even lower
		int veryLongTarget = score("foo", "foo/very/long/path");
		assertTrue(longTarget > veryLongTarget);
	}

	@Test
	public void testPartialMatches() {
		// Should return null if not all query characters are found
		assertNull(FuzzyMatcher.score("xyz", "foo"));
		assertNull(FuzzyMatcher.score("foobar", "foo"));

		// Should work if all characters are present but scattered
		score("foo", "f/o/o");
		score("java", "MyJavaFile.java");
	}

	@Test
	public void testCaseInsensitive() {
		int lowerCase = score("foo", "foo");
		int upperCase = score("FOO", "foo");
		int mixedCase = score("FoO", "foo");
		int targetMixed = score("foo", "FoO");

		// All should have same score since matching is case-insensitive
		assertTrue(lowerCase == upperCase);
		assertTrue(lowerCase == mixedCase);
		assertTrue(lowerCase == targetMixed);
	}

	@Test
	public void testComplexScoring() {
		// Test combining multiple heuristics - boundary bonus should be significant
		int boundaryScore = score("Test", "TestFile.java");

		// Should score higher than a match without boundary bonus
		int noBoundaryScore = score("est", "TestFile.java");
		assertTrue(boundaryScore > noBoundaryScore);

		// Consecutive matches should score higher than non-consecutive when no
		// boundaries interfere
		int consecutive = score("abc", "abcx");
		int nonConsecutive = score("abc", "abxc");
		assertTrue(consecutive > nonConsecutive);
	}
}
