package pasta.util;

import java.security.Permission;

class SystemExitControl {

  public static class ExitTrappedException extends SecurityException {
  }

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

  public static void enableSystemExit() {
    System.setSecurityManager(null);
  }
}
