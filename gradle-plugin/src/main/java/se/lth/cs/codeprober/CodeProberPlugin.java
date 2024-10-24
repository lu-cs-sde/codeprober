package se.lth.cs.codeprober;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class CodeProberPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    project.getTasks().create("launchCodeProber", LaunchCodeProber.class).setDescription("Start CodeProber and keep it running until Ctrl+C is pressed");
  }
}
