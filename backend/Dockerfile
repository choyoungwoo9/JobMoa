FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar copyRuntimeLibs --no-daemon --console=plain

FROM eclipse-temurin:21-jre-noble
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN --mount=from=build,source=/app/build/runtime-libs,target=/tmp/playwright-libs \
    apt-get update && apt-get install -y --no-install-recommends curl fonts-noto-cjk \
    && java -cp '/tmp/playwright-libs/*' com.microsoft.playwright.CLI install --with-deps chromium --only-shell \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/build/libs/*-0.1.0.jar app.jar
EXPOSE 18080
ENTRYPOINT ["java", "-jar", "app.jar"]
