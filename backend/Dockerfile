# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper first — cached unless pom.xml or wrapper changes
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Resolve all dependencies (cached layer)
RUN ./mvnw dependency:go-offline -q

# Copy source and package (skip tests — run them in CI, not at image build time)
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Non-root user for security
RUN addgroup -S busnow && adduser -S busnow -G busnow
USER busnow

COPY --from=builder /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# ── Runtime config overridable via environment variables ──────────────────────
# Database
ENV SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/busnow_db
ENV SPRING_DATASOURCE_USERNAME=busnow_user
ENV SPRING_DATASOURCE_PASSWORD=busnow_pass

# Redis
ENV SPRING_DATA_REDIS_HOST=redis
ENV SPRING_DATA_REDIS_PORT=6379

# Keycloak
ENV SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://keycloak:8080/realms/busnow-client

# ML service
ENV ML_SERVICE_BASE_URL=http://ml-service:8000

# JVM tuning (override as needed, e.g. -Xmx1g for more memory)
ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx512m -XX:+UseG1GC"

EXPOSE 8087

ENTRYPOINT ["java", "-jar", "app.jar"]
