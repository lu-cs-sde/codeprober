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

    private static Integer changeBufferTime;

    public static int getChangeBufferTime() {
      if (changeBufferTime == null) {
        final String debounceSetting = System.getenv("CHANGE_BUFFER_TIME");
        if (debounceSetting == null) {
          if (shouldApplyCompatHacks()) {
            System.out.println("Asking client to buffer changes for 1/2 second since we are running in codespaces");
            System.out.println("Set CHANGE_BUFFER_TIME to 0 to avoid this");
            changeBufferTime = 500;
          } else {
            // Default, no need to print anything
            changeBufferTime = 0;
          }
          return changeBufferTime;
        }
        int debounceNumber;
        try {
          debounceNumber = Integer.parseInt(debounceSetting);
        } catch (NumberFormatException e) {
          System.err.println("Invalid CHANGE_BUFFER_TIME value");
          e.printStackTrace();
          changeBufferTime = 0;
          return 0;
        }

        changeBufferTime = debounceNumber;
        System.out.println("Asking client to buffer changes for " + changeBufferTime +"ms");
      }
      return changeBufferTime;
    }
}
