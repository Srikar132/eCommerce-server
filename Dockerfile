# ==============================================================================
# STAGE 1: Build Stage
# ==============================================================================
# Purpose: This stage compiles your Spring Boot application and creates the JAR file
# We use Maven with Java 17 (matching your pom.xml configuration)
FROM maven:3.9.9-eclipse-temurin-17-alpine AS build

# Set working directory inside the container
WORKDIR /app

# Copy only the pom.xml first (optimization for Docker layer caching)
# If dependencies don't change, Docker will use cached layers
COPY pom.xml .

# Download all dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy the entire source code
COPY src ./src

# Build the application (skip tests for faster builds, you can remove -DskipTests if you want to run tests)
# This creates a JAR file in the target directory
RUN mvn clean package -DskipTests -B

# ==============================================================================
# STAGE 2: Runtime Stage
# ==============================================================================
# Purpose: This stage creates a lightweight image with only the runtime requirements
# We use a smaller JRE image instead of the full JDK to reduce image size
FROM eclipse-temurin:17-jre-alpine

# Set working directory
WORKDIR /app

# Create a non-root user for security (running as root is a security risk)
RUN addgroup -S spring && adduser -S spring -G spring

# Copy the JAR file from the build stage
# The JAR name matches your artifact ID in pom.xml: armoire-0.0.1-SNAPSHOT.jar
COPY --from=build /app/target/armoire-0.0.1-SNAPSHOT.jar app.jar

# Change ownership of the JAR file to the spring user
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring

# Expose port 8080 (Spring Boot default port)
# If you have a different port configured in application.properties, change this
EXPOSE 8080

# Set environment variables (these can be overridden when running the container)
ENV JAVA_OPTS="-Xms512m -Xmx1024m"

# Health check to monitor if the application is running
# This checks the Spring Boot actuator health endpoint (you need to ensure actuator is enabled)
# Comment out if you don't have actuator configured
# HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
#   CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
# The ENTRYPOINT uses shell form to allow environment variable expansion
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
