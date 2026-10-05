# Write-up

## Atomic decision and race freedom

The service uses PostgreSQL as the system of record and makes the booking decision inside a single database transaction.

For each `(show, user)`, the transaction takes `pg_advisory_xact_lock(hashtextextended(show_id || ':' || user_id, 0))`. That makes concurrent booking/cancel operations for the same user and show serialize, so the per-user seat count cannot be checked concurrently by two requests that both then exceed the limit.

For the requested seats, the transaction executes `SELECT ... FOR UPDATE` against `show_seats` using a deterministic `ORDER BY seat_number`. The first transaction that locks a free seat can confirm it; the next transaction waits for the lock and then sees `CONFIRMED`, producing a `409 seat-taken`. Multi-seat requests therefore have a consistent lock acquisition order. The application also has a `UNIQUE(show_seat_id)` constraint on `reservation_seats` as a database-level guard against double assignment.

The partial-request policy is **all-or-nothing**. A request for multiple seats confirms only if every requested seat exists and is currently available.

## Idempotency

The idempotency record is stored in PostgreSQL in `idempotency_keys` with a unique `(show_id, user_id, idempotency_key)` constraint. The request's canonical seat list is SHA-256 hashed and stored with the key.

The transaction inserts the key using `ON CONFLICT DO NOTHING`, then selects that row `FOR UPDATE`. This means two concurrent retries of the same key cannot both create independent reservations. The first completed response, including a domain decline, is stored in the row. A later identical request replays the stored HTTP status and response body without touching seat state. A later request that reuses the key with a different seat set gets `409 idempotent-key-mismatch`.

The idempotency scope is per show and authenticated user, which keeps identities isolated while preventing a caller from using another user's key namespace.

## Holds and cancellation

The implementation uses an explicit cancellation model rather than an automatic expiry model. Reservations become `confirmed` immediately and can later be cancelled only by their owner. Cancellation locks the reservation, then the user's advisory lock, then the reservation's seats in sorted order; it marks the seats available, removes the seat mappings, and marks the reservation cancelled in one transaction.

Removing `reservation_seats` on cancellation is intentional: the table has `UNIQUE(show_seat_id)`, while historical reservation metadata remains in `reservations`.

## Consistency vs. availability

The service prioritizes consistency for seat ownership. PostgreSQL row locks and constraints can block briefly under contention, but the system never intentionally returns a seat to two users. If PostgreSQL becomes unavailable, readiness fails closed and booking cannot safely continue because the database is the source of truth.

The service does not attempt cross-region or partition-tolerant booking. Under a database partition, availability is sacrificed rather than accepting uncertain ownership. A successful reservation is committed only when PostgreSQL accepts the transaction.

## Observability

The service exposes Prometheus metrics for confirmed reservations, reservation outcomes by reason, and available seats by show. The `seats_available` gauge reads the current count from PostgreSQL at scrape time so it tracks the system of record even after a process restart.

Every request receives an `X-Request-Id` and logs are JSON with MDC fields for request ID and reservation context. This gives an operator a stable correlation handle across application logs while a load burst is running.

At 2am, useful alerts would include persistent readiness failures, any 5xx responses on reservation endpoints, a sudden increase in database lock wait time or pool exhaustion, reconciliation mismatches, and a divergence between reservation counters and independently queried show state. These are operational alert concepts, not automated alert rules in this sample.

## AI usage

AI was used directly to scaffold and review this implementation: project structure, Spring Boot/PostgreSQL wiring, transaction boundaries, PostgreSQL locking patterns, idempotency storage design, metrics/logging setup, Docker/Render assets, and the concurrency test/burst harness were generated and then assembled into the repository.

The important design decisions were made specifically against the challenge requirements: PostgreSQL remains the atomic system of record; idempotency is persisted rather than held in memory; per-user serialization uses transaction-scoped advisory locks; multi-seat locking is deterministic; and the database has a uniqueness guard as a second correctness layer.

## What I'd do next

For a production system I would replace the demo bearer-token identity mechanism with a real OIDC/JWT integration, add rate limiting and abuse controls, separate metrics by deployment instance where useful, add database migration tooling such as Flyway, add a durable outbox for downstream payment/order events, and run fault-injection tests around database restarts, connection exhaustion, and transaction cancellation.
