## Multi-stage Dockerfile to build the multi-module Maven project and produce a runnable image
FROM --platform=$BUILDPLATFORM maven:4.0.0-rc-4-eclipse-temurin-21-noble AS build
ARG MAVEN_OPTS="-Xmx512m"
WORKDIR /workspace

# Copy only the parent and module POMs first to leverage Docker layer caching
COPY pom.xml ./
COPY domain/pom.xml domain/
COPY data/pom.xml data/
COPY api/pom.xml api/
COPY application/pom.xml application/

# Pre-download dependencies into the Maven local repo using BuildKit cache
# This step is cached as long as POMs don't change.
RUN --mount=type=cache,target=/root/.m2 \
	mvn -B -DskipTests dependency:go-offline

# Copy the full source and build the project. Use the same cache for Maven.
COPY . /workspace
RUN --mount=type=cache,target=/root/.m2 \
	mvn -B -DskipTests package

## Use a stable multi-arch Temurin runtime (Debian jammy).
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the built Spring Boot jar from the build stage
COPY --from=build /workspace/application/target/*.jar app.jar

# Expose the application port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
