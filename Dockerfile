FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY . .

RUN apk add --no-cache bash && ./gradlew bootJar

CMD ["java", "-jar", "build/libs/aggregator.jar"]
