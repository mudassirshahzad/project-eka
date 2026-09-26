# syntax=docker/dockerfile:1
#
# v0.6.1 (ADR EX10) — closes the "no Dockerfile for the application itself" gap from the
# post-Phase-5 audit. docker-compose.yml already containerizes Postgres/Weaviate/Ollama; this
# adds the one piece it didn't: the Spring Boot application. Deliberately simple — a two-stage
# build, no orchestration changes, no docker-compose wiring (out of this milestone's scope).

# ---- Build stage --------------------------------------------------------------------------
FROM gradle:8.12-jdk21 AS build
WORKDIR /workspace

COPY build.gradle settings.gradle ./
COPY src ./src

# Tests run in CI (.github/workflows/build.yml), not here — an image build isn't the place to
# also stand up Testcontainers-backed Postgres, and duplicating that verification here would
# only slow every image build without adding coverage CI doesn't already provide.
#
# The build stage normalises the jar to a fixed path. build.gradle gives the executable
# jar the `-boot` classifier so the plain jar can be the published *library* artifact,
# which means `build/libs/` can legitimately hold two jars. Selecting by the `-boot`
# suffix here (rather than the `*.jar` glob this used to use) keeps the COPY below
# unambiguous, and resolving the name inside the build stage avoids restating the
# project version in this file — `project.version` stays the single source of truth
# (ADR EX03).
RUN gradle clean bootJar --no-daemon -x test \
 && cp build/libs/*-boot.jar /workspace/app.jar

# ---- Runtime stage -------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/app.jar app.jar
RUN chown app:app app.jar
USER app

EXPOSE 8080

# Liveness/readiness probes: point your orchestrator at /actuator/health/liveness and
# /actuator/health/readiness (P05.4, ADR OB01/OB05) rather than duplicating that logic in a
# Docker-level HEALTHCHECK — this image doesn't bundle curl/wget, and those two paths are already
# the source of truth for "is this instance up" vs. "can this instance serve traffic."
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
