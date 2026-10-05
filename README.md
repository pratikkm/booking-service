# Seat Booking Service — Spring Boot + PostgreSQL

A small assigned-seat reservation API designed around database-enforced correctness under contention.

## Stack

- Java 21
- Spring Boot 3.5.x
- PostgreSQL 16+
- Spring JDBC + transactions
- Micrometer / Prometheus
- Docker / Docker Compose
- Testcontainers integration tests

## Correctness model

This implementation chooses **explicit cancellation** rather than time-boxed holds. A successful reservation is immediately `confirmed`; cancelled reservations release their seats back to `available`. There is no `held` database state, so the API reports `held_seats: 0`.

Reservation requests are **all-or-nothing**: asking for `A12,A13` succeeds only when both are available. Otherwise the whole request receives `409` with `reason: seat-taken`.

The atomic decision is made inside one PostgreSQL transaction:

1. The `(show_id,user_id,idempotency_key)` row is created with `INSERT ... ON CONFLICT DO NOTHING`, then selected `FOR UPDATE`. This serializes concurrent retries and stores the response so a retry cannot create another reservation.
2. The transaction takes a PostgreSQL transaction-scoped advisory lock derived from `show_id:user_id`. That serializes a user's concurrent reservations and cancellation operations for one show, making the per-user limit check exact.
3. Requested `show_seats` rows are selected `FOR UPDATE` in deterministic seat-number order. The transaction checks all are `AVAILABLE`, updates them to `CONFIRMED`, inserts the reservation and seat mappings, and commits as one unit.
4. A database `UNIQUE(show_seat_id)` constraint on `reservation_seats` is a second line of defense: one seat cannot be linked to two active reservations.

Because multi-seat requests lock seats in sorted order, transactions taking overlapping seat sets use the same lock order, avoiding lock-order deadlocks. The per-user advisory lock is taken before seat locks by both reservation and cancellation paths.

## API

### Create show (admin)

`POST /shows`

```json
{
  "name": "friday-night",
  "seats": ["A1", "A2", "A3"],
  "price_paise": 25000,
  "per_user_limit": 4
}
```

`per_user_limit` is optional; default is 4.

Authentication: `Authorization: Bearer <ADMIN_TOKEN>`.

### Get show

`GET /shows/{id}`

Returns every seat and aggregate counts. The response includes `held_seats: 0` because this implementation has no active hold state.

### Reserve seats

`POST /shows/{id}/reserve`

Authentication: `Authorization: Bearer <user-token>`.

```json
{
  "seats": ["A12"],
  "idempotency_key": "checkout-123"
}
```

The identity is **only** the bearer token. There is no user ID in the request body.

- `201` confirmed reservation.
- `409 reason=seat-taken` when any requested seat is unavailable.
- `409 reason=per-user-limit` when the request would exceed the show limit.
- Reusing the same key and same logical seat set replays the stored response exactly.
- Reusing the key with a different seat set returns `409 reason=idempotent-key-mismatch`.

Seat lists are canonicalized by trimming and sorting before hashing, so the same logical set in a different order is treated as the same idempotent request.

### Cancel reservation

`POST /reservations/{reservationId}/cancel`

Authentication: `Authorization: Bearer <owner-token>`.

Only the reservation owner may cancel. Cancellation is transactional: seats are locked, released, and the reservation is marked cancelled in the same transaction. The seats then become re-bookable.

## Health

- Liveness: `GET /live` or `GET /actuator/health/liveness`
- Readiness: `GET /ready` or `GET /actuator/health/readiness`
- Prometheus metrics: `GET /actuator/prometheus`

Readiness executes `SELECT 1` against PostgreSQL and returns `503` when PostgreSQL is unreachable.

## Metrics

The main metrics are:

- `reservations_confirmed_total`
- `reservations_declined_total{reason="seat-taken|per-user-limit|idempotent-replay|idempotent-key-mismatch"}`
- `seats_available{show_id="..."}`

`idempotent-replay` is tracked in the declined-reason family because the challenge explicitly asks for that dimension; a replay itself returns the original HTTP status and does not increment confirmed reservations again.

The `seats_available` gauge queries PostgreSQL at scrape time, so it remains aligned with the database even across process restarts or multiple application instances.

## Structured logs

Console logs are JSON via Logstash Logback Encoder and include `request_id`, plus `show_id` and `user_id` for reservation calls. Every response gets an `X-Request-Id` header, generated when the caller did not supply one.

## Run locally

### Docker Compose

```bash
docker compose up --build
```

The API starts on `http://localhost:8080` and PostgreSQL on port `5432`.

### Maven

Requires Java 21 and PostgreSQL.

```bash
mvn spring-boot:run
```

Environment variables:

- `DB_URL` — default `postgresql://localhost:5432/ticketing` (the application adds the `jdbc:` prefix)
- `DB_USER` — default `ticketing`
- `DB_PASSWORD` — default `ticketing`
- `ADMIN_TOKEN` — default `admin-token` locally; set a secret in deployment
- `DEFAULT_PER_USER_LIMIT` — default `4`
- `DB_POOL_SIZE` — default `40`
- `SERVER_MAX_THREADS` — default `300`

## Smoke test

Create a show:

```bash
curl -X POST http://localhost:8080/shows \
  -H 'Authorization: Bearer admin-token' \
  -H 'Content-Type: application/json' \
  -d '{"name":"friday-night","seats":["A1","A2","A3"],"price_paise":25000}'
```

Then reserve `A1` as `user-1`:

```bash
curl -X POST http://localhost:8080/shows/SHOW_ID/reserve \
  -H 'Authorization: Bearer user-1' \
  -H 'Content-Type: application/json' \
  -d '{"seats":["A1"],"idempotency_key":"checkout-1"}'
```

## Concurrency burst

One command:

```bash
./burst.sh http://localhost:8080
```

The script creates a fresh show, storms a hot seat with many distinct users, also sends idempotent retries, and prints confirmed / declined-by-reason / 5xx distributions plus the final reconciliation.

Defaults are deliberately adjustable through environment variables:

```bash
REQUESTS=20000 HOT_SEAT_REQUESTS=10000 CONCURRENCY=200 ./burst.sh https://your-live-url
```

The script uses only Python's standard library.

## Tests

The integration suite uses Testcontainers with PostgreSQL and verifies:

- exactly one hot-seat winner under concurrent requests;
- no 5xx responses for contention;
- per-user limit under concurrent requests;
- idempotent replay and same-key/different-body rejection;
- show reconciliation after contention.

```bash
mvn test
```

## Deployment

`render.yaml` contains a Render blueprint for a web service plus PostgreSQL. The deployment expects `ADMIN_TOKEN` to be supplied as a secret.

For any public deployment, expose `/actuator/prometheus` only to the intended monitoring surface if the hosting platform allows private networking; the sample leaves it reachable for evaluation.

## Git history

The repository is intentionally committed in incremental steps so a reviewer can inspect how the concurrency design, observability, tests, and deployment assets were added.
