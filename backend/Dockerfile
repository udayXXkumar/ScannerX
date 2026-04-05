# Multi-stage build for ScannerX backend
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -DdownloadSources -DdownloadJavadoc || true

# Copy source code
COPY src ./src
COPY .mvn ./.mvn

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Install runtime dependencies for scanning tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    httpx \
    whatweb \
    nuclei \
    ffuf \
    nikto \
    sqlmap \
    dnsmasq \
    wget \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

# Copy the built JAR from builder
COPY --from=builder /build/target/app-*.jar app.jar

# Create data directory for H2 database
RUN mkdir -p /app/data

# Health check
HEALTHCHECK --interval=10s --timeout=5s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

# Default environment variables (can be overridden)
ENV SPRING_PROFILES_ACTIVE=local
ENV APP_QUEUE_MODE=local
ENV APP_JWT_SECRET=CHANGE_THIS_TO_A_LONG_RANDOM_SECRET
ENV PORT=8080

EXPOSE 8080

# Entrypoint script
COPY docker-entrypoint.sh /
RUN chmod +x /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["java", "-Xmx2g", "-jar", "app.jar"]
