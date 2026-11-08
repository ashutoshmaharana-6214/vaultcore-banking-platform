# Redis integration design

## 1. Account read cache

`BankingService.getAccount` uses Spring Cache (`@Cacheable`) with cache name `account-balance` and the account number as the key. `RedisConfig` sets a 30-second TTL and JSON value serialization so Java records can be cached safely. Null results are not cached.

## 2. Cache consistency after transfers

A transfer updates both account rows inside one PostgreSQL transaction while holding pessimistic write locks in a deterministic account-number order. The service registers a transaction synchronization and evicts the source and destination balance keys in `afterCommit`. This avoids deleting the cache before the database commit and avoids leaving a stale balance in Redis after a successful transfer. If the transaction rolls back, the after-commit callback does not run.

## 3. API rate limiting

`RateLimitFilter` uses Redis `INCR` and a one-minute expiry for a key composed of client IP and API path. It allows up to 60 requests per minute for each IP/path pair and returns HTTP 429 after that. Redis failure currently uses a documented fail-open policy for local development; production should decide fail-open versus fail-closed based on threat model and availability requirements. Behind a reverse proxy, only trust forwarded client IP headers after configuring trusted proxies.

## 4. Inspect Redis locally

```bash
docker compose exec redis redis-cli
KEYS account-balance::*
KEYS rate-limit::*
TTL 'account-balance::ASHU00000001'
```

Spring's Redis cache key prefix may vary with configuration. Use `SCAN` rather than `KEYS` in production.

## 5. Important invariant

Redis is never the ledger. Account balances and transaction records are persisted in PostgreSQL. Never use a cached balance to authorize a transfer; transfer logic locks and reads the current database rows.
