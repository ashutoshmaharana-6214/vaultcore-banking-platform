# Digital Banking & Payment System

A Java 21 / Spring Boot REST API demonstrating core banking workflows with PostgreSQL as the system of record and Redis for low-latency caching and API rate limiting.

## Implemented in this starter
- Create accounts and retrieve account details.
- Transfer funds atomically in a PostgreSQL transaction; pessimistic row locks are acquired in a deterministic account-number order to reduce deadlocks.
- Transaction history (latest 50 records involving an account).
- Redis-backed account read cache (30-second TTL).
- Cache entries for both accounts are evicted only after a successful database commit, preventing stale cached balances after transfers.
- Redis-backed per-IP/per-endpoint rate limiting (60 requests per minute). If Redis is unavailable, this starter currently fails open and adds a response header; change this policy for production financial systems.
- Docker Compose for API, PostgreSQL and Redis; OpenAPI UI; health endpoints; Postman collection; initial Kubernetes manifests.

## Run locally
Requires Java 21, Maven 3.9+, Docker Desktop and Docker Compose.

```bash
docker compose up --build
```

API: http://localhost:8080
Swagger UI: http://localhost:8080/swagger-ui.html
Health: http://localhost:8080/actuator/health

## Run tests
```bash
mvn clean verify
```

## Redis behavior
- Cache name: `account-balance`; key: account number; TTL: 30 seconds.
- `GET /api/accounts/{accountNumber}` is cacheable.
- Successful `POST /api/transfers` evicts both account keys after transaction commit.
- API paths under `/api/` are limited to 60 requests per IP and endpoint per 60-second window.
- Redis is not authoritative for money. PostgreSQL transaction boundaries and row locks protect balance updates.

## API examples
Create an account:
```http
POST /api/accounts
Content-Type: application/json

{"accountNumber":"ASHU00000001","holderName":"Ashutosh","openingBalance":5000.00}
```
Transfer:
```http
POST /api/transfers
Content-Type: application/json

{"fromAccount":"ASHU00000001","toAccount":"ASHU00000002","amount":250.00}
```

Import `postman/Digital-Banking-API.postman_collection.json` into Postman. Run the two account-creation requests first, then balance lookup, transfer and history. Use unique account numbers if rerunning against a database with existing data.

## Kubernetes (starter manifests)
The manifests in `k8s/` include Redis and the API. They assume a PostgreSQL Service named `postgres` already exists in the `banking` namespace and that the API image has been built and loaded into the cluster as `digital-banking-payment-system:latest`. They are a starting point, not a complete production deployment. For a real deployment, use Kubernetes Secrets, persistent volumes, managed PostgreSQL/Redis, TLS/Ingress, network policies and resource tuning.

## Important scope and security notes
This is a portfolio learning project, not production banking software. Authentication/JWT, authorization, idempotency-key persistence, Kafka eventing, fraud rules, audit trails, distributed saga orchestration and full microservice decomposition are not yet implemented. Do not claim those features on a CV until they are added and tested. The demo Compose credentials are for local development only.
