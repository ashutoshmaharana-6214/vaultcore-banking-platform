# VaultCore — Digital Banking API (backend hardening)

Java 21 + Spring Boot 3.4.x + PostgreSQL + Redis. This is an **engineering portfolio sandbox**, not a real bank. This backend security milestone adds customer registration/login, hashed credentials, short-lived signed access tokens, authorization-bound account access, a fail-closed API rate limiter and safer demo-account funding rules.

> **Important**: This branch is a security foundation, not a production-ready financial service. Do not deploy publicly without the release blockers in [docs/BACKEND-SECURITY.md](docs/BACKEND-SECURITY.md).

## Run locally (requires Docker / Compose)

```bash
cp .env.example .env
# Replace DB_PASSWORD with a locally generated strong value in .env.
# Add JWT_SECRET to .env using a newly generated random secret:
printf '\nJWT_SECRET=%s\n' "$(openssl rand -hex 32)" >> .env
docker compose up --build
```

Do not add secrets to GitHub. The app intentionally fails startup if JWT_SECRET is missing or too short. Redis and PostgreSQL are not exposed on host ports by default; the API listens only on 127.0.0.1:8080 for local Compose.

## API examples

Register (choose a real test password of at least 12 characters):
```bash
curl -i http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"use-a-long-unique-test-password"}'
```

Log in:
```bash
curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"use-a-long-unique-test-password"}'
```

The login response contains a **15-minute Bearer token**. Supply it using `Authorization: Bearer <accessToken>` for all account/transfer/history endpoints. Keep tokens out of logs, commits, screenshots and browser localStorage.

Create a demo account with a **zero** opening balance:
```http
POST /api/accounts
Authorization: Bearer <accessToken>
Content-Type: application/json

{"accountNumber":"DEMOACCOUNT01","holderName":"Demo Customer","openingBalance":0}
```

Transfer funds only from an account owned by the authenticated user:
```http
POST /api/transfers
Authorization: Bearer <accessToken>
Idempotency-Key: stable-unique-key-for-this-transfer
Content-Type: application/json

{"fromAccount":"DEMOACCOUNT01","toAccount":"DEMOACCOUNT02","amount":25.00}
```

New accounts start at zero; **funding is deliberately unavailable through the public API**. Future controlled sandbox funding will be auditable; callers cannot mint balances by sending arbitrary opening amounts.

Other endpoints:
- `GET /api/accounts/{accountNumber}` — owner only.
- `GET /api/accounts/{accountNumber}/transactions` — owner only.
- `GET /actuator/health` — local readiness check.

## Tests

```bash
mvn -B clean verify
```

The backend preserves PostgreSQL transactional transfers, deterministic row locking, idempotency-key persistence, and owner-specific Redis balance cache keys. Rate-limiter counters use one Redis Lua script for atomic increment + expiry. When Redis is unavailable, protected API requests return 503 rather than failing open.

## Deployment / migration caveats

- `HIBERNATE_DDL_AUTO=update` is **local-only**. A database migration process and schema validation are required before any shared staging deployment.
- Existing demo accounts have no owner assigned and intentionally **cannot be accessed** using a new customer's token. Do not claim they belong to a user.
- Swagger/OpenAPI is disabled; avoid publishing operational endpoints.
- The checked-in Kubernetes manifests and old Postman collection are starter artifacts, not hardened deployment instructions.
- Use TLS, managed secrets, backups, audit trails, stronger user identity verification and enhanced fraud/idempotency resilience before any public service.
