FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk-alpine AS builder
ARG APP_VERSION=0.1.0
ARG GITHUB_SHA=unknown
ARG BUILD_TIME=unknown
ENV GITHUB_SHA=$GITHUB_SHA
ENV BUILD_TIME=$BUILD_TIME
WORKDIR /app
COPY gradlew ./
COPY gradle/ ./gradle/
RUN ./gradlew --version --no-daemon -q
COPY build.gradle.kts gradle.properties settings.gradle.kts* ./
COPY src/ ./src/
COPY server-admin/ ./server-admin/
COPY server-auth/ ./server-auth/
COPY server-cache/ ./server-cache/
COPY server-core/ ./server-core/
COPY server-db/ ./server-db/
COPY server-domain/ ./server-domain/
COPY server-downloader/ ./server-downloader/
COPY server-http/ ./server-http/
COPY server-playback/ ./server-playback/
COPY server-portability/ ./server-portability/
COPY server-sabr/ ./server-sabr/
COPY server-test-support/ ./server-test-support/
COPY server-services/ ./server-services/
COPY server-token-gateway/ ./server-token-gateway/
RUN ./gradlew dependencies --no-daemon -q || true
RUN ./gradlew shadowJar --no-daemon -q -PappVersion="$APP_VERSION"

FROM eclipse-temurin:25-jre-alpine AS runner
RUN apk upgrade --no-cache \
    && addgroup -S typetype \
    && adduser -S typetype -G typetype
WORKDIR /app
COPY --from=builder /app/build/libs/typetype-server-all.jar app.jar
USER typetype
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
