# ----------------------------
# 1️⃣ Build stage
# ----------------------------
FROM maven:3.9.9-eclipse-temurin-17 AS build

# Set working directory inside build container
WORKDIR /app

# Copy the Maven project files
COPY binance-trader/pom.xml ./
COPY binance-trader/src ./src

# Build the application (skip tests for faster deploy)
RUN mvn clean package -DskipTests


# ----------------------------
# 2️⃣ Runtime stage
# ----------------------------
FROM openjdk:17-jdk-slim

# Set working directory in runtime container
WORKDIR /app

# Copy the built jar from the previous stage
COPY --from=build /app/target/*.jar app.jar

# Expose the port for Render
ENV PORT=8080
EXPOSE 8080

# Run the Spring Boot app
ENTRYPOINT ["java", "-jar", "app.jar"]
