# VaultCore — safe synthetic transfer funding

## Scope

Sandbox funding is an **opt-in, non-web Java runner**. It is not an API,
not real money, and not suitable for a shared or production database.
SMS is not part of this branch.

The runner creates a synthetic system clearing account,
`VC-SANDBOX-TREASURY`, and one isolated source/destination account pair
per requested fixture. The source receives 100 **test units**, and the
treasury receives an equal negative test balance. Both ledger postings,
the transaction record, and account balances commit atomically. 
The public API cannot transfer money to or from SYSTEM accounts.

The Treasury account explicitly permits negative balances but
ordinary CUSTOMER accounts are still protected by a database
nonnegative-balance constraint.

## Required safeguards

- Database must be named **banking_sandbox** and should be an isolated
  Docker Compose project (`-p vaultcore-sandbox`).
- Explicit `sandbox-seed` profile, `vaultcore.sandbox.seed-run=true`,
  `VAULTCORE_SANDBOX_ACK=I_AGREE_TEST_ONLY`, non-web application type.
- Registered synthetic owner required (not a real customer).
- Default maximum 100 pairs; larger runs up to 10,000 require
  `SANDBOX_APPROVE_SCALE=true`.
- Do not use `docker compose down -v` unless you explicitly want to
  permanently erase **that Compose project's database volume**.
- Do not use personal phone numbers, real bank credentials, or customer data.
- A single customer-token/IP limiter still caps transfer calls. Never
  disable security controls merely to raise benchmark throughput.

## Local startup

This assumes a new checkout on
`feature/sandbox-funding-fixtures`, Docker Desktop running, and
local `.env` with strong `DB_PASSWORD` and `JWT_SECRET`.

1. Start separate Postgres, Redis and API using the override:

```bash
docker compose -f docker-compose.yml -f docker-compose.sandbox.yml \
  -p vaultcore-sandbox up --build -d
```

2. Register a **synthetic** test-only user through
`POST http://127.0.0.1:8080/api/auth/register`.
Use a unique locally chosen password and do not share it in chat.
Then stop the API *only* before running the one-shot seed:
```bash
docker compose -f docker-compose.yml -f docker-compose.sandbox.yml \
  -p vaultcore-sandbox stop banking-api
```

3. Run the seed process. Replace the synthetic email with the one you
registered. The `-e` switches pass variables to the ephemeral container;
the database and Redis remain private to Compose.

```bash
docker compose -f docker-compose.yml -f docker-compose.sandbox.yml \
  -p vaultcore-sandbox run --rm \
  -e SPRING_PROFILES_ACTIVE=sandbox-seed \
  -e VAULTCORE_SANDBOX_SEED_RUN=true \
  -e VAULTCORE_SANDBOX_ACK=I_AGREE_TEST_ONLY \
  -e SANDBOX_OWNER_EMAIL=benchmark@example.test \
  -e SANDBOX_PAIRS=2 \
  -e SPRING_MAIN_WEB_APPLICATION_TYPE=none \
  banking-api --spring.main.web-application-type=none \
  --vaultcore.sandbox.seed-run=true
```

4. Restart the API:

```bash
docker compose -f docker-compose.yml -f docker-compose.sandbox.yml \
  -p vaultcore-sandbox start banking-api
```

5. Generate k6 account fixtures (contains **no credentials**):

```bash
python3 scripts/generate_transfer_fixtures.py --pairs 2
```

The generated file
`tests/performance/transfer-fixtures.local.json` is ignored by Git.
Only the accounts created during seeding are valid. The user who owns
these fixtures must also be the user whose JWT is supplied to k6.

6. Obtain a new short-lived JWT through local login and run:

```bash
export TEST_JWT='<your short-lived synthetic account JWT>'
export RUN_ID='trial-001'
VUS=2 TRANSFER_FIXTURES=./transfer-fixtures.local.json \
  k6 run tests/performance/transfer-concurrency.js
```

Do not paste the token to the chat. The k6 test issues one transfer per
fixture pair and sends no SMS.

## Idempotency

Seed operation `sandbox-funding-v1-N` is unique per fixture index.
Restarting the seed against the same database returns the recorded
transaction reference instead of crediting a source account twice.
A second synthetic identity must use a separate sandbox database,
rather than reassigning already-provisioned accounts.

**Warning:** Transfer performance still has not been benchmarked.
10,000 simultaneous commits with p95 below 50 ms remains an unverified
stretch target. The seed runner itself is sequential and intended for
controlled fixture preparation, not for benchmarking.
