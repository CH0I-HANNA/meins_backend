# --- Build stage ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 의존성만 먼저 복사해 레이어 캐싱 (build.gradle 변경 없으면 재다운로드 스킵)
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# --- Run stage ---
FROM eclipse-temurin:17-jre AS run
WORKDIR /app

RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

COPY --from=build /app/build/libs/*.jar app.jar

# Railway가 PORT를 주입 (application.properties의 server.port=${PORT:8080}와 연결됨)
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]