package org.codeprober;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class CodeProberPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    LaunchCodeProber task = project.getTasks().create("launchCodeProber", LaunchCodeProber.class);
    task.setGroup("CodeProber");
    task.setDescription("Start CodeProber and keep it running until Ctrl+C is pressed");
  }
}
