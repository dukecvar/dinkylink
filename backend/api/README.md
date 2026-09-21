# backend/api

The Dinky Link public API (Kotlin, Spring Boot).

## Prerequisites

Postgres and Redis running via `cd ../../local && docker compose up -d`.

## Build and test

```bash
./gradlew build
./gradlew test
```

Run a single test class:
```bash
./gradlew test --tests "dev.dukecvar.dinkylink.api.DinkyLinkApiApplicationTests"
```

## Run locally

```bash
./gradlew bootRun
```

Serves on `http://localhost:8081` directly (reached via the nginx gateway
at `http://localhost:8080` once `local/` and `frontend/` are also up —
see the top-level README). Health check: `GET /actuator/health`.
