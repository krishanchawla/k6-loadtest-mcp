# Standalone Spring Boot JAR with its own embedded Tomcat (11.x, matches
# this app's Spring Boot 4.1.0 baseline — Tomcat 9 can't run it at all,
# wrong Servlet/Jakarta namespace).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
