# syntax=docker/dockerfile:1

# Build stage: compiles and packages the service. Tests and quality gates run
# in CI before an image is built, so they are skipped here.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies change less often than sources; resolving them first lets
# Docker reuse this layer across source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sh ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN sh ./mvnw -B -q package -DskipTests -Dspotless.check.skip=true -Djacoco.skip=true \
    && java -Djarmode=tools -jar target/core-banking-service-*.jar extract --layers --launcher --destination extracted

# Runtime stage: JRE only, no build tools or sources, running as an
# unprivileged user.
FROM eclipse-temurin:21-jre
RUN groupadd --system corebanking && useradd --system --gid corebanking --no-create-home corebanking
WORKDIR /app

# One layer per Spring Boot layer, from least to most frequently changed.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER corebanking
# 8080: API. 8081: probes and metrics, for the platform only.
EXPOSE 8080 8081

# Size the heap from the container's memory limit, and let the orchestrator
# restart the container rather than run on after an out-of-memory error.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
