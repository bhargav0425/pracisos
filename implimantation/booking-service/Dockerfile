FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY booking-service/pom.xml ./booking-service/
COPY booking-service/src ./booking-service/src
RUN apk add --no-cache maven && mvn clean package -pl booking-service -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/booking-service/target/booking-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
