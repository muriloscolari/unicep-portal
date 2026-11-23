# syntax=docker/dockerfile:1

# Stage 1: Compila o frontend Compose Multiplatform Web (Wasm)
FROM eclipse-temurin:21-jdk AS web-builder
RUN apt-get update && apt-get install -y --no-install-recommends libatomic1 && rm -rf /var/lib/apt/lists/*
WORKDIR /src/app
COPY app/ .
RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    chmod +x ./gradlew && ./gradlew :composeApp:wasmJsBrowserDistribution -PwebOnly=true --no-daemon

# Stage 2: Compila o backend em Go (estático, sem CGO)
FROM golang:1.24-alpine AS go-builder
WORKDIR /src/backend
COPY backend/go.mod backend/go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download
COPY backend/ .
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/server ./cmd/server

# Stage 3: Imagem final enxuta
FROM alpine:3.21
RUN apk add --no-cache ca-certificates tzdata wget \
    && addgroup -S unicep && adduser -S -G unicep unicep
WORKDIR /app

COPY --from=go-builder /out/server /app/server
COPY --from=web-builder /src/app/composeApp/build/dist/wasmJs/productionExecutable /app/web

RUN mkdir -p /app/data && chown -R unicep:unicep /app
USER unicep

ENV PORT=47832 \
    DATA_DIR=/app/data \
    WEB_DIR=/app/web \
    TZ=America/Sao_Paulo

EXPOSE 47832
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:47832/api/health || exit 1

ENTRYPOINT ["/app/server"]
