# Gradle Plugin

There is a gradle plugin that simplifies fetching and starting CodeProber.

## Basic Usage

Add the following to your build.gradle:
```gradle
plugins {
  id 'org.codeprober' version '0.0.4' // Or whichever version is latest, check "https://plugins.gradle.org/plugin/org.codeprober"
}
```

You likely want to add `.codeprober` to your `.gitignore`.
This plugin will download CodeProber to `.codeprober/codeprober.jar`.
It will also write some metadata inside the `.codeprober` directory to keep track of which version it most recently downloaded.

Then, launch with:
```bash
./gradlew launchCodeProber
```

CodeProber will keep running until you interrupt gradle, for example with `Ctrl+C`.

For convenience, you likely want to configure `launchCodeProber` with a number of parameters.
For example, you may want to add the following to your `build.gradle`.

```gradle
launchCodeProber {
  dependsOn 'jar'
  toolJar = file(jar.archiveFile)
  port = 8000
  workspace = file('myworkspace')
}
```

## Tasks

The plugin contains two tasks:

- `launchCodeProber`: Download CodeProber and run it.
- `updateCodeProber`: Check if a new version of CodeProber is available, and download it.

## Parameters

The plugin contains a number of parameters, all of which are optional.

| Property       | Type     | Default value                                     | Description                                                     |
| -------------- | -------- | ------------------------------------------------- | --------------------------------------------------------------- |
| toolJar        | File     | null                                              | The tool to explore with CodeProber. This is technically optional, but you very likely want to set this. For example, you can set it based on the output of a `jar` task, as shown in basic usage above. |
| toolArgs       | String[] | null                                              | Arguments to pass to the underlying tool. This corresponds to "`X`" in `java -jar cprJar toolJar X`. |
| cprJar         | File     | null                                              | A codeprober.jar file to use. By default, this plugin will download the latest release from github and save it in `.codeprober/codeprober.jar`. |
| cprArgs        | String[] | null                                              | Arguments to pass to CodeProber. This corresponds to "`X`" in `java -jar cprJar X toolJar`. |
| jvmArgs        | String[] | null                                              | Arguments to pass to the JVM. This corresponds to "`X`" in `java X -jar cprJar toolJar`. This can for example be used to set system properties, such as `cpr.backing_file` |
| cprVersion     | String   | null                                              | The version of CodeProber to run. Should be set to the tag of a release, like "0.0.3". By default, this plugin will download the latest version of CodeProber, and periodically check for new versions (see `cprUpdateCheck`). Use this parameter to pin a specific version to use. |
| cprUpdateCheck | boolean  | true                                              | Whether or not to periodically check for new versions of CodeProber. This is done once a week by default, i.e when the lastModified of the downloaded `codeprober.jar` is over a week old, the plugin will try to retrieve the latest jar from github. This parameter has no effect if `cprVersion` is set. |
| openBrowser    | boolean  | true                                              | Whether to automatically open a web browser with the the CodeProber URL immediately after starting. |
| port           | int      | 0                                                 | The port to serve requests on. If set to 0, then a random free port is automatically picked. |
| workspace      | File     | null                                              | Set a directory to be used as a "file system" in the CodeProber UI. This can also be set with the system property 'cpr.workspace'. |
| repoApiUrl     | String   | https://api.github.com/repos/lu-cs-sde/codeprober | Base url for the API requests used for fetching and downloading CodeProber. This can be used to run CodeProber forks. |

All parameters can be set with project properties on the command-line, like `-Pfoo=bar`, or in the `build.gradle` file.
For example, the customized `launchCodeProber` block in `Basic Usage` above is equivalent to the following bash command (assuming `jar.archiveFile`==`compiler.jar`):
```bash
./gradlew jar launchCodeProber -PtoolJar=compiler.jar -Pport=8000 -Pworkspace="myworkspace"
```

The `String[]` args are space-separated when set in the command-line.
If you want the space to be included in a value, you can escape it with `\`.
For example, the following two declarations are equal:
```gradle
task foo(type: org.codeprober.LaunchCodeProber) {
  toolArgs = ['foo=bar baz', 'lorem=ipsum']
}
```
```bash
./gradlew launchCodeProber -DtoolArgs="foo=bar\ baz lorem=ipsum"
```

## Example custom tasks

You can have multiple tasks in your `build.gradle` that runs CodeProber, not just the default `launchCodeProber`.
For example, you may want to have one that runs your text probe tests, like this:

```gradle
task runCodeProberTests(type: org.codeprober.LaunchCodeProber) {
  dependsOn 'jar'
  toolJar = file(jar.archiveFile)
  workspace = file('myworkspace')
  cprArgs = ['--test']
}
```

You may also want a task that opens CodeProber with the contents of a specific file in the file system, like this:
```gradle
task openCodeProberWithMyTestFile(type: org.LaunchCodeProber) {
  dependsOn 'jar'
  toolJar = file(jar.archiveFile)
  jvmArgs = ['-Dcpr.backing_file=tests/my_test_file.in']
}
```

## Customize all CodeProber tasks simultaneously

Using `tasks.withType` you can provide a common configuration for all CodeProber-related tasks, like this:

```gradle
tasks.withType(org.codeprober.LaunchCodeProber) {
  dependsOn 'jar'
  toolJar = file('compiler.jar')
  workspace = file('myworkspace')
}
```
