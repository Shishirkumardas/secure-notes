FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget \
  && addgroup -S app \
  && adduser -S app -G app
COPY --from=build /src/target/secure-notes-api.jar /app/secure-notes-api.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/api/health
ENTRYPOINT ["java", "-jar", "/app/secure-notes-api.jar"]
