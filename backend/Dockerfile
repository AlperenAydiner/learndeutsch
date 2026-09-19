# =====================================================================
# Backend imajı — iki asamali.
# Derleme JDK ile yapilir, calistirma sadece JRE tasiyan kucuk imajda.
# =====================================================================

# ---------- 1. asama: derle ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Once sadece bagimlilik tanimlarini kopyala: kaynak kod degisince
# Docker bu katmani yeniden indirmez, derleme hizlanir.
COPY gradlew ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

COPY src src
RUN ./gradlew --no-daemon bootJar

# ---------- 2. asama: calistir ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# root olarak calistirma.
RUN useradd --system --create-home --uid 10001 spring
USER spring

COPY --from=build --chown=spring:spring /app/build/libs/*.jar app.jar

# Render PORT degiskenini kendisi verir; application.yml onu okur.
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
