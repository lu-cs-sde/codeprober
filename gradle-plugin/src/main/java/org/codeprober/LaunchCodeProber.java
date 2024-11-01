package org.codeprober;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.services.BuildService;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Input;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.configurationcache.problems.PropertyTrace.Gradle;
import org.gradle.internal.impldep.com.fasterxml.jackson.databind.ser.std.StdKeySerializers.Default;
import org.gradle.internal.impldep.org.apache.ivy.util.PropertiesFile;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class LaunchCodeProber extends JavaExec {

  @Optional @InputFile
  public File toolJar;
  public File getToolJar() { return toolJar; }
  private File getOverriddenToolJar() {
    Object val = getProject().getProperties().get("toolJar");
    if (val != null) {
      return overriddenPathToFile("" + val);
    }
    return toolJar;
  }

  @Optional @InputFile
  public File cprJar;
  public File getCprJar() { return cprJar; }
  private File getOverriddenCprJar() {
    Object val = getProject().getProperties().get("cprJar");
    if (val != null) {
      return overriddenPathToFile("" + val);
    }
    if (cprJar != null) {
      return cprJar;
    }
    // Else, need to download
    final File expectedCprLocation = new File(getProject().getProjectDir(), ".codeprober" + File.separatorChar + "codeprober.jar");
    if (expectedCprLocation.exists()) {
      // CodeProber is already downloaded, but it may be old. Check its age
      final long age = System.currentTimeMillis() - expectedCprLocation.lastModified();
      if (getOverriddenCprUpdateCheck() && age > (7 * 24 * 60 * 60 * 1000)) {
        System.out.println("Currently downloaded codeprober.jar is over a week old, checking if new version exists. Disable this by setting '-PcprUpdateCheck=false'");
        try {
          downloadCodeProber(expectedCprLocation);
        } catch (IOException e) {
          System.err.println("Error while downloading newer codeprober.jar");
          e.printStackTrace();
        }
      } else {
        System.out.println("Reusing previously downloaded " + expectedCprLocation);
      }
    } else {
      // Not downloaded yet
      try {
        downloadCodeProber(expectedCprLocation);
      } catch (IOException e) {
        System.err.println("Error while downloading codeprober.jar");
        e.printStackTrace();
      }
    }
    if (!expectedCprLocation.exists()) {
      // System.err.println();
      throw new RuntimeException("Failed downloading codeprober.jar. Try again, or manually download it and specify with the property 'cpr'. For example: `./gradlew launchCodeProber -Pcpr=~/Downloads/codeprober.jar`");
    }
    return expectedCprLocation;
  }

  // @Optional @Input
  // public List<String> sysProps;
  // public List<String> getSysProps() { return sysProps; }
  // private List<String> getOverriddenSysProps() {
  //   Object val = getProject().getProperties().get("sysProps");
  //   if (val != null) {
  //     return parseArgsList(val + "");
  //   }
  //   return sysProps;
  // }

  @Optional @Input
  public List<String> cprArgs;
  public List<String> getCprArgs() { return cprArgs; }
  private List<String> getOverriddenCprArgs() {
    Object val = getProject().getProperties().get("cprArgs");
    if (val != null) {
      return parseArgsList(val + "");
    }
    return cprArgs;
  }

  @Optional @Input
  public List<String> toolArgs;
  public List<String> getToolArgs() { return toolArgs; }
  private List<String> getOverriddenToolArgs() {
    Object val = getProject().getProperties().get("toolArgs");
    if (val != null) {
      return parseArgsList(val + "");
    }
    return toolArgs;
  }

  @Optional @Input
  public Boolean openBrowser;
  public Boolean getOpenBrowser() { return openBrowser; }
  private boolean getOverriddenOpenBrowser() {
    Object val = getProject().getProperties().get("openBrowser");
    if (val != null) {
      return Boolean.valueOf(val + "");
    }
    if (openBrowser != null) {
      return openBrowser;
    }
    return true;
  }

  @Optional @Input
  public Boolean cprUpdateCheck;
  public Boolean getCprUpdateCheck() { return cprUpdateCheck; }
  private boolean getOverriddenCprUpdateCheck() {
    Object val = getProject().getProperties().get("cprUpdateCheck");
    if (val != null) {
      return Boolean.valueOf(val + "");
    }
    if (cprUpdateCheck != null) {
      return cprUpdateCheck;
    }
    return true;
  }

  @Optional @Input
  public Integer port;
  public Integer getPort() { return port; }
  private int getOverriddenPort() {
    Object val = getProject().getProperties().get("port");
    if (val != null) {
      return Integer.parseInt(val + "");
    }
    if (port != null) {
      return port;
    }
    return 0;
  }

  @Optional @Input
  public String repoApiUrl;
  public String getRepoApiUrl() { return repoApiUrl; }
  private String getOverriddenRepoApiUrl() {
    Object val = getProject().getProperties().get("repoApiUrl");
    if (val != null) {
      return val + "";
    }
    return "https://api.github.com/repos/lu-cs-sde/codeprober";
  }

  // Parse space separated args, while allowing spaces to be escaped
  // E.g "a b\\ c" becomes ["a", "b c"]
  private List<String> parseArgsList(String val) {
    String[] parts = val.split("(?<!\\\\) ");
    String[] mapped = new String[parts.length];
    for (int i = 0; i < parts.length; ++i) {
      mapped[i] = parts[i].replace("\\ ", " ");
    }
    return Arrays.asList(mapped);
  }

  private File overriddenPathToFile(String path) {
    if (path.startsWith("~/") || path.startsWith("~" + File.separator) /* For windows */) {
      // Gradle does not expand home paths, we'll try doing it manually
      final String homePath = System.getProperty("user.home");
      if (homePath != null) {
        return new File(homePath, path.substring(2));
      }
    }
    return new File(path);
  }

  public LaunchCodeProber() {
    super();

    super.getMainClass().set("codeprober.CodeProber");
  }

  private void downloadCodeProber(File dst) throws IOException {
    System.out.println("Downloading latest CodeProber release..");
    final HttpURLConnection apiConn = (HttpURLConnection) new URL(getOverriddenRepoApiUrl() + "/releases/latest").openConnection();
    apiConn.setRequestMethod("GET");
    apiConn.setConnectTimeout(10000);
    apiConn.setReadTimeout(10000);

    final int apiStatus = apiConn.getResponseCode();
    if (apiStatus != 200) {
      throw new IOException("Unexpected status code " + apiStatus + " when fetching information about latest CodeProber release");
    }
    final BufferedReader in = new BufferedReader(new InputStreamReader(apiConn.getInputStream()));
    String inputLine;
    final StringBuffer content = new StringBuffer();
    while ((inputLine = in.readLine()) != null) {
      content.append(inputLine + "\n");
    }
    apiConn.disconnect();

    String tagName = null;
    String assetUrl = null;
    final String fullVersionFile = content.toString();
    try {
      final JSONObject parsed = new JSONObject(fullVersionFile);
      tagName = parsed.getString("tag_name");
      final JSONArray assets = parsed.getJSONArray("assets");
      for (int i = 0; i < assets.length(); ++i) {
        JSONObject asset = assets.getJSONObject(i);
        if ("codeprober.jar".equals(asset.getString("name"))) {
          assetUrl = asset.getString("browser_download_url");
        }
      }
    } catch (JSONException e) {
      throw new IOException("Unxpected response from releases/latest", e);
    }

    if (tagName == null || assetUrl == null) {
      throw new IOException("codeprober.jar missing from latest release");
    }
    final File propFile = new File(dst.getAbsolutePath() +".props");

    final Properties dlProps = new Properties();
    if (dst.exists() && propFile.exists()) {
      try {
        try (FileInputStream fis = new FileInputStream(propFile)) {
          dlProps.load(fis);
        }
        if (tagName.equals(dlProps.getProperty("tagName", null))) {
          System.out.println("Latest release already downloaded");
          // Just update the dst file to look newer, so we do not try to download it again
          dst.setLastModified(System.currentTimeMillis());
          return;
        }
        System.out.println("New release version: " + tagName +", currently downloaded version: " + dlProps.getProperty("tagName", null));
      } catch (IOException e) {
        System.err.println("Error when checking if currently downloaded version is up-to-date");
        e.printStackTrace();
        // Fall down to downloading it below
      }
    }

    System.out.println("Found latest release at '" + assetUrl +"', downloading");

    final HttpURLConnection downloadConn = (HttpURLConnection) new URL(assetUrl).openConnection();
    downloadConn.setRequestMethod("GET");
    downloadConn.setConnectTimeout(10000);
    downloadConn.setReadTimeout(10000);

    final int downloadStatus = downloadConn.getResponseCode();
    if (downloadStatus != 200) {
      throw new IOException("Unexpected status code " + downloadStatus + " when downloading codeprober.jar");
    }

    final InputStream dlStream = downloadConn.getInputStream();

    dst.getParentFile().mkdirs();
    final OutputStream fileStream = new FileOutputStream(dst);

    final byte[] buf = new byte[32 * 1024];
    int read;
    while ((read = dlStream.read(buf)) != -1) {
      fileStream.write(buf, 0, read);
    }

    fileStream.close();
    downloadConn.disconnect();

    System.out.println("Downloaded " + dst);

    dlProps.clear();
    dlProps.setProperty("tagName", tagName);
    try (FileOutputStream fos = new FileOutputStream(propFile)) {
      dlProps.store(fos, "");
    }
  }

  @Override
  public void exec() {
    final Object val = getProject().getProperties().get("jvmArgs");
    if (val != null) {
      setJvmArgs(parseArgsList("" + val));
    }
    // List<String> sysProps = getOverriddenSysProps();
    // if (sysProps != null) {
    //   setJvmArgs(sysProps);
    // }
    List<String> args = new ArrayList<>();
    List<String> cArgs = getOverriddenCprArgs();
    if (cArgs != null) {
      args.addAll(cArgs);
    }
    File tjar = getOverriddenToolJar();
    if (tjar != null) {
      args.add(tjar.getPath());
    }
    List<String> tArgs = getOverriddenToolArgs();
    if (tArgs != null) {
      args.addAll(tArgs);
    }
    System.out.println("Finished args: " + args);
    super.setArgs(args);

    super.getEnvironment().put("PORT",  getOverriddenPort());
    // Let CodeProber know it is run from gradle
    super.getEnvironment().put("GRADLEPLUGIN", "true");
    // It is up to us (the plugin) to manage versions, avoid the checker inside CodeProber itself
    super.getEnvironment().put("DISABLE_VERISON_CHECKER_BY_DEFAULT", "true");

    super.setClasspath(getClasspath().plus(getProject().files(getOverriddenCprJar())));

    // Intercept stdout in order to detect the port (which is randomly generated by default)
    setStandardOutput(new OutputStream() {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      boolean lookForPort = true;
      public void write(int b) throws IOException {
        if (!lookForPort) {
          System.out.write(b);
          return;
        }
        if ((char)b != '\n') {
          baos.write(b);
          return;
        }
        // Else, looking for port and wrote a newline
        final byte[] bytes = baos.toByteArray();
        baos.reset();
        final String needle = "CPRGRADLE_URL=";
        for (int i = 0; i < needle.length(); ++i) {
          if (i >= bytes.length || bytes[i] != needle.charAt(i)) {
            // Not a match
            System.out.write(bytes);
            System.out.write(b);
            return;
          }
        }
        // Match!
        lookForPort = false;
        String url = new String(bytes, needle.length(), bytes.length - needle.length() /* Use default encoding on purpose */);
        System.out.println("CodeProber is running.. Press Ctrl+C to stop it.");
        if (getOverriddenOpenBrowser()) {
          // final String url = line.substring("CPRGRADLE_URL=".length()).trim();
          try {
            java.awt.Desktop.getDesktop().browse(new URI(url));
          } catch (Exception e) {
            System.out.printf("Failed to launch browser. Please manually visit '%s'%n", url);
          }
        }
      };
    });
    super.exec();
  };
}
