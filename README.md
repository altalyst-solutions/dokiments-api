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

[Getting started](docs/getting-started.md) ·
[IDE setup](docs/ide-setup.md) ·
[Configuration](docs/configuration.md) ·
[Architecture](docs/architecture.md) ·
[Contributing](docs/contributing.md)

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
| External services | Spring `RestClient` on the JDK HTTP client, with shared timeouts and safe retries |
| Architecture | Modular monolith in hexagonal style, checked by Spring Modulith and ArchUnit |
| Code quality | Spotless (palantir-java-format), Error Prone, NullAway with JSpecify, JaCoCo |
| Tests | JUnit, AssertJ, Testcontainers (Redis and the Firestore emulator) |
| Runtime | Docker image (Eclipse Temurin JRE), configured through environment variables |

## Quick start

You need JDK 25, Docker and Git. No accounts or keys: everything runs against local containers and the Firestore
emulator.

```bash
git clone https://github.com/altalyst-solutions/dokiments-api.git
cd dokiments-api
./gradlew build      # first run takes a few minutes
./gradlew bootRun    # starts Redis and the Firestore emulator too
curl localhost:8080/actuator/health
```

New to the project? [Getting started](docs/getting-started.md) walks through installing everything on macOS, Linux
and Windows (WSL 2).

## Documentation

| Guide | What it covers |
| --- | --- |
| [Getting started](docs/getting-started.md) | Installing the tools on macOS, Linux or Windows (WSL 2), first build, troubleshooting |
| [IDE setup](docs/ide-setup.md) | IntelliJ IDEA and VS Code, including with WSL, and seeing problems in the editor |
| [Local development](docs/local-development.md) | What `bootRun` starts, local data, running the API as a container, the Docker image |
| [Configuration](docs/configuration.md) | Environment variables, profiles, `.env` local settings, endpoints and error responses |
| [Testing and quality](docs/testing-and-quality.md) | Tests, the build's checks, IntelliJ inspections, CI and vulnerability scans |
| [Architecture](docs/architecture.md) | Modules, the hexagonal layout and how the rules are enforced |
| [Contributing](docs/contributing.md) | Branches, Conventional Commits, pull requests, working with AI agents, versioning |

The conventions for writing code here are in [the agent instructions](.github/copilot-instructions.md). They are
written for AI coding agents (GitHub Copilot reads them directly, and `CLAUDE.md` imports them for Claude Code), and
are just as useful for developers.

## Common commands

| Task | Command |
| --- | --- |
| Run locally | `./gradlew bootRun` |
| Fast tests (no Docker) | `./gradlew test` |
| Everything CI checks | `./gradlew build` |
| Fix formatting | `./gradlew spotlessApply` |
| Run the API as a container with its services | `docker compose --profile full up --build` |
| IntelliJ inspections from the command line | `scripts/inspect.sh` |
