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
