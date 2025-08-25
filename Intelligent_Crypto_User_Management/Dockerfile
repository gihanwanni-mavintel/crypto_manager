# Use an official OpenJDK 17 runtime as the base image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build the application
RUN apt-get update && apt-get install -y maven \
    && mvn clean package -DskipTests \
    && apt-get remove -y maven \
    && rm -rf /var/lib/apt/lists/*

# Copy the built jar to the working directory
COPY target/*.jar app.jar

# Expose the port Render will use
EXPOSE 10000

# Set the start command
CMD ["java", "-Dserver.port=$PORT", "-jar", "app.jar"]
