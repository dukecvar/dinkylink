# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project: Dinky Link

A URL shortener. A URL that's too long to copy to a tweet this tool creates a short URL to a redirect to the long URL.

## Tech Stack

- **Backend**: Kotlin, Spring Boot 4, Gradle (kts), JVM 25. API Project in `backend/api`. Workers Project in `backend/workers`
- **Database**: PostgreSQL with Flyway migrations
- **Cache**: Redis
- **Frontend**: React 19, Vite, TypeScript, TailwindCSS, shadcn/ui
- **E2E Tests**: Separate Gradle project in `backend/api-tests` using REST Assured

## Local Development

Start infrastructure — PostgreSQL, Redis, the nginx gateway, and Flyway
migrations (the `flyway` container applies migrations then exits):
```bash
cd local
docker compose up -d
```

This does **not** start the backend or frontend — run those yourself,
outside compose (see below). Until they're running, requests through the
nginx gateway (`http://localhost:8080`) will 502.

To bring up a completely fresh ephemeral environment (wipes the Postgres
volume; Flyway re-applies all migrations from scratch on next `up`):
```bash
cd local
docker compose down -v
docker compose up -d
```

Run the backend, workers, and frontend:
```bash
cd backend/api && ./gradlew bootRun      # localhost:8081
cd backend/workers && ./gradlew bootRun  # localhost:8082, internal-only
cd frontend && npm run dev               # localhost:5173
```

With all of the above running, the app is available at
`http://localhost:8080` (nginx routes `GET /` to the frontend, `POST /`
and `GET /<shortcode>` to the backend).

## Backend (`backend/api`)

All commands below require Postgres and Redis running (`cd local &&
docker compose up -d`) — the generated context-load test opens a real
datasource connection, so even `./gradlew build`/`test` fail without it.

Build and run unit tests:
```bash
./gradlew build
./gradlew test
```

Run a single test class:
```bash
./gradlew test --tests "dev.dukecvar.dinkylink.api.DinkyLinkApiApplicationTests"
```

Run the service locally:
```bash
./gradlew bootRun
```
Serves on `http://localhost:8081` directly (nginx's public gateway is on
`8080` — see Local Development above).

## Backend (`backend/workers`)

Internal background jobs — never routed through the nginx gateway. Same
Postgres/Redis prerequisites and commands as `backend/api` (`./gradlew
build`/`test`/`bootRun`). Serves on `http://localhost:8082`. See
`backend/workers/README.md` and "Worker(s)" in `docs/design.md` for job
details.

## Frontend (`frontend/`)

```bash
npm install
npm run dev       # dev server
npm run build     # production build (tsc + vite)
```

## Architecture

### Database

## Keeping Docs in Sync

When a change alters how a part of the project works (setup steps, commands, architecture, endpoints), update the relevant `README.md` in the same change — the top-level [`README.md`](README.md) and, if the sub-project has its own (`backend/api`, `backend/workers`, `frontend/`, `database/`, `cache/`, etc.), that one too. Update this file (`CLAUDE.md`) alongside them whenever it goes stale.

## Code Style
- backend
  - Prefer `val` over `var`; use data classes for DTOs.
  - Constructor injection only — no field injection.
  - Controllers are thin; business logic belongs in `@Service` classes. Use appropriate stereotypes such as `@RestController`
  - Unit tests are isolated and readable.
  - E2E tests go in `backend/api-tests`.
- Frontend: functional components + hooks, strict TypeScript, Tailwind utility classes via `cn()` helper — no ad-hoc inline styles.
