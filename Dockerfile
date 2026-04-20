FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests


FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080
ENV FILE_UPLOAD_DIR=/app/files

COPY --from=build /workspace/target/facility-management-1.0.0.jar /app/app.jar
COPY files/ /app/files/

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
