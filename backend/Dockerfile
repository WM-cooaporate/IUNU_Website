# --- Build stage -------------------------------------------------------------
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /app

# Dependencies resolve in their own layer, keyed only on pom.xml, so editing
# source does not re-download the world on every deploy.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# Not -o: go-offline does not reliably pre-fetch every plugin `package` needs,
# and a forced-offline build fails on the first one it missed. The layer cache
# above is the point; the network is just a fallback.
#
# Tests already ran in CI / locally against a real PostgreSQL; re-running them
# here would need a database this stage does not have.
COPY src ./src
RUN mvn -B -q -DskipTests package

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --create-home app
# Upload root on the Render persistent disk (render.yaml mounts it at
# /var/data). Created and owned by the runtime user so the app can write to it
# when the disk is mounted with the image's ownership, and so a run without a
# disk still has a writable directory for LocalImageStorage's startup check.
RUN mkdir -p /var/data/uploads && chown -R app:app /var/data
WORKDIR /app

COPY --from=build /app/target/real-estate-backend.jar app.jar

# Render's free instance is 512 MB / 0.1 CPU. MaxRAMPercentage keeps the heap
# inside the container limit instead of the JVM sizing off the host; SerialGC
# is the right collector below one full CPU (G1's parallel phases only add
# context switching here); the smaller stack buys back a few MB across Tomcat's
# thread pool.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
