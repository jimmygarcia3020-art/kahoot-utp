# Etapa 1: compilar Angular (la salida se escribe en src/main/resources/static)
FROM node:22-bookworm-slim AS frontend
WORKDIR /app
COPY frontend ./frontend
RUN mkdir -p src/main/resources/static \
 && npm --prefix frontend ci --legacy-peer-deps \
 && npm --prefix frontend run build

# Etapa 2: compilar el backend con el front incluido en el jar
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
COPY --from=frontend /app/src/main/resources/static src/main/resources/static
RUN ./mvnw -q -B -DskipTests package

# Etapa 3: solo ejecución
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
