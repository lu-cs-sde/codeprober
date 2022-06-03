package pasta.util;

import java.security.Permission;

class SystemExitControl {

  @SuppressWarnings("serial")
public static class ExitTrappedException extends SecurityException {
  }

  @SuppressWarnings("removal")
  public static void disableSystemExit() {
    final SecurityManager securityManager = new SecurityManager() {
      @Override public void checkPermission(Permission permission) {
        if (permission.getName().contains("exitVM")) {
          throw new ExitTrappedException();
        }
      }
    };
    System.setSecurityManager(securityManager);
  }

  @SuppressWarnings("removal")
  public static void enableSystemExit() {
    System.setSecurityManager(null);
  }
}
