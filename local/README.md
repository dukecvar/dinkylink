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
