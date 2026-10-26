# Build context is the repository root (the server shares the :core module):
#   docker build -f server/Dockerfile -t tg-finance-server .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew ./
COPY gradle ./gradle
COPY core ./core
COPY server ./server
RUN chmod +x gradlew && ./gradlew -p server installDist --no-daemon --console=plain

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home /app app \
    && mkdir -p /data && chown app:app /data
COPY --from=build /src/server/build/install/tg-finance-server /app
USER app
ENV PORT=8080 \
    DATABASE_URL=jdbc:sqlite:/data/tgfinance.db
EXPOSE 8080
VOLUME ["/data"]
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD ["sh", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 || exit 1"]
ENTRYPOINT ["/app/bin/tg-finance-server"]
