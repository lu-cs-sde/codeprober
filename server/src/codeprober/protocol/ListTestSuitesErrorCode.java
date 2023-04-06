package codeprober.protocol;

import java.util.Locale;

public enum ListTestSuitesErrorCode {

	NO_TEST_DIR_SET,

	ERROR_WHEN_LISTING_TEST_DIR,
	;

	public static ListTestSuitesErrorCode parseFromJson(String string) {
		try {
			return ListTestSuitesErrorCode.valueOf(string.toUpperCase(Locale.ENGLISH));
		} catch (IllegalArgumentException e) {
			System.out.println("Invalid ListTestSuitesErrorCode value '" + string + "'");
			e.printStackTrace();
			return null;
		}
	}
}
