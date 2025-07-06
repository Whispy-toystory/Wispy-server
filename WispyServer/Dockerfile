# Multi-stage build for optimized image
FROM openjdk:21-jdk-slim as builder

WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle/ gradle/
COPY gradlew .
COPY src ./src

# Gradle 실행 권한 부여 및 빌드
RUN chmod +x gradlew
RUN ./gradlew clean bootJar --no-daemon

# 실행 이미지
FROM openjdk:21-jdk-slim

WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 환경 변수 설정
ENV SPRING_PROFILES_ACTIVE=prod

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]

