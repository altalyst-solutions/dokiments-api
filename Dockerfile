# syntax=docker/dockerfile:1

# Dokiments API container image.
#
#   docker build -t dokiments-api .
#
# Optional build arguments. CI passes them, because there is no .git directory inside
# the build context to read them from:
#   --build-arg APP_VERSION=1.4.0 --build-arg GIT_COMMIT=$(git rev-parse --short=12 HEAD)
#
# On a network with a TLS-inspecting proxy, hand the proxy's root CA certificate (PEM) to
# the build stage as a secret. It is only used while Gradle downloads dependencies and
# never ends up in an image layer:
#   --secret id=extra_ca,src=/path/to/proxy-root-ca.pem

# The JDK (build) and JRE (runtime) tags are written out in full rather than through a
# shared ARG, because Dependabot can only update tags it can read directly. Keep both on
# the same Java version.

# Stage 1, build: compile and package the Spring Boot jar.
FROM eclipse-temurin:26.0.2.1_1-jdk-noble AS build
WORKDIR /workspace

# Gradle wrapper and build scripts first: this layer, and the dependency cache below,
# are reused until the build definition changes.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY src/main ./src/main

ARG APP_VERSION=""
ARG GIT_COMMIT=""

# The Gradle cache mount keeps downloaded dependencies between builds on the same machine.
# The optional `extra_ca` secret is added to a throwaway truststore used by this step only.
# Tests don't run here: CI runs them before building the image.
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=secret,id=extra_ca,required=false \
    set -eu; \
    if [ -s /run/secrets/extra_ca ]; then \
        cp "$JAVA_HOME/lib/security/cacerts" /tmp/truststore; \
        keytool -importcert -noprompt -alias extra-ca -file /run/secrets/extra_ca \
            -keystore /tmp/truststore -storepass changeit >/dev/null; \
        export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/tmp/truststore -Djavax.net.ssl.trustStorePassword=changeit"; \
    fi; \
    ./gradlew bootJar --no-daemon --no-configuration-cache --console=plain \
        -PappVersion="${APP_VERSION}" -PgitCommit="${GIT_COMMIT}"; \
    cp build/libs/dokiments-api-*.jar /workspace/app.jar

# Split the jar into layers ordered from least to most frequently changing (dependencies,
# loader, snapshot dependencies, application), so a code change only rebuilds and
# re-pushes the small application layer.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination /workspace/extracted

# Stage 2, runtime: JRE only, non-root, no build tools.
FROM eclipse-temurin:26.0.2.1_1-jre-noble AS runtime

ARG APP_VERSION=""
ARG GIT_COMMIT=""
LABEL org.opencontainers.image.title="dokiments-api" \
      org.opencontainers.image.description="Dokiments backend API" \
      org.opencontainers.image.source="https://github.com/altalyst-solutions/dokiments-api" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${GIT_COMMIT}"

# Install the Ubuntu security updates published since the base image was built. The
# Temurin images are only rebuilt every few weeks, so without this the image ships OS
# packages with known, already-fixed vulnerabilities. Only the runtime stage needs it: the
# build stage is never shipped. The package lists are removed afterwards to keep the image
# small.
#
# Docker caches this step like any other, so it would keep the updates from the first build
# forever. CI passes today's date as SECURITY_UPDATES_DATE: a new value invalidates the
# cache, so the updates are fetched again at most once a day. Local builds leave it empty.
ARG SECURITY_UPDATES_DATE=""
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER app

# Production by default. Platforms inject everything else (REDIS_URL, ...) as environment
# variables.
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080
EXPOSE 8080

# Used by Docker and Compose. Cloud Run ignores HEALTHCHECK and probes /actuator/health/*
# itself. It uses plain bash (/dev/tcp) because the JRE image has no curl, and adding a
# package only for this would add attack surface.
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${PORT} && printf 'GET /actuator/health/liveness HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && grep -q '\"UP\"' <&3"]

# Exec form: the JVM is PID 1 and receives SIGTERM directly, so graceful shutdown works.
# MaxRAMPercentage sizes the heap from the container's memory limit, not the host's.
# ExitOnOutOfMemoryError crashes the JVM so the platform restarts it, instead of letting
# it limp on. Extra JVM flags can be passed at runtime with JAVA_TOOL_OPTIONS.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
