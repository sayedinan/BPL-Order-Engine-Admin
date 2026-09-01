# v0.3 backend — multi-stage production image.
#
# Build:    docker build -t bpl-backend:prod ./backend
# Run:      see RUNBOOK.md (uses docker-compose.prod.yml)
#
# Secrets (JWT_SECRET, JASYPT_ENCRYPTOR_PASSWORD, DB_*) are passed
# at runtime via env_file in the compose file. They are NEVER baked
# into the image.

# ---- Build stage ----
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /src

# Cache the dependency layer first.
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./gradlew
RUN chmod +x ./gradlew && ./gradlew --version >/dev/null

# Now copy the rest and build the bootJar.
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# ---- Run stage ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Non-root user. The image needs to be able to write to /tmp for
# Apache MINA SSHD scratch files; a read-only /tmp is fine because
# MINA uses an in-process buffer by default, but a writable /tmp is
# safer if future SSH options are enabled.
RUN groupadd -g 1000 bpl && useradd -u 1000 -g 1000 -d /app -s /usr/sbin/nologin bpl

# Pull just the jar from the build stage.
COPY --from=build /src/build/libs/*.jar /app/app.jar
RUN chown -R bpl:bpl /app

USER 1000
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
