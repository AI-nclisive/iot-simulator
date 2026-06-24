# Multi-stage build for the backend app. Workers are launched by the supervisor
# as child processes inside the same image (built by the full Gradle build).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :app:bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
