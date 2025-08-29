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
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Input;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
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

public abstract class UpdateCodeProber extends LaunchCodeProber {

  public UpdateCodeProber() {
    super();
  }

  @Override
  public void exec() {
    final CprDownloader dl = createDownloader();

    // If pinned to a specific version, do nothing
    if (!"latest".equals(dl.getMaskedVersion()) && dl.downloadedCodeProberSeemsUpToDate()) {
      return;
    }

    try {
      dl.downloadCodeProber();
    } catch (IOException e) {
      System.err.println("Error while downloading CodeProber.");
      e.printStackTrace();
      throw new GradleException("Error while downloading: " + e);
    }
  };
}
