# Multi-stage build for TPE Position Tracker

# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/app.jar /app/app.jar

EXPOSE 20121 20122

ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-Xms256m", \
    "-Xmx512m", \
    "-Daeron.dir=/tmp/aeron", \
    "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED", \
    "-jar", \
    "app.jar"]
