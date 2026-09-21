# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project: Dirky Link

A URL shortener. A URL that's too long to copy to a tweet this tool creates a short URL to a redirect to the long URL.

## Tech Stack

- **Backend**: Kotlin, Spring Boot 4, Gradle (kts), JVM 25. API Project in `backend/api`. Workers Project in `backend/workers`
- **Database**: PostgreSQL with Flyway migrations
- **Cache**: Redis
- **Frontend**: React 18, Vite, TypeScript, TailwindCSS, shadcn/ui
- **E2E Tests**: Separate Gradle project in `backend/api-tests` using REST Assured

## Local Development

Start all infrastructure and the backend service:
```bash
cd local
docker compose up -d
```

This starts PostgreSQL, runs Flyway migrations, Redis.

To bring up a completely fresh ephemeral environment (re-runs FusionAuth's Kickstart provisioning from scratch):
```bash
cd local
docker compose down -v
docker compose up -d
```

Also bring up the containerized frontend (nginx serving the Vite build) on port 5173:
```bash
docker compose up -d --build frontend
```

Run E2E tests (requires the stack to be up):
```bash
cd local
docker compose --profile test up api-tests
```

## Backend (`backend/bridgespeak-service`)

Build and run unit tests:
```bash
./gradlew build
./gradlew test
```

Run a single test class:
```bash
./gradlew test --tests "com.bridgespeak.chat.service.UserServiceTest"
```

Run the service locally (requires Postgres and Redis running via docker compose):
```bash
./gradlew bootRun
```

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
