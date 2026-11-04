# =============================================================================
# Multi-stage Dockerfile for Public Land Map API Server (Ktor + Netty)
# Stage 1: Build with Gradle
# Stage 2: Run with minimal JRE
# =============================================================================

# --- Build Stage ---
FROM gradle:8.7-jdk17 AS build

WORKDIR /app

# Copy build files first for dependency caching
COPY build.gradle.kts settings.gradle.kts gradle.properties ./

# Download dependencies (this layer is cached unless build files change)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src/ src/

# Build the fat JAR
RUN gradle buildFatJar --no-daemon

# --- Runtime Stage ---
FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy the fat JAR from build stage
COPY --from=build /app/build/libs/public-land-map-api.jar /app/public-land-map-api.jar

# Create tiles directory for offline downloads
RUN mkdir -p /tiles/offline && chown -R appuser:appgroup /tiles

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD wget -q --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/public-land-map-api.jar"]
