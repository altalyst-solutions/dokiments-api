# Getting started

From a fresh machine to the API running locally. No accounts or keys are needed: everything runs against local
containers and the Firestore emulator.

Pick your operating system:

- [macOS](#macos)
- [Linux](#linux)
- [Windows](#windows): through WSL 2, which gives you a real Linux environment inside Windows

Then continue with [Clone, build and run](#clone-build-and-run), which is the same everywhere.

## What you need

| Tool | Version | Why |
| --- | --- | --- |
| JDK | 25 (Eclipse Temurin recommended) | Runs Gradle and compiles the code |
| Docker with Docker Compose | Docker Desktop, or Docker Engine with the Compose plugin | Redis and the Firestore emulator locally, and the integration tests |
| Git | Any recent version | |
| An editor | IntelliJ IDEA or VS Code | See [IDE setup](ide-setup.md) |

Gradle doesn't need to be installed: the repository includes the Gradle wrapper (`./gradlew`), which downloads the
right Gradle version on first use.

Give Docker at least 4 GB of memory: the Firestore emulator image is large.

## macOS

With [Homebrew](https://brew.sh/):

```bash
brew install --cask temurin@25
brew install --cask docker-desktop
```

Start Docker Desktop once from Applications and accept its prompts. In its settings, under Advanced, make sure "Allow
the default Docker socket to be used" is on: the integration tests need it.

Instead of Homebrew, you can install the JDK with [SDKMAN](https://sdkman.io/) (`sdk list java`, then
`sdk install java` with the `25.x-tem` identifier) and Docker Desktop from
[docker.com](https://www.docker.com/products/docker-desktop/).

## Linux

The JDK, with [SDKMAN](https://sdkman.io/) (works on every distribution):

```bash
curl -s "https://get.sdkman.io" | bash
# Open a new terminal, then pick the 25.x "-tem" identifier from the list:
sdk list java
sdk install java <25.x-tem identifier>
```

Docker: install Docker Engine and the Compose plugin by following
[Docker's guide for your distribution](https://docs.docker.com/engine/install/), then let your user run Docker without
`sudo`, as Testcontainers needs:

```bash
sudo usermod -aG docker "$USER"
# Log out and back in for this to apply.
```

Docker Desktop for Linux works too.

## Windows

Develop inside **WSL 2** (Windows Subsystem for Linux) with Ubuntu. The repository, the JDK, Gradle and all commands
live in Linux, and your editor on Windows connects to it. This is the setup we support on Windows because:

- the scripts and commands in these guides run unchanged;
- Docker Desktop runs on WSL 2 anyway, so containers and the integration tests work without extra setup;
- builds and file watching are much faster on the Linux file system than on a Windows folder.

Running natively on Windows (PowerShell and `gradlew.bat`) may work for building, but it isn't tested.

### 1. Install WSL with Ubuntu

In PowerShell as administrator:

```powershell
wsl --install -d Ubuntu
```

Restart when asked, then open **Ubuntu** from the Start menu and create your Linux username and password. Check that
it runs WSL 2:

```powershell
wsl -l -v
```

The `VERSION` column should say `2`. If it says `1`, run `wsl --set-version Ubuntu 2`.

### 2. Install Docker Desktop

Install [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/), keeping "Use WSL 2 instead of
Hyper-V" selected. Then, in Docker Desktop, open Settings, Resources, WSL integration, and turn on **Ubuntu**.

Don't install Docker separately inside Ubuntu: Docker Desktop provides the `docker` command there.

### 3. Install the JDK and Git inside Ubuntu

In the Ubuntu terminal:

```bash
sudo apt update && sudo apt install -y git curl unzip zip
curl -s "https://get.sdkman.io" | bash
# Open a new Ubuntu terminal, then pick the 25.x "-tem" identifier from the list:
sdk list java
sdk install java <25.x-tem identifier>
```

A JDK installed on the Windows side isn't used: everything runs in Ubuntu.

### 4. Work in the Linux file system

Clone the repository under your Linux home folder (for example `~/code`), **not** under `/mnt/c/...`. Files on the
Windows drive are many times slower to read from Linux, and changes to them aren't reliably noticed, so automatic
restarts during development would stop working.

From Windows, the Linux files are visible in Explorer under `\\wsl$\Ubuntu\home\<your user>`.

## Clone, build and run

In a terminal (on Windows, the Ubuntu terminal):

### 1. Check the tools

```bash
java -version            # openjdk version "25..."
docker info              # prints details, not "Cannot connect to the Docker daemon"
docker compose version   # Docker Compose version v2 or later
```

### 2. Clone

```bash
git clone https://github.com/altalyst-solutions/dokiments-api.git
cd dokiments-api
```

### 3. Build once

```bash
./gradlew build
```

The first run downloads Gradle, all dependencies and the Redis and Firestore emulator images, which takes a few
minutes. Later builds are much faster. It ends with a summary of both test suites and `BUILD SUCCESSFUL`.

### 4. Run

```bash
./gradlew bootRun
```

In a second terminal:

```bash
curl localhost:8080/actuator/health
```

The answer shows `"status":"UP"`, with Redis and Firestore listed. Stop the application with `Ctrl+C`.

### 5. Open it in your editor

Follow [IDE setup](ide-setup.md) for IntelliJ IDEA or VS Code.

From here, [Local development](local-development.md) explains what `bootRun` starts, how local data is kept and how to
run the API as a container.

## Behind a TLS-inspecting proxy

Some company networks inspect HTTPS traffic with their own root certificate. Browsers and `curl` trust it through the
operating system, but Java uses its own list of trusted certificates and doesn't, so Gradle fails to download anything
with `PKIX path building failed`.

To fix it, create a truststore containing the JDK's certificates plus the proxy's root certificate (ask your IT team
for it as a PEM file), outside the repository:

```bash
mkdir -p ~/.config/java
cp "$JAVA_HOME/lib/security/cacerts" ~/.config/java/truststore.p12
keytool -importcert -noprompt -alias proxy-root-ca -file /path/to/proxy-root-ca.pem \
  -keystore ~/.config/java/truststore.p12 -storepass changeit
```

On macOS without `JAVA_HOME` set, use `"$(/usr/libexec/java_home -v 25)/lib/security/cacerts"` as the source.

Then point Gradle at it in your **user-level** `~/.gradle/gradle.properties`, never in the project's
`gradle.properties`, because this is specific to your machine:

```properties
systemProp.javax.net.ssl.trustStore=/absolute/path/to/home/.config/java/truststore.p12
systemProp.javax.net.ssl.trustStorePassword=changeit
```

Stop running Gradle daemons (`./gradlew --stop`) so the change is picked up. Docker builds need the certificate too:
see [Docker image](local-development.md#docker-image).

## Troubleshooting

| Problem | Fix |
| --- | --- |
| `Cannot connect to the Docker daemon` | Start Docker Desktop (or the Docker service on Linux). In WSL, check that WSL integration is on for Ubuntu in Docker Desktop. |
| `permission denied` on `/var/run/docker.sock` (Linux) | Your user isn't in the `docker` group: see [Linux](#linux), then log out and back in. |
| Integration tests fail with `Could not find a valid Docker environment` | Testcontainers can't reach Docker. In Docker Desktop on macOS, enable "Allow the default Docker socket to be used" (Settings, Advanced). With Colima or another runtime, set `DOCKER_HOST`. |
| `Port 8080 was already in use` | Another program uses 8080. Set another port in `.env`, for example `PORT=8081` (see [Local settings](configuration.md#local-settings)). |
| `Bind for 0.0.0.0:8085 failed` | Port 8085 (Firestore emulator) is taken, often by containers left from an earlier run: `docker compose down`. Or set `FIRESTORE_EMULATOR_PORT` in `.env`. |
| `Timeout waiting to lock ... cache` | The IDE is importing the project at the same time. Wait for it to finish and run again. |
| `PKIX path building failed` | See [Behind a TLS-inspecting proxy](#behind-a-tls-inspecting-proxy). |
| `./gradlew build` fails on formatting | Run `./gradlew spotlessApply`. |
| `/usr/bin/env: 'bash\r': No such file or directory` | The repository was checked out with Windows line endings, usually by cloning on the Windows side. Clone again inside Ubuntu. |
| Automatic restarts don't happen in WSL | The project is under `/mnt/c/...`. Clone it into your Linux home folder. |
