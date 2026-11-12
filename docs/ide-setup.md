# IDE setup

The project works in **IntelliJ IDEA** (Community or Ultimate) and **VS Code**. Both are set up from files in the
repository, so formatting and imports match the build without manual configuration:

| File | What it does |
| --- | --- |
| `.editorconfig` | Indentation, line endings and line length, for every editor |
| `.idea/externalDependencies.xml` | IntelliJ asks to install the required plugins |
| `.idea/palantir-java-format.xml` | Turns palantir-java-format on, so "Reformat Code" matches the build |
| `.idea/inspectionProfiles/Project_Default.xml` | The project's inspection settings, shared by the editor and `scripts/inspect.sh` |
| `.vscode/extensions.json` | VS Code suggests the recommended extensions |
| `.vscode/settings.json` | Java settings that match the build (import order, no format-on-save) |

First follow [Getting started](getting-started.md), so the JDK and Docker are in place and `./gradlew build` passes.

- [IntelliJ IDEA](#intellij-idea)
- [VS Code](#vs-code)
- [Seeing problems in the editor](#seeing-problems-in-the-editor)

## IntelliJ IDEA

### Open the project

1. File, Open, and pick the `dokiments-api` folder. Choose **Trust Project** when asked.
2. IntelliJ recognises the Gradle build and imports it. Wait for the import to finish (progress at the bottom).
3. When IntelliJ offers to install the required plugins (palantir-java-format, Spotless Applier), accept and restart.

### Set the JDK

- File, Project Structure, Project: set **SDK** to a JDK 25. If none is listed, choose Add SDK and either point it at
  the JDK you installed or let IntelliJ download Eclipse Temurin 25.
- Settings, Build, Execution, Deployment, Build Tools, Gradle: set **Gradle JVM** to "Project SDK".

Name the JDK `25` (the default when IntelliJ detects it). The project refers to it by that name.

### Run the application

Run `bootRun` from the Gradle tool window (Tasks, application, bootRun), or `./gradlew bootRun` in IntelliJ's
terminal. Both use the `dev` profile and start Redis and the Firebase emulators.

Avoid running `DokimentsApiApplication` directly with the green arrow: that skips the Gradle setup, so the `dev`
profile isn't set and the application refuses to start without a Firestore project and Redis.

Spring Boot DevTools restarts the application when compiled classes change. With `bootRun` running, use Build, Build
Project (`Cmd+F9` on macOS, `Ctrl+F9` elsewhere) after a change. To have this happen on save, turn on Settings, Build,
Execution, Deployment, Compiler, "Build project automatically", and in Settings, Advanced Settings, "Allow auto-make to
start even if developed application is currently running".

### Run the tests

Right-click a test class or folder and choose Run. IntelliJ runs tests through Gradle, so integration tests (names
ending in `IT`) start their containers as they do on the command line. Docker must be running.

### Format

Code, Reformat Code (`Cmd+Alt+L` on macOS, `Ctrl+Alt+L` elsewhere) gives exactly the same result as the build.
Settings, Tools, Actions on Save, "Reformat code" applies it on every save. The Spotless Applier plugin adds a "Reformat
with Spotless" action that also formats the Gradle scripts.

### IntelliJ with WSL (Windows)

Keep IntelliJ installed on Windows and the project in Ubuntu, as described in
[Getting started](getting-started.md#windows):

1. File, Open, and enter the path to the project in the Linux file system, for example
   `\\wsl$\Ubuntu\home\<your user>\code\dokiments-api`.
2. IntelliJ detects that the project lives in WSL. For the project SDK, choose Add SDK, then the JDK you installed in
   Ubuntu with SDKMAN (under `~/.sdkman/candidates/java/`), or let IntelliJ download one into WSL.
3. Gradle and the terminal then run inside Ubuntu, with Docker from Docker Desktop.

If the IDE feels slow with a large project in WSL, JetBrains remote development (File, Remote Development, WSL) runs
the IDE's back end inside Ubuntu instead, with the same settings.

## VS Code

### Open the project

1. File, Open Folder, and pick the `dokiments-api` folder. Choose "Yes, I trust the authors" when asked.
2. Accept the prompt to install the **recommended extensions**: Java, Gradle, Spring Boot, EditorConfig, YAML, Docker
   containers, GitHub Actions and TOML support. If you missed it, open the Extensions view and filter by
   `@recommended`.
3. The Java extension imports the Gradle project by itself. Wait for "Java: Ready" in the status bar.

### Set the JDK

The Java extension uses `JAVA_HOME` to find the JDK. If it picks the wrong one, or can't find JDK 25, add this to your
**user** settings (not the project's `.vscode/settings.json`, because the path is specific to your machine):

```json
{
  "java.import.gradle.java.home": "/path/to/jdk-25"
}
```

With SDKMAN, the path is `~/.sdkman/candidates/java/<version>` (spelled out in full, without `~`).

### Run the application

Run `./gradlew bootRun` in VS Code's terminal. This uses the `dev` profile and starts Redis and the Firebase emulators.

Avoid the Run button above `main` and the Spring Boot Dashboard: they skip the Gradle setup, so the `dev` profile
isn't set and the application refuses to start without a Firestore project and Redis.

VS Code's Java extension compiles into its own folder, which `bootRun` doesn't watch. For automatic restarts, run this
in a second terminal while `bootRun` is running:

```bash
./gradlew classes --continuous
```

### Run the tests

Use the Testing view, or the Run Test links above each test. Integration tests (names ending in `IT`) start their
containers, so Docker must be running. `./gradlew test` and `./gradlew integrationTest` in the terminal run them
exactly as CI does.

### Format

VS Code can't run palantir-java-format, and its own Java formatter would lay code out differently, so the project
turns format-on-save off for Java. Run this before committing:

```bash
./gradlew spotlessApply
```

The project also sets the same import order as the build (static imports first, then the rest alphabetically), so
"Organize Imports" doesn't conflict with it.

### VS Code with WSL (Windows)

Keep VS Code installed on Windows and the project in Ubuntu, as described in
[Getting started](getting-started.md#windows):

1. In VS Code on Windows, install the **WSL** extension (`ms-vscode-remote.remote-wsl`).
2. In the Ubuntu terminal, go to the project and run `code .`. VS Code opens connected to Ubuntu, shown as
   "WSL: Ubuntu" in the bottom-left corner.
3. Install the recommended extensions when asked. They are installed on the Ubuntu side, where the JDK and Docker are.

Everything else works as described above, with the terminal running in Ubuntu.

## Seeing problems in the editor

The **Gradle build is the reference**: it runs the same checks on every operating system, in both editors and in CI,
and fails on any of them. The editors show many of the same problems as you type, plus some of their own.

| Check | IntelliJ IDEA | VS Code | `./gradlew build` |
| --- | --- | --- | --- |
| Compiler errors | As you type | As you type | Yes |
| Compiler warnings (`-Xlint`) | As you type | As you type, from its own compiler, so the list can differ slightly | Yes, as errors |
| Error Prone and NullAway | No | No | Yes, as errors |
| Formatting | Reformat Code gives the build's result | No, run `./gradlew spotlessApply` | Yes |
| Architecture rules | When running the tests | When running the tests | Yes |
| IntelliJ inspections (Spring configuration, YAML, Gradle scripts, Markdown, spelling) | As you type, and Code, Inspect Code for the whole project | No | No |
| Vulnerable libraries | Problems, Security tab (Package Checker) | No | The image scan in CI |

In IntelliJ, open the **Problems** tool window (`Cmd+6` on macOS, `Alt+6` elsewhere): "File" shows the open file,
"Project Errors" the whole project, and "Security" vulnerable libraries. Code, Inspect Code runs every inspection on
the whole project at once.

In VS Code, open the **Problems** panel (`Cmd+Shift+M` on macOS, `Ctrl+Shift+M` elsewhere).

Either way, run `./gradlew build` before pushing: it is what CI runs. See [Testing and quality](testing-and-quality.md)
for what each check covers.
