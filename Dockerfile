# Multi-stage build. Stage 1 runs the full Gradle build (which also downloads a pinned Node and
# builds the Angular UI into the jar); stage 2 ships only a JRE and the fat jar.
#
# Built on the target machine (an Oracle Ampere A1 instance is arm64), so no cross-compilation:
# both Temurin and the Node distribution the Gradle node plugin fetches are multi-arch.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

# The Node distribution the Gradle node plugin downloads is dynamically linked against libatomic,
# which the Temurin image does not ship. Without this, npm dies with
# "libatomic.so.1: cannot open shared object file" on arm64.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libatomic1 \
    && rm -rf /var/lib/apt/lists/*

# Wrapper + build scripts first: this layer only busts when the build definition changes, so
# dependency resolution and the Node download stay cached across source-only rebuilds.
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet || true

# npm deps next, for the same reason: package-lock.json changes far less often than app source.
COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN ./gradlew --no-daemon npmInstall

COPY frontend ./frontend
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test && cp build/libs/*.jar /src/app.jar

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Don't run the app as root.
RUN useradd --system --uid 1001 --create-home wealthstack
USER wealthstack

COPY --from=build --chown=wealthstack:wealthstack /src/app.jar /app/app.jar

EXPOSE 8088

# MaxRAMPercentage rather than a fixed -Xmx so the heap tracks whatever the container limit is.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
