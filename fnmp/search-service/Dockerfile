FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY common/pom.xml common/
COPY article-service/pom.xml article-service/
COPY search-service/pom.xml search-service/
COPY ingestion-service/pom.xml ingestion-service/

RUN mvn dependency:go-offline -pl search-service -am -q

COPY common/ common/
COPY search-service/ search-service/
RUN mvn package -pl search-service -am -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S fnmp && adduser -S fnmp -G fnmp

COPY --from=builder /build/search-service/target/*.jar app.jar

EXPOSE 8081
USER fnmp

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]