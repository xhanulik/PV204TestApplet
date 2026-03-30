# PV204TestApplet


This is simple Java Card applet using Gradle build system.

You can develop your Java Card applets and build cap files with the Gradle!
Moreover the project template enables you to test the applet with [JCardSim] or on the physical cards.

Gradle project contains one module:

- `applet`: contains the Java Card applet. Can be used both for testing and building CAP
- `libs-sdks`: contains SDKs for building the applet for physical card

Features:
 - Gradle build (usable in CLI / compatible with IntelliJ Idea)
 - Build CAP for applets
 - Test applet code in [JCardSim] / physical cards

### Applet

The implementation contains a simple applet for testing in PV204 assignment.
There is also implemented very simple test that sends static APDU command to this applet - in JCardSim.

The Gradle project can be opened and run in the IntelliJ Idea.
If you do not use IntelliJ Idea, you can build the applet in CLI using directly the `gradlew` or `gradlew.bat` scripts.

## How to use

- Run Gradle wrapper `./gradlew` on Unix-like system or `./gradlew.bat` on Windows
to build the project for the first time (Gradle will be downloaded if not installed).

### Building cap file

- Run the `buildJavaCard` task:

```bash
./gradlew buildJavaCard  --info --rerun-tasks
```

Generates a new cap file `./applet/build/javacard/applet.cap`

Note: `--rerun-tasks` is to force re-run the task even though the cached input/output seems to be up to date.

Typical output:

```
[ant:cap] [ INFO: ] Converter [v3.0.5]
[ant:cap] [ INFO: ]     Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
[ant:cap]     
[ant:cap]     
[ant:cap] [ INFO: ] conversion completed with 0 errors and 0 warnings.
[ant:verify] XII 10, 2017 10:45:05 ODP.  
[ant:verify] INFO: Verifier [v3.0.5]
[ant:verify] XII 10, 2017 10:45:05 ODP.  
[ant:verify] INFO:     Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
[ant:verify]     
[ant:verify]     
[ant:verify] XII 10, 2017 10:45:05 ODP.  
[ant:verify] INFO: Verifying CAP file /path/jcard/applet/out/cap/applet.cap
[ant:verify] javacard/framework/Applet
[ant:verify] XII 10, 2017 10:45:05 ODP.  
[ant:verify] INFO: Verification completed with 0 warnings and 0 errors.
```

### Installation on a (physical) card

```bash
./gradlew installJavaCard
```

Or inspect already installed applets:

```bash
./gradlew listJavaCard
```

## Running on simulator (jCardSim)

As simple as:

```bash
./gradlew build
./gradlew run
```

By default the run task will run the main Java application implemented at: `main/java/main/Run.java`, using the `TestApplet` applet.
