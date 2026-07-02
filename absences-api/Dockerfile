# ─────────────────────────────────────────────────────────────────────────
# Dockerfile — absences-api (Spring Boot 3 / Java 21)
# Build multi-stage : compile avec Maven, run avec JRE slim
# ─────────────────────────────────────────────────────────────────────────

# ── Stage 1 : build Maven ────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copier pom.xml en premier pour profiter du cache des dépendances
COPY pom.xml .
RUN mvn dependency:go-offline -B 2>/dev/null || true

# Copier les sources et construire le jar
COPY src ./src
RUN mvn package -Dmaven.test.skip=true -B

# ── Stage 2 : image runtime ──────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Créer un utilisateur non-root
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=builder /app/target/absences-api-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
