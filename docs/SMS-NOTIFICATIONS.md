# VaultCore SMS notification feature — sandbox only

This branch implements the first SMS infrastructure milestone. Real texts are **disabled by default**. Enable only with explicit authorization, real verification, verified recipient consent, and an approved SMS provider configuration. Using a test bank does not mean sending texts to arbitrary numbers is acceptable.

## Design

1. An authenticated customer requests OTP verification of an E.164 phone number using `POST /api/me/phone/verification`. The number is stored temporarily in Redis and Twilio Verify sends an OTP.
2. The customer submits the OTP to `POST /api/me/phone/verification/confirm`. Only an approved provider verification persists the phone number to the user record.
3. The customer explicitly enables SMS transaction notifications using `PUT /api/me/notifications/sms` body `{"enabled":true}`.
4. A completed simulated transfer writes a row to the SMS outbox for each opted-in, verified account owner, **inside the same PostgreSQL transaction as the transfer**. Retried transfers do not enqueue duplicate notifications.
5. A scheduled worker claims pending SMS and submits to Twilio Messaging in a separate operation. It checks consent again immediately before sending. Provider timeouts/retries do not block the transfer response. A submitted SMS is not proof of handset delivery.

### Endpoints

All endpoints require the same JWT Bearer token as banking APIs.

- `POST /api/me/phone/verification` with `{"phoneNumber":"+919876543210"}`
- `POST /api/me/phone/verification/confirm` with `{"phoneNumber":"+919876543210","code":"123456"}` (illustrative OTP only)
- `PUT /api/me/notifications/sms` with `{"enabled":true}` or `{"enabled":false}`

### Configuration

All SMS settings are disabled by default. Do not expose credentials to frontend or commit them to Git.

```text
SMS_ENABLED=false
TWILIO_ACCOUNT_SID=...
TWILIO_AUTH_TOKEN=...
TWILIO_VERIFY_SERVICE_SID=...
TWILIO_MESSAGING_SERVICE_SID=...
```

Use a secret manager or local private Compose environment. An account and service sender must be approved to send into its target region. India-based sender registration, DLT template and regulatory requirements may apply. Costs are charged by the provider.

### Delivery semantics

This is **at-least-once attempted submission**, not exactly-once handset delivery. A worker can crash after the SMS provider accepts a request but before the database records success; retry could submit the same alert again. Production enhancements require provider callbacks, delivery receipts, deduplication policy, a dead-letter/replay workflow and stronger operational controls. Worker restarts recover stale claims; success means provider accepted the message, not handset delivered it. Do not report transfer outcome based on the SMS provider status.

### Security cautions

- Real SMS only for verified numbers with the user's opt-in; no bulk, promotional or unsolicited messaging.
- Current OTP frequency constraints are by user plus endpoint IP and need more comprehensive abuse controls: per-number limits, multi-account abuse, OTP fraud, provider-side risk checks and monitoring before public access.
- Phone numbers and message bodies reside in the DB outbox. Restrict database privileges and configure retention, access audit, encryption and deletion policies before real users.
- Never store OTPs, bearer tokens or provider credentials in logs.
- Use explicit HTTPS, managed provider credentials, rotation and verified webhook signatures for provider callbacks.
- Add a real account-funded sandbox ledger and comprehensive integration tests before claiming banking-grade suitability.
