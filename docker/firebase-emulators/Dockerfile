# syntax=docker/dockerfile:1

# The Firebase emulators (Auth, Firestore and the Emulator UI) for local development and the
# integration tests. Never deployed: the application talks to the real Firebase in production.
#
#   docker build -t dokiments-firebase-emulators:local docker/firebase-emulators
#
# compose.yaml and the Gradle build (buildFirebaseEmulatorsImage) both build it with that name.
#
# On a network with a TLS-inspecting proxy, hand the proxy's root CA certificate (PEM) to the
# build as a secret, as for the application image. It is only used while the Firebase CLI and
# the emulators are downloaded, and never ends up in an image layer:
#   --secret id=extra_ca,src=/path/to/proxy-root-ca.pem

# Node runs the Firebase CLI and the Auth emulator; the Firestore emulator is a Java program
# that needs Java 21 or newer, which this Debian release provides.
FROM node:24.22.3-trixie-slim

RUN apt-get update \
    && apt-get install --yes --no-install-recommends openjdk-21-jre-headless curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Saves of the local data (see start.sh). Created here owned by the unprivileged user, because a
# new Docker volume mounted on it takes over this folder's owner.
RUN mkdir -p /data && chown node:node /data

# The image's own unprivileged user.
USER node
WORKDIR /home/node/emulators

# The CLI version is pinned in package.json and package-lock.json, so Dependabot can keep it
# current and every machine runs the same emulators. .npmrc names the public registry.
COPY --chown=node:node package.json package-lock.json .npmrc ./

# Installs the CLI, then downloads the Firestore emulator and the Emulator UI into the image,
# so starting a container needs no network access. The Auth emulator is part of the CLI.
RUN --mount=type=secret,id=extra_ca,required=false,uid=1000 \
    if [ -s /run/secrets/extra_ca ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/extra_ca; fi \
    && npm ci --omit=dev --no-audit --no-fund \
    && npx firebase setup:emulators:firestore \
    && npx firebase setup:emulators:ui

COPY --chown=node:node start.sh ./

# The CLI asks about usage statistics and update checks otherwise.
ENV FIREBASE_CLI_EXPERIMENTS="" \
    NO_UPDATE_NOTIFIER=1 \
    CI=true

ENTRYPOINT ["bash", "./start.sh"]
