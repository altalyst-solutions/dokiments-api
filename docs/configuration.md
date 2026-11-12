# Configuration

All configuration comes from environment variables, so the same image runs on any container platform. Locally, a
`.env` file covers what you'd want to change (see [Local settings](#local-settings)).

## Environment variables

| Variable | Required | Description |
| --- | --- | --- |
| `REDIS_URL` | Yes, outside `dev` | Redis connection, for example `redis://host:6379` or `rediss://user:password@host:6379` for TLS. The application refuses to start without it. In `dev` it defaults to the Compose service. |
| `PORT` | No | HTTP port. Defaults to `8080`. |
| `FIREBASE_PROJECT_ID` | Yes, outside `dev` | Firebase project holding the Firestore database. In `dev` and tests it is `demo-dokiments` on the emulator. |
| `FIRESTORE_EMULATOR_HOST` | No | `host:port` of a Firestore emulator to use instead of the real Firestore. Only allowed with a `demo-` project ID. |
| `GOOGLE_APPLICATION_CREDENTIALS` | Outside Google Cloud | Path to service account credentials for Firestore. On Cloud Run, the attached service account is used instead, so no file is needed. |
| `RESEND_API_KEY` | No | Resend API key for sending email: a send-only key limited to the sending domain. Without it, nothing is sent. Locally, set it in your shell for one run, never in `.env`. |
| `EMAIL_FROM` | No | Sender, as `Name <address>` on the domain verified in Resend. Defaults to `Dokiments <notifications@mail.dokiments.com>`. |
| `EMAIL_REPLY_TO` | No | Where replies go, such as a support inbox. The sending domain can't receive mail. |
| `EMAIL_ALLOW_LIST` | No | Comma-separated addresses. When set, only they receive email: for staging. Leave it unset in production. |
| `SITE_URL` | No | The web app's address, for links in emails. Defaults to `https://dokiments.com`, and to `http://localhost:3000` in `dev`. |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | No | OpenTelemetry collector for traces (and metrics, see below), for example `http://collector:4318`. Without it, nothing is exported. See [Observability](#observability). |
| `OTEL_METRICS_EXPORTER` | No | `otlp` to export metrics too. Off by default. |
| `OTEL_TRACES_SAMPLER_ARG` | No | Share of requests whose trace is kept, from `0` to `1`. Defaults to `0.1`, and `1` in `dev`. |
| `SPRING_PROFILES_ACTIVE` | No | `prod` in the Docker image, `dev` for `bootRun`. |
| `DOCUMENT_SHARING_ENABLED` | No | `true` turns on sharing documents for signature. Off by default; while off, the sharing endpoints answer `404`. |
| `TIER_LOCKS_ENABLED` | No | `false` stops enforcing plan limits. On by default; only switch it off for testing. |

## Profiles

| Profile | Used by | File |
| --- | --- | --- |
| none | Base settings, always loaded | `src/main/resources/application.yml`: production-safe defaults |
| `dev` | `./gradlew bootRun` | `application-dev.yml`: local conveniences (readable logs, local services, the emulator) |
| `prod` | The Docker image | `application-prod.yml`: only what differs in production |
| `test` | Integration tests | `src/test/resources/application-test.yml` |

Running without a profile is never less safe than `prod`: anything that loosens a setting belongs in `dev`.

## Start-up checks

The application checks its Firestore settings at start-up and refuses to start with a combination that could reach
the wrong database:

- the emulator only runs with a `demo-` project ID, a prefix no real project can have;
- a `demo-` project ID needs the emulator;
- in `dev` and tests the emulator is required, so a laptop can never read or write real data.

Required settings, such as `REDIS_URL` and `FIREBASE_PROJECT_ID`, have no default outside `dev`, so a misconfigured
deployment stops at start-up with a message naming the missing setting.

## Calls to external services

Every call to an external service (Firebase Auth, Google sign-in, Resend) follows the same rules, set in
`platform.http`:

- A connection must be set up within 2 seconds. One attempt may take 3 seconds for a `GET` or `HEAD` and 5 seconds for
  other requests, including reading the whole response. A call never takes more than 8 seconds in total.
