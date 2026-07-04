# Two-stage build: Gradle's `application` plugin installDist produces a runnable dir
# (start script + jars, no fat-jar/shadow plugin needed), copied into a slim JRE runtime.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew && ./gradlew :server:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/server/build/install/server ./
EXPOSE 8080
ENTRYPOINT ["./bin/server"]
