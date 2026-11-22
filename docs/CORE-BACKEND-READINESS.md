# VaultCore — backend first, no SMS

**Status:** Engineering portfolio sandbox. This branch is intentionally based on
\`feature/backend-capacity-security-tests\`, before SMS work. Do not merge SMS until
core financial correctness, security, and release checks have passed.

## Implemented in this milestone
- Flyway baseline schema for **new databases**; Hibernate in \`validate\` mode.
- PostgreSQL constraints for nonnegative balances, positive transfer amounts, unique
  idempotency keys, and valid ledger posting types.
- Two append-only ledger postings (DEBIT and CREDIT) per transfer, stored inside the
  **same ACID transaction** as balance updates and transaction record.
- Strict owner-only reconciliation endpoint:
  \`GET /api/transfers/{reference}/reconciliation\`.
- Database-backed tests for concurrent retry and zero-sum ledger postings.
- Docker image copies the correct versioned JAR.
- This does **not** implement SMS or external banking connections.

## Ledger invariants

For every committed transfer of x units from A to B:
1. A decreases by x, B increases by x, so A+B remains unchanged.
2. One DEBIT posting for A and one CREDIT posting for B, both with amount x.
3. Both postings refer to the same transaction reference and are immutable.
4. Retry with the same idempotency key returns the existing transfer without posting again.

## Migration warning — existing local databases

Flyway V1 is designed for a **new, empty** PostgreSQL database. It must not be applied
directly over the older schema from the prior Hibernate \`ddl-auto=update\` prototype.
Existing volume data is **not automatically migrated**. Preserve and back up any
existing data, or use a new Compose project name and new named volume. Never run
\`docker compose down -v\` without explicitly accepting permanent data loss.

Do not force \`flyway.baseline-on-migrate=true\` merely to make startup pass:
a deliberate legacy-data migration, reconciliation and ownership audit are required.

## Local quick start (new isolated Compose project)

\`\`\`bash
# In a NEW checkout of this branch; use synthetic accounts only:
cp .env.example .env
# Replace DB_PASSWORD with a random local password; set JWT_SECRET to a random >=32-byte secret.
docker compose -p vaultcore-core up --build
\`\`\`

The account creation API accepts **zero** opening balance only; there is not yet a
user-accessible sandbox funding path. Existing integration tests seed synthetic
balances directly in isolated Testcontainers databases. An auditable, local-only
funding workflow must be added before broader end-to-end payment demonstrations.

## Still required before describing the backend as finished
- Explicit sandbox funding backed by offsetting ledger postings, operational audit
  logging, account-specific limits, transfer retry semantics under failure injection.
- Auth security: verified identity, revocation/rotation, MFA, password recovery and
  abuse controls. Scope idempotency keys to the authenticated customer.
- Independent concurrency, isolation and load tests; replay and negative-case tests.
- Stronger DB constraints for paired posting invariants; recovery/reconciliation jobs.
- End-to-end API tests, vulnerability scanning, TLS/secrets, monitoring and alerting.
- Precise load tests for **p95 under 50 ms on cached account reads**. A passing build
  is not latency proof; **one million concurrent API requests remains unverified**.
