# syntax=docker/dockerfile:1

FROM gradle:8.10.2-jdk17-jammy AS builder

WORKDIR /workspace

COPY --chown=gradle:gradle gradle gradle
COPY --chown=gradle:gradle gradlew build.gradle settings.gradle ./
COPY --chown=gradle:gradle docs/openapi.yaml docs/openapi.yaml
COPY --chown=gradle:gradle src src

RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system spring \
    && useradd --system --gid spring --no-create-home spring

WORKDIR /app

COPY --from=builder --chown=spring:spring /workspace/build/libs/*.jar app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
