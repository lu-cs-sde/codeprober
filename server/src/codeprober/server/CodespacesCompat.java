package codeprober.server;

public class CodespacesCompat {

    private static Boolean shouldCompat;

    public static boolean shouldApplyCompatHacks() {
        if (shouldCompat == null) {
            if ("true".equals(System.getenv("CODESPACES"))) {
                if ("false".equals(System.getenv("CODESPACES_COMPATIBILITY_HACK"))) {
                    System.out.println("We seem to be running in Github Codespaces, but compatibility hacks are disabled, so we'll act as normal.");
                    shouldCompat = false;
                } else {
                    System.out.println("We seem to be running in Github Codespaces, enabling compatibility hacks.");
                    System.out.println("Set 'CODESPACES_COMPATIBILITY_HACK=false' to disable this");
                    shouldCompat = true;
                }
			} else {
                shouldCompat = false;
            }
        }
        return shouldCompat;
    }
}