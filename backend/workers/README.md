# backend/workers

Internal background jobs for Dinky Link (Kotlin, Spring Boot). Never
exposed through the nginx gateway — see `docs/design.md` ("Worker(s)").

## Prerequisites

Postgres and Redis running via `cd ../../local && docker compose up -d`.

## Build and test

```bash
./gradlew build
./gradlew test
```

## Run locally

```bash
./gradlew bootRun
```

Runs on `http://localhost:8082` (not routed through nginx). Health check:
`GET /actuator/health`.

## Jobs

### Last-touched flush worker

Every 60 seconds (`LastTouchedFlushWorker`), drains the `last-touched:live`
Redis hash — populated by `backend/api` on every shortcode redirect — into
Postgres:

1. If `last-touched:live` exists, rename it to `last-touched:flushing:<ts>`.
2. Scan the flushing bucket in batches of 1000, updating each record's
   `last_touched_timestamp` and removing the entry from the bucket.
3. Delete the (now-empty) flushing key.

If the live bucket doesn't exist (nothing to flush), the job is a no-op
until the next cycle.
