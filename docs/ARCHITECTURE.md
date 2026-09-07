# Architecture Knowledge Base

## Призначення системи

`constructor-service` — Spring Boot застосунок Telegram-бота, який показує меню створення/редагування ботів, перевіряє тариф користувача та надсилає інструкції й навчальну анімацію. Підтверджені сценарії: `/start`, callbacks `CREATE:*` і `VIDEO:*` (`constructor-service/src/main/java/org/demchenko/tg/handler/StartCommandHandler.java`, `CreateBtnHandler.java`, `VideoBtnHandler.java`).

## Структура та відповідальність

- **Bootstrap/config** — запуск Spring, завантаження медіа, реєстрація Telegram long-polling bot і властивості bot (`ConstructorApplication.java`, `tg/config/TelegramBotConnection.java`, `tg/config/BotOptionsConfig.java`, `src/main/resources/application.yml`).
- **Dispatch/input** — `BotUpdateDispatcher` перебирає Spring-список `BotInputService`; абстрактні handlers класифікують command, callback, text і reply-button updates (`tg/dispetcher/BotUpdateDispatcher.java`, `tg/service/BotInputService.java`, `tg/service/input/`).
- **Use-case handlers** — `/start`, create та video flows (`tg/handler/`). `EditBtnHandler` не має `@Component`, тому зараз не входить до dispatcher (`tg/handler/EditBtnHandler.java`).
- **Telegram output** — побудова inline/reply keyboards і відправлення message/animation через `DefaultAbsSender` (`tg/service/impl/TelegramMessageService.java`, `TelegramInlineKeyboardService.java`, `TelegramReplyKeyboardService.java`, `HelpBotSender.java`).
- **Data/state** — JPA entity/repository для `user_data`; сесії та state machine зберігаються лише у процесі в `ConcurrentHashMap` (`tg/model/UserData.java`, `tg/data/repo/UserRepository.java`, `tg/data/UserService.java`, `tg/service/state/`).

Залежності йдуть у напрямку `TelegramBotConnection -> BotUpdateDispatcher -> handlers -> Telegram/data services -> Telegram API або PostgreSQL`. State services наразі не викликаються активними handlers (`tg/config/TelegramBotConnection.java`, `tg/handler/`, `tg/service/state/`).

## Точки входу та основні flows

- **Process startup:** `ConstructorApplication.main` запускає Spring; static initializer копіює `assets/videos/video.mp4` у temp-файл. Після DI `TelegramBotConnection.registerBot()` реєструє long-polling session (`ConstructorApplication.java`, `tg/config/TelegramBotConnection.java`).
- **Telegram inbound:** Telegram update -> `onUpdateReceived` -> dispatcher -> перший handler, де `canHandle=true` (`tg/config/TelegramBotConnection.java`, `tg/dispetcher/BotUpdateDispatcher.java`). Порядок handlers явно не заданий.
- **`/start`:** повертає inline-кнопки Create/Edit (`tg/handler/StartCommandHandler.java`).
- **`CREATE:*`:** читає користувача з PostgreSQL або створює незбережений default `FREE`; далі показує обмеження плану чи інструкцію (`tg/handler/CreateBtnHandler.java`, `tg/data/UserService.java`).
- **`VIDEO:*`:** надсилає classpath GIF і Back-кнопку; переданий `video.lesson.dir` фактично не визначає ресурс, бо sender жорстко читає `assets/animations/output.gif` (`tg/handler/VideoBtnHandler.java`, `tg/service/impl/TelegramMessageService.java`, `application.yml`).
- **HTTP:** `spring-boot-starter-web` підключено, але controller/request mappings відсутні (`constructor-service/pom.xml`, `tg/exceptionHandler/GlobalExceptionHandler.java`).
- **Kafka:** Kafka dependency, producers/listeners і topics відсутні (`constructor-service/pom.xml`, `constructor-service/src/main/java/`).
- **Scheduled:** scheduling annotations/configuration відсутні (`constructor-service/src/main/java/`).

## Дані та інтеграції

- **PostgreSQL:** datasource параметризований через `SPRING_DATASOURCE_*`; локальний запуск IDE використовує `localhost:5433/bot_constructor`, а контейнеризований застосунок — `postgres:5432/bot_constructor`. Flyway застосовує versioned migrations перед Hibernate `ddl-auto=validate`; canonical tenant tables описані в authoritative persistence section нижче.
- **Telegram Bot API:** long polling для input, `DefaultAbsSender` для output; token/username беруться з `bot.*` (`tg/config/TelegramBotConnection.java`, `tg/service/impl/HelpBotSender.java`, `application.yml`).
- **Kafka topics:** не визначені. Compose для локальної розробки запускає лише PostgreSQL; застосунок запускається окремо з IDE і підключається до `localhost` (`docker-compose.yml`, `application-local.yml`).
- **Assets:** video, MOV і GIF лежать у `constructor-service/src/main/resources/assets/`; startup вимагає `video.mp4`, активний animation flow використовує GIF, а MOV не потрапляє в Docker build context (`ConstructorApplication.java`, `TelegramMessageService.java`, `constructor-service/.dockerignore`).

## Security, observability та помилки

- Authentication/authorization у Spring відсутні: немає Spring Security dependency або security config. Бізнес-обмеження є лише для `FREE` plan у create flow (`pom.xml`, `tg/handler/CreateBtnHandler.java`). Telegram user/chat IDs приймаються з updates без додаткової перевірки.
- Bot token і datasource credentials читаються з environment variables; Compose вимагає `BOT_TOKEN` і `BOT_USERNAME`, а `.env` ігнорується Git (`application.yml`, `docker-compose.yml`, `.env.example`, `.gitignore`). Раніше закомічений token слід вважати скомпрометованим і ротувати.
- Observability обмежена SLF4J startup/error logs, `System.out` для unmatched update та увімкненим Hibernate SQL logging; Actuator/metrics/tracing dependencies відсутні (`TelegramBotConnection.java`, `BotUpdateDispatcher.java`, `TelegramMessageService.java`, `application.yml`, `pom.xml`).
- Telegram registration і outbound failures перехоплюються та логуються без retry/propagation (`TelegramBotConnection.java`, `TelegramMessageService.java`). `@ControllerAdvice` обробляє `NotFoundFileException`, але exception не кидається поточним кодом і advice орієнтований на MVC (`tg/exceptionHandler/`).

## Build, test і local run

Для host build потрібні JDK 21, Maven 3.9+ і доступний PostgreSQL для database-backed profiles (`pom.xml`, `application.yml`). З `constructor-service/`:

```text
mvn clean package
mvn test
mvn spring-boot:run
```

Compiler використовує Java 21 LTS через `maven.compiler.release=21`, без preview flags; Maven Enforcer вимагає JDK 21 і Maven 3.9+. Spring Boot Maven plugin створює executable `target/app.jar`. JUnit 5 configuration tests і ArchUnit architecture tests виконуються звичайним Maven lifecycle. Docker build і runtime images також використовують Temurin 21.

## Реалізована modular-monolith структура (BE-02)

Один Maven artifact і один Spring Boot entry point `org.demchenko.ConstructorApplication` містять сім package modules:

| Module | Відповідальність | Поточна реалізація |
|---|---|---|
| `identity` | platform identity і Telegram user mapping | `UserData`, repository і identity service |
| `workspace` | tenant, membership, access policy | boundary задокументовано через `package-info.java` |
| `bot` | metadata/lifecycle user-created bots | boundary задокументовано через `package-info.java` |
| `flow` | drafts, versions, validation, publish logic | boundary задокументовано через `package-info.java` |
| `runtime` | executable flow/conversation state | поточний in-memory session state |
| `telegram` | Telegram-specific management adapter | handlers, dispatcher, senders, Telegram API connection |
| `audit` | security audit events and ports | boundary задокументовано через `package-info.java` |

Allowed dependency matrix (`→` means “may depend on”):

| From | Allowed internal targets |
|---|---|
| `identity`, `workspace`, `bot`, `flow`, `audit` | own module only |
| `runtime` | `runtime`, stable `bot` and `flow` contracts |
| `telegram` | application contracts in `identity`, `runtime`, `bot`, `flow`, `audit` |
| technical `configuration` | module bootstrap/configuration only |

Core modules may not import `org.telegram.*` or `org.demchenko.telegram.*`. ArchUnit checks required packages, cycles, dependency direction, the legacy-package ban, and entry-point location. The temporary exception is that the prototype Telegram adapter calls the concrete identity service until application ports are introduced by later identity tasks.

## Configuration model (BE-03)

`application.yml` contains shared safe settings only. `application-local.yml`, `application-test.yml`, and `application-prod.yml` define profile behavior without committing `spring.profiles.active`. Immutable validated configuration records bind `app.telegram.management`, `app.telegram.media`, and `app.web`; structured application configuration does not use `@Value`.

The `test` profile disables datasource/JPA auto-configuration and excludes the conditional Telegram adapter, so the application context starts without secrets, PostgreSQL, or network calls. `local` disables Telegram by default and uses explicit local-only PostgreSQL defaults. `prod` always enables management Telegram and requires datasource credentials, public URL, token, and username externally; invalid Telegram credentials fail binding with a message that does not echo the token.

## Baseline decision (BE-01)

