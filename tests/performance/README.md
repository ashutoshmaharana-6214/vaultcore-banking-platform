# VaultCore controlled performance testing

The target for **cached authenticated account reads is p95 < 50 ms** in a same-region setup, under a stated load with security and per-identity quotas enabled. This is a target, not a measured capability.

The tests are conservative, read-only and intended for an owned/authorized environment with synthetic data. Do not bypass server rate limits. One million concurrent API requests is an **unverified stretch goal**, requiring independently measured multi-region/distributed load infrastructure.

## Local health baseline
```bash
k6 run tests/performance/health.js
RATE=50 DURATION=1m k6 run tests/performance/health.js
```
## Cached authenticated account reads
Log in to your own local synthetic account, set environment variables without checking secrets into Git, then run:
```bash
export TEST_JWT='<short-lived-test-token>'
export TEST_ACCOUNT_NUMBER='<synthetic-account>'
k6 run tests/performance/account-read.js
```

The read test defaults to 20 requests/minute and a 2-minute run to respect the current per-IP limits and 15-minute JWT lifetime. Warm up the read cache before interpreting any measurements. Record hardware, commit SHA, server CPU/GC, PostgreSQL connections, Redis latency, request rate, median/p95/p99, error rates and load-generator saturation. The read benchmark does not include SMS provider latency and cannot prove financial transfer latency.

The current backend's rate limiter intentionally returns 429 when limits are exceeded, so simply sending more requests from one source is not a valid measure of one-million-client capacity.
