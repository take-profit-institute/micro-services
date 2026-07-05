# syntax=docker/dockerfile:1
# 모노레포 공용 Dockerfile.
# 빌드: docker build --build-arg SERVICE_MODULE=user-service -t <image> .
#
# 서비스 목록을 열거하지 않는다 — settings.gradle 에 서비스가 추가돼도 이 파일은 수정 불필요.
# 전체 소스를 ephemeral bind mount 로 붙여 빌드하고(레이어에 소스가 남지 않음), 산출 jar 만
# 런타임 이미지로 복사한다. gradle 의존성은 cache mount 로 빌드 간 재사용한다.
# (BuildKit 필요 — buildx/최신 docker 기본값)
ARG SERVICE_MODULE=user-service

# ── 1단계: Gradle 빌드 ────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
ARG SERVICE_MODULE
# bind mount: 빌드 컨텍스트 전체를 읽기/쓰기(ephemeral)로 마운트 → 개별 파일 COPY 열거 불필요.
# cache mount: ~/.gradle(의존성·wrapper) 를 빌드 간 캐시.
# 산출 jar 는 마운트 밖 경로(/app.jar)로 복사해야 레이어에 남는다.
RUN --mount=type=bind,target=/workspace,rw \
    --mount=type=cache,target=/root/.gradle \
    if [ "$SERVICE_MODULE" = "batch" ]; then \
      sh ./gradlew :batch:bootJar --no-daemon -x test \
      && cp batch/build/libs/*.jar /app.jar; \
    else \
      sh ./gradlew :services:${SERVICE_MODULE}:bootJar --no-daemon -x test \
      && cp services/${SERVICE_MODULE}/build/libs/*.jar /app.jar; \
    fi

# ── 2단계: 런타임 이미지 ────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
