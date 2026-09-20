# =====================================================================
# Backend imajı — iki asamali.
#
# DIKKAT: derleme baglami depo KOKUDUR (backend/ degil), cunku icerik
# CSV'leri /content altinda durur ve jar'a oradan kopyalanir.
#   docker build -f backend/Dockerfile .
# =====================================================================

# ---------- 1. asama: derle ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Once sadece bagimlilik tanimlarini kopyala: kaynak kod degisince
# Docker bu katmani yeniden indirmez, derleme hizlanir.
COPY backend/gradlew ./
COPY backend/gradle gradle
COPY backend/build.gradle.kts backend/settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

# processResources bunu ../content olarak arar.
COPY content /content
COPY backend/src src

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
