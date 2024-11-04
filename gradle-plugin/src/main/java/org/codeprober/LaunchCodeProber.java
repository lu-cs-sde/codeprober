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
    final CprDownloader dl = getDownloader();
    final File cprFile = dl.getCprDownloadLocation();
    if (!cprFile.exists()) {
      try {
        dl.downloadCodeProber();
      } catch (IOException e) {
        System.err.println("Error while downloading CodeProber");
        e.printStackTrace();
      }
    } else {
      if (!dl.downloadedCodeProberSeemsUpToDate()) {
        try {
          dl.downloadCodeProber();
        } catch (IOException e) {
          System.err.println("Error while (re-)downloading CodeProber. Will reuse previously downloaded version");
          e.printStackTrace();
        }
      }
      if (!cprFile.exists()) {
        throw new RuntimeException("Failed downloading codeprober.jar. Try again, or manually download it and specify with the property 'cpr'. For example: `./gradlew launchCodeProber -Pcpr=~/Downloads/codeprober.jar`");
      }
    }
    return cprFile;
  }

  private CprDownloader getDownloader() {
    return new CprDownloader(getProject().getProjectDir(), getOverriddenCprVersion(), getOverriddenRepoApiUrl(), getOverriddenCprUpdateCheck());
  }



  @Optional @Input
  public String cprVersion;
  public String getCprVersion() { return cprVersion; }
  private String getOverriddenCprVersion() {
    Object val = getProject().getProperties().get("cprVersion");
    if (val != null) {
      return val + "";
    }
    return cprVersion;
  }

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
