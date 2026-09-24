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

## API

See `docs/design.md` for the full contract. Summary:

- `POST /` — body `{"url": "<input-url>"}`. Returns `201` with
  `{"url": ..., "shortURL": "<dinkylink.base-url>/<shortcode>"}`, or `400`
  with `{"url": ..., "error": "<reason>"}` if the URL is blank or over
  2048 characters. Submitting the same URL twice returns the same
  shortcode.
- `GET /<shortcode>` — `300` with a `Location` header pointing at the
  original URL, or `404` if the shortcode is unknown.

`dinkylink.base-url` (in `application.yml`) controls the host used to
build `shortURL` — defaults to `http://localhost:8080`, the local nginx
gateway.
