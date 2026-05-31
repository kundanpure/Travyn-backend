# ═══════════════════════════════════════════════════════════════
#  Travyn Backend — Multi-Stage Docker Build
#  Stage 1: Maven build → fat JAR
#  Stage 2: Minimal JRE runtime
# ═══════════════════════════════════════════════════════════════

# ── Stage 1: Build ──────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy POM first for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Runtime ────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the application port
EXPOSE 8080

# JVM tuning for free-tier containers (512MB RAM, 0.1 vCPU)
# TieredStopAtLevel=1 → skips expensive JIT, much faster startup
# UseSerialGC → better than G1GC on single low-CPU containers
# security.egd → prevents Tomcat from blocking on /dev/random
ENV JAVA_OPTS="-Xms128m -Xmx384m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
