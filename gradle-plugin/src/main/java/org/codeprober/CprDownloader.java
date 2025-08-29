package org.codeprober;

import java.io.BufferedReader;
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

import org.gradle.internal.impldep.com.fasterxml.jackson.databind.annotation.JsonAppend.Prop;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;
import java.util.Properties;

public class CprDownloader {

  /**
   * Home directory of the gradle project
   */
  private File projectDir;

  /**
   * Pinned version of CodeProber to download.
   * Can be set to a git tag, like 0.0.3, 1.0.0, etc.
   * <code>null</code> means download the latest version.
   */
  private String cprVersion;

  /**
   * The Github URL to check releases and download CodeProber from.
   * Expected format: "https://api.github.com/repos/USER/REPO"
   */
  private String repoApiUrl;

  /**
   * If <code>true</code> and {@link #cprVersion} is <code>null</code>, periodically poll {@link #repoApiUrl} for new versions of CodeProber.
   * If {@link #cprVersion} is non-null, then this field has no effect.
   */
  private boolean shouldPeriodicallyCheckForNewVersions;

  private Properties oldProps = null;

  public CprDownloader(File projectDir, String cprVersion, String repoApiUrl, boolean shouldPeriodicallyCheckForNewVersions) {
    this.projectDir = projectDir;
    this.cprVersion = cprVersion;
    this.repoApiUrl = repoApiUrl;
    this.shouldPeriodicallyCheckForNewVersions = shouldPeriodicallyCheckForNewVersions;
  }

  public File getCprDownloadLocation() {
    return new File(projectDir, ".codeprober" + File.separatorChar + "codeprober.jar");
  }

  private File getCprPropsLocation() {
    return new File(getCprDownloadLocation().getAbsolutePath() + ".props");
  }

  protected String getMaskedVersion() {
    return cprVersion != null ? cprVersion : "latest";
  }

  private Properties loadOldProps() {
    if (oldProps == null) {
      oldProps = new Properties();
      try {
        try (FileInputStream fis = new FileInputStream(getCprPropsLocation())) {
          oldProps.load(fis);
        }
      } catch (IOException e) {
        System.err.println("Error when loading previous props");
        e.printStackTrace();
      }
    }
    return oldProps;
  }

  public boolean downloadedCodeProberSeemsUpToDate() {
    final Properties oldProps = loadOldProps();
    if (oldProps.size() == 0) {
      System.err.println("Error when checking if downloaded version is up-to-date. Treating it as out-of-date.");
      return false;
    }
    final String oldApiUrl = oldProps.getProperty(PropKeys.API_URL);
    if (!repoApiUrl.equals(oldApiUrl)) {
      System.out.println("It seems like the API url changed, from '" + oldApiUrl + "' to '" + repoApiUrl + "', need to re-download codeprober.jar");
      return false;
    }
    final String newVersion = getMaskedVersion();
    final String oldVersion = oldProps.getProperty(PropKeys.CPR_VERSION);
    if (!newVersion.equals(oldVersion)) {
      System.out.println("It seems like the CodeProber version changed, from '" + oldVersion + "' to '" + newVersion + "', need to re-download codeprober.jar");
      return false;
    }

    // Version and url is up-to-date. However, if version is null/"latest", we may need to redownload anyway
    final File dlLocation = getCprDownloadLocation();
    if (cprVersion == null && shouldPeriodicallyCheckForNewVersions) {
      final long age = System.currentTimeMillis() - dlLocation.lastModified();
      if (age > (7 * 24 * 60 * 60 * 1000)) {
        System.out.println("Currently downloaded codeprober.jar is over a week old, checking if new version exists. Disable this by setting '-PcprUpdateCheck=false'");
        return false;
      }
    }

    System.out.println("Reusing previously downloaded " + dlLocation);
    return true;
  }

  public void downloadCodeProber() throws IOException {
    System.out.println("Downloading information about " + ((cprVersion == null) ? "latest CodeProber release.." : ("CodeProber version " + cprVersion)));
    final String fullApiUrl = repoApiUrl + "/releases/" + (cprVersion == null ? "latest" : ("tags/" + cprVersion));
    final HttpURLConnection apiConn = (HttpURLConnection) new URL(fullApiUrl).openConnection();
    apiConn.setRequestMethod("GET");
    apiConn.setConnectTimeout(10000);
    apiConn.setReadTimeout(10000);

    final int apiStatus = apiConn.getResponseCode();
    if (apiStatus != 200) {
      throw new IOException("Unexpected status code " + apiStatus + " when fetching release information from " + fullApiUrl);
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

    final File cprDst = getCprDownloadLocation();
    if (cprVersion == null && cprDst.exists()) {
      final Properties oldProps = loadOldProps();
      if (repoApiUrl.equals(oldProps.getProperty(PropKeys.API_URL)) && tagName.equals(oldProps.get(PropKeys.TAG_NAME))) {
        System.out.println("Latest release already downloaded");
        // Just update the dst file to look newer, so we do not try to download it again
        cprDst.setLastModified(System.currentTimeMillis());
        maybeWriteUpdatedDownloadProps(tagName);
        return;
      }
    }

    System.out.println("Found release jar at '" + assetUrl + "', downloading..");

    final HttpURLConnection downloadConn = (HttpURLConnection) new URL(assetUrl).openConnection();
    downloadConn.setRequestMethod("GET");
    downloadConn.setConnectTimeout(10000);
    downloadConn.setReadTimeout(10000);

    final int downloadStatus = downloadConn.getResponseCode();
    if (downloadStatus != 200) {
      throw new IOException("Unexpected status code " + downloadStatus + " when downloading codeprober.jar from " + assetUrl);
    }

    final InputStream dlStream = downloadConn.getInputStream();

    cprDst.getParentFile().mkdirs();
    final OutputStream fileStream = new FileOutputStream(cprDst);

    final byte[] buf = new byte[32 * 1024];
    int read;
    while ((read = dlStream.read(buf)) != -1) {
      fileStream.write(buf, 0, read);
    }

    fileStream.close();
    downloadConn.disconnect();

    System.out.println("Downloaded " + cprDst);

    maybeWriteUpdatedDownloadProps(tagName);
  }

  private void maybeWriteUpdatedDownloadProps(String tagName) throws IOException {
    final Properties freshProps = new Properties();
    freshProps.setProperty(PropKeys.API_URL, repoApiUrl);
    freshProps.setProperty(PropKeys.TAG_NAME, tagName);
    freshProps.setProperty(PropKeys.CPR_VERSION, getMaskedVersion());
    if (!freshProps.equals(oldProps)) {
      try (FileOutputStream fos = new FileOutputStream(getCprPropsLocation())) {
        freshProps.store(fos, "");
      }
    }
  }

  private static class PropKeys {
    public static final String API_URL = "repoApiUrl";
    public static final String TAG_NAME = "tagName";
    public static final String CPR_VERSION = "version";
  }
}
