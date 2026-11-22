# VaultCore backend security audit and roadmap

## This milestone

1. Require signed JWT Bearer tokens on all banking APIs, with a bounded 15-minute lifetime, issuer and audience validation. Authentication endpoints are the only public API routes.
2. Store passwords using BCrypt and enforce minimum length. Registration and login are explicitly a local sandbox identity flow, not a bank-grade identity/verification system.
3. Bind account access and transaction history to the authenticated email; require source ownership before any transfer. Do not disclose pre-existing unowned demo accounts through a token.
4. Reject nonzero account opening balances supplied by a client. Only a future admin-controlled, audited sandbox funding mechanism should introduce funds.
5. Preserve transactional transfers, deterministic pessimistic locking, global transaction idempotency keys and after-commit cache eviction. Scope cached account data by authenticated identity.
6. Atomic Redis rate-limit counters; fail closed with 503 when Redis is unavailable, including login/register.
7. Limit exposed ports in Compose and require a high-entropy JWT secret. Disable public Swagger endpoints by default.

## Critical release blockers (not solved yet)

- **Schema migration**: replace local Hibernate `ddl-auto=update` with versioned Flyway/Liquibase migrations, database backups and `validate` mode. Design deliberate handling of old unowned accounts.
- **Identity**: add verified identity/email, password reset with secure recovery, optional MFA, login lockout and session revocation/refresh-token strategy. Evaluate managed OIDC provider and asymmetric signing / rotation.
- **Transaction accounting**: implement append-only double-entry ledger, reconciliations, limits and controlled sandbox funding. Add robust concurrency/idempotency load tests with actual PostgreSQL containers.
- **Observability**: tracing, structured redacted logs, security audit trail, metrics and alerts. No credentials, bearer tokens or PII in logs.
- **Deployment**: require TLS, strict CORS/origin policy, credentials in a secret manager, restricted database networks, managed PostgreSQL/Redis, backups, monitoring, least-privilege users and security reviews.
- **Service boundaries**: the GitHub main backend is currently a single Spring Boot service, not fully implemented microservices; do not claim Kafka/outbox/sagas are deployed.
- **API contracts**: update Postman collection and frontend contracts for new mandatory Bearer auth and rejected nonzero balances.
- **Financial compliance**: never handle real customer money or production personal information in this sandbox.

## Security model caveats

JWTs live until expiration: there is no token revocation, compromise recovery or secure refresh path yet. Browser clients should keep the Bearer token in memory only until a secure session design is selected. CSRF is disabled **only** because this API authenticates by explicit Authorization headers, not browser cookies.

Redis rate limits key on servlet `remoteAddr`; when deploying behind a reverse proxy, configure *trusted* proxy IP handling first. Never trust an arbitrary X-Forwarded-For header directly.

The global idempotency key is database-unique, so separate customers must currently use distinct keys. A per-user scoped key migration and permanent request fingerprinting are recommended.

Hibernate may add `owner_email` to an existing local demo database, but ownership backfilling is intentionally not automatic. Do not assign legacy balances to a user without verification.
