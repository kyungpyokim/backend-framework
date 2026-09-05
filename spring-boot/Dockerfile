FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

ENV PORT=8080
ENV NODE_ID=spring-default
ENV REDIS_HOST=redis
ENV REDIS_PORT=6379

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
