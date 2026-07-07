# 자체 완결형 멀티스테이지 빌드: 프론트(Vite) → 백엔드(bootJar) → 경량 런타임.
# 결과 이미지는 프론트+백을 한 jar 로 담아 8080 에서 서빙한다.

# 1) 프론트엔드 빌드 (vite outDir = ../src/main/resources/static)
FROM node:20-alpine AS frontend
WORKDIR /app
RUN mkdir -p src/main/resources/static
COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN cd frontend && npm ci
COPY frontend/ ./frontend/
RUN cd frontend && npm run build

# 2) 백엔드 빌드 (프론트 산출물을 정적 리소스로 주입한 뒤 bootJar)
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ ./gradle/
RUN chmod +x gradlew && ./gradlew --version --no-daemon
COPY src/ ./src/
COPY --from=frontend /app/src/main/resources/static/ ./src/main/resources/static/
RUN ./gradlew bootJar --no-daemon -x test

# 3) 런타임 (JRE, 비루트 사용자, 저사양 GC)
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 homepoker \
    && mkdir -p /var/lib/homepoker \
    && chown homepoker /var/lib/homepoker
COPY --from=backend /app/build/libs/poker-0.0.1-SNAPSHOT.jar app.jar
USER homepoker
EXPOSE 8080
# 2GB 박스: 힙을 램의 절반으로 제한, 1 vCPU 라 SerialGC 가 유리.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50 -XX:+UseSerialGC"
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
