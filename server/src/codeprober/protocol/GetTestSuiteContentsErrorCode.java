package codeprober.protocol;

import java.util.Locale;

public enum GetTestSuiteContentsErrorCode {

	NO_TEST_DIR_SET,

	NO_SUCH_TEST_SUITE,

	ERROR_WHEN_READING_FILE,
	;

	public static GetTestSuiteContentsErrorCode parseFromJson(String string) {
		try {
			return GetTestSuiteContentsErrorCode.valueOf(string.toUpperCase(Locale.ENGLISH));
		} catch (IllegalArgumentException e) {
			System.out.println("Invalid GetTestSuiteContentsErrorCode value '" + string + "'");
			e.printStackTrace();
			return null;
		}
	}
}
