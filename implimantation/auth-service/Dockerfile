FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY auth-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
