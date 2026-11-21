FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar copyRuntimeLibs --no-daemon --console=plain

FROM eclipse-temurin:21-jre-noble
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
    DISPLAY=:99
RUN --mount=from=build,source=/app/build/runtime-libs,target=/tmp/playwright-libs \
    apt-get update && apt-get install -y --no-install-recommends curl fonts-noto-cjk libatomic1 tesseract-ocr tesseract-ocr-kor xvfb xauth \
    && java -cp '/tmp/playwright-libs/*' com.microsoft.playwright.CLI install --with-deps chromium \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/build/libs/*-0.1.0.jar app.jar
COPY docker-entrypoint.sh ./docker-entrypoint.sh
RUN chmod +x ./docker-entrypoint.sh
EXPOSE 18080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
