# Use an official OpenJDK runtime as the base image
FROM openjdk:17-jdk-slim

# Set the working directory inside the container
WORKDIR /app

# Copy the compiled JAR file and other resources to the container
COPY target/Chatroom-server-1.0-SNAPSHOT.jar app.jar
COPY data.json data.json

# Expose the application's port
EXPOSE 8080

# Set the command to run the application
CMD ["java", "-jar", "app.jar"]