# VaultCore — 10,000 concurrent committed transfers and p95 under 50 ms

## Definitions and limits
The stretch goal means 10,000 simultaneous in-flight money-transfer requests, with measured HTTP p95 latency below 50 ms and every successful response representing a durable committed PostgreSQL transfer with balanced double-entry ledger. This is **not verified today**. It is not equivalent to 10,000 requests per second or 10,000 customers logged in. Queue acceptance is not proof of a completed transfer.

At 10,000 in-flight requests and mean response time of 50 ms, Little's Law suggests throughput near 200,000 requests/sec under steady load. p95 does not determine the mean. Infrastructure capacity and transaction commit times must be measured.

## Key bottlenecks
- PostgreSQL row-level locks serialize transactions on hot account pairs.
- Transaction writes, ledger postings, WAL fsync, DB connection pools, JVM memory, and networking constrain the real completion rate.
- Current Redis per-IP quota allows 60 transfer-group requests per minute and will reject a single-generator stress test. Design and verify legitimate identity-aware rate controls and trusted-proxy handling rather than disabling security.
- The current service is a Spring Boot monolith with one PostgreSQL primary, not a proven distributed system.

## Tests added
- PostgreSQL Testcontainers: 32 separate account pairs using 8 worker threads, checking balances and exactly one DEBIT plus CREDIT per committed transfer.
- PostgreSQL Testcontainers: 12 concurrent requests on the same pair to verify serializable effective outcomes and no overdraft.
- k6 transfer-concurrency.js: unique pre-funded and authenticated sandbox account pairs, one transfer per VU with a unique idempotency key. Defaults to 2 VUs, local cap 50. Controlled remote default cap 100. Larger runs require explicit authorization and an isolated staging setup.
- k6 HTTP thresholds: p95 below 50 ms, unexpected errors under 0.1%, and checks above 99.9%. These are **failing gates when not met**, not guarantees.

## Local baseline
Install k6 on your Mac (brew install k6). Provision and fund **two synthetic source accounts** and **two destination accounts** in an isolated local test database; the example fixture is only illustrative and NOT funded. Obtain a short-lived test JWT for the owning user, then set TEST_JWT and a unique RUN_ID in your local environment without sharing or committing them. Run from the repository root:

    VUS=2 k6 run tests/performance/transfer-concurrency.js

The sample account numbers are in tests/performance/transfer-fixtures.example.json. Actual valid seeded accounts must match. Do not reuse a RUN_ID with changed parameters, and do not run against real financial services.

## Benchmark evidence required
Capture topology, test SHA, number of API replicas, Postgres CPU/IO/WAL/lock waits, Redis metrics, 201/429/503 distribution, dropped requests, p50/p95/p99, completed RPS, and all-account balance reconciliation. Verify every successful transfer has exactly two balanced immutable ledger legs. Test same-key retries separately and monitor DB health for an extended soak. Scale incrementally on authorized infrastructure; a personal laptop cannot validate 10k simultaneous DB commits.

## Status
The backend must add safe auditable sandbox funding, more robust risk/rate controls and production-like observability before meaningful high-volume transfer tests. A Java CI pass is not an empirical throughput or latency result. SMS remains out of scope.
