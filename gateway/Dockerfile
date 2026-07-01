# gateway 전용 Dockerfile (루트 Dockerfile과 별개)
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
RUN chmod +x gradlew

# 의존성 해석에 필요한 모든 모듈 build.gradle
COPY build.gradle settings.gradle ./
COPY common/build.gradle common/build.gradle
COPY batch/build.gradle batch/build.gradle
COPY services/auth-service/build.gradle       services/auth-service/build.gradle
COPY services/user-service/build.gradle       services/user-service/build.gradle
COPY services/market-service/build.gradle     services/market-service/build.gradle
COPY services/stock-service/build.gradle      services/stock-service/build.gradle
COPY services/trading-service/build.gradle    services/trading-service/build.gradle
COPY services/portfolio-service/build.gradle  services/portfolio-service/build.gradle
COPY services/ranking-service/build.gradle    services/ranking-service/build.gradle
COPY services/mission-service/build.gradle    services/mission-service/build.gradle
COPY services/learning-service/build.gradle   services/learning-service/build.gradle
COPY services/notification-service/build.gradle services/notification-service/build.gradle
COPY services/chatting-service/build.gradle   services/chatting-service/build.gradle
COPY gateway/build.gradle gateway/build.gradle

# 의존성 먼저 다운로드 (소스 변경 시 재사용)
RUN ./gradlew :gateway:dependencies --no-daemon -q 2>/dev/null || true

COPY gateway/src/ gateway/src/
RUN ./gradlew :gateway:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /workspace/gateway/build/libs/*.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
