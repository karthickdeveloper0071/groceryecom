# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Download dependencies first so they are cached until pom.xml changes. The cache mount
# keeps the local Maven repository between builds, so a code-only change does not
# re-download the world.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI; the image build only packages
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/groceryecom-*.jar extract --layers --launcher --destination extracted

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

LABEL org.opencontainers.image.title="GroceryEcom API" \
      org.opencontainers.image.description="Multi-vendor grocery marketplace backend" \
      org.opencontainers.image.source="https://github.com/karthickdeveloper0071/groceryecom"

RUN addgroup -S app && adduser -S -G app app

# Layers ordered from least to most frequently changed, for faster pulls
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

USER app
EXPOSE 8080

# Size the heap from the container memory limit; restart cleanly instead of limping
# after an OOM. Timestamps are UTC everywhere, including inside the container.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC" \
    TZ=UTC

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
