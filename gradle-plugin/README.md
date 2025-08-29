# Gradle Plugin

This is a gradle plugin that simplifies fetching and starting CodeProber.
Documentation is available [in docs](../docs/usage/gradle_plugin.md).

## Testing updates

Before publishing a new version you likely want to test it locally.
Make your changes to the plugin, then run:
```
./gradlew publishToMavenLocal
```
This will "publish" the plugin locally on your machine.

In the project where you want to test your changes, create a file `settings.gradle` with the following content:
```gradle
pluginManagement {
  repositories {
      mavenLocal()
      gradlePluginPortal()
  }
}
```

This will make gradle first look in "locally published" plugins before looking at the public plugin portal.

The plugin version is present in two `build.gradle` files:

1. Locally (next to this file), using e.g. `verison = '1.2.3'`.
2. The `plugins` section in the project where you intend to test the your changes (using e.g. `id 'org.codeprober' version '1.2.3'`).

Make sure the version number is the same in both places, otherwise you might accidentally use a version from the public plugin portal when testing.

If done correctly, you'll see your changes running. You can re-run `publishToMavenLocal` to test new revisions, you do not need to update the version each time.

## Publishing updates

First, open `build.gradle` and change `version` to a higher number.

Next, login to the gradle portal (only necessary once per machine):
```
./gradlew login
```

Finally: publish!
```
./gradlew publishPlugins
```
