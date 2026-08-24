# Stage 1: Build Maven dependencies
FROM maven:3.9.6-eclipse-temurin-17 AS dependencies
WORKDIR /app

# Copy only the pom.xml file to cache dependencies
COPY pom.xml .

# Run the go-offline goal to download all dependencies
RUN mvn dependency:go-offline

# Stage 2: Build the JAR file
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the cached dependencies from the first stage
COPY --from=dependencies /root/.m2 /root/.m2

# Copy the rest of the project files
COPY . .

# Build the project (los tests corren en CI con `mvn test`)
RUN mvn clean package -DskipTests

# Stage 3: Run the JAR file
FROM eclipse-temurin:17-jre-alpine

# Usuario no-root: buenas prácticas de contenedores seguros
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copy the built JAR file from the build stage
COPY --from=build /app/target/*.jar /app/app.jar

# El usuario spring necesita escribir solo en su directorio
RUN chown -R spring:spring /app

# Cambia al usuario no privilegiado
USER spring:spring

# Eureka escucha en 8761 por defecto (SPRING_EUREKA_PORT)
EXPOSE 8761

# Healthcheck: usa el endpoint de actuator para saber si el contenedor está vivo
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -q --spider http://localhost:8761/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
