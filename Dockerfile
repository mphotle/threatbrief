# Stage 1: Build Java Application using Temurin JDK 25
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

# Copy build wrappers and config first to leverage Docker layer caching
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

# Copy source code and build executable JAR
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Minimal Production JRE 25 Runtime
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Create non-root system user for container security
RUN addgroup -S threatgroup && adduser -S threatuser -G threatgroup
USER threatuser

# Copy compiled JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Dynamic port assignment
ENV PORT=8080
EXPOSE 8080

# Restrict JVM RAM usage so it stays comfortably within EC2 t2.micro limits
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=65.0", "-Dserver.port=${PORT}", "-jar", "app.jar"]
