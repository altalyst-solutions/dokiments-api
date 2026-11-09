<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./docs/assets/logo-dark.png" />
  <source media="(prefers-color-scheme: light)" srcset="./docs/assets/logo-light.png" />
  <img src="./docs/assets/logo-light.png" alt="Dokiments logo" width="96" height="96" />
</picture>

# Dokiments API

**The backend API behind [Dokiments](https://dokiments.com)**

[![CI](https://github.com/altalyst-solutions/dokiments-api/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/altalyst-solutions/dokiments-api/actions/workflows/ci.yml)
[![Security scan](https://github.com/altalyst-solutions/dokiments-api/actions/workflows/security-scan.yml/badge.svg)](https://github.com/altalyst-solutions/dokiments-api/actions/workflows/security-scan.yml)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Gradle](https://img.shields.io/badge/Gradle-9.9-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)](./Dockerfile)
[![Conventional Commits](https://img.shields.io/badge/Conventional%20Commits-1.0.0-FE5196?logo=conventionalcommits&logoColor=white)](https://www.conventionalcommits.org/)

[Getting started](#getting-started) ·
[Running locally](#running-locally) ·
[Architecture](#architecture) ·
[Configuration](#configuration) ·
[Contributing](#contributing)

</div>

## Overview

Dokiments is a workspace for business documents: templates, documents, clients, document sharing and e-signing. This
repository is its backend. The Dokiments web app is a separate Next.js project that forwards its `/api/*` requests
here, so the browser only ever talks to one domain.

```mermaid
flowchart LR
    browser(["Browser"]) --> web["Dokiments web app<br/>(Next.js)"]
    web -- "/api/*" --> api["dokiments-api<br/>(Spring Boot)"]
    api --> firestore[("Firestore<br/>data")]
    api --> redis[("Redis<br/>sessions")]
    api --> auth["Firebase Auth<br/>sign-in"]
```

## Tech stack

| Area | Choice |
| --- | --- |
| Language and framework | Java 25, Spring Boot 4.1 (Spring Framework 7), Spring MVC on virtual threads |
| Build | Gradle (Kotlin DSL) with a version catalog in `gradle/libs.versions.toml` |
| Data | Firestore, through the Firebase Admin SDK |
| Authentication | Firebase Auth |
| Sessions | HttpOnly cookies backed by Redis (Spring Session) |
| Architecture | Modular monolith in hexagonal style, checked by Spring Modulith and ArchUnit |
| Code quality | Spotless (palantir-java-format), Error Prone, NullAway with JSpecify, JaCoCo |
| Tests | JUnit, AssertJ, Testcontainers (Redis and the Firestore emulator) |
| Runtime | Docker image (Eclipse Temurin JRE), configured through environment variables |

## Getting started

> [!NOTE]
> The commands in this README are written for macOS and Linux shells. On Windows, use Git Bash or WSL, or replace
> `./gradlew` with `gradlew.bat` in PowerShell.

### 1. Install the prerequisites

| Tool | Version | Why |
| --- | --- | --- |
| JDK | 25 (Eclipse Temurin recommended) | Runs Gradle and compiles the code |
| Docker with Docker Compose | Docker Desktop, or Docker Engine with the Compose plugin on Linux | Redis and the Firestore emulator locally, and the integration tests |
| Git | Any recent version | |

Gradle doesn't need to be installed: the repository includes the Gradle wrapper (`./gradlew`), which downloads the
right Gradle version on first use. The wrapper itself needs a JDK (17 or newer) to start. If that JDK isn't version
25, Gradle downloads JDK 25 for the build automatically, but installing 25 directly keeps the IDE and the terminal on
the same version.

Install the JDK with one of these:

```bash
# macOS (Homebrew)
brew install --cask temurin@25

# macOS or Linux (SDKMAN): pick the 25.x "-tem" identifier from the list
sdk list java
sdk install java <25.x-tem identifier>

# Windows (PowerShell)
winget install EclipseAdoptium.Temurin.25.JDK
```

Install Docker Desktop from [docker.com](https://www.docker.com/products/docker-desktop/), or on Linux, Docker Engine
and the Compose plugin from your distribution. Give Docker at least 4 GB of memory: the Firestore emulator image is
large.

Check that everything is in place:

```bash
java -version            # openjdk version "25..."
docker info              # prints details, not "Cannot connect to the Docker daemon"
docker compose version   # Docker Compose version v2 or later
```

### 2. Clone the repository

```bash
git clone https://github.com/altalyst-solutions/dokiments-api.git
cd dokiments-api
```

### 3. Open it in your editor

- **VS Code:** open the folder and accept the prompt to install the recommended extensions (Java, Gradle, Spring Boot
  and a few others, listed in `.vscode/extensions.json`). The Java extension imports the Gradle project by itself.
- **IntelliJ IDEA:** open the folder as a Gradle project and accept the prompt to install the required plugins
  (palantir-java-format, Spotless Applier). Check that the project SDK and the Gradle JVM are both JDK 25 under
  Settings, Build Tools, Gradle.

Both editors are set up so that formatting matches the build. See [Formatting](#formatting).

### 4. Build once

```bash
./gradlew build
```

The first run downloads Gradle, all dependencies and the Redis and Firestore emulator images, which takes a few
minutes. Later builds are much faster. It ends with a summary like:

```text
test: 15 passed, 0 failed, 0 skipped (SUCCESS)
integrationTest: 11 passed, 0 failed, 0 skipped (SUCCESS)
BUILD SUCCESSFUL
```

### 5. Run it

```bash
./gradlew bootRun
curl localhost:8080/actuator/health
```

See [Running locally](#running-locally) for what this starts and how to work with it.

> [!TIP]
> No accounts or keys are needed. Everything local runs against containers and the Firestore emulator. A `.env`
> file is optional: see [Local settings](#local-settings).

<details>
<summary><strong>Behind a TLS-inspecting proxy</strong></summary>

<br />

Some company networks inspect HTTPS traffic with their own root certificate. Browsers and `curl` trust it through the
operating system, but Java uses its own list of trusted certificates and doesn't, so Gradle fails to download anything
with `PKIX path building failed`.

To fix it, create a truststore containing the JDK's certificates plus the proxy's root certificate (ask your IT team
for it as a PEM file), outside the repository:

```bash
mkdir -p ~/.config/java
cp "$(/usr/libexec/java_home -v 25)/lib/security/cacerts" ~/.config/java/truststore.p12   # on Linux: "$JAVA_HOME/lib/security/cacerts"
keytool -importcert -noprompt -alias proxy-root-ca -file /path/to/proxy-root-ca.pem \
  -keystore ~/.config/java/truststore.p12 -storepass changeit
```

Then point Gradle at it in your **user-level** `~/.gradle/gradle.properties`, never in the project's
`gradle.properties`, because this is specific to your machine:

```properties
systemProp.javax.net.ssl.trustStore=/absolute/path/to/home/.config/java/truststore.p12
systemProp.javax.net.ssl.trustStorePassword=changeit
```

Stop running Gradle daemons (`./gradlew --stop`) so the change is picked up. Docker builds need the certificate too:
see [Docker image](#docker-image).

</details>

<details>
<summary><strong>Troubleshooting</strong></summary>

<br />

| Problem | Fix |
| --- | --- |
| `Cannot connect to the Docker daemon` | Start Docker Desktop (or the Docker service on Linux) |
| Integration tests fail with `Could not find a valid Docker environment` | Testcontainers can't reach Docker. In Docker Desktop, enable "Allow the default Docker socket to be used" (Settings, Advanced). With Colima or another runtime, set `DOCKER_HOST`. |
| `Port 8080 was already in use` | Another program uses 8080. Set another port in `.env`, for example `PORT=8081` (see [Local settings](#local-settings)) |
| `Bind for 0.0.0.0:8085 failed` | Port 8085 (Firestore emulator) is taken, often by containers left from an earlier run: `docker compose down`. Or set `FIRESTORE_EMULATOR_PORT` in `.env`. |
| `Timeout waiting to lock ... cache` | The IDE is importing the project at the same time. Wait for it to finish and run again. |
| `PKIX path building failed` | See "Behind a TLS-inspecting proxy" above |
| `./gradlew build` fails on formatting | Run `./gradlew spotlessApply` |

</details>

## Running locally

```bash
./gradlew bootRun
```

This starts the application with the `dev` profile. Through Spring Boot's Docker Compose support it also starts Redis
and the Firestore emulator from `compose.yaml`, connects to them, and stops them again when the application stops.
The `dev` profile shows the Spring banner, readable coloured logs and full health details.

```mermaid
flowchart LR
    run["./gradlew bootRun"] --> app["API on :8080<br/>(dev profile)"]
    run -. "starts and stops" .-> services
    subgraph services["compose.yaml"]
        redis[("Redis<br/>random host port")]
        emulator[("Firestore emulator<br/>:8085, data kept")]
    end
    app --> redis
    app --> emulator
```

Spring Boot DevTools restarts the application automatically whenever compiled classes change. IDEs compile on save;
from a terminal, run `./gradlew classes --continuous` in a second window.

Check that it is up:

```bash
curl localhost:8080/actuator/health
```

If port 8080 is already in use on your machine, set `PORT=8081` (or any free port) in `.env`. See
[Local settings](#local-settings).

To keep Redis and the emulator running between application restarts, start them yourself with
`docker compose up -d`. The application then uses them and leaves them running when it stops. Stop them with
`docker compose down`.

### Local data

The Firestore emulator is the local database: nothing is read from or written to Cloud Firestore. Its data **is kept
between restarts**, including stopping `bootRun`, `docker compose down` and restarting your computer. The emulator
itself only holds data in memory, so `docker/firestore-emulator/start.sh` saves it to a Docker volume: once a minute
while it runs (`FIRESTORE_SAVE_INTERVAL_SECONDS`), and once more when it stops. On start, it loads the latest save.

| You want to | Do this |
| --- | --- |
| Keep your data | Nothing: it is kept automatically |
| Start again with an empty database | `docker compose down --volumes` |
| See the saves | `docker compose exec firestore ls /data/exports` |

If the emulator is stopped abruptly (Docker force-quit, a crash, power loss), changes since the last save can be lost:
at most a minute with the default interval. Integration tests never use this data: they run against a fresh emulator
of their own.

### Running the API as a container

To run the API itself as a Docker container, the way it runs when deployed:

```bash
docker compose --profile full up --build
```

The container uses the `prod` profile, so logs are JSON. `API_PORT` in `.env` changes the host port (default 8080).

| Command | Starts |
| --- | --- |
| `docker compose up` | Redis and the Firestore emulator |
| `docker compose --profile full up --build` | Redis, the emulator and the API container |
| `./gradlew bootRun` | Redis and the emulator (automatically), and the API from your code |

## Tests

| Command | What runs | Needs Docker |
| --- | --- | --- |
| `./gradlew test` | Unit and architecture tests | No |
| `./gradlew integrationTest` | Integration tests against real Redis and Firestore emulator containers | Yes |
| `./gradlew build` | Formatting check, compile checks, both test suites and coverage | Yes |

Reports are written to `build/reports/`: tests under `tests/`, coverage under `jacoco/`. Spring Modulith writes module
diagrams to `build/spring-modulith-docs/`.

## Formatting

Code is formatted by Spotless. `./gradlew build` fails on unformatted code, and this fixes it:

```bash
./gradlew spotlessApply
```

Editor setup is shared in the repository:

- **IntelliJ IDEA** asks to install the palantir-java-format plugin (`.idea/externalDependencies.xml`), and the project
  turns it on (`.idea/palantir-java-format.xml`), so "Reformat Code" gives exactly the same result as Spotless.
- **VS Code** suggests the recommended extensions from `.vscode/extensions.json`. VS Code can't run
  palantir-java-format, and its own Java formatter would lay code out differently, so `.vscode/settings.json` turns
  format-on-save off for Java: run `./gradlew spotlessApply` before committing instead. It also sets the same import
  order as palantir-java-format (static imports first, then the rest alphabetically), so organising imports doesn't
  fight Spotless, and re-imports the Gradle project when the build files change.

## Code quality checks

The build is strict on purpose, so problems are fixed while the code is written:

- **Formatting:** `./gradlew build` fails on unformatted code. `./gradlew spotlessApply` fixes it.
- **Compiler and static analysis:** every compiler warning and every Error Prone finding, including unused variables,
  methods and parameters, fails the build. NullAway fails it on possible null-pointer errors.
- **Architecture:** `HexagonalArchitectureTest` and `ModularityTest` fail it when layers or modules depend on each
  other the wrong way.

For everything else IntelliJ IDEA checks (Spring configuration, YAML, Gradle scripts, docs), run its inspections from
the command line. It takes about a minute, works while the IDE is open, and doesn't change your IDE settings:

```bash
scripts/inspect.sh
```

It should print `No problems.` and exit with `0`. It exits with `1` when it finds problems, and with `2` when IntelliJ
couldn't inspect part of the project, so a run that quietly checked less is never mistaken for a clean one. It needs
the project to have been opened in IntelliJ once, so it knows which JDK the project uses. A few findings that don't
apply to this project are left out, with the reason for each at the top of the script.

## Configuration

All configuration comes from environment variables, so the same image runs on any container platform.

| Variable | Required | Description |
| --- | --- | --- |
| `REDIS_URL` | Yes, outside `dev` | Redis connection, for example `redis://host:6379` or `rediss://user:password@host:6379` for TLS. The application refuses to start without it. In `dev` it defaults to the Compose service. |
| `PORT` | No | HTTP port. Defaults to `8080`. |
| `FIREBASE_PROJECT_ID` | Yes, outside `dev` | Firebase project holding the Firestore database. In `dev` and tests it is `demo-dokiments` on the emulator. |
| `FIRESTORE_EMULATOR_HOST` | No | `host:port` of a Firestore emulator to use instead of the real Firestore. Only allowed with a `demo-` project ID. |
| `GOOGLE_APPLICATION_CREDENTIALS` | Outside Google Cloud | Path to service account credentials for Firestore. On Cloud Run, the attached service account is used instead, so no file is needed. |
| `SPRING_PROFILES_ACTIVE` | No | `prod` in the Docker image, `dev` for `bootRun`. |
| `DOCUMENT_SHARING_ENABLED` | No | `true` turns on sharing documents for signature. Off by default; while off, the sharing endpoints answer `404`. |
| `TIER_LOCKS_ENABLED` | No | `false` stops enforcing plan limits. On by default; only switch it off for testing. |

The base `application.yml` holds production-safe defaults, `application-dev.yml` local conveniences, and
`application-prod.yml` what differs in production.

The application checks its Firestore settings at start-up and refuses to start with a combination that could reach
the wrong database: the emulator only runs with a `demo-` project ID (a prefix no real project can have), and in `dev`
and tests the emulator is required, so a laptop can never read or write real data.

### Local settings

For local development, put your own values in a `.env` file in the project folder. Start from the example, which lists
every setting with its default:

```bash
cp .env.example .env
```

`.env` is git-ignored, so it stays on your machine. Docker Compose reads it automatically, and so does the `dev`
profile, so one file covers `./gradlew bootRun`, `docker compose up` and `docker compose --profile full up`. A value
set as a real environment variable, such as `PORT=8090 ./gradlew bootRun`, still wins over the file.

| Setting | Default | What it changes |
| --- | --- | --- |
| `PORT` | `8080` | Port of the API when running `./gradlew bootRun` |
| `API_PORT` | `8080` | Host port of the API container (`--profile full`) |
| `FIRESTORE_EMULATOR_PORT` | `8085` | Host port of the Firestore emulator |
| `FIRESTORE_SAVE_INTERVAL_SECONDS` | `60` | How often the emulator saves local data |
| `FIRESTORE_KEEP_SAVES` | `3` | How many emulator saves are kept (at least 1) |
| `REDIS_URL` | not set | Use a different Redis instead of the one Docker Compose starts |

Write one `KEY=value` per line, without quotes and without comments on the same line: both Docker Compose and Spring
Boot read the file. Don't put secrets in it. Deployed containers never read `.env`; they get their settings from the
platform.

### Endpoints

| Path | Access | Purpose |
| --- | --- | --- |
| `/actuator/health` | Public | Overall health. Also `/actuator/health/liveness` and `/actuator/health/readiness` for container probes. |
| `/actuator/info` | Public | Version, Git commit and Java version |
| Everything else | Denied (`401`) | Opened explicitly as features are added |

### Errors

Every error response has the same body, `{ "error": "<message safe to show the user>" }`, with the status codes and
wording the web app already expects:

| Status | When | Message |
| --- | --- | --- |
| `400` | The body isn't valid JSON, or is missing | `Request body must be valid JSON.` |
| `400` | The body doesn't match what the endpoint expects | `Invalid request body.` |
| `400` | A query parameter is missing or invalid | `Invalid query parameters.` |
| `401` | Not signed in, or the session has ended | `Unauthorised.` |
| `403` | Signed in, but not allowed to do this | Specific reason, or `Forbidden.` |
| `404` | Doesn't exist, or belongs to someone else | Specific, such as `Document not found.`, or `Not found.` |
| `409` | Clashes with the current state | Specific, such as `This document is not shared with anyone.` |
| `503` | An external service couldn't be reached | `Couldn't reach an external service. Please try again in a moment.` |
| `500` | Anything unexpected | `Something went wrong. Please try again.` |

What exactly was wrong (which field failed validation, the underlying exception) is logged, never returned.

## Docker image

```bash
docker build -t dokiments-api .
```

The image runs as a non-root user with the `prod` profile and sizes the JVM heap from the container's memory limit.
The jar is split into layers, so a code change only rebuilds the small application layer.

On a network with a TLS-inspecting proxy, the build can't download dependencies until it trusts the proxy's root
certificate. Pass it as a build secret; it is only used during the build and isn't stored in the image:

```bash
docker build -t dokiments-api --secret id=extra_ca,src=/path/to/proxy-root-ca.pem .
```

With Docker Compose, pass the certificate's contents instead:

```bash
EXTRA_CA_PEM="$(cat /path/to/proxy-root-ca.pem)" docker compose --profile full up --build
```

## Architecture

The code is a modular monolith. Each direct sub-package of `com.dokiments` is a module:

- **Feature modules:** `auth`, `users`, `documents`, `sharing`, `clients`, `templates`, `notifications`, `feedback`,
  `promo`, `newsletter`.
- **`shared`:** small value types used by every module.
- **`platform`:** technical setup such as security, configuration and error handling.

```mermaid
flowchart TB
    features["Feature modules<br/>auth, users, documents, sharing, clients,<br/>templates, notifications, feedback, promo, newsletter"]
    features --> platform["platform<br/>security, configuration, error handling"]
    features --> shared["shared<br/>small value types"]
    platform --> shared
```

Inside a feature module, the code follows the hexagonal (ports and adapters) style. Dependencies point inwards, so the
business rules in `domain` don't depend on Spring, Firestore or HTTP:

```mermaid
flowchart LR
    request(["HTTP request"]) --> adapterIn
    subgraph module["Feature module, for example com.dokiments.sharing"]
        adapterIn["adapter.in<br/>controllers, event listeners"] --> application["application<br/>use cases and ports"]
        application --> domain["domain<br/>business rules"]
        adapterOut["adapter.out<br/>Firestore repositories, clients"] -- "implements the ports" --> application
    end
    adapterOut --> external[("Firestore and<br/>external services")]
```

Modules only use each other's public API: a module's base package. `HexagonalArchitectureTest` (ArchUnit) and
`ModularityTest` (Spring Modulith) enforce these rules on every build.

The full set of conventions is in [the agent instructions](.github/copilot-instructions.md). It is written
for AI coding agents (GitHub Copilot reads it directly, and `CLAUDE.md` imports it for Claude Code), and is just as
useful as a contributor guide.

## Contributing

- `main` is the only long-lived branch and is always deployable. Work on a short-lived branch named
  `<type>/<short-description>`, such as `feat/newsletter-subscribe`, and open a pull request.
- Pull requests are merged with a merge commit, so **every commit** lands on `main` and must follow
  [Conventional Commits](https://www.conventionalcommits.org/): `<type>(<scope>): <description>`, for example
  `feat(newsletter): add subscribe endpoint`. The pull request title follows the same format. CI checks both.
- Types: `feat`, `fix`, `perf`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `style`, `revert`. Mark breaking
  changes with `!`, as in `feat(sharing)!: ...`.
- The pull request template asks for a summary, how the change was tested, and its impact on the API contract,
  Firestore data, configuration and security. Those are the areas where a change can break the web app or a deployment.
- Clean up commits before merging, for example with `git commit --fixup <sha>` and
  `git rebase -i --autosquash main`. CI rejects `fixup!` and `squash!` commits.

```mermaid
flowchart LR
    branch["Branch from main<br/>type/description"] --> commits["Conventional<br/>commits"]
    commits --> pr["Pull request<br/>template filled in"]
    pr --> checks{"CI checks<br/>pass?"}
    checks -- "no" --> commits
    checks -- "yes" --> merge["Merge commit<br/>on main"]
```

## Continuous integration

Two GitHub Actions workflows check the code and the image:

```mermaid
flowchart LR
    pr["Pull request"] --> commits["Commit messages"]
    pr --> build["Build and test"]
    build --> docker["Docker image<br/>build and scan"]
    push["Push to main"] --> build2["Build and test"]
    build2 --> docker2["Docker image<br/>build and scan"]
    weekly["Every Monday"] --> scan["Security scan<br/>of main"]
```

**CI** (`ci.yml`) runs on every pull request and every push to `main`, and decides whether a change can be merged:

1. **Commit messages** (pull requests only): every commit in the pull request, and its title, follow Conventional
   Commits.
2. **Build and test:** `./gradlew build`, including the integration tests.
3. **Docker image:** the image builds, and [Trivy](https://trivy.dev/) scans it. The job fails if the image contains a
   HIGH or CRITICAL vulnerability that already has a fix, in the Ubuntu packages or in any library inside the jar.

**Security scan** (`security-scan.yml`) rebuilds the image from `main` every Monday and scans it the same way. New
vulnerabilities are published all the time, including for code that hasn't changed, and this catches them even when
nothing has been merged. It is separate from CI so that a newly published vulnerability doesn't look like broken code.
It can also be started by hand from the Actions tab.

Dependabot opens weekly pull requests for Gradle dependencies, GitHub Actions, the Dockerfile base images and the
Compose images.

### When the vulnerability scan fails

The log shows a table with the package, the vulnerability and the version that fixes it.

- **A library inside the jar:** if a Dependabot pull request doesn't already update it, raise the version in
  `gradle/libs.versions.toml`. For a library that arrives through another dependency, add it under "Security
  overrides" there and in `build.gradle.kts`, with the vulnerability ID.
- **An Ubuntu package:** the image installs Ubuntu's security updates when it is built, and CI refreshes them daily, so
  this usually resolves itself within a day. If it doesn't, Ubuntu hasn't released the fix to the archive yet.
- **A vulnerability that can't affect this service, or can't be fixed yet:** add its ID to `.trivyignore` with the
  reason and a review date.

To scan an image locally:

```bash
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:0.76.2 image \
  --severity HIGH,CRITICAL --ignore-unfixed dokiments-api
```

## Versioning and releases

Versions follow [SemVer](https://semver.org/) and come from Git tags: tagging a commit `v1.4.0` makes it version
`1.4.0`. No version number is stored in the build files. Untagged builds get a version such as `1.4.0-5-gabc1234`, or
`0.0.0-SNAPSHOT` before the first tag. The running version is shown by `/actuator/info`.