Java 21 is the selected supported LTS. Preview was removed to make compilation/runtime reproducible on standard JDK 21 tooling. Spring Boot is pinned to 3.5.13: 3.4.x reached the end of open-source support, while 3.5 is the supported 3.x line and avoids a major-version migration to Boot 4. Spring Cloud BOM was removed because the application has no Spring Cloud dependencies. The legacy Telegram library remains pinned to 5.7.1 as explicit technical debt rather than changing bot behavior in this foundation task.

## Authoritative persistence architecture (DB-01/DB-02)

This section supersedes earlier prototype references to Hibernate schema creation and a canonical `user_data` model.

Flyway is the sole schema owner. Versioned SQL is stored under `src/main/resources/db/migration`; Hibernate always uses `ddl-auto=validate` and `generate-ddl=false`. `baselineOnMigrate` is false, including production. Applied migrations are immutable and every schema change uses the next free version.

| Module | Entity/table | Ownership |
|---|---|---|
| `identity` | `PlatformUserEntity` / `platform_users` | UUID internal identity; unique positive Telegram user ID |
| `workspace` | `WorkspaceEntity` / `workspaces` | required scalar `owner_user_id` FK to platform user |
| `workspace` | `MembershipEntity` / `memberships` | required workspace/user FKs and unique pair |
| `bot` | `BotEntity` / `bots` | required scalar `workspace_id`; no credential/token column |
| `identity` legacy | `UserData` / `user_data` | compatibility only for current management create flow |

All new primary keys are UUIDs generated by JPA. Cross-module entity associations are intentionally avoided: scalar UUID fields preserve package boundaries and database foreign keys preserve referential integrity. `Instant`/`TIMESTAMP WITH TIME ZONE` is the UTC timestamp policy; mutable aggregates use optimistic-lock `version` columns.

Delete policies are explicit: workspace owner and membership user references are `RESTRICT`; workspace-to-membership is `CASCADE` because memberships have no lifecycle outside a workspace; workspace-to-bot is `RESTRICT` because hard-delete bot lifecycle is not yet defined. Query indexes cover workspace owner, membership user, and bot workspace. Unique constraints provide indexes for Telegram user ID, `(workspace_id,user_id)`, and nullable Telegram bot ID; the membership unique index also serves workspace-member lookup.

`CON-01` is absent, so no flow tables or `active_flow_version_id` exist. The legacy `user_data` table is not canonical and must not receive new dependencies; it is retained because the existing Telegram create handler actively reads plan/bot-count prototype fields and the local schema could not be inspected while Docker was unavailable.

## Authentication and tenant authorization (AUTH-01–AUTH-04)

The HTTP auth path is `generated AuthApi -> TelegramAuthenticationController -> AuthenticateWithTelegramUseCase -> TelegramInitDataVerifier -> transactional identity/workspace bootstrap -> PlatformSessionIssuer`. First-party Telegram verification uses HMAC-SHA-256 with the management bot token, constant-time hash comparison, a five-minute configurable replay window, and 30-second configurable future skew. Signed values are percent-decoded but never normalized or reserialized before verification; raw `initData` is neither stored nor logged.

Bootstrap uses a PostgreSQL transaction advisory lock keyed by Telegram user ID. V2 adds `workspaces.is_default` and a partial unique index limiting each owner to one default Workspace; the same transaction ensures the OWNER Membership. Existing owners are backfilled deterministically by earliest creation time and UUID, without deleting data.

Platform sessions are stateless HS256 JWTs signed with an independent Base64 key of at least 256 bits. Required claims are `sub`, `iss`, `aud`, `iat`, `exp`, and `jti`; TTL defaults to 15 minutes. Spring Security allows only HS256, validates issuer/audience/time, disables form login, Basic auth, CSRF state and server sessions, and reads bearer tokens only from `Authorization`. No refresh token exists; clients reauthenticate with fresh Telegram data and keep the access token in memory only.

Membership in PostgreSQL is the tenant-access source of truth. `GET /api/v1/workspaces/{workspaceId}` performs an access-scoped query and returns `404` for non-members to avoid tenant enumeration. Owner-only policy returns `403` to authenticated members lacking OWNER. Bot persistence exposes tenant-scoped ID lookup methods; future Flow ports must require explicit `workspaceId`. Security failures use the canonical generated `ApiError` with the request correlation ID.

## REST/API boundary and test foundation (BE-04/BE-05/TEST-01)

`org.demchenko.api.web` is the inbound HTTP adapter. `CorrelationIdFilter` validates or creates `X-Correlation-ID`, keeps it in MDC only for request processing, and returns the same value in the canonical error body. `RestExceptionHandler` translates MVC/validation failures; `RestFallbackErrorController` prevents the standard Spring `/error` payload from exposing a second format. Both map through `ApiErrorMapper` to the generated OpenAPI model and expose only `code`, safe `message`, `traceId`, and sorted `fieldErrors`.

