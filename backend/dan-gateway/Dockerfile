# El contexto de build es la raiz del repo (docker build -f backend/dan-gateway/Dockerfile .), porque
# el servicio depende del modulo compartido backend/dan-common.

# Stage 1: instala dan-common y baja las dependencias del servicio (cacheable)
FROM maven:3.9.6-eclipse-temurin-17 AS dependencies
WORKDIR /build

COPY backend/dan-common/pom.xml backend/dan-common/pom.xml
COPY backend/dan-common/src backend/dan-common/src
RUN mvn -B -f backend/dan-common/pom.xml install -DskipTests

COPY backend/dan-gateway/pom.xml backend/dan-gateway/pom.xml
RUN mvn -B -f backend/dan-gateway/pom.xml dependency:go-offline

# Stage 2: compila el servicio
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /build

# dan-common ya quedo instalado en el repositorio local de la etapa anterior
COPY --from=dependencies /root/.m2 /root/.m2

COPY backend/dan-gateway backend/dan-gateway

# Los tests corren en CI con `mvn verify`
RUN mvn -B -f backend/dan-gateway/pom.xml clean package -DskipTests


# Stage 3: Run the JAR file
FROM eclipse-temurin:17-jre-alpine

# Usuario no-root: buenas prácticas de contenedores seguros
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copy the built JAR file from the build stage
COPY --from=build /build/backend/dan-gateway/target/*.jar /app/app.jar

# El usuario spring necesita escribir solo en su directorio
RUN chown -R spring:spring /app

# Cambia al usuario no privilegiado
USER spring:spring

# Expose the port
EXPOSE 8080

# Healthcheck: usa el endpoint de actuator para saber si el contenedor está vivo
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -q --spider http://localhost:8081/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
