FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY billing-service/target/billing-service-1.0.0.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
