# VaultCore capacity testing — controlled environments only

The project does **not** currently support a verified 1,000,000 concurrent API requests. These scripts create reproducible baselines and deliberately default to low, local-only load. They are not a million-user generator.

## Prerequisites
- Backend running on localhost:8080 (e.g. `docker compose up --build`) and required local env secrets.
- Install Grafana k6 locally: `brew install k6` (macOS) or use its documented supported installation method.
- Synthetic accounts only. Never use customer data or real bank credentials.

## Read-only health baseline

```bash
k6 run tests/performance/health.js
RATE=50 DURATION=1m k6 run tests/performance/health.js
```

The arrival-rate executor attempts 10 requests/second over 30 seconds by default. No account balances or transfers are changed. Local test caps at 200 req/sec. `RATE` measures **requests/sec**, not simultaneous in-flight requests or concurrent users.

## Authenticated account-read test

Create and own a synthetic zero-balance account with the hardened backend. Obtain a short-lived test JWT via `/api/auth/login`, then export it in your local shell (do not commit it):

```bash
export TEST_JWT='<short-lived-test-token>'
export TEST_ACCOUNT_NUMBER='<synthetic-account-number>'
k6 run tests/performance/account-read.js
```

Default 20 requests/minute accommodates a single-IP backend rate limiter (60/minute) and the 15-minute token lifetime. Remote load requires explicit authorization and `APPROVED_STAGING_TEST=I_HAVE_PERMISSION`, and the script refuses rates over 30/minute per test process. No unsafe rate-limit bypass is provided.

## Understanding the results

Record `http_reqs`, `http_req_failed`, `http_req_duration` p95/p99, `dropped_iterations`, CPU/memory/GC, PostgreSQL connections, SQL locks, Redis latency and 429/503 rates. Save machine specifications, service replicas, exact test dataset and application commit SHA. A single-machine k6 run cannot prove million-request concurrency.

## Road to an authorized distributed benchmark

1. First prove single-node success at low rates with production-like data.
2. Add synthetic traffic mix: cached account reads, history, authentication and transfers (each with distinct accounts, credentials and stable idempotency keys).
3. Scale horizontally and provision realistic PostgreSQL/Redis capacity; use distributed load agents in an isolated staging network.
4. Keep tenant-level controls and realistic identity diversity. Do not disable security merely to inflate benchmark numbers.
5. Verify zero double-spends, conservation of ledger value and correct 429/503 behavior under load.
6. Run independent security, failure injection and disaster recovery checks before calling the system banking-grade.

**1 million users, requests/sec and requests in-flight are different targets.** Resolve which is required and measure it consistently. Observe load-generator bottlenecks as well as target-system bottlenecks.
