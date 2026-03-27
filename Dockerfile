# Stage 1: Build Environment
# Using Maven with Eclipse Temurin (Java 21) on Alpine Linux for a small footprint
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Optimization: Copy pom.xml and download dependencies first to leverage Docker layer caching
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code and execute production build, skipping unit tests for deployment speed
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime Environment
# Using a lightweight JRE 21 image to reduce the final attack surface and image size
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Compliance: Configure system timezone to Asia/Taipei for accurate financial audit timestamps
RUN apk add --no-cache tzdata
ENV TZ=Asia/Taipei

# Transfer the compiled executable from the build stage
COPY --from=build /app/target/*.jar app.jar

# Network: Define the application port (8081) to be proxied by Nginx
ENV SERVER_PORT=8081
EXPOSE 8081

# Performance & Cloud Optimization:
# 1. -Xmx2g: Allocating 2GB Heap memory to utilize the high RAM available on OCI A1 Flex.
# 2. -Djava.security.egd: Accelerates Tomcat startup by using a non-blocking entropy source.
ENTRYPOINT ["java", \
            "-Xmx2g", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "app.jar"]