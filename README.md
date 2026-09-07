# BotConstructor

One deployable Spring Boot modular monolith for the Telegram bot builder. The backend uses Java 21 LTS, Spring Boot 3.5.13, Maven, and PostgreSQL. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for module boundaries.

## Prerequisites

- JDK 21 (the build rejects other Java feature releases)
- Maven 3.9 or newer
- Docker Compose v2 for local PostgreSQL

## Profiles

No profile is activated in committed configuration; select it externally.

| Profile | Telegram | Database | Intended use |
|---|---|---|---|
| `local` | Disabled by default; opt in with environment variables | PostgreSQL at `localhost:5433` with explicit local-only defaults | IDE/local development |
| `test` | Disabled; no credentials or API calls | JDBC/JPA auto-configuration disabled | Unit, configuration, and architecture tests |
| `prod` | Required and enabled | All connection values required externally; Hibernate validates schema | Production |

## Local development

Copy `.env.example` to the ignored `.env` and keep `APP_TELEGRAM_MANAGEMENT_ENABLED=false` for a test-safe startup. Start PostgreSQL without deleting its volume:

```bash
docker compose up -d postgres
cd constructor-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

For local development, provide a development BotFather token and username through `APP_TELEGRAM_MANAGEMENT_TOKEN` and `APP_TELEGRAM_MANAGEMENT_USERNAME`, then explicitly select the management delivery mode. Production uses the webhook adapter; tokens must never be committed or logged.

Mini App authentication also uses the management-bot token, even when long polling is disabled. Set `APP_SESSION_SIGNING_KEY` to a Base64-encoded secret of at least 32 random bytes; it must be independent from the bot token. The local auth defaults accept Telegram `initData` for five minutes with 30 seconds of future clock skew and issue 15-minute HS256 bearer sessions.

The local datasource defaults match Compose (`localhost:5433/bot_constructor`, local user/password `bot_constructor` / `local_dev_password`). Override them with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. Existing volumes are retained. Flyway owns schema changes and Hibernate only validates them; Hibernate schema-mutation modes are prohibited.

## Tests and build

From `constructor-service`:

```bash
mvn test
mvn clean verify
```

The build compiles with Java release 21 without preview features. `*Test` classes (JUnit unit and ArchUnit tests) run with Surefire; `*IT` classes (REST and PostgreSQL integration tests) run with Failsafe during `verify`. `mvn clean verify` is the single complete backend check and requires a running Docker engine.

Persistence integration tests use a disposable PostgreSQL 17.6 Testcontainer:

```bash
mvn -Dit.test=CorePersistenceIT failsafe:integration-test failsafe:verify
```

Docker must be running. The test never connects to the local Compose database and is not silently skipped when Docker is unavailable. If startup fails, first verify `docker version` and `docker info`, then rerun `mvn clean verify`; inspect the Failsafe report under `target/failsafe-reports`.

## REST contract and OpenAPI

The canonical OpenAPI 3.1 contract is `constructor-service/src/main/openapi/openapi.yaml`, version `1.0.0`, with the `/api/v1` base path. It defines the API-01 groups Auth, Workspace, Bots, Credentials, Draft, Validation, Publish, Versions, Status, Health, and reusable common errors. Only `GET /api/v1/status` is implemented in this foundation phase; every other product operation is contract-only and belongs to its named backlog task.

During `generate-sources`, pinned OpenAPI Generator 7.24.0 validates the specification and writes DTOs plus tag-based API interfaces below `constructor-service/target/generated-sources/openapi`. Generated files are build artifacts: never edit or commit them. The status controller implements the generated `StatusApi`, delegates to an application service, and uses an explicit mapper to produce `StatusResponse`. Change the specification and run `mvn clean verify`; `OpenApiContractTest`, Java compilation, architecture rules, and `PlatformStatusIT` detect contract drift.

Every MVC error response uses `application/problem+json` and has `code`, a safe `message`, a non-empty `traceId`, and deterministic `fieldErrors`. Non-field errors use an empty array. `X-Correlation-ID` contains the same validated/generated trace ID. Client responses never include exception types, stack traces, SQL, credentials, tokens, or raw internal exception messages. Controllers must use generated API DTOs and an explicit adapter mapper; JPA entities and repositories are forbidden at the HTTP boundary by architecture tests.

Run only the semantic contract tests with `mvn -Dtest=OpenApiContractTest test`; the authoritative full Phase 1 verification remains `mvn clean verify` and requires Docker for PostgreSQL Testcontainers.

## Database migrations

Flyway is the only schema owner. Migrations live in `constructor-service/src/main/resources/db/migration` and follow `V<next-number>__<description>.sql`. Never edit or rename a migration after it may have been applied; add the next version instead. `V1__create_core_tenant_schema.sql` creates `platform_users`, `workspaces`, `memberships`, `bots`, and the compatibility-only `user_data` table. Flow tables are intentionally absent because CON-01 is not yet available.

New persistence IDs use UUID consistently and are distinct from Telegram IDs. Java `Instant` maps to PostgreSQL `TIMESTAMP WITH TIME ZONE`; timestamps represent UTC instants. Entity references across modules are scalar UUIDs, while PostgreSQL foreign keys enforce ownership.

For a new empty local database, start PostgreSQL and run the `local` profile; Flyway applies V1 before Hibernate validation. A second startup reads `flyway_schema_history` and does not reapply V1.

An older local database may contain a Hibernate-created `user_data` table without Flyway history. Do not enable automatic baselining globally and do not run V1 blindly. After making a verified backup and confirming that no unknown tables are present:

1. Stop the application and rename the legacy table to `user_data_legacy_backup`.
2. For one audited local run only, set `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` and `SPRING_FLYWAY_BASELINE_VERSION=0`; never use this procedure in production.
3. Let Flyway apply V1, then copy the compatible `chain_id`, `count_of_bots`, and `plan` values into the new `user_data` table.
4. Verify row counts and management-bot behavior. Retain the backup table until the data is verified.
5. Remove the one-time baseline variables before subsequent starts.

If the old columns or any other tables differ, stop and prepare a reviewed forward migration instead of altering or deleting data.

## Production requirements

Activate production externally, for example:

```bash
java -jar target/app.jar --spring.profiles.active=prod
```

The following environment variables are required and have no production defaults:

| Variable | Meaning |
|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL user |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password |
| `APP_WEB_URL` | Public application/WebApp URL |
| `APP_TELEGRAM_MANAGEMENT_TOKEN` | Management bot token |
| `APP_TELEGRAM_MANAGEMENT_USERNAME` | Management bot username |
| `APP_TELEGRAM_MANAGEMENT_CONNECTION_MODE` | `DISABLED`, `POLLING` (local only), or `WEBHOOK` (production) |
| `APP_SESSION_SIGNING_KEY` | Base64-encoded platform-session key containing at least 32 random bytes |
| `APP_SESSION_ISSUER` | Stable HTTPS JWT issuer identifier |
| `APP_SESSION_AUDIENCE` | Expected API audience |
| `APP_SESSION_TTL` | Short platform-session lifetime; default `15m` |
| `APP_AUTH_TELEGRAM_MAX_INIT_DATA_AGE` | Telegram replay window; default `5m` |
| `APP_AUTH_TELEGRAM_ALLOWED_FUTURE_SKEW` | Allowed future clock skew; default `30s` |

## Mini App authentication and tenancy

`POST /api/v1/auth/telegram/session` verifies Telegram Mini App `initData` using the official first-party HMAC-SHA-256 construction and the management-bot token. Only after signature and timestamp validation does one transaction ensure the canonical User, one default Workspace, and its OWNER Membership. A PostgreSQL transaction advisory lock keyed by Telegram user ID plus database uniqueness constraints makes concurrent first login idempotent.

The response contains a signed short-lived JWT (`Bearer`, 15 minutes by default). There is no refresh token or refresh endpoint: after expiry, the frontend obtains fresh Telegram `initData` and authenticates again. The frontend security contract requires keeping the token in memory only—not `localStorage` or `sessionStorage`.

All `/api/v1/**` operations are authenticated by default except auth exchange, status, and health. `GET /api/v1/workspaces/{workspaceId}` is the implemented tenant-policy slice: PostgreSQL Membership is the source of truth, cross-tenant access returns `404`, an authenticated non-owner would receive `403` for owner-only operations, and missing/invalid/expired sessions return the canonical `401` error. Bot repository access has explicit `(resourceId, workspaceId)` methods; future Flow persistence must follow the same convention.

Raw `initData`, its hash, bearer tokens, signing keys, and management bot tokens are never stored or logged. The five-minute age check is the MVP replay control; strict one-time replay storage is intentionally deferred and repeat authentication remains idempotent.

Missing or malformed management-bot configuration fails startup with a sanitized validation message. Production always enables the management bot. `JAVA_OPTS` may contain ordinary JVM tuning and is passed by the Docker image; preview flags are not required.

## Known scoped technical debt

- `telegrambots` remains pinned to 5.7.1 to preserve current `/start`, create, and video behavior. Its upgrade and isolation behind newer ports belongs to a separate Telegram adapter task.
- Legacy `user_data` remains isolated for management-bot compatibility; new identity features use `platform_users`.
- The management adapter exposes a native Mini App button and a secret-validated webhook path. Production is pinned to `WEBHOOK`; polling is restricted to explicit local development configuration. Inbound user-created bot routing remains the separately scoped TG-02 increment.
