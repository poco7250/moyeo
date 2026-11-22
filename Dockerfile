# moyeo 백엔드 이미지(Task 067). 빌드: docker build -t moyeo-app .
# 테스트는 Testcontainers(도커 안의 도커)가 필요해 이미지 빌드에서는 건너뛴다. 배포 전 ./gradlew build를 따로 돌린다

# 1) 빌드 단계: 의존성 레이어를 먼저 받아 소스만 바뀌면 캐시를 재사용한다
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null
COPY src src
RUN ./gradlew bootJar -x test --no-daemon -q

# 2) 실행 단계: JRE만, 비루트 사용자
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Seoul
# curl은 compose 헬스 체크(/actuator/health/readiness)용
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system moyeo && useradd --system --gid moyeo --home /app moyeo \
    && mkdir -p /app/data/receipts && chown -R moyeo:moyeo /app
WORKDIR /app
COPY --from=build /workspace/build/libs/moyeo.jar app.jar
USER moyeo
EXPOSE 8080
# 작업 디렉터리가 /app이라 영수증 기본 경로 ./data/receipts = /app/data/receipts(볼륨)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
