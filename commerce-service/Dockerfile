FROM maven:3.9.10-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl commerce-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update     && apt-get install -y --no-install-recommends curl     && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/commerce-service/target/commerce-service-0.1.0-SNAPSHOT.jar /app/app.jar
COPY infrastructure/docker/service-entrypoint.sh /app/service-entrypoint.sh
EXPOSE 8084
ENTRYPOINT ["/app/service-entrypoint.sh"]
