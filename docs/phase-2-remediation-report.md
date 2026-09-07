# Phase 2 Remediation Report

## Initial Findings

The verification baseline found partial/failed BOT-01, SEC-01, BOT-02, BOT-03
and MGT-01 evidence: Telegram calls inside a database transaction; no PostgreSQL
onboarding/replacement proof; global Bot lookup before authorization; incomplete
HTTP/redaction/concurrency tests; literal `active` key metadata; missing persisted
replacement/reconnect recovery; and a Bot DELETE operation outside BOT-01 scope.
The available frontend checkout has no authentication implementation, so FE-05 is
still a cross-phase dependency.

## Root Causes

| Finding | Root cause | Affected component |
|---|---|---|
| Long Telegram transaction | orchestration and persistence were combined | Bot application service |
| Weak recovery evidence | no persisted operation state or SQL E2E assertions | credential persistence/tests |
| Tenant lookup | global ID methods were used before membership decision | Bot repository/service |
| Missing failure proof | HTTP adapters lacked complete local-stub matrix | Telegram clients |
| API scope drift | OpenAPI retained unsupported Bot DELETE | OpenAPI/controller |

## Implemented Corrections

| Finding | Change | Files | Tests |
|---|---|---|---|
| Transaction boundary | prepare/verify/activate phases; Telegram calls outside transactions | `bot/application/BotApplicationService.java`, `BotCredentialPersistenceService.java` | `BotOnboardingIT` transaction probe |
| Recovery/state | V4 operation/history tables and one in-progress operation per Bot | `V4__add_bot_credential_onboarding_state.sql`, bot model/data classes | `BotOnboardingIT` restart/retry/replacement |
| Tenant isolation | membership-scoped locked Bot queries | `BotRepository.java`, Bot services | `AuthenticationIT`, `BotOnboardingIT` |
| Idempotency | PostgreSQL unique winner reread and parallel REST requests | `BotCreateTransaction.java`, `BotApplicationService.java` | `AuthenticationIT` |
| Telegram failures | bounded transport, JSON/content validation and safe mappings | `TelegramBotApiClient.java` | `TelegramBotApiClientTest`, E2E |
| Encryption metadata | configured key ID with AES-GCM AAD/envelope | cipher/persistence/config | `BotCredentialCipherTest`, E2E SQL |
| Management delivery | bounded webhook registration, secret header, native WebApp | management config/controller/handler | management transport/runtime/controller tests |
| Contract/REST | removed Bot DELETE; required header maps to safe 400 | `openapi.yaml`, `RestExceptionHandler.java` | `OpenApiContractTest`, `AuthenticationIT` |

## Database Changes

V4 is forward-only Flyway. It adds encrypted onboarding operations and replacement
history, operation type/state, optimistic versioning, foreign keys and a partial
unique index preventing multiple pending operations per Bot. No plaintext
credential column or key material was introduced; active credentials remain
one-to-one with the Bot.

## Transaction Boundary

```text
short authorization/reservation transaction
→ getMe (no transaction)
→ short verification-state transaction
→ setWebhook (no transaction)
→ short activation transaction
```

`BotOnboardingIT` wraps the Telegram gateway and asserts
`TransactionSynchronizationManager.isActualTransactionActive() == false` for
getMe/setWebhook during connect, replacement and reconnect. It also resumes a
persisted pending reconnect after a second Spring context starts.

## Tenant Isolation

Bot get/update and all credential commands use membership-scoped repository
queries before returning a Bot or calling Telegram. Two PostgreSQL-backed tenants
are exercised; cross-tenant credential/reconnect calls return safe not-found
errors and the gateway call count remains unchanged.

## Credential Lifecycle

Initial connect stores an encrypted pending operation, verifies getMe, registers
the webhook, and atomically activates. Failed verification or webhook registration
leaves an existing active credential unchanged. Replacement keeps A active while B
is pending; successful B activation archives A's encrypted envelope and switches
the active row. Reconnect reuses the active encrypted credential/secret, shares a
single pending operation under concurrency, and can resume after restart.

