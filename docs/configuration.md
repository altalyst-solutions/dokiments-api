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

Write one `KEY=value` per line, without quotes and without comments on the same line: both Docker Compose and Spring
Boot read the file. Don't put secrets in it. Deployed containers never read `.env`; they get their settings from the
platform.

When adding a setting, add it to `.env.example` and to the table above.

## Endpoints

| Path | Access | Purpose |
| --- | --- | --- |
| `/actuator/health` | Public | Overall health, including Redis and Firestore. Also `/actuator/health/liveness` and `/actuator/health/readiness` for container probes. |
| `/actuator/info` | Public | Version, Git commit and Java version |
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
