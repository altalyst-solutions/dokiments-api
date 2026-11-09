# Instructions for AI coding agents

This file is the single source of instructions for AI agents working in this repository. GitHub Copilot reads it
directly, and `CLAUDE.md` imports it for Claude Code. Edit this file, not a copy.

## About the project

`dokiments-api` is the backend API of [Dokiments](https://dokiments.com), a business-document workspace (templates,
documents, clients, document sharing and e-signing). The web app is a separate Next.js project that forwards `/api/*`
requests to this service.

- Java 25, Spring Boot 4.1 (Spring Framework 7), Gradle with the Kotlin DSL.
- Data lives in **Firestore**, accessed with the Firebase Admin SDK. Users sign in with **Firebase Auth**.
- Sessions are HttpOnly cookies backed by **Redis** (Spring Session).
- Runs as a **Docker** image. It must stay portable across container platforms.

The API contract is fixed by the existing web app: keep the same paths, request and response bodies, status codes and
the `{ "error": "..." }` error shape. Changing any of them breaks the UI.

Spring Boot 4 renamed and reorganised many modules, starters, packages and properties compared with Boot 3 (for
example `spring-boot-starter-webmvc`, `spring.session.data.redis.*`, Jackson 3 under `tools.jackson`). Don't rely on
memory for names: check the jars in the Gradle cache or the official docs before using one.

## Commands

| Task | Command |
| --- | --- |
| Fast tests (unit and architecture, no Docker) | `./gradlew test` |
| Integration tests (needs Docker) | `./gradlew integrationTest` |
| Everything: format check, compile checks, both test suites, coverage | `./gradlew build` |
| Fix formatting | `./gradlew spotlessApply` |
| Run locally (`dev` profile; starts Redis and the Firestore emulator from `compose.yaml`) | `./gradlew bootRun` |
| Run the API as a container with its services | `docker compose --profile full up --build` |
| Build the image | `docker build -t dokiments-api .` |
| IntelliJ inspections (needs IntelliJ IDEA) | `scripts/inspect.sh` |

Before saying a change is done, run `./gradlew spotlessApply`, then `./gradlew build`, then `scripts/inspect.sh` when
IntelliJ IDEA is available, and fix every problem they report. The build treats every compiler and Error Prone warning
as an error, so don't silence one to make it pass: fix the code. Suppress a finding (`@SuppressWarnings`) only when it
is a false positive, with a comment explaining why, for example a field that a framework reads by reflection.

## Architecture

The code is a modular monolith in hexagonal (ports and adapters) style, organised by feature.

Each direct sub-package of `com.dokiments` is a module, verified by Spring Modulith:

- Feature modules: `auth`, `users`, `documents`, `sharing`, `clients`, `templates`, `notifications`, `feedback`,
  `promo`, `newsletter`.
- `shared`: small value types every module may use (for example `UserId`, `Email`). Keep it minimal.
- `platform`: technical setup only (security, configuration, error handling, Firestore, email). It must never depend
  on a feature module.

Packages inside a feature module `com.dokiments.<module>`:

- `domain`: pure Java models, business rules, domain events and exceptions.
- `application.port.in`: use cases (interfaces) and their commands.
- `application.port.out`: what the use cases need from outside, such as repositories and senders.
- `application.service`: the use case implementations.
- `adapter.in`: web controllers and their DTOs, event listeners.
- `adapter.out`: Firestore repositories and entities, clients for external services.

Rules, enforced by `HexagonalArchitectureTest` (ArchUnit) and `ModularityTest` (Spring Modulith):

- Dependencies point inwards: `adapter` uses `application`, which uses `domain`.
- `domain` has no Spring, Jakarta, Google/Firebase or Jackson imports, so business rules are testable without a
  framework.
- `application` doesn't use HTTP, servlets, JSON or Firestore types.
- Inbound and outbound adapters never call each other directly.
- A module's base package is its public API, and its sub-packages are internal. Other modules use only base-package
  types, a `@NamedInterface`, or application events. No cycles between modules.
- Keep three kinds of model apart: web DTOs (the API contract), domain models (the rules) and Firestore entities (the
  stored shape). Map between them instead of reusing one class for several roles.
- Side effects such as sending email happen in listeners reacting to domain events, not inline in a use case.
- A new module needs a `package-info.java` with `@NullMarked` and `@ApplicationModule(displayName = "...")`, and must
  be added to `FEATURE_MODULES` in `HexagonalArchitectureTest`.

## Code style

- Formatting is palantir-java-format (120 columns) through Spotless, and ktlint (IntelliJ style) for `*.gradle.kts`.
  Don't hand-format: run `./gradlew spotlessApply`.
- Editor setup is shared in `.vscode/extensions.json`, `.vscode/settings.json`, `.idea/externalDependencies.xml`
  and `.idea/palantir-java-format.xml`. When recommending an extension or plugin, add it there and check that it
  exists and is maintained.
- Use **records** for DTOs, commands, value objects and events. **Lombok is not used** and an architecture test
  rejects it.
- Constructor injection only, never `@Autowired` on fields. A single constructor needs no annotation.
- Null safety: everything under `com.dokiments` is non-null by default (NullAway in JSpecify mode fails the build).
  Mark anything that can be null with `org.jspecify.annotations.Nullable`.
- Blocking code is fine and expected: the app runs on virtual threads. Don't introduce WebFlux or reactive types.
- Log with SLF4J. Never use `System.out`, `System.err` or `java.util.logging`.
- Never log tokens, passwords, session contents or other secrets.
- Configuration comes from environment variables, bound with `@ConfigurationProperties` classes. Never hard-code
  URLs, keys or environment-specific values. Don't use platform-specific SDKs (for example Spring Cloud GCP); the
  service must run on any container platform.
