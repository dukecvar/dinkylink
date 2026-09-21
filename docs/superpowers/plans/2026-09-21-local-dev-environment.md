# Local Dev Environment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up a runnable local dev environment for Dinky Link — infra containers (Postgres, Redis, nginx, Flyway), a Flyway migration project, a Kotlin/Spring Boot backend scaffold, and a React/Vite frontend scaffold — with no product feature logic yet.

**Architecture:** Four independent pieces wired together by convention, not by code: `local/docker-compose.yaml` runs the infra containers; `database/migrations/` holds the SQL the `flyway` container applies to `postgres`; `backend/api` and `frontend/` run outside compose (`./gradlew bootRun`, `npm run dev`) and are reached by the `nginx` container via `host.docker.internal`. Nothing here talks to anything else over application code — the only integration point verified is HTTP routing through nginx.

**Tech Stack:** Docker Compose; PostgreSQL 17, Redis 7, Flyway 11, nginx 1.27 (all `-alpine`); Kotlin + Gradle (Kotlin DSL) + Spring Boot 4.1.1 on Java 25; React 19 + TypeScript + Vite + Tailwind CSS 4 + shadcn/ui.

**Spec:** `docs/superpowers/specs/2026-09-21-local-dev-environment-design.md`

## Global Constraints

- Postgres: `postgres:17-alpine`, db/user/password all `dinkylink` (dev-only credentials).
- Redis: `redis:7-alpine`, no auth.
- Flyway: `flyway/flyway:11-alpine`, migrations live in `database/migrations/`, `spring.flyway.enabled=false` in the backend (the standalone Flyway container owns migrations, not the app).
- nginx: `nginx:1.27-alpine`, no TLS locally (design's Let's Encrypt/certbot is a prod concern, out of scope here), exposed on host port `8080`.
- Backend: Kotlin, Gradle Kotlin DSL, Spring Boot `4.1.1`, Java `25` toolchain, group `dev.dukecvar`, package `dev.dukecvar.dinkylink.api`, deps `web`, `actuator`, `data-jdbc`, `postgresql`, `data-redis`. No controllers/services beyond the generated skeleton — this plan does not implement `POST /` or `GET /<shortcode>`.
- Frontend: `npm create vite@latest` React+TypeScript template, React 19, Tailwind CSS 4 (`@tailwindcss/vite` plugin, CSS-first — no `tailwind.config.js`), shadcn/ui via `npx shadcn@latest init`. No dinky.link-specific UI — default starter page only.
- `docs/design.md`'s `records` table has no `salt` column even though `docs/sequence.puml`'s insert statement references one; this plan matches `docs/design.md`'s 4 columns and leaves that reconciliation to whoever implements the create-shortcode logic.
- `backend/workers`, `backend/api-tests`, frontend containerization, and TLS are explicitly out of scope for this plan (per the spec's "Out of scope" section).

## Review Focus

- **Postgres data must survive `docker compose down` (no `-v`) but be wiped by `docker compose down -v`** — this is the exact distinction `CLAUDE.md` already documents, so it needs a named volume, not the default (volume-less) compose behavior. Test added to Task 2.
- **Flyway must be safe to re-run** — every `docker compose up -d` after the first (e.g. a routine restart) re-runs the `flyway` service; it must exit 0 and report "no migration necessary" rather than erroring on an already-applied migration. Test added to Task 2.
- **nginx must fail cleanly (502), not silently misroute or crash, when only infra is up** — `CLAUDE.md`'s documented `cd local && docker compose up -d` does not start the backend/frontend dev servers, so nginx's upstreams are legitimately down every time someone follows that exact instruction. Test added to Task 3.
- **The backend must not attempt its own Flyway migration at boot** — `database/`'s standalone Flyway container is the single owner of schema migrations; if a future dependency change accidentally pulls `flyway-core` onto the classpath, the app would race the migration container. Pinned with a startup-log assertion in Task 4.
- **nginx must forward a valid `Host` header to the backend** — discovered while verifying this plan: bare `proxy_pass http://backend_api;` forwards the upstream block's literal name as the `Host` header, and Tomcat rejects `backend_api` (underscore is invalid in a domain name) with a `400`/`IllegalArgumentException` on *every* request, not just some. Test added to Task 6 (`proxy_set_header Host $host;` is the fix, already included in Task 3's nginx.conf).

