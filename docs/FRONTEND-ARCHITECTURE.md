# VaultCore frontend architecture (Phase 1)

## Current boundaries

```text
Browser
└── React + TypeScript / Vite
    ├── Routing and UI shell
    ├── Dashboard (read-only Spring Actuator health probe)
    ├── Accounts (placeholder)
    ├── Transfers (placeholder; disabled)
    ├── Transactions (placeholder)
    └── Authentication (placeholder)
         ↓ local Vite development proxy
Spring Boot API currently in main (localhost:8080)
         ├── PostgreSQL
         └── Redis
```

## Planned target boundaries

React frontend -> HTTPS ingress / API gateway -> authenticated and authorized backend -> account & payment domain services -> PostgreSQL and Redis. This is a **target**, not a claim that all services exist in GitHub main.

## Decisions
- Feature-oriented code under `src/features`.
- React Router for client-side navigation.
- Typed browser fetch utilities in `src/shared/api`.
- Native CSS for a small, dependency-light initial shell. Tailwind is optional after foundation is stable.
- Introduce TanStack Query for secured backend reads and mutations once API contracts are finalized.
- Prefer backend-managed, secure HttpOnly cookies or a carefully designed OIDC/OAuth flow over persisting bearer tokens in localStorage.
- No browser-held database credentials or confidential service credentials.

## Acceptance criteria for milestone 1
- `npm install && npm run build` succeeds.
- Dashboard and placeholder routes render with responsive navigation.
- Health status accurately reflects availability of the local backend.
- Banking mutations are not wired to unprotected endpoints.
- Review and merge this branch independently of unfinished local backend microservices changes.

## Next milestones
1. Finalize protected backend auth/session and API contracts.
2. Add accessible login/registration flow and protected routes.
3. Connect accounts and transaction history with loading/error/empty states.
4. Add transfer form with server-side authorization, idempotency and verification.
5. Test, containerize, deploy to staging over HTTPS.
