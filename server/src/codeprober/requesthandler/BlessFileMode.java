package codeprober.requesthandler;

public enum BlessFileMode {
	DRY_RUN, UPDATE_IN_PLACE, ECHO_RESULT;

	public static BlessFileMode parseFromJson(String str) {
		try {
			return BlessFileMode.valueOf(str);
		} catch (IllegalArgumentException e) {
			System.out.println("Got invalid BlessFileMode argument: " + str);
			return DRY_RUN;
		}
	}
}
