# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY services/platform-api/pom.xml ./services/platform-api/pom.xml
COPY services/platform-api/src ./services/platform-api/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl services/platform-api -am package -Dmaven.test.skip=true

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S -G app -u 10001 app
WORKDIR /app
COPY --from=build /workspace/services/platform-api/target/application.jar /app/application.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/application.jar"]
