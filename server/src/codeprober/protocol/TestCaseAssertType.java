package codeprober.protocol;

public enum TestCaseAssertType {

	/**
	 * Output should be exactly identical
	 */
	IDENTITY,

	/**
	 * Output should have same content, but top-level lines may be out of order
	 */
	SET,

	/**
	 * Evaluation should not throw a RuntimeException.
	 */
	SMOKE,

	;

	public static TestCaseAssertType parseFromJson(String val) {
		try {
			return TestCaseAssertType.valueOf(val);
		} catch (IllegalArgumentException e) {
			System.out.println("Got invalid " + TestCaseAssertType.class.getSimpleName() + " value: " + val);
			return IDENTITY;
		}

	}
}
