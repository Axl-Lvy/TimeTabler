# Build stage: the server distribution and the wasm web bundle it serves.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
ARG BUILD_SHA=dev
COPY . .
# The Kotlin/Wasm Node toolchain needs libatomic.
RUN apt-get update && apt-get install -y --no-install-recommends libatomic1 \
  && rm -rf /var/lib/apt/lists/*
RUN ./gradlew :server:installDist :composeApp:wasmJsBrowserDistribution -PbuildSha="${BUILD_SHA}" --no-daemon --console=plain

FROM eclipse-temurin:25-jre AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends wget \
  && rm -rf /var/lib/apt/lists/*
RUN groupadd --gid 10001 timetabler \
  && useradd --uid 10001 --gid timetabler --no-create-home --shell /usr/sbin/nologin timetabler \
  && mkdir -p /data/posters && chown timetabler:timetabler /data/posters
COPY --from=build --chown=root:root /workspace/server/build/install/server /app
COPY --from=build --chown=root:root /workspace/composeApp/build/dist/wasmJs/productionExecutable /app/www
RUN rm -f /app/www/composeApp.js.map

# Headless Chromium and its system libraries for the Shotgun scraper, installed by the Playwright
# version the server ships so the two always match.
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN java -cp "/app/lib/*" com.microsoft.playwright.CLI install --with-deps --only-shell chromium \
  && rm -rf /var/lib/apt/lists/* /tmp/playwright-java-*
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

USER timetabler
WORKDIR /app

# Chromium shares the deployment's mem_limit during Shotgun runs, so the heap gets only half of it.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50"
ENV TT_STATIC_DIR="/app/www"
ENV TT_POSTER_DIR="/data/posters"

# Everything else comes from the environment: TT_DB_URL, TT_DB_USER, TT_DB_PASSWORD, optional TT_PORT,
# TT_REMOVAL_RETENTION (how long removed events stay in the feed, such as 30d) and TT_SHOTGUN_ENABLED
# (true to scrape Shotgun, off by default). Admin routes need TT_AUTH_ISSUER and TT_AUTH_AUDIENCE
# (optional TT_AUTH_JWKS_URL, default <issuer>/jwks) and refuse every request without them.
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s CMD wget -qO- http://localhost:8080/health || exit 1
ENTRYPOINT ["/app/bin/server"]
