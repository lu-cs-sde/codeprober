package codeprober.protocol;

import java.util.Locale;

public enum PutTestSuiteContentsErrorCode {

	NO_TEST_DIR_SET,

	ERROR_WHEN_WRITING_FILE,
	;

	public static PutTestSuiteContentsErrorCode parseFromJson(String string) {
		try {
			return PutTestSuiteContentsErrorCode.valueOf(string.toUpperCase(Locale.ENGLISH));
		} catch (IllegalArgumentException e) {
			System.out.println("Invalid PutTestSuiteContentsErrorCode value '" + string + "'");
			e.printStackTrace();
			return null;
		}
	}
}
