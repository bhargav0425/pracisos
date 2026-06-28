FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY booking-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