The single OpenAPI 3.1 source is `constructor-service/src/main/openapi/openapi.yaml` (contract `1.0.0`, base path `/api/v1`). API-01 defines Auth, Workspace, Bots, write-only Credentials, Draft, Validation, Publish, Versions, Status, Health, bearer-session security, and reusable errors. The paths follow the approved SAD; `/api/v1/status` is the only implemented product vertical slice. All other paths are contract-only until their backlog tasks are implemented. The FlowDraft envelope follows the SAD's illustrative v1 shape; this does not implement the still-pending CON-01 node/config JSON Schema decision.

The Maven `generate-sources` phase validates the canonical document and generates both models and tag-based interfaces into `target/generated-sources/openapi`; generated Java is never edited or committed. `PlatformStatusController` implements generated `StatusApi`, delegates to `PlatformStatusService`, and maps the application `PlatformStatus` result through `PlatformStatusMapper` to generated `StatusResponse`. Compilation, semantic `OpenApiContractTest`, ArchUnit dependency rules, mapper unit testing, and a real-HTTP `PlatformStatusIT` make contract drift visible.

Surefire runs `*Test` unit/configuration/architecture/contract tests. Failsafe runs `*IT` REST/status and PostgreSQL/Testcontainers integration tests in `integration-test`/`verify`. Thus `mvn clean verify` validates and semantically audits the specification, regenerates and compiles DTO/interface usage, checks module/API dependency rules, exercises real HTTP contracts, and migrates plus validates a disposable PostgreSQL database. Docker is mandatory for the complete suite; unavailable Docker is a visible failure, not a skipped test.

## Bot onboarding and management delivery (PH2)

Bot credential onboarding uses persisted short transaction phases. An owner and Bot
are resolved through a membership-scoped locking query, then an encrypted candidate
operation is reserved and the transaction ends. Telegram `getMe` and `setWebhook`
run without an active database transaction. Separate transactions move the operation
from `PENDING_VERIFICATION` to `WEBHOOK_PENDING` and finally atomically activate it.
The active credential stays untouched during replacement failures; successful
replacement copies its encrypted envelopes to history before switching the one-to-one
active row. A partial unique index permits only one in-progress operation per Bot,
and persisted operations can be resumed explicitly after process restart. Telegram
bot identity conflicts are serialized with a PostgreSQL advisory transaction lock.

Tokens and per-Bot webhook secrets are AES-256-GCM versioned envelopes using random
96-bit IVs, the configured key ID, and Bot/secret-type AAD. Key material never enters
the schema. The registered user-Bot webhook route contains only the opaque Bot UUID.
Phase 2 owns outbound registration and encrypted secret persistence; the future TG-02
increment owns inbound user-Bot routing, secret-header verification, and runtime
execution.

Bot REST operations are generated from OpenAPI and owner-scoped: create/list/get/update,
credential set/remove, and reconnect. There is no public Bot delete operation in the
Phase 2 contract. Idempotency records are unique per workspace and key; concurrent
unique-key races return the committed logical winner. The management adapter is
disabled unless configured. Local long polling is opt-in, while production pins
`WEBHOOK`, registers with bounded HTTP timeouts, and exposes a distinct inbound
management endpoint guarded by a constant-time comparison of
`X-Telegram-Bot-Api-Secret-Token`. `/start` emits a native `web_app` inline button.

## Архітектурні обмеження

- Один deployable Maven-модуль; routing базується на Spring DI списку handlers і string prefixes (`pom.xml`, `BotUpdateDispatcher.java`, `tg/service/input/`).
- Сесії локальні для JVM, не переживають restart і не діляться між instances (`tg/service/state/UserSessionService.java`).
- Schema змінюється лише versioned Flyway migrations; Hibernate у всіх database profiles виконує лише validation.
- `sendVideo` закоментований; startup все одно вимагає MP4, а animation sender не перевіряє null stream (`ConstructorApplication.java`, `TelegramMessageService.java`).

## Відомі прогалини та непідтверджені припущення

- Немає тестів, API contract, CI, migrations, application health endpoint або deployment manifests (`pom.xml`, repository tree). PostgreSQL має лише container healthcheck (`docker-compose.yml`).
- Edit, Back, Slide і довільний text/state flow не мають активних concrete handlers; `EditBtnHandler` порожній і не зареєстрований (`tg/handler/`, `tg/service/state/`).
- Новий default user у `UserService` не зберігається; життєвий цикл створення ботів і оновлення `countOfBots` не реалізовані (`tg/data/UserService.java`, `tg/handler/CreateBtnHandler.java`).
- Потрібність HTTP starter, production topology та очікувані Kafka/scheduled flows не підтверджені кодом (`pom.xml`, `constructor-service/src/main/java/`).