---

### Task 1: Fix stale project naming in `CLAUDE.md`

`CLAUDE.md` currently titles the project "Dirky Link" and its Backend section references a leftover template project (`backend/bridgespeak-service`, `com.bridgespeak.chat.service.UserServiceTest`). `docs/design.md` (the authoritative spec) calls it "Dinky Link" with `backend/api`. This task corrects `CLAUDE.md` to match, and updates its frontend tech-stack line from "React 18" to "React 19" (current Vite/shadcn tooling defaults to React 19; decided during spec review rather than fighting the toolchain).

**Files:**
- Modify: `CLAUDE.md`

**Interfaces:** None — documentation only, no code produced or consumed.

- [ ] **Step 1: Confirm the stale text is there**

Run: `grep -n "Dirky Link\|bridgespeak" CLAUDE.md`
Expected: three matches — the title line, the Backend heading, and the single-test example.

- [ ] **Step 2: Fix the title**

In `CLAUDE.md`, change:
```
## Project: Dirky Link
```
to:
```
## Project: Dinky Link
```

- [ ] **Step 3: Fix the Backend section**

Change:
```
## Backend (`backend/bridgespeak-service`)
```
to:
```
## Backend (`backend/api`)
```

Change:
```bash
./gradlew test --tests "com.bridgespeak.chat.service.UserServiceTest"
```
to:
```bash
./gradlew test --tests "dev.dukecvar.dinkylink.api.DinkyLinkApiApplicationTests"
```

- [ ] **Step 4: Fix the React version**

Change:
```
- **Frontend**: React 18, Vite, TypeScript, TailwindCSS, shadcn/ui
```
to:
```
- **Frontend**: React 19, Vite, TypeScript, TailwindCSS, shadcn/ui
```

- [ ] **Step 5: Verify the fix**

Run: `grep -n "Dirky Link\|bridgespeak\|React 18" CLAUDE.md`
Expected: no output (no matches).

Run: `grep -n "Dinky Link\|backend/api\|React 19" CLAUDE.md`
Expected: three matches.

