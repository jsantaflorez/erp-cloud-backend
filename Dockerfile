# Backend image for Cloud Run (service, migration job and restore-check job).
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew \
 && ./gradlew --no-daemon bootJar -x test \
 && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar')" /app.jar

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
