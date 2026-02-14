# 1. Build Stage
FROM gradle:8.5-jdk21-alpine AS builder
WORKDIR /app
COPY . .
# gradlew 실행 권한 부여
RUN chmod +x gradlew
# 테스트 제외하고 빌드 (시간 절약)
RUN ./gradlew build -x test --no-daemon

# 2. Run Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

ENV SERVER_PORT=8080
# JVM 메모리 설정 (컨테이너 메모리 제한에 맞춰 튜닝)
ENV JAVA_OPTS="-Xms256m -Xmx384m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]