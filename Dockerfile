# Dockerfile multi-stage pour l'Assistant Scalper
# Étape 1: Construction avec Maven
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

# Métadonnées
LABEL maintainer="scalper-assistant"
LABEL description="Assistant du Scalper Éclairé - Multi-Sessions Trading"
LABEL version="1.0.0"

# Variables d'environnement pour la construction
ENV MAVEN_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=256m"
ENV JAVA_TOOL_OPTIONS="-Xmx1024m"

# Répertoire de travail
WORKDIR /app

# Copie des fichiers de configuration Maven
COPY pom.xml .
COPY src ./src

# Téléchargement des dépendances (mise en cache Docker)
RUN mvn dependency:go-offline -B

# Construction de l'application
RUN mvn clean package -DskipTests -B \
    && mkdir -p target/dependency \
    && cd target/dependency \
    && jar -xf ../*.jar

# Étape 2: Image de production
FROM eclipse-temurin:21-jre-alpine

# Installation des outils nécessaires pour les screenshots
RUN apk add --no-cache \
    chromium \
    chromium-chromedriver \
    font-noto \
    font-noto-cjk \
    font-noto-extra \
    && rm -rf /var/cache/apk/*

# Variables d'environnement pour Chromium
ENV CHROME_BIN=/usr/bin/chromium-browser
ENV CHROME_PATH=/usr/lib/chromium/
ENV CHROMIUM_FLAGS="--disable-software-rasterizer --disable-background-timer-throttling --disable-renderer-backgrounding --disable-backgrounding-occluded-windows --disable-dev-shm-usage --no-sandbox --disable-gpu --headless"

# Création de l'utilisateur non-root pour la sécurité
RUN addgroup -g 1001 scalper && \
    adduser -D -s /bin/sh -u 1001 -G scalper scalper

# Répertoires de l'application
WORKDIR /app

# Création des répertoires nécessaires
RUN mkdir -p logs screenshots temp && \
    chown -R scalper:scalper /app

# Copie des artefacts depuis l'étape de construction
COPY --from=builder --chown=scalper:scalper /app/target/dependency/BOOT-INF/lib /app/lib
COPY --from=builder --chown=scalper:scalper /app/target/dependency/META-INF /app/META-INF
COPY --from=builder --chown=scalper:scalper /app/target/dependency/BOOT-INF/classes /app

# Configuration JVM optimisée
ENV JAVA_OPTS="-server \
    -Xms512m \
    -Xmx1024m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -Djava.security.egd=file:/dev/./urandom \
    -Djava.awt.headless=true \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=UTC"

# Configuration Spring Boot
ENV SPRING_PROFILES_ACTIVE=docker
ENV SPRING_OUTPUT_ANSI_ENABLED=ALWAYS

# Configuration de l'application
ENV SERVER_PORT=8080
ENV MANAGEMENT_SERVER_PORT=8081

# Cette commande APK fonctionnera dans le conteneur Linux
RUN apk add --no-cache wget

# Puis utiliser wget
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# Exposition des ports
EXPOSE 8080 8081

# Changement vers l'utilisateur non-root
USER scalper

# Point d'entrée avec gestion des signaux
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp /app:/app/lib/* com.scalper.ScalperApplication"]

# Ajout de metadata pour inspection
LABEL org.opencontainers.image.title="Scalper Assistant"
LABEL org.opencontainers.image.description="Assistant du Scalper Éclairé - Multi-Sessions Trading Application"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.created="2025-09-19"
LABEL org.opencontainers.image.source="https://github.com/patricklamba/scalper-java"