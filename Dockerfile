# 모노레포 공용 Dockerfile.
# 빌드: docker build --build-arg SERVICE_MODULE=user-service -t <image> .
ARG SERVICE_MODULE=user-service

# ── 1단계: Gradle 빌드 ────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Gradle wrapper 먼저 (레이어 캐시 활용)
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
RUN chmod +x gradlew

# 루트·모듈 빌드 스크립트 (의존성 해석에 필요)
COPY build.gradle settings.gradle ./
COPY common/build.gradle common/build.gradle
COPY batch/build.gradle batch/build.gradle
COPY services/auth-service/build.gradle       services/auth-service/build.gradle
COPY services/user-service/build.gradle       services/user-service/build.gradle
COPY services/market-service/build.gradle     services/market-service/build.gradle
COPY services/trading-service/build.gradle    services/trading-service/build.gradle
COPY services/portfolio-service/build.gradle  services/portfolio-service/build.gradle
COPY services/ranking-service/build.gradle    services/ranking-service/build.gradle
COPY services/mission-service/build.gradle    services/mission-service/build.gradle
COPY services/learning-service/build.gradle   services/learning-service/build.gradle
COPY services/notification-service/build.gradle services/notification-service/build.gradle
COPY services/chatting-service/build.gradle    services/chatting-service/build.gradle
COPY gateway/build.gradle gateway/build.gradle

# 의존성 다운로드만 먼저 (소스 변경 시 이 레이어 재사용)
ARG SERVICE_MODULE
RUN ./gradlew :services:${SERVICE_MODULE}:dependencies --no-daemon -q 2>/dev/null || true

# 소스 복사 (common, proto, 대상 서비스)
COPY common/src/ common/src/
COPY proto/ proto/
COPY services/${SERVICE_MODULE}/src/ services/${SERVICE_MODULE}/src/

# JAR 빌드
RUN ./gradlew :services:${SERVICE_MODULE}:bootJar --no-daemon -x test

# ── 2단계: 런타임 이미지 ────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

ARG SERVICE_MODULE
COPY --from=build /workspace/services/${SERVICE_MODULE}/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
