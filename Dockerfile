# syntax=docker/dockerfile:1.7
# ---------------------------------------------------------------------------
# Backend image: multi-stage build -> layered Spring Boot jar on a slim JRE.
#   docker build -t examprep-backend .
# ---------------------------------------------------------------------------

# ---------- build stage ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolve dependencies first so this layer is cached until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/exam-platform-*.jar extract --layers --launcher --destination extracted

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

# Least-frequently-changing layers first for better image-layer reuse.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app
EXPOSE 8080

# Container-aware heap sizing; the rest is tuned via JAVA_OPTS at deploy time.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
