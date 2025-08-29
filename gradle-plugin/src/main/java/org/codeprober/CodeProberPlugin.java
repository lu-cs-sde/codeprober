package org.codeprober;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class CodeProberPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    LaunchCodeProber launchTask = project.getTasks().create("launchCodeProber", LaunchCodeProber.class);
    launchTask.setGroup("CodeProber");
    launchTask.setDescription("Start CodeProber and keep it running until Ctrl+C is pressed");

    LaunchCodeProber updateTask = project.getTasks().create("updateCodeProber", UpdateCodeProber.class);
    updateTask.setGroup("CodeProber");
    updateTask.setDescription("Download the latest version of CodeProber. Run this to avoid waiting for the periodic update check performed by launchCodeProber");
  }
}
