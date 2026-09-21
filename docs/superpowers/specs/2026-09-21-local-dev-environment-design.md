# Local Dev Environment Setup

## Summary

Stand up the local development environment for Dinky Link: the infra
containers (Postgres, Redis, nginx gateway, Flyway) in
`local/docker-compose.yaml`, the Flyway migration project in `database/`,
a scaffolded Kotlin/Spring Boot backend in `backend/api`, and a
scaffolded React/Vite frontend in `frontend/`. This turns the
already-approved design in `docs/design.md`, `docs/sequence.puml`, and
`docs/system.drawio` into a runnable local stack. No product features
(shortcode creation, redirect) are implemented in this pass — this is
scaffolding only.

## Naming cleanup

`CLAUDE.md` currently has leftover template text: it titles the project
"Dirky Link" and its Backend section references `backend/bridgespeak-service`
and `com.bridgespeak.chat.service.UserServiceTest`. `docs/design.md` (the
authoritative product spec) calls it "Dinky Link" and specifies
`backend/api` / `backend/workers`. As part of this change, `CLAUDE.md` is
corrected to say "Dinky Link" and to reference `backend/api` with the
actual package (`dev.dukecvar.dinkylink.api`) instead of the bridgespeak
example.

## Components

### 1. `local/docker-compose.yaml`

Four services, dev-only credentials, no TLS:

| Service | Image | Config |
|---|---|---|
| `postgres` | `postgres:17-alpine` | `POSTGRES_DB=dinkylink`, `POSTGRES_USER=dinkylink`, `POSTGRES_PASSWORD=dinkylink`, port `5432`, healthcheck `pg_isready` |
| `redis` | `redis:7-alpine` | no auth, port `6379`, healthcheck `redis-cli ping` |
| `flyway` | `flyway/flyway:11-alpine` | mounts `../database/migrations` → `/flyway/sql`, `command: migrate`, env vars point at `postgres` service (internal network name), `depends_on: postgres` (service_healthy), `restart: "no"` (one-shot job) |
| `nginx` | `nginx:1.27-alpine` | mounts `./nginx/nginx.conf`, `depends_on: postgres, redis`, `extra_hosts: ["host.docker.internal:host-gateway"]`, exposes `8080:80` |

All services share one compose network. `postgres` and `redis` publish
their ports to the host as well, so `./gradlew bootRun` (outside
compose) can reach them at `localhost:5432` / `localhost:6379`.

**nginx routing** (`local/nginx/nginx.conf`), implementing the table in
`docs/design.md`:

- `location = /` — method-based split: `if ($request_method = POST)`
  proxies to `http://host.docker.internal:8080` (backend), otherwise
  proxies to `http://host.docker.internal:5173` (frontend dev server).
- `location ~ ^/[0-9A-Za-z]+$` — proxies to backend on `8080`.
- `limit_req_zone`s: `write` (5 r/s, burst 10, keyed on
  `$binary_remote_addr`) applied to the POST branch; `read` (50 r/s,
  burst 100) applied to the shortcode-redirect location.
- `limit_conn_zone` capping concurrent connections per IP.
- `client_max_body_size` set small (payload is just a URL).
- `server_tokens off`.
- No TLS — this is local-only; the design's Let's Encrypt/certbot
  termination is a prod concern, out of scope here.

This means: for a fully-proxied local flow, run backend via
`./gradlew bootRun` and frontend via `npm run dev`, then hit
`http://localhost:8080`. Running only `docker compose up -d` (per
`CLAUDE.md`) brings up just the infra; nginx will 502 until the backend
and frontend dev servers are also running — that's expected and will be
called out in `local/README.md`.

### 2. `database/`

New sub-project, own `README.md` per `CLAUDE.md`'s "keep docs in sync"
rule. Contents:

```
database/
  README.md
  migrations/
    V1__create_records_table.sql
```

`V1__create_records_table.sql` creates the `records` table from
`docs/design.md`, translated to Postgres types:

```sql
CREATE TABLE records (
    shortcode              CHAR(8)      PRIMARY KEY,
    urlhash                BYTEA        NOT NULL,
    url                    VARCHAR(2048) NOT NULL,
    last_touched_timestamp TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_records_urlhash ON records (urlhash);
```

Notes / deliberate deviations from the doc, called out here so they're
not mistaken for oversights:
- `binary(128)` in `docs/design.md`'s table is inconsistent with the
  "128 bit hash" described in the Shortcodes section (128 bits = 16
  bytes, not 128 bytes). This migration uses `bytea` (Postgres has no
  fixed-length binary type) sized for a 16-byte hash in practice.
- `docs/sequence.puml`'s insert statement references a `salt` column
  that isn't in `docs/design.md`'s table. Since this pass only
  scaffolds the schema (no create-shortcode logic yet), `salt` is
  intentionally omitted. Whoever implements the actual insert/collision
  procedure will need a follow-up migration to add it (and should
  reconcile the two docs at that point).

### 3. `backend/api/`

Generated via start.spring.io, not hand-rolled, to get a correct
current scaffold:

- Kotlin, Gradle (Kotlin DSL), Spring Boot **4.1.1**, Java **25**
- Group `dev.dukecvar`, artifact `api`, package
  `dev.dukecvar.dinkylink.api`
- Dependencies: `web`, `actuator`, `data-jdbc`, `postgresql`,
  `data-redis`
- `src/main/resources/application.yml` configured with:
  - `spring.datasource.url=jdbc:postgresql://localhost:5432/dinkylink`
    (user/pass `dinkylink`)
  - `spring.data.redis.host=localhost`, `port=6379`
  - `spring.flyway.enabled=false` — migrations are owned by the
    standalone `database/` Flyway container, not the app, so the app
    must not attempt its own migrations
  - actuator health endpoint exposed

No controllers, services, or repositories beyond what Spring
Initializr generates (the default `DinkyLinkApiApplication.kt`) —
per the "bare skeleton" scope agreed earlier, business logic
(shortcode creation, redirect) is future work.

### 4. `frontend/`

- `npm create vite@latest` with the React + TypeScript template
- Tailwind CSS added per Vite's standard setup
- shadcn/ui initialized (`npx shadcn@latest init`) with defaults
- Default starter page left as-is — no dinky.link-specific UI in this
  pass, per the "bare tooling" scope agreed earlier

### 5. Docs updated in the same change

- `CLAUDE.md`: title → "Dinky Link"; Backend section's example paths/
  package corrected to `backend/api` / `dev.dukecvar.dinkylink.api`
- Top-level `README.md`: created/updated with the local dev quickstart
  (mirrors the commands already documented in `CLAUDE.md`)
- `local/README.md`, `database/README.md`, `backend/api/README.md`,
  `frontend/README.md`: brief per-project README (what it is, how to
  build/run it); `local/README.md` explicitly notes that nginx expects
  the backend and frontend dev servers to be running on the host for
  proxied requests to succeed


## Testing

This is scaffolding, not feature work, so verification is smoke-level:

- `docker compose up -d` in `local/` brings up postgres/redis/flyway/
  nginx cleanly; `flyway` container exits 0 having applied `V1`; `psql`
  confirms the `records` table exists.
- `./gradlew build` in `backend/api` succeeds; `./gradlew bootRun`
  starts and `GET /actuator/health` returns `200 {"status":"UP"}`.
- `npm install && npm run build` in `frontend/` succeeds; `npm run dev`
  serves the default Vite starter page.
- No unit/integration tests are written in this pass (there is no
  behavior yet to test).

## Out of scope

- Implementing `POST /` and `GET /<shortcode>` (shortcode
  create/redirect logic), the hashing/collision procedure, and the
  Redis caching layer's actual read/write paths.
- `backend/workers` project (last-touched flusher, cleanup job).
- `backend/api-tests` (REST Assured E2E project) and the
  `--profile test` compose entry.
- TLS/certbot, production nginx config, or any deployment concerns.
- Containerizing the frontend build (the `docker compose up -d --build
  frontend` flow mentioned in `CLAUDE.md`) — local dev here uses
  `npm run dev` directly.