- Error Prone runs on every compile. Fix its findings rather than suppressing them.

## Errors

- For an expected failure, throw one of the exceptions in `com.dokiments.shared.error` (`InvalidRequestException`,
  `UnauthenticatedException`, `ForbiddenException`, `NotFoundException`, `ConflictException`,
  `UpstreamUnavailableException`). `platform.web.ApiExceptionHandler` turns them into the status code and the
  `{ "error": "..." }` body. Never build error responses in controllers.
- The exception's message is sent to the client as is, so it must be written for users and never contain internal
  details. Put diagnostic information in the `cause`, which is logged only.
- For a more specific failure in a module, extend the matching type (for example a `DocumentLockedException` extending
  `ConflictException`). Don't add new types to the sealed `DokimentsException` hierarchy without also mapping them in
  `ApiExceptionHandler`; the compiler enforces this.
- Answer "exists but belongs to someone else" exactly like "doesn't exist" (`NotFoundException`), so IDs can't be
  probed.
- An unreachable external service is `UpstreamUnavailableException` (`503`), never `401` or `400`.
- Keep the generic messages in `platform.web.ErrorMessages` worded exactly as they are: the web app shows them.

## Comments

- Comments explain **why**, in full sentences, with enough detail for someone new to the code. Don't restate what the
  code obviously does.
- No decorative formatting: no separator or banner lines (`// --- Section ---`, `# ------`), no em dashes, no
  arrow-aligned lists. A section heading is a plain one-line comment, such as `// Web / API`.
- Only refer to things that exist in this repository. Don't mention plans, phases, tickets or decisions that live
  elsewhere, or details of anyone's own machine or network setup. Describe such things generically (for example "a
  TLS-inspecting proxy").

## Security

- Security is deny-by-default (`platform/security/SecurityConfig`). Only `/actuator/health/**` and `/actuator/info`
  are public. Open each new endpoint explicitly and deliberately.
- The request cache is disabled on purpose. Re-enabling it creates a Redis session for every rejected anonymous
  request.
- Error responses must never include stack traces, exception names or internal messages.
- Upstream outages (Firebase, Google, Resend unreachable) answer `503`, never `401`.

## Configuration and profiles

- `application.yml` holds production-safe defaults. Running without a profile must never be less safe than `prod`.
- `application-dev.yml` holds local conveniences and is activated by `./gradlew bootRun`.
- `application-prod.yml` holds only what differs in production. The Docker image sets `SPRING_PROFILES_ACTIVE=prod`.
- `src/test/resources/application-test.yml` is used by integration tests.
- Required settings, such as `REDIS_URL`, have no default in the base configuration so a misconfigured deployment
  fails at startup.
- Application settings are bound into `platform.config.DokimentsProperties` (prefix `dokiments`), with each
  environment variable and its default mapped in `application.yml`. Read settings from there, not with `@Value`.
- Local development settings live in a git-ignored `.env`, read by Docker Compose and by the `dev` profile only.
  Every setting has its default in `.env.example`. When adding one, add it to `.env.example` and to the "Local settings"
  table in the README. Never read `.env` outside the `dev` profile, and never put secrets in it.

## Tests

- Unit tests are plain JUnit 5 and AssertJ. Domain and application tests use in-memory fakes of the ports rather than
  mocks where practical.
- Integration tests use `@IntegrationTest` (full Spring context, `test` profile, Testcontainers). Tests that don't
  start Spring use `@Tag(IntegrationTest.TAG)`. Both run only in `./gradlew integrationTest`.
- Container images for tests are pinned in `support/TestImages`, and must match `compose.yaml`.
- Firestore tests use the emulator with a `demo-` project ID and must assert the client points at the emulator, never
  at `firestore.googleapis.com`.
- Every behaviour change comes with tests. For a fix, write a test that fails without it.

## Dependencies

- All versions live in `gradle/libs.versions.toml`. Libraries managed by the Spring Boot or Spring Modulith BOM are
  declared without a version.
- Add a dependency only when code uses it, and check the latest stable version on Maven Central rather than guessing.
  Don't use milestones or release candidates.
- When a vulnerable version arrives through another dependency or a BOM, override it under "Security overrides" in
  `gradle/libs.versions.toml` and `build.gradle.kts`, with the vulnerability ID and a note to remove it once the BOM
  catches up. Add to `.trivyignore` only with a reason and a review date.

## Git and versioning

- Agents don't commit, push or open pull requests unless asked to.
- Trunk-based: `main` is the only long-lived branch and is always deployable. Work happens on short-lived branches
  merged through pull requests with a merge commit.
- Branch names: `<type>/<short-description>`, for example `feat/newsletter-subscribe`.
- **Every commit** follows Conventional Commits, because merge commits keep every branch commit on `main`:
  `<type>(<scope>): <description>`, imperative and lower case, for example `feat(newsletter): add subscribe endpoint`.
  - Types: `feat`, `fix`, `perf`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `style`.
  - Scopes: a module name (`auth`, `sharing`, ...) or `platform`, `architecture`, `build`, `ci`, `docker`, `deps`.
  - Mark breaking changes with `!`, for example `feat(sharing)!: ...`.
- The pull request title follows the same format and becomes the merge commit title.
- Pull request descriptions follow `.github/pull_request_template.md`. Fill in every section, including the impact on
  the API contract, Firestore data, configuration and security, and remove the options that don't apply.
- The version comes from Git tags (`vX.Y.Z`, SemVer). Never write a version number into the build files.