- A request is only sent again when that is certainly safe: when it never left the application (the address couldn't
  be resolved, the connection was refused or couldn't be set up in time), up to 3 attempts, or once when a `GET` or
  `HEAD` timed out. A request that may have reached the service, such as a timed-out `POST`, is never sent twice.
  The waits between attempts start at 150 ms and triple each time, give or take a random quarter.
- A service that can't be reached, is failing (`5xx`) or asks us to slow down (`429`) is answered with `503`, so the
  web app knows to try again.

The defaults rarely need changing, and aren't in `.env.example`. If they must, they are settings under
`dokiments.http` (for example `DOKIMENTS_HTTP_TOTAL_TIMEOUT=10s`), described in `HttpClientProperties`.

## Email

Email is sent through [Resend](https://resend.com). Who receives it depends on the settings:

| Mode | When | Who receives email |
| --- | --- | --- |
| Off | No `RESEND_API_KEY` | Nobody. The application works normally, and logs that the email wasn't sent. |
| Restricted | A key and `EMAIL_ALLOW_LIST`, or a key in `dev` and tests | Only the addresses on `EMAIL_ALLOW_LIST`. In `dev` and tests an empty list means nobody, so trying something out can never email a real user. |
| On | A key, no allow list, outside `dev` and tests | Everyone. Production only. |

The start-up log says which mode applies. Logs never show full addresses (`j***@example.com`). Resend's test
address `delivered@resend.dev` is always safe to send to.

Locally, without a key, the text of every email that would have been sent is written to the log, so you can check
it. To send real emails to yourself, add your address to `EMAIL_ALLOW_LIST` in `.env` and run with the key:

```bash
RESEND_API_KEY=re_... ./gradlew bootRun
```

Use the key for staging and local use, never the production one. See [Testing and quality](testing-and-quality.md)
for how emails are tested, and `src/main/resources/email/` for the templates.

## Observability

Every request gets a trace. Its ID is in every log line written while handling it (`trace.id` in the JSON logs) and
in the `X-Trace-Id` response header, so a problem someone reports from their browser leads straight to the request's
logs. A request that arrives with a W3C `traceparent` header, for example from the platform's load balancer,
continues that trace. Firestore calls, Redis commands and calls to external services appear inside the request's
trace. Health checks (`/actuator/**`) aren't traced.

Traces and metrics are exported with OTLP, the OpenTelemetry protocol, so they work with any backend that has an
OpenTelemetry collector or accepts OTLP directly. They are configured with the standard OpenTelemetry variables:

| Variable | Effect |
| --- | --- |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Where to send them, such as a collector running next to the container. Traces are exported once this is set. |
| `OTEL_METRICS_EXPORTER=otlp` | Exports metrics too, every minute. Off by default, because without a collector each attempt would fail and log a warning. |
| `OTEL_TRACES_SAMPLER_ARG` | The share of traces kept: `0.1` (one in ten) by default. The trace ID exists for every request either way. |
| `OTEL_SERVICE_NAME`, `OTEL_RESOURCE_ATTRIBUTES` | How the service appears in the backend. Defaults to `dokiments-api`. |
| `OTEL_SDK_DISABLED=true` | Switches OpenTelemetry off completely. |

These must be real environment variables: Spring Boot doesn't read them from `.env`.

Besides Spring Boot's own metrics (requests, JVM, Redis, HTTP clients), the application records `dokiments.emails`:
emails by `template` and `result` (`sent`, `disabled`, `blocked`, `invalid_recipient` or `provider_error`).

To view traces locally, see [Viewing traces](local-development.md#viewing-traces).

## Local settings

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
| `SITE_URL` | `http://localhost:3000` | The web app, for links in emails |
| `EMAIL_ALLOW_LIST` | not set | The only addresses that receive email locally (see [Email](#email)) |
| `JAEGER_UI_PORT` | `16686` | Host port of the Jaeger trace viewer |
| `OTLP_PORT` | `4318` | Host port Jaeger receives traces on |

Write one `KEY=value` per line, without quotes and without comments on the same line: both Docker Compose and Spring
Boot read the file. Don't put secrets in it. Deployed containers never read `.env`; they get their settings from the
platform.

When adding a setting, add it to `.env.example` and to the table above.

## Endpoints

| Path | Access | Purpose |
| --- | --- | --- |
| `/actuator/health` | Public | Overall health, including Redis and Firestore. Also `/actuator/health/liveness` and `/actuator/health/readiness` for container probes. |
| `/actuator/info` | Public | Version, Git commit and Java version |
| `POST /api/newsletter` | Public | Newsletter sign-up from the website |
| `/swagger-ui.html`, `/v3/api-docs` | `dev` only | Swagger UI and the API description. Elsewhere they don't exist and answer `401`. |
| Everything else | Denied (`401`) | Opened explicitly as features are added |

## Errors

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