- [ ] **Step 6: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: fix stale project naming and React version in CLAUDE.md"
```

---

### Task 2: Database migrations + Postgres/Redis/Flyway in `local/docker-compose.yaml`

Creates the `database/` Flyway project and the first three `local/docker-compose.yaml` services (Postgres, Redis, Flyway — nginx comes in Task 3). Brings up real infra and proves the schema migration applies.

**Files:**
- Create: `database/migrations/V1__create_records_table.sql`
- Create: `database/README.md`
- Create: `local/docker-compose.yaml`
- Create: `local/README.md`

**Interfaces:**
- Produces: a `records` table (`shortcode CHAR(8)` PK, `urlhash BYTEA`, `url VARCHAR(2048)`, `last_touched_timestamp TIMESTAMPTZ`, indexed on `urlhash`) in a `dinkylink` Postgres database reachable at `localhost:5432` (user/pass `dinkylink`), and Redis reachable at `localhost:6379`. Task 4 (backend) consumes both.

- [ ] **Step 1: Create the migration file**

```bash
mkdir -p database/migrations
```

Create `database/migrations/V1__create_records_table.sql`:
```sql
-- docs/design.md specifies a 128-bit (16-byte) urlhash and a plain
-- `binary(128)` column; Postgres has no fixed-length binary type, so
-- this uses bytea sized for a 16-byte hash in practice.
CREATE TABLE records (
    shortcode              CHAR(8)       PRIMARY KEY,
    urlhash                BYTEA         NOT NULL,
    url                    VARCHAR(2048) NOT NULL,
    last_touched_timestamp TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_records_urlhash ON records (urlhash);
```

- [ ] **Step 2: Create `database/README.md`**

```markdown
# database

Flyway migration scripts for the Dinky Link Postgres schema.

## Layout

- `migrations/` — versioned SQL migrations, named `V<version>__<description>.sql`
  per Flyway convention. Applied in order by the `flyway` service in
  `local/docker-compose.yaml`; never applied by the backend itself
  (`spring.flyway.enabled=false` in `backend/api`).

## Adding a migration

Add a new `V<next-version>__<description>.sql` file to `migrations/`, then
run `docker compose up -d flyway` from `local/` to apply it. Never edit an
already-applied migration file — add a new one instead.
```

- [ ] **Step 3: Create `local/docker-compose.yaml`**

```bash
mkdir -p local
```

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: dinkylink
      POSTGRES_USER: dinkylink
      POSTGRES_PASSWORD: dinkylink
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U dinkylink -d dinkylink"]
      interval: 5s
      timeout: 5s
      retries: 10

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 10

  flyway:
    image: flyway/flyway:11-alpine
    command:
      - -url=jdbc:postgresql://postgres:5432/dinkylink
      - -user=dinkylink
      - -password=dinkylink
      - -connectRetries=10
      - -locations=filesystem:/flyway/sql
      - migrate
    volumes:
      - ../database/migrations:/flyway/sql
    depends_on:
      postgres:
        condition: service_healthy
    restart: "no"

volumes:
  pgdata:
```

- [ ] **Step 4: Create `local/README.md`**

```markdown
# local

Local development infrastructure for Dinky Link.

## Usage

```bash
cd local
docker compose up -d
```

Brings up Postgres, Redis, and runs Flyway migrations (the `flyway`
container applies `../database/migrations` then exits — `docker compose ps`
will show it as `Exited (0)`, which is expected).

For a completely fresh environment (wipes the Postgres volume too):

```bash
docker compose down -v
docker compose up -d
```

## Running the app

`docker compose up -d` only brings up infra. Run the backend and frontend
yourselves, outside compose:

```bash
cd backend/api && ./gradlew bootRun    # localhost:8080
cd frontend && npm run dev             # localhost:5173
```

The `nginx` gateway (added once `backend/api` and `frontend` exist) proxies
`http://localhost:8080` to whichever of those is running on the host; if
you've only run `docker compose up -d`, requests through nginx will 502
until you start the backend/frontend dev servers.
```

- [ ] **Step 5: Bring up infra and verify the migration applied**

```bash
cd local
docker compose up -d
```

Run: `docker compose ps`
Expected: `postgres` and `redis` show `healthy`; `flyway` shows `Exited (0)`.

Run: `docker compose exec postgres psql -U dinkylink -d dinkylink -c "\d records"`
Expected: the `records` table with columns `shortcode`, `urlhash`, `url`,
`last_touched_timestamp`, and index `idx_records_urlhash`.

- [ ] **Step 6: Verify Flyway is idempotent (Review Focus)**

```bash
docker compose up -d flyway
```

Run: `docker compose logs flyway | tail -5`
Expected: `Schema "public" is up to date. No migration necessary.` — not an
error, and the container still exits 0 (`docker compose ps` shows `flyway`
as `Exited (0)`).

- [ ] **Step 7: Verify data survives `down` but not `down -v` (Review Focus)**

```bash
docker compose down
docker compose up -d
```

Run: `docker compose exec postgres psql -U dinkylink -d dinkylink -c "\d records"`
Expected: the `records` table is still there (no re-migration needed —
`docker compose logs flyway` should again say "no migration necessary").

```bash
docker compose down -v
docker compose up -d
```

Run: `docker compose logs flyway | tail -5`
Expected: `Migrating schema "public" to version "1 - create records table"` —
proving the volume was actually wiped and Flyway re-created the table from
scratch.

- [ ] **Step 8: Leave infra running for later tasks, commit**

```bash
git add database/migrations/V1__create_records_table.sql database/README.md \
        local/docker-compose.yaml local/README.md
git commit -m "feat: add database migrations and postgres/redis/flyway compose services"
```

---

### Task 3: nginx gateway

Adds the `nginx` service to `local/docker-compose.yaml` and its routing config. Full routing behavior (hitting real backend/frontend upstreams) is verified in Task 6, once both exist — this task verifies the config loads correctly and fails cleanly with nothing behind it, which is the exact state after a bare `docker compose up -d`.

**Files:**
- Create: `local/nginx/nginx.conf`
- Modify: `local/docker-compose.yaml`

**Interfaces:**
- Consumes: nothing yet at runtime (upstreams `host.docker.internal:8080` / `:5173` don't exist until Tasks 4/5).
- Produces: `http://localhost:8080` — routes `GET /` and other non-alnum-single-segment paths to the `frontend` upstream, `POST /` and `GET /<8-62 alnum chars>` to the `backend_api` upstream. Task 6 consumes this to verify end-to-end routing.

- [ ] **Step 1: Create `local/nginx/nginx.conf`**

```bash
mkdir -p local/nginx
```

```nginx
worker_processes auto;

events {
    worker_connections 1024;
}

http {
    server_tokens off;
    client_max_body_size 16k;

    upstream backend_api {
        server host.docker.internal:8080;
    }

    upstream frontend {
        server host.docker.internal:5173;
    }

    # Route GET / to the frontend, POST / to the backend, without `if`.
    map $request_method $root_upstream {
        default frontend;
        POST    backend_api;
    }

    # Only rate-limit the write path; empty key = not accounted (nginx
    # skips limiting when a limit_req_zone key evaluates to "").
    map $request_method $write_limit_key {
        default "";
        POST    $binary_remote_addr;
    }

    limit_req_zone $write_limit_key zone=write:10m rate=5r/s;
    limit_req_zone $binary_remote_addr zone=read:10m rate=50r/s;
    limit_conn_zone $binary_remote_addr zone=perip:10m;

    server {
        listen 80;
        limit_conn perip 20;

        # Forward the real Host header — proxy_pass to a named upstream
        # otherwise sends the upstream block's literal name (e.g.
        # "backend_api") as Host, which Tomcat rejects (underscores are
        # not valid in a domain name).
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        location = / {
            limit_req zone=write burst=10 nodelay;
            proxy_pass http://$root_upstream;
        }

        location ~ ^/[0-9A-Za-z]+$ {
            limit_req zone=read burst=100 nodelay;
            proxy_pass http://backend_api;
        }

        location / {
            proxy_pass http://frontend;
        }
    }
}
```

- [ ] **Step 2: Add the `nginx` service to `local/docker-compose.yaml`**

Add to the `services:` block (after `flyway`):
```yaml
  nginx:
    image: nginx:1.27-alpine
    ports:
      - "8080:80"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
    extra_hosts:
      - "host.docker.internal:host-gateway"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
```

- [ ] **Step 3: Verify config syntax and startup**

```bash
cd local
docker compose up -d
```

Run: `docker compose exec nginx nginx -t`
Expected: `nginx: configuration file /etc/nginx/nginx.conf syntax is ok` /
`test is successful`.

Run: `docker compose ps nginx`
Expected: `Up` (not restarting/crash-looping).

- [ ] **Step 4: Verify graceful failure with no upstreams running (Review Focus)**

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/
```

Expected: `502` — proves nginx loaded the routing config correctly and is
attempting to reach the (not-yet-running) frontend, rather than erroring
out or returning something misleading. This is the exact state anyone
following `CLAUDE.md`'s bare `docker compose up -d` instruction will see
until they also start the backend/frontend dev servers (documented in
`local/README.md` already).

- [ ] **Step 5: Commit**

```bash
git add local/nginx/nginx.conf local/docker-compose.yaml
git commit -m "feat: add nginx gateway with method-based routing and rate limiting"
```

---

### Task 4: Backend scaffold (`backend/api`)

Generates the Kotlin/Spring Boot skeleton via start.spring.io, wires it to the Postgres/Redis started in Task 2, and confirms it boots.

**Files:**
- Create: `backend/api/` (generated — `build.gradle.kts`, `settings.gradle.kts`, `gradlew`, `gradle/wrapper/*`, `src/main/kotlin/dev/dukecvar/dinkylink/api/DinkyLinkApiApplication.kt`, `src/test/kotlin/dev/dukecvar/dinkylink/api/DinkyLinkApiApplicationTests.kt`)
- Modify: `backend/api/src/main/resources/application.properties` → replace with `application.yml`
- Create: `backend/api/README.md`

**Interfaces:**
- Consumes: Postgres at `localhost:5432/dinkylink` and Redis at `localhost:6379` (Task 2).
- Produces: `GET http://localhost:8080/actuator/health` → `200 {"status":"UP"}`. Task 6 consumes this (via nginx) to confirm backend routing.

- [ ] **Step 1: Generate the project**

```bash
mkdir -p backend
cd backend
curl -s "https://start.spring.io/starter.zip" \
  -d type=gradle-project-kotlin \
  -d language=kotlin \
  -d bootVersion=4.1.1 \
  -d baseDir=api \
  -d groupId=dev.dukecvar \
  -d artifactId=api \
  -d name=DinkyLinkApi \
  -d description="Dinky Link API" \
  -d packageName=dev.dukecvar.dinkylink.api \
  -d packaging=jar \
  -d javaVersion=25 \
  -d dependencies=web,actuator,data-jdbc,postgresql,data-redis \
  -o api.zip
unzip -q api.zip
rm api.zip
```

Expected: `backend/api/` now contains `build.gradle.kts`, `gradlew`,
`src/main/kotlin/dev/dukecvar/dinkylink/api/DinkyLinkApiApplication.kt`, etc.

- [ ] **Step 2: Replace `application.properties` with `application.yml`**

```bash
rm backend/api/src/main/resources/application.properties
```

Create `backend/api/src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: DinkyLinkApi
  datasource:
    url: jdbc:postgresql://localhost:5432/dinkylink
    username: dinkylink
    password: dinkylink
  data:
    redis:
      host: localhost
      port: 6379
  flyway:
    enabled: false

management:
  endpoints:
    web:
      exposure:
        include: health
```

- [ ] **Step 3: Create `backend/api/README.md`**

```markdown
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

Serves on `http://localhost:8080`. Health check: `GET /actuator/health`.
```

- [ ] **Step 4: Build**

```bash
cd backend/api
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. (First run downloads a Java 25 toolchain and
the Gradle 9.7.1 distribution if not already cached — can take a couple of
minutes.)

- [ ] **Step 5: Run and verify health, with infra up**

Ensure Task 2's infra is still running (`docker compose ps` in `local/`
shows `postgres`/`redis` healthy), then:

```bash
cd backend/api
./gradlew bootRun
```

In another terminal:
```bash
curl -s http://localhost:8080/actuator/health
```

Expected: `{"status":"UP",...}` with HTTP 200.

- [ ] **Step 6: Verify the app doesn't self-migrate (Review Focus)**

While the app from Step 5 is running, check its startup output:

```bash
./gradlew bootRun --console=plain 2>&1 | grep -i flyway
```

Expected: no output — nothing in the startup log mentions Flyway. (This
passes today because `flyway-core` isn't on the classpath at all; the
assertion exists so a future dependency change that pulls it in trips a
visible check rather than silently double-migrating against the Task 2
Flyway container.)

Stop the app (Ctrl-C) once verified.

- [ ] **Step 7: Commit**

```bash
git add backend/api
git commit -m "feat: scaffold backend/api Kotlin/Spring Boot project"
```

---

### Task 5: Frontend scaffold (`frontend/`)

Generates the Vite + React + TypeScript project, adds Tailwind CSS 4 and shadcn/ui, and confirms it builds and serves. Binds the dev server to `0.0.0.0` so the nginx container (running inside the colima/Docker VM) can reach it via `host.docker.internal` — bound to `127.0.0.1` (Vite's default), it isn't reachable from inside the VM at all.

**Files:**
- Create: `frontend/` (generated by `npm create vite@latest` — `package.json`, `src/`, etc.)
- Modify: `frontend/vite.config.ts`
- Modify: `frontend/tsconfig.json`, `frontend/tsconfig.app.json`
- Modify: `frontend/src/index.css`
- Create: `frontend/README.md`

**Interfaces:**
- Produces: `GET http://localhost:5173/` → Vite's default starter page, `0.0.0.0`-bound. Task 6 consumes this (via nginx) to confirm frontend routing.

- [ ] **Step 1: Generate the project**

```bash
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
```

- [ ] **Step 2: Add Tailwind CSS 4**

```bash
npm install -D tailwindcss @tailwindcss/vite
```

Replace `frontend/src/index.css`'s contents with:
```css
@import "tailwindcss";
```

- [ ] **Step 3: Wire Tailwind and the `@/` path alias into `vite.config.ts`**

Replace `frontend/vite.config.ts` with:
```typescript
import path from "node:path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vite"

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "./src"),
    },
  },
  server: {
    // Bind 0.0.0.0, not just 127.0.0.1 — the nginx container (Task 3)
    // reaches this via host.docker.internal from inside the Docker VM,
    // which can't see a loopback-only bind.
    host: true,
  },
})
```

- [ ] **Step 4: Add the `@/*` path alias to TypeScript config**

In `frontend/tsconfig.json`, add a top-level `compilerOptions` key:
```json
{
  "files": [],
  "references": [
    { "path": "./tsconfig.app.json" },
    { "path": "./tsconfig.node.json" }
  ],
  "compilerOptions": {
    "paths": {
      "@/*": ["./src/*"]
    }
  }
}
```

In `frontend/tsconfig.app.json`, add `paths` as the first entry inside the
existing `compilerOptions` block (do **not** add `baseUrl` — it's
deprecated under this TypeScript version and `paths` alone is sufficient
with `moduleResolution: "bundler"`):
```json
{
  "compilerOptions": {
    "paths": {
      "@/*": ["./src/*"]
    },
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.app.tsbuildinfo",
    ...
```

- [ ] **Step 5: Initialize shadcn/ui**

```bash
npx shadcn@latest init -d -y
```

Expected: `components.json` created, `src/lib/utils.ts` (the `cn()`
helper) added, and `class-variance-authority`/`cn`/`lucide-react` etc.
added to `package.json`.

- [ ] **Step 6: Create `frontend/README.md`**

```markdown
# frontend

The Dinky Link web UI (React, Vite, TypeScript, Tailwind CSS, shadcn/ui).

## Install and run

```bash
npm install
npm run dev       # dev server on http://localhost:5173, bound to 0.0.0.0
npm run build     # production build (tsc + vite)
```

## Adding shadcn/ui components

```bash
npx shadcn@latest add <component>
```

Use the `cn()` helper from `@/lib/utils` to compose Tailwind classes —
no ad-hoc inline styles (see the repo's `CLAUDE.md`).
```

- [ ] **Step 7: Verify the production build**

```bash
npm run build
```

Expected: `tsc -b && vite build` completes with `✓ built in ...`, no
TypeScript errors, no `__dirname`/config deprecation warnings.

- [ ] **Step 8: Verify the dev server binds correctly**

```bash
npm run dev
```

Expected output includes both:
```
➜  Local:   http://localhost:5173/
➜  Network: http://<lan-ip>:5173/
```
(the `Network` line confirms it bound `0.0.0.0`, not just loopback — if
only `Local:` appears, `server: { host: true }` from Step 3 didn't take).

In another terminal: `curl -s -o /dev/null -w "%{http_code}\n" http://localhost:5173/`
Expected: `200`.

Stop the dev server (Ctrl-C) once verified.

- [ ] **Step 9: Commit**

```bash
git add frontend
git commit -m "feat: scaffold frontend Vite/React/Tailwind/shadcn project"
```

---

### Task 6: Full-stack integration verification + top-level README

Brings up every piece at once and proves nginx routes each request class to the correct upstream — this is the only point where Tasks 2–5 are tested together. Also writes the top-level quickstart doc.

**Files:**
- Create: `README.md` (top-level)

**Interfaces:**
- Consumes: Task 2 (`docker compose up -d`), Task 3 (nginx on `:8080`), Task 4 (backend on `:8080`... internally, reached via nginx), Task 5 (frontend on `:5173`, reached via nginx).
- Produces: nothing further downstream — this is the terminal verification task.

- [ ] **Step 1: Bring up the full stack**

```bash
cd local
docker compose up -d
```

In separate terminals:
```bash
cd backend/api && ./gradlew bootRun
```
```bash
cd frontend && npm run dev
```

Wait for both to report ready (`Started DinkyLinkApiApplicationKt...` and
`VITE ... ready in ...`).

- [ ] **Step 2: Verify `GET /` routes to the frontend**

```bash
curl -s http://localhost:8080/ | grep -o "<title>[^<]*</title>"
```

Expected: the Vite starter page's `<title>` tag (not a Spring error page,
not a 502).

- [ ] **Step 3: Verify `POST /` routes to the backend (Review Focus: Host header)**

```bash
curl -s -o /tmp/post-root.json -w "%{http_code}\n" -X POST http://localhost:8080/ \
  -H "Content-Type: application/json" -d '{"url":"http://example.com"}'
cat /tmp/post-root.json
```

Expected: HTTP `404` with a JSON body like
`{"timestamp":"...","status":404,"error":"Not Found","path":"/"}` — this
is Spring's default "no controller mapped" response, proving the request
reached the backend with a valid `Host` header. (Before the `proxy_set_header
Host $host;` fix in Task 3, this failed with a `400` Tomcat error page
and `IllegalArgumentException: The character [_] is never valid in a
domain name` in the backend log, because nginx forwarded the upstream
name `backend_api` as the Host header.)

- [ ] **Step 4: Verify `GET /<shortcode>` routes to the backend**

```bash
curl -s -o /tmp/shortcode.json -w "%{http_code}\n" http://localhost:8080/abc12345
cat /tmp/shortcode.json
```

Expected: HTTP `404` with the same Spring JSON error shape as Step 3
(proving it reached the backend, not the frontend's SPA fallback).

- [ ] **Step 5: Verify a multi-segment frontend asset path still routes to the frontend**

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/src/main.tsx
```

Expected: `200` (Vite serving the dev asset — proves the shortcode regex,
which requires a single path segment with no `/`, doesn't swallow
frontend routes).

- [ ] **Step 6: Tear down**

Stop the backend (Ctrl-C) and frontend (Ctrl-C) dev servers. Leave infra
running or run `cd local && docker compose down` per preference — no
further tasks depend on it being up.

- [ ] **Step 7: Write the top-level `README.md`**

```markdown
# Dinky Link

A URL shortener. See `docs/design.md` for the full product and system design.

## Local development

Start the infra (Postgres, Redis, nginx gateway, Flyway migrations):

```bash
cd local
docker compose up -d
```

Run the backend:

```bash
cd backend/api
./gradlew bootRun
```

Run the frontend:

```bash
cd frontend
npm install
npm run dev
```

With all three running, the app is available at `http://localhost:8080`
(nginx routes `GET /` to the frontend, `POST /` and `GET /<shortcode>` to
the backend). Running only `docker compose up -d` brings up infra only —
requests through nginx will 502 until the backend and frontend are also
running.

## Sub-projects

- `database/` — Flyway migrations ([README](database/README.md))
- `backend/api` — the public API ([README](backend/api/README.md))
- `frontend/` — the web UI ([README](frontend/README.md))
- `local/` — Docker Compose infra ([README](local/README.md))
```

- [ ] **Step 8: Commit**

```bash
git add README.md
git commit -m "docs: add top-level README with local dev quickstart"
```