## Security Verification

| Check | Status | Evidence |
|---|---|---|
| Plaintext token/webhook secret/key in PostgreSQL | PASS | `BotOnboardingIT` direct SQL and decrypt assertions |
| Secrets in REST/errors/logs | PASS | E2E capture and failure-matrix assertions |
| Configured key ID (not `active`) | PASS | SQL assertions against active/history rows |
| Token in webhook URL | PASS | captured setWebhook request |
| Production long polling | PASS | profile pin and runtime mode test |
| Tamper/AAD/wrong-key handling | PASS | `BotCredentialCipherTest` |

## Telegram Adapter Verification

Local HTTP stubs verify getMe/setWebhook success, 401, 429, 5xx, malformed JSON,
unexpected content, timeout and connection-refused behavior. Safe codes distinguish
invalid credential, rate limit, unavailable, network and protocol failures. Token
appears in outbound URI only where Telegram requires it and is never logged or
returned. Management registration has bounded timeouts and sanitized failures.

## Commands

| Command | Exit code | Actual result |
|---|---:|---|
| `mvn clean verify` (service) | 0 | 54 Surefire + 29 Failsafe; 83/83 passed, 0 failures/errors/skips; PostgreSQL/Flyway/Hibernate validation passed |
| `mvn -q -Dit.test=BotOnboardingIT failsafe:integration-test failsafe:verify` | 0 | onboarding, replacement, reconnect, restart, isolation E2E passed |
| `mvn -q -Dit.test=AuthenticationIT failsafe:integration-test failsafe:verify` | 0 | auth, rollback, CRUD, tenant and idempotency tests passed |
| `docker build -f Dockerfile .` (service) | 0 | application image built |
| `docker compose --env-file .env.example config --quiet` | 0 | valid; application service remains commented for OPS topology |
| `git diff --check` | 0 | no whitespace errors |

## Acceptance Matrix

| Task | Criterion | Before | After | Evidence |
|---|---|---|---|---|
| AUTH-01 | HMAC/freshness/forged rejection | PARTIAL | PASS | verifier unit + REST |
| AUTH-02 | signed short stateless session and negative JWTs | PARTIAL | PASS | session unit + REST |
| AUTH-03 | idempotent/concurrent bootstrap and rollback | PARTIAL | PASS | PostgreSQL AuthenticationIT |
| AUTH-04 | membership source and tenant denial | PARTIAL | PASS | AuthenticationIT/BotOnboardingIT |
| BOT-01 | owner CRUD and database idempotency | PARTIAL | PASS | REST + parallel PostgreSQL |
| SEC-01 | AES-GCM, SQL secrecy, key ID, redaction | PARTIAL | PASS | cipher + E2E SQL/log |
| BOT-02 | getMe and distinct failure classes | PARTIAL | PASS | local HTTP stub/E2E |
| BOT-03 | webhook lifecycle/replacement/reconnect/recovery | FAIL | PASS | E2E + transport tests |
| MGT-01 | WebApp, production webhook, secret, no polling | PARTIAL | PASS | management tests/runtime |
| FE-05 | memory-only frontend session | BLOCKED | BLOCKED | no auth implementation in available frontend checkout |

## Remaining Limitations

Frontend FE-05 is unavailable and remains outside this backend remediation. TG-02
inbound user-created bot routing/secret enforcement remains separately scoped;
Phase 2 reserves the opaque route and encrypted secret but does not execute runtime
updates. Operator action remains required and was not performed programmatically:

```text
Revoke/rotate previously exposed management bot credential through BotFather.
```

## Release Gate

```text
Phase 2 backend gate: PASS
Full Phase 2 cross-repository gate: BLOCKED (FE-05 unavailable)
```
