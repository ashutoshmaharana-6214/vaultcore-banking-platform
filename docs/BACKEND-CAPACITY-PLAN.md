# VaultCore backend capacity and security targets

## Status and language

The 1,000,000 concurrent-request aspiration is **unverified**. Current implementation is a single Spring Boot REST application on PostgreSQL and Redis, with an account-level locking model; it does not have verified service decomposition, a replicated ledger, or a distributed scaling benchmark. Do not claim financial-production or compliance certification.

## Precise metrics

- In-flight requests: requests actively processing at a moment in time.
- Requests per second (RPS): completed/arriving traffic over time, not the number of active users.
- Concurrent connected clients: open network connections; not every client is issuing an API request.
- Measure reads, histories and transfers separately; write locks and ACID transaction cost differ from cached reads.
- Initial proposed service level objectives to validate: cached reads p95 < 200 ms; unexpected error rate < 0.1%; **zero** balance-conservation violations, duplicate postings or unauthorized successful access.

## Proposed architecture (not built)

```text
Distributed k6 load generators -> WAF + rate limiting + L7 load balancer
                                  -> horizontally scaled API replicas
                                  -> Redis cache and Redis rate-limit counter
                                  -> PostgreSQL primary for financial transactions
                                  -> transactional outbox -> Kafka consumers
                                  -> audit/reconciliation/metrics pipelines
```

Critical: PostgreSQL writes must remain strongly consistent, idempotent and serializable at appropriate isolation boundaries. Stateless API replicas alone cannot remove row-lock contention or database connection limits. Avoid globally serializing operations to a single hot account. Load test with realistic account-distribution and distinct user identities; keep throttling enabled.

## Security verification gates (OWASP ASVS 5.0 reference)
- Authentication: brute-force defense, verified identity, session expiration, credential reset, token rotation, MFA, MFA step-up for risky transfers.
- Authorization: IDOR/BOLA access controls, cross-tenant denial, transfer-source ownership, transaction-history isolation, least privilege.
- Input: validation, mass assignment, request size limits, money amount precision and bounds, JSON error hygiene.
- Crypto: managed signing keys, rotation, TLS everywhere, encrypted backups and sensitive-data minimization.
- Financial invariants: append-only double-entry ledger, signed/auditable events, replay-safe idempotency, reconciled balances, failed-transfer compensation.
- Operation: threat modeling, SAST/SCA/secret scanning, redacted logs, penetration testing, alerts and incident response exercises.
- Availability: overload control, quotas, circuit breakers, resource limits, retries with jitter, timeouts, controlled shutdown, database backup restore drills.

## Test pyramid and evidence
1. Unit: JWT issuer/audience/expiry rejection, rate-limit fail-closed, owner-scoped account access, value validation.
2. Docker-backed integration: real PostgreSQL transaction locking and concurrent idempotency; Redis availability.
3. HTTP contract: 401/403/404, 429/503, validation, no cross-user data leakage.
4. Controlled performance: local k6 smoke and account read, then distributed staging tests.
5. Soak + chaos: prolonged load, Redis/DB failover drills, process restarts and rollback tests.
6. Capacity certification: published run configuration, topology, p50/p95/p99 latencies, completed throughput, success/error distribution, financial invariant checks.

## Open design blockers
- Local Hibernate schema update (replace with migrations).
- Single application and shared relational database (not distributed microservices).
- No append-only double-entry ledger or audited funding workflow.
- JWT tokens cannot be revoked; email ownership is currently a temporary sandbox identity key.
- Global idempotency keys lack per-user partitioning.
- Basic IP limiter: one IP has a 60/min banking limit; a single load generator must not be misrepresented as 1M users. Design distributed, identity-aware quotas under a trusted proxy boundary.
- Unverified recovery, backups, capacity, monitoring and compliance.
- Need gated post-auth deposit simulation before meaningful transfer load testing.

See `tests/performance/README.md` for controlled measurement commands. Security verification is aligned conceptually to OWASP ASVS 5.0; this is **not a security certification**.

## New latency objective
Target p95 under **50 ms for cached authenticated account reads** in a same-region controlled benchmark, with security and rate limits enabled. It is not a target for financial writes, OTP provider calls, or SMS provider delivery. Measure p50/p95/p99, throughput, environment, dataset, concurrency and load duration. This is an unverified SLO until demonstrated by published test results.
