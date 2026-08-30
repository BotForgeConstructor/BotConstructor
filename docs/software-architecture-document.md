# Software Architecture Document — Telegram Bot Builder

| Field | Value |
|---|---|
| Document | Software Architecture Document (SAD) |
| Status | Architecture baseline proposed for implementation |
| Date | 2026-08-27 |
| Product source | master-mvp-document(2).md, version 1.0 dated 2026-08-26 |
| Frontend snapshot | VladislavDemchenko/bot-builder-ui, master at 13b13b0e3812e386c091772cc3c0f23a3c5d5de7, 2025-07-07 |
| Backend snapshot | BotForgeConstructor/BotConstructor, main at f6868417797ff0bc7290bf322535b4f876ca89ef, 2026-08-26 |
| Decision owner | Product owner / principal developer |
| Canonical future location | Private repository bot-builder-ui: docs/system/software-architecture-document.md |

## Executive Architecture Decision

### Direct answers

| # | Question | Decision |
|---|---|---|
| 1 | Recommended primary stack | TypeScript + React + React Flow for the Telegram Mini App; Java 25 LTS + Spring Boot 4.1.x for the API, management bot, flow publishing and runtime; PostgreSQL for durable data. Use supported patch versions at implementation time. |
| 2 | Keep Java? | Yes. Java remains the only production backend/runtime language. The current application is small enough to refactor, and the developer’s strongest production expertise is Java/Spring. |
| 3 | Keep Python? | No production Python. The Flask file is a prototype/reference only. Remove it after its useful contract examples are converted into tests and fixtures. |
| 4 | Move frontend to TypeScript? | Yes. Rewrite the active prototype into typed React components and domain state. Do not mechanically translate the current global JavaScript. |
| 5 | Need a Node.js backend? | No. Node.js is build/test tooling for the frontend only. The Mini App is a static application and does not require a Node server. |
| 6 | Need separate services? | No for MVP. Use one Java modular monolith containing management, authoring, publishing, runtime and background-job modules. The static frontend is a separate artifact, not a backend service. |
| 7 | Need REST between frontend and Java backend? | Yes. HTTPS JSON REST is the correct process and trust boundary. |
| 8 | Need REST between Java backend and Python runtime? | No. There is no Python runtime. Backend and runtime are modules in one JVM and communicate through in-process application interfaces. |
| 9 | Should the management bot be part of the backend application? | Yes for MVP, as a separate Telegram adapter/module in the same Spring Boot application. It is not the user-bot runtime. |
| 10 | Where does a user-created bot flow execute? | In a shared multi-tenant Java interpreter that loads an immutable published flow version and durable conversation state. |
| 11 | How many deployable applications in MVP? | Two application artifacts: one static frontend and one Java backend/runtime. Only one dynamic product process is required. PostgreSQL is infrastructure. Self-hosted Compose normally has three containers: static web, Java app and PostgreSQL. |
| 12 | Repository responsibilities | bot-builder-ui becomes frontend-only plus the private canonical system SAD. BotConstructor contains the Java modular monolith, database migrations, OpenAPI, flow JSON Schemas and sanitized module-level documentation. No third runtime repository. |

### Architecture verdict

Choose **Option B — TypeScript frontend + Java backend/runtime**.

The target is deliberately not a three-language compromise:

- TypeScript owns rendering, interaction, typed draft editing and API consumption.
- Java owns every trusted server-side decision, persistence, Telegram integration and flow execution.
- PostgreSQL owns durable platform and runtime state.
- Python is removed from the production architecture.
- Node.js is not a server component.
- REST exists only across the browser-to-server boundary.
- The management bot and user-created bot runtime are separate roles, but modules of one application in MVP.

## 1. Document Purpose

This document defines an implementable architecture baseline for rebuilding Telegram Bot Builder from a visual prototype and a small Telegram management bot into a production-capable MVP. It resolves language, repository, module, process, API, data, runtime, security and deployment decisions.

The document is intended to let implementation start without repeating high-level design. It does not approve unresolved product scope, pricing or go-to-market assumptions.

## 2. Scope

### In scope

- Telegram management bot and Mini App entry.
- Telegram Mini App visual builder.
- Telegram initData authentication.
- Users, workspaces and user-created bot configurations.
- Secure Telegram bot credential onboarding.
- Versioned draft and published flows.
- Server-side graph validation and compilation.
- Shared multi-tenant execution of Start, SendMessage and button branches.
- Durable conversations, update idempotency and outbound retry.
- PostgreSQL, Docker Compose, CI, logs, metrics, health and backups.
- An evolution path from modular monolith to separately scalable runtime.

### Out of scope for the first MVP

- Generated source code per bot.
- Process/container/serverless function per bot.
- Python runtime or Node.js backend.
- Kubernetes, Kafka and Redis.
- Payment, broadcast, arbitrary HTTP request, variables and general-purpose scripting nodes.
- A general workflow engine competing with Temporal, Camunda or Node-RED.
- Multi-channel execution outside Telegram.
- Advanced billing, collaboration, analytics and marketplace features.
- Infrastructure sized for millions of bots.

## 3. Product Context

Telegram Bot Builder is a no-code/low-code SaaS concept in which a platform user opens a Telegram Mini App, visually composes a bot flow, connects a bot identity and publishes a flow that is executed for end users through the Telegram Bot API.

The product document confirms a working visual prototype but leaves the production schema, API, runtime, state, token lifecycle and deployment unresolved. The minimum release outcome used by this SAD is:

1. a platform user opens the management bot and Mini App;
2. Telegram identity is verified by the backend;
3. a flow is edited and saved as a draft;
4. the backend validates and publishes an immutable version;
5. a Telegram user-bot update is durably received;
6. the shared runtime executes Start → SendMessage → button branch;
7. conversation state survives restart;
8. a new version can be published without overwriting the previous version.

The product is primarily developed by one person. Therefore, change cost and operational load are first-class architecture drivers.

## 4. Current-State Architecture

### 4.1 Evidence baseline

The repositories were inspected at the exact commits listed in the metadata table. README files were treated as secondary evidence and checked against the repository tree and source.

Repository links:

- [bot-builder-ui snapshot](https://github.com/VladislavDemchenko/bot-builder-ui/commit/13b13b0e3812e386c091772cc3c0f23a3c5d5de7)
- [BotConstructor snapshot](https://github.com/BotForgeConstructor/BotConstructor/commit/f6868417797ff0bc7290bf322535b4f876ca89ef)

### 4.2 bot-builder-ui facts

| Area | Fact at inspected HEAD | Architectural consequence |
|---|---|---|
| Build | Vite 5 and Prettier are the only npm development dependencies. No framework, TypeScript, lint or test setup exists. | Treat as a prototype, not a production frontend foundation. |
| Active UI | builder.html loads Drawflow from a CDN and loads main.js. index.html, builder.html and my-bots.html contain substantial inline scripts. | The advertised navigation/storage/node modules are not the active architecture. |
| Duplicate logic | main.js, main-old.js, node-manager.js, bot-manager.js, storage.js and inline page scripts overlap. | Rewrite by feature boundaries; do not preserve global duplication. |
| Persistence | Bots, Drawflow export, status, statistics and generated user ID are stored in localStorage/sessionStorage. | Browser storage cannot be the platform source of truth. |
| Editor state | Drawflow data, selectedNode/selectedNodeId, modal DOM, rendered HTML and temporary arrays are all read or mutated. | Multiple sources of truth directly explain stale-state and reconnection risk. |
| Node update | Text/button changes remove the old Drawflow node and add a new node after saving/restoring connections with a timeout. | IDs are unstable; asynchronous restoration can lose or mis-map edges. |
| Activation | my-bots.html converts Drawflow to another JSON shape and POSTs to http://localhost:8000/api/bots/activate. | The conversion is an unversioned client-side contract and trusts unauthenticated local identity. |
| Secret handling | The fake server token returned by Flask is written to localStorage. | This pattern must be removed; no bot token may reach persistent browser storage. |
| Export | example_bot_export.json differs from generated behavior: the fixture includes loop_detected while the current traversal returns no such item. | The export is a reference fixture, not an executable contract. |
| Security | User-supplied text/button values are inserted into HTML templates without a systematic escaping boundary. | A framework rendering model and security tests are required to prevent client-side injection. |
| Backend example | bot_server_example.py validates a few fields, generates a fake token and writes bot files to disk. | It demonstrates an HTTP shape only; it is not a persistence, auth, runtime or security design. |

### 4.3 BotConstructor facts

| Area | Fact at inspected HEAD | Architectural consequence |
|---|---|---|
| Platform | Spring Boot 3.4.5, Java 23 preview, Maven, telegrambots 5.7.1, JPA and PostgreSQL. | Move to an LTS JDK without preview and isolate Telegram API dependency behind an adapter. |
| Role | The active bot shows platform create/edit/instruction menus. It does not execute user-created flows. | Classify it as the Platform Management Bot. |
| Telegram ingress | A single TelegramLongPollingBot registers at startup and dispatches updates to the first matching handler. | Keep long polling for local development only; use webhook ingress in production. |
| Handlers | /start and create/instruction flows exist. Edit is empty. The create flow asks the user to send a token, but no active token handler persists it. | Reuse concepts and copy, not the current orchestration as the target runtime. |
| Concurrency | AbstractTextInputHandler stores the current Update in a singleton field. Handler ordering is not explicit. | Current handler design is unsafe for concurrent requests and must be refactored. |
| Defect | The plan-limit branch in CreateBtnHandler accesses update.getMessage() while handling a callback update. | The branch can fail with a null message; do not treat handlers as production-ready. |
| Data | Only user_data exists. A missing user is constructed but not saved. List<String> and enum persistence are not explicitly mapped. | Replace with deliberate migrations and a product domain schema. |
| Sessions | ConcurrentHashMap stores a lead/account data session state not used by active handlers. | This is neither platform auth state nor the user-bot Conversation model; remove or archive it. |
| HTTP | spring-boot-starter-web exists, but controllers and request mappings do not. | Build the API as a new application adapter around use cases. |
| Tests/migrations | No test source, test dependency, Flyway or Liquibase. Hibernate ddl-auto is environment-controlled. | Add tests and versioned migrations before product data. |
| Docker | Compose has the Java service commented out. Dockerfile copies target/app.jar, but pom.xml does not set that artifact name. | Current container build/deployment is incomplete and should be replaced. |
| Configuration | README references an application-local.yml that is absent at HEAD. | Documentation and local-run instructions are not authoritative. |
| Docs | docs/ARCHITECTURE.md uses old package paths/names and references absent resources/config after a package rename. | Keep a small public module map, but update it after the target structure exists. |
| Security/ops | No Spring Security, Actuator, metrics, migrations, audit or retry. Repository documentation reports a previously committed bot token. | Rotate any exposed token immediately; add explicit security and operations modules. |

### 4.4 Current role classification

The current Java bot is a **Platform Management Bot prototype**. It is not a user-created bot runtime. It does not currently mix the roles in working code; instead, the runtime role is absent. The in-memory UserSession classes are unrelated lead/account form state and are not evidence of a generic flow interpreter.

## 5. Architecture Drivers

| Driver | Priority | Architectural response |
|---|---:|---|
| One primary developer | Critical | Two repositories, one dynamic backend process, one database, no production Python. |
| Complete end-to-end MVP | Critical | Implement authoring and runtime as one vertical slice before broad node scope. |
| Tenant and secret isolation | Critical | Server-side authorization, composite tenant keys, encrypted tokens and no browser secrets. |
| Durable bot conversations | Critical | PostgreSQL conversation state and idempotent update inbox. |
| Safe flow evolution | Critical | Immutable published versions and conversations pinned to a version. |
| Mobile Mini App editor UX | High | React/TypeScript, React Flow and normalized local editor state. |
| Telegram update reliability | High | Webhook, per-bot secret, durable inbox and DB-backed outbox/retry. |
| Future independent runtime scaling | Medium | Clean module ports and data ownership; split only on measured triggers. |
| Existing Java knowledge/code | Medium | Refactor management concepts; do not preserve accidental package structure. |
| Current prototype value | Medium | Preserve UX behavior and test fixtures, not Drawflow/global-state internals. |

## 6. Constraints and Assumptions

- Telegram is the only execution channel for MVP.
- A user can own multiple bots in one default workspace; the schema supports multiple workspaces without requiring workspace UI in the first slice.
- The platform may accept a user-supplied bot token. Telegram Managed Bots should become the preferred onboarding route if a short spike confirms required BotFather mode and client behavior; bring-your-own token remains the fallback.
- The initial node registry is Start, SendMessage with inline buttons, and optionally WaitForText after the button slice works.
- A published version is immutable.
- Existing conversations remain pinned to the version on which they started.
- PostgreSQL is sufficient for platform data, runtime state, inbox and outbox at MVP load.
- A single Java application instance is acceptable initially; its state is never required to survive only in memory.
- The hosting provider and KMS product are not yet selected.

## 7. Compared Architecture Options

### 7.1 Scoring method

Scores are 1 (poor) to 5 (excellent). Weights sum to 100 and reflect a solo-developer MVP. Weighted totals are shown out of 100.

| Criterion | Weight | A: TS end-to-end | B: TS + Java | C: TS + Python | D: TS + Java + Python |
|---|---:|---:|---:|---:|---:|
| MVP delivery speed | 12 | 4 | 4 | 3 | 2 |
| Frontend development quality | 6 | 5 | 5 | 5 | 5 |
| Visual builder ecosystem | 5 | 5 | 5 | 5 | 5 |
| Telegram ecosystem | 6 | 4 | 4 | 5 | 5 |
| Type safety | 6 | 5 | 5 | 3 | 4 |
| Runtime performance | 4 | 4 | 5 | 4 | 4 |
| Solo-developer maintainability | 12 | 4 | 5 | 3 | 1 |
| Deployable count | 6 | 4 | 5 | 4 | 1 |
| Operational simplicity | 10 | 4 | 5 | 4 | 1 |
| Testability | 6 | 4 | 5 | 4 | 3 |
| Security | 6 | 4 | 5 | 4 | 3 |
| Scaling path | 5 | 4 | 4 | 4 | 5 |
| Existing code reuse | 5 | 2 | 4 | 2 | 4 |
| Migration cost | 5 | 3 | 4 | 2 | 2 |
| Future hiring | 6 | 5 | 5 | 4 | 3 |
| **Weighted total** | **100** | **81.6** | **93.4** | **73.4** | **57.2** |

### 7.2 Score explanation

| Criterion | A | B | C | D |
|---|---|---|---|---|
| MVP speed | One language, but all Java work is discarded. | Frontend is rewritten and backend is refactored, but no cross-service runtime is built. | Requires replacing the Java bot and creating production Python structure. | Requires two backends, a contract and failure handling before the first bot works. |
| Frontend quality | React/TS ecosystem is first-class. | Same first-class frontend, independent of backend language. | Same first-class frontend. | Same first-class frontend. |
| Visual ecosystem | React Flow and typed UI tools fit directly. | Same frontend choice. | Same frontend choice. | Same frontend choice. |
| Telegram ecosystem | Good Node libraries and direct HTTP are available. | Java ecosystem is adequate; direct Bot API adapter avoids old-library lock-in. | Python bot libraries are strong and concise. | Both ecosystems are available, but duplicated. |
| Type safety | Shared TS types are convenient, though runtime validation remains mandatory. | Strong compile-time types in both codebases with OpenAPI/JSON Schema generation. | Python typing is useful but less enforceable at runtime and across boundaries. | Java/Python contracts add serialization/version drift despite typed Java. |
| Performance | More than adequate. | JVM throughput and concurrency are strong for shared runtime. | Adequate for MVP; async discipline is required. | Extra network hop reduces efficiency. |
| Solo maintenance | One language helps, but the developer must move core expertise to Node. | Matches strongest backend skill and keeps only two necessary languages. | Requires owning Python production operations and rewriting existing work. | Three languages and two backend processes are disproportionate. |
| Deployables | Static UI plus one Node backend. | Static UI plus one Java backend. | Static UI plus one Python backend. | Static UI, Java management backend and Python runtime. |
| Operations | Simple, but still a new backend stack. | One JVM and PostgreSQL, with in-process modules. | One Python service and PostgreSQL are manageable. | Cross-service networking, two health models, two deployments and contract rollout. |
| Testability | Good unified tooling, but runtime and API still require integration tests. | JUnit/Testcontainers plus frontend tests and generated contracts are mature. | Good pytest ecosystem, but dynamic runtime contracts need extra discipline. | Cross-language integration and failure tests dominate. |
| Security | Viable, but security patterns must be rebuilt in Node. | Mature Spring security, validation and transaction ecosystem; still requires correct design. | Viable but requires a new secure production baseline. | More attack surface, credentials and network boundaries. |
| Scaling | Node runtime can split later. | Runtime module can split later; JVM supports substantial load before that. | Runtime can split later. | Already independently scalable, but prematurely. |
| Reuse | Frontend concepts only; Java implementation is discarded. | Management flows and Java knowledge are reused selectively. | Frontend concepts only; Java is retired. | Java is reused, but Python prototype still provides almost no production runtime. |
| Migration | Full backend rewrite. | Targeted frontend rewrite plus Java refactor. | Full backend rewrite and language/process change. | Refactor Java and invent a separate Python service simultaneously. |
| Hiring | Large TS market. | Java backend and React/TS are common, clear roles. | React plus Python is hireable, but less aligned with the current owner. | Requires polyglot hires or narrow ownership boundaries too early. |

### 7.3 Decision

Option B wins because it minimizes operational boundaries without sacrificing frontend quality or future scale. TypeScript end-to-end remains a technically valid second choice, but it provides no product capability that compensates for discarding the owner’s Java expertise and current management-bot foundation. Python has no unique production responsibility in this product.

## 8. Final Technology Decision

| Layer | Selected technology | Notes |
|---|---|---|
| Mini App | TypeScript, React, Vite | Static build; no Node runtime. |
| Graph editor | React Flow from xyflow | Adapter isolates library data from domain contract. |
| Client state | Zustand normalized FlowDraft store | Single editor source of truth; React Flow is a projection. |
| Server state | TanStack Query | Cache, retries and invalidation for REST resources. |
| Forms/validation | React Hook Form + Zod | UX validation only; backend remains authoritative. |
| UI | Tailwind CSS + Radix/shadcn-style primitives | Lightweight, accessible, customizable for Telegram themes. |
| Telegram frontend | Official telegram-web-app.js behind a typed adapter | Never trust initDataUnsafe for authorization. |
| Backend/runtime | Java 25 LTS, Spring Boot 4.1.x | No preview features; supported patch at implementation time. |
| Internal modularity | Package-by-feature, Spring Modulith or ArchUnit checks | One Maven deployable; logical modules are not services. |
| Telegram backend | Webhook controllers + a minimal Telegram Bot API HTTP adapter | Avoid making the domain depend on telegrambots 5.7.1. |
| Persistence | PostgreSQL, JPA/JdbcClient as appropriate, Flyway | JSONB for versioned graph snapshots; relational metadata and state. |
| Contracts | OpenAPI 3.1 + JSON Schema 2020-12 | Backend repository is canonical. |
| Reliability | PostgreSQL inbox/outbox and in-process workers | No Kafka/Redis for MVP. |
| Observability | Actuator, Micrometer, structured logs | Prometheus export only if the host consumes it. |
| Tests | JUnit 5, Testcontainers, WireMock; Vitest, Testing Library, Playwright | Contract and cross-tenant tests are mandatory. |

Java 25 is the current LTS choice; Spring Boot 4.1 supports Java 17 through Java 26 according to its system requirements. If one required Telegram dependency blocks Boot 4.1 during the Phase 0 compatibility spike, the temporary fallback is the latest supported Spring Boot 3.5 patch on Java 25, without changing module or contract decisions.

## 9. Target System Architecture

### 9.1 System Context Diagram

~~~mermaid
flowchart TB
    Owner["Platform user / bot owner"]
    EndUser["End user of created bot"]
    Platform["Telegram Bot Builder platform"]
    Telegram["Telegram platform and Bot API"]
    External["Future external integrations"]

    Owner -->|"opens Mini App; designs and publishes"| Platform
    EndUser -->|"messages and button clicks"| Telegram
    Platform <-->|"Mini App identity, webhooks, Bot API calls"| Telegram
    Platform -.->|"future HTTPS calls"| External
~~~

### 9.2 Container Diagram

~~~mermaid
flowchart TB
    Web["Static Mini App\nReact + TypeScript"]
    App["Java modular monolith\nAPI + management bot + runtime"]
    Db["PostgreSQL\nplatform + flows + runtime + jobs"]
    Tg["Telegram\nMini Apps + Bot API"]
    Ops["Logs, metrics and backup target"]

    Web -->|"REST /api/v1"| App
    Web <-->|"Mini App client context"| Tg
    Tg -->|"management and per-bot webhooks"| App
    App -->|"Bot API HTTPS"| Tg
    App <-->|"transactions, inbox, outbox"| Db
    App -->|"telemetry"| Ops
    Db -->|"backups"| Ops
~~~

### 9.3 Component responsibility map

| Component | Responsibility | Language/technology | Data | Contract | Deployment |
|---|---|---|---|---|---|
| Mini App | Screens, graph editing, draft UX, preview, API calls | React/TypeScript | In-memory FlowDraft; optional recovery cache | REST/OpenAPI; Flow Draft schema | Static artifact |
| API/Auth | initData verification, sessions, authorization, resource APIs | Java/Spring | User/workspace/session metadata | HTTPS REST /api/v1 | Java application |
| Management Bot Adapter | Opens Mini App, account/bot status and managed-bot onboarding | Java | No private canonical state | Telegram webhook and in-process use cases | Java application |
| Bot/Workspace Module | Tenant, bot metadata, credentials lifecycle | Java | Relational tables; encrypted secret | In-process interfaces | Java application |
| Flow Authoring Module | Draft, validation, publish, versions and activation | Java | JSONB draft/version + relational metadata | REST and in-process interfaces | Java application |
| Runtime Module | Interpret immutable flow, transition conversation | Java | Conversation, inbox, outbox | In-process interfaces | Java application |
| Telegram Runtime Adapter | Route per-bot webhook and send Telegram commands | Java | Webhook key/secret metadata | Telegram HTTPS | Java application |
| Job Module | Claim inbox/outbox, retry, cleanup and reconciliation | Java | PostgreSQL job rows | In-process | Java application |
| PostgreSQL | Canonical durable state and work queues | PostgreSQL | All server-side product data | SQL | Managed DB or container |

## 10. Repository and Module Boundaries

### 10.1 Repository, module and deployment terminology

| Term | Meaning | Product example |
|---|---|---|
| Repository | Source-control and collaboration boundary. | bot-builder-ui or BotConstructor. |
| Module | Cohesive code boundary with an explicit interface, often in one process. | flow-authoring or runtime inside Java. |
| Process | Running OS program with its own memory. | One Spring Boot JVM. |
| Service | Independently owned runtime capability exposed over a network or messaging boundary. | A future separately deployed runtime service, not an MVP module. |
| Deployable unit | Artifact released independently. | Static frontend bundle or Java container image. |
| Container | Packaging/runtime isolation for a process or static server. | Java app container, Nginx static container, PostgreSQL container. |

An independent repository does not imply a microservice. A module does not need REST. A container is not automatically a service.

### 10.2 Recommended repository map

~~~text
bot-builder-ui/
  docs/system/software-architecture-document.md
  src/app/
  src/features/auth/
  src/features/bots/
  src/features/flow-builder/
  src/features/templates/
  src/domain/flow/
  src/editor/react-flow/
  src/shared/api/generated/
  src/shared/telegram/
  src/shared/ui/
  tests/

BotConstructor/
  constructor-service/
    src/main/java/.../identity/
    src/main/java/.../workspace/
    src/main/java/.../bot/
    src/main/java/.../flow/
    src/main/java/.../runtime/
    src/main/java/.../telegram/management/
    src/main/java/.../telegram/runtime/
    src/main/java/.../jobs/
    src/main/java/.../template/
    src/main/java/.../audit/
    src/main/java/.../shared/
    src/main/resources/db/migration/
  contracts/openapi/platform-api.yaml
  contracts/flow/v1/
  docs/ARCHITECTURE.md
~~~

### 10.3 Repository/module table

| Repository / module | Language | Responsibility | Deployment unit | Dependencies |
|---|---|---|---|---|
| bot-builder-ui / app | TypeScript | Routing, shell, Telegram theme/lifecycle | Static frontend | React, Telegram adapter |
| bot-builder-ui / flow-builder | TypeScript | Graph canvas, node forms, preview, commands | Static frontend | Domain store, React Flow |
| bot-builder-ui / domain/flow | TypeScript | Typed editor model, local invariants and mappings | Static frontend | Generated schema/types |
| bot-builder-ui / api | TypeScript | Generated client, auth and error mapping | Static frontend | OpenAPI artifact |
| BotConstructor / identity-workspace | Java | initData auth, user/workspace authorization | Java app | PostgreSQL |
| BotConstructor / bot | Java | Bot metadata, credential verification/encryption, webhook setup | Java app | Telegram adapter, PostgreSQL |
| BotConstructor / flow | Java | Draft, validation, publish, version activation | Java app | JSON Schema, PostgreSQL |
| BotConstructor / runtime | Java | Interpreter, conversations, idempotency, transitions | Java app | Flow module, PostgreSQL |
| BotConstructor / telegram-management | Java | Platform bot update mapping and Mini App launcher | Java app | Identity/bot use cases |
| BotConstructor / telegram-runtime | Java | Per-bot webhook routing and outbound API | Java app | Runtime/bot ports |
| BotConstructor / jobs | Java | Inbox/outbox processing, retry and cleanup | Java app | PostgreSQL |
| BotConstructor / contracts | YAML/JSON | Public machine-readable API and flow contracts | Release artifact | OpenAPI/JSON Schema tooling |

### 10.4 Two repositories vs monorepo

Keep two repositories. Their concerns and release artifacts are genuinely different, and the current public/private split matters. Do not add a third runtime repository. A monorepo would simplify atomic contract changes, but migrating repositories now creates organizational work without reducing runtime complexity.

Contract workflow:

1. BotConstructor owns OpenAPI and JSON Schema.
2. Backend CI validates contracts and publishes versioned artifacts from a tag/release.
3. bot-builder-ui pins a contract version and generates TypeScript client/types.
4. Generated client code may be committed for deterministic frontend builds.
5. CI fails if generated code differs from the pinned contract.

The system SAD remains private in bot-builder-ui because BotConstructor is public. The public backend docs should contain a sanitized module map and links to public contracts, not token-encryption operational details, threat assumptions, infrastructure topology or provider account information.

## 11. Frontend Architecture

### 11.1 Repository role

bot-builder-ui becomes a clean frontend repository. It may contain frontend build tooling, test tooling, generated API code and the private system SAD. It must not contain a production HTTP backend, Telegram webhook receiver, bot runtime or secret storage.

The frontend is responsible for:

- rendering screens and navigation;
- Telegram Mini App lifecycle/theme integration;
- graph drag-and-drop and viewport behavior;
- node configuration forms;
- normalized local draft editing;
- immediate UX validation and preview;
- optimistic-save UX and conflict presentation;
- calling the Java REST API.

The frontend is not responsible for:

- durable canonical persistence;
- Telegram bot token storage or display after submission;
- authorization decisions or tenant isolation;
- authoritative flow validation;
- publishing/compiling a runtime flow;
- processing Telegram webhooks or executing flows;
- billing enforcement or audit truth.

### 11.2 Framework and graph-library decision

| Candidate | Strengths | Weaknesses | Decision |
|---|---|---|---|
| React + React Flow | Strong typed graph ecosystem, custom nodes/handles, active project, large hiring pool, fits React state/test tools | Requires a rewrite and disciplined adapter boundaries | **Selected** |
| Vue + Vue Flow | Strong developer experience and active Vue-specific graph library | Smaller ecosystem/team familiarity than the selected path | Viable fallback only if Vue becomes the owner’s clear preference before Phase 1 |
| Svelte + Svelte Flow | Compact components and good performance | Smaller hiring/component ecosystem and fewer established complex-editor patterns | Not selected |
| Rete.js | Powerful node-editor/processing abstractions and plugins | More engine abstraction and customization than the MVP needs | Not selected |
| Drawflow | Current prototype reuse and low initial learning cost | Loosely typed data, global/DOM-oriented integration and weak current maintenance signal | Replace |

At the 2026-08-27 review, xyflow/xyflow was active in August 2026, while the last Drawflow repository commit was in September 2024. The product document’s earlier decision to defer React Flow is superseded by this SAD because the prototype is still small and the cost of preserving its unstable state model is higher than rewriting the editor boundary now.

Primary references:

- [xyflow/xyflow](https://github.com/xyflow/xyflow)
- [jerosoler/Drawflow](https://github.com/jerosoler/Drawflow)
- [Vue Flow](https://github.com/bcakmakoglu/vue-flow)
- [Rete.js](https://github.com/retejs/rete)

### 11.3 Frontend Module Diagram

~~~mermaid
flowchart TB
    Shell["App shell\nrouting, Telegram lifecycle"]
    Features["Feature screens\nbots, builder, versions"]
    Store["Normalized FlowDraft store\ncommands, undo, dirty state"]
    Editor["React Flow adapter\nnodes, edges, viewport"]
    Gateways["Generated REST client\nTelegram typed adapter"]
    UI["Forms and UI primitives\nvalidation and accessibility"]

    Shell --> Features
    Features --> Store
    Features --> UI
    Store --> Editor
    Features --> Gateways
~~~

### 11.4 Canonical frontend state

The in-memory normalized FlowDraft store is the sole source of truth while the user edits. It contains domain nodes, ports, buttons and edges keyed by stable UUID. React Flow nodes/edges are derived projections. Forms dispatch commands to the store. Rendered DOM and node HTML never represent domain state.

State hierarchy:

1. The backend draft with revision is canonical across sessions and devices.
2. The current Zustand FlowDraft is canonical inside an editing session.
3. React Flow receives derived nodes/edges and emits user intents.
4. Forms receive selected entity values and dispatch typed commands.
5. localStorage may hold an encrypted-free, token-free crash-recovery draft cache with a TTL, but never the authoritative bot or published state.

Each state mutation is a command such as AddNode, UpdateNodeConfig, AddButton, ReorderButton, DeleteButton, ConnectPorts or DeleteEdge. Commands make undo/redo and tests possible. They must not recreate a node solely to update its label.

### 11.5 Existing frontend component disposition

| Current item | Decision | Target | Reason |
|---|---|---|---|
| bot_server_example.py | Use only as reference, then Remove | Backend contract tests/fixtures | It is a fake unauthenticated file-backed server, not frontend or production runtime. |
| requirements.txt | Remove | None | No production Python remains. |
| localStorage bot database | Replace | REST + PostgreSQL | Browser storage cannot provide tenancy, durability or secure multi-device use. |
| localStorage recovery | Keep temporarily with strict scope | Expiring unsaved-draft recovery only | It may protect user edits but stores no credentials or canonical status. |
| Vanilla JavaScript | Rewrite | TypeScript/React | Current globals, inline scripts and duplicate modules are more costly to stabilize than to replace. |
| Drawflow | Replace | React Flow adapter | Typed custom nodes and active ecosystem justify migration before runtime coupling grows. |
| example_bot_export.json | Migrate | Versioned FlowDraft/ExecutableFlow test fixture | Its current shape is inconsistent and has no schema/version. |
| main-old.js | Remove after behavior inventory | Tests and git history | Duplicate inactive implementation. |
| storage.js, bot-manager.js, node-manager.js | Use as behavioral reference | Feature modules and store commands | They are not consistently loaded by active pages. |
| HTML inline scripts | Rewrite | React components/hooks | Navigation, persistence and activation logic must not remain page globals. |

### 11.6 Frontend validation and tests

- Zod schemas give immediate field feedback using types generated or aligned with backend JSON Schema.
- Server validation is always run before publish and can return graph-level errors mapped to node/edge IDs.
- Vitest covers reducers/commands, schema adapters and graph rules.
- React Testing Library covers node forms, error presentation and Telegram theme integration.
- Playwright covers Mini App auth bootstrap, draft save conflict, publish and preview using a mocked Telegram adapter/backend.
- Accessibility checks cover keyboard navigation, focus management and labels. Mobile viewport tests are mandatory because the editor runs inside Telegram.

## 12. Java/Backend Architecture

### 12.1 Modular monolith decision

Use one Spring Boot application and initially one Maven module. Enforce package-by-feature boundaries using Spring Modulith verification or ArchUnit. A multi-module Maven build is optional only if package rules prove insufficient; it is not required to obtain clean architecture.

Modules expose application use-case interfaces. They do not call each other’s repositories. Telegram and REST controllers are adapters. Domain code does not depend on Telegram library classes, HTTP requests or JPA entities.

### 12.2 Java/Backend Module Diagram

~~~mermaid
flowchart TB
    Ingress["Inbound adapters\nREST, management webhook, bot webhook"]
    Access["Identity and workspace\nsession and authorization"]
    Control["Bot and flow control plane\ndrafts, credentials, publish"]
    Runtime["Runtime data plane\ninbox, interpreter, conversation"]
    Jobs["Background jobs\noutbox, retry, cleanup"]
    Infra["Outbound/persistence adapters\nTelegram API, PostgreSQL, crypto"]

    Ingress --> Access
    Access --> Control
    Ingress --> Runtime
    Control --> Runtime
    Runtime --> Jobs
    Control --> Infra
    Runtime --> Infra
    Jobs --> Infra
~~~

### 12.3 Module responsibilities

| Module | Owns | Must not own |
|---|---|---|
| identity | Telegram initData validation, short-lived platform session, User identity | Bot credentials or graph state |
| workspace | Workspace/membership and tenant authorization policies | Telegram update parsing |
| bot | Bot metadata, credential lifecycle, getMe verification, webhook configuration | Draft editing UI or conversation transition |
| flow | FlowDraft, validation, compilation, immutable FlowVersion, active-version pointer | Telegram network calls |
| telegram-management | Mapping management-bot updates to application use cases and Mini App links | User-created flow execution |
| telegram-runtime | Per-bot webhook identity and Telegram outbound command adapter | Business graph semantics |
| runtime | Inbox processing, interpreter, conversation state, action tokens | HTTP controllers or encryption implementation |
| jobs | Work claiming, retry schedules, dead-letter/reconciliation and retention | Product decisions |
| template | Versioned seed flows | Billing or runtime state |
| billing | Deferred capability boundary | MVP behavior until pricing is approved |
| audit | Security/product audit events | Raw message bodies or secrets |

### 12.4 Evaluation of current Java choices

| Current choice | Decision | Detail |
|---|---|---|
| Existing handlers | Refactor | Preserve user-visible /start and instruction intent; move behavior to use cases and stateless adapters. |
| Long polling | Keep only for local development | Production uses webhooks for both platform and user-created bots. |
| ConcurrentHashMap sessions | Remove | Replace with PostgreSQL Conversation and platform session models. |
| UserData entity | Rewrite | Replace chainId/count/list model with User, Workspace, Membership and Bot tables. |
| JPA/PostgreSQL | Keep and refactor | Add explicit mappings, Flyway and repository boundaries. Use JdbcClient where work claiming is clearer than JPA. |
| spring-boot-starter-web | Keep | Add REST and webhook adapters. |
| telegrambots 5.7.1 | Replace or update behind adapter | It predates current Bot API capabilities and should not define domain DTOs. A minimal direct HTTP adapter is the default. |
| Java 23 preview | Replace | Java 25 LTS, no preview flags. |
| Spring Cloud BOM | Remove unless used | It provides no MVP capability. |
| ddl-auto update | Replace | Flyway owns schema; Hibernate validate only outside tests. |
| Dockerfile/Compose | Refactor | Correct artifact build, enable app service and add health dependencies. |
| docs/ARCHITECTURE.md | Rewrite as sanitized module map | It is not the system SAD and is stale at current HEAD. |

## 13. Bot Runtime Architecture

### 13.1 Runtime model comparison

| Model | Complexity | Hosting cost | Isolation | Scaling/deployment | Debug/rollback | Solo support | Decision |
|---|---|---|---|---|---|---|---|
| Generate source per bot | Very high | High | Medium | Build/deploy pipeline per change | Difficult; generated artifacts drift | Poor | Reject |
| Process/container per bot | High | High even when idle | Strong process isolation | Operational explosion as bots grow | Clear per bot but hard fleet management | Poor | Reject for MVP |
| Shared multi-tenant interpreter | Moderate | Low | Logical isolation with quotas | Scale workers/app replicas together, split later | Central trace plus version checksum; instant version activation | Best | **Select** |
| Serverless function per bot | High | Variable; cold starts | Strong compute isolation | Secret/config distribution and many functions | Provider-specific versioning | Poor for one developer | Reject |

### 13.2 Shared runtime model

The runtime does not generate or deploy code. It interprets a server-validated ExecutableFlowVersion:

1. identify Bot from an unguessable webhook key and verify Telegram secret header;
2. insert Telegram update into update_inbox with unique (bot_id, update_id);
3. return HTTP 200 after durable acceptance;
4. an in-process worker claims the update;
5. lock or optimistically update the relevant Conversation;
6. load the conversation’s pinned FlowVersion;
7. resolve input/action and execute bounded transitions;
8. persist the new ConversationState and outbound commands in one database transaction;
9. an outbox worker sends Telegram requests with retry/backoff;
10. expose terminal failures through runtime status, logs and metrics.

There is no shared mutable conversation state in JVM memory. Caches may hold immutable published versions by checksum and can be discarded at any time.

### 13.3 Runtime fault isolation

- Per-bot and per-workspace rate limits and quotas.
- Unique update deduplication.
- Conversation-level serialization/locking.
- Maximum automatic transitions per update.
- Timeout and retry policy per Telegram/external call.
- Poison update status and dead-letter table after bounded attempts.
- Per-bot failure counters and circuit breaking for invalid/revoked credentials.
- No tenant data in static fields or cross-request singleton mutation.
- Published flow validation before activation.

Telegram outbound methods do not provide a general exactly-once idempotency key. The platform guarantees durable at-least-once processing with deduplicated inbound updates; an extremely narrow crash window after Telegram accepts a send but before outbox acknowledgement can cause a duplicate outbound message. This known limitation should be documented and measured.

### 13.4 Python decision

Python has no unique workload here. JSON parsing, graph validation, state machines, webhook handling, PostgreSQL transactions and Telegram HTTPS calls are all straightforward in Java. A Python service would add:

- another deployment and health model;
- a cross-language contract and version rollout;
- token propagation or duplicated Telegram clients;
- network failure/retry semantics between control plane and runtime;
- separate testing, logging and dependency-security work.

Therefore, do not use Python in production. Revisit only if a future capability depends on a mature Python-only library with measured product value and cannot safely run as an offline worker or be implemented at reasonable Java cost.

## 14. Telegram Integration

### 14.1 Platform Management Bot

The platform bot:

- opens the Mini App through a menu/main Mini App button;
- shows account, bot and publication status;
- starts bot credential/Managed Bot onboarding;
- may expose billing/settings later;
- never executes a user-authored business flow.

Its token is a platform deployment secret, not a row in the user bot credential table. Production ingress is:

- POST /webhooks/telegram/management/{randomKey}
- Telegram X-Telegram-Bot-Api-Secret-Token verification
- update_id deduplication where actions have side effects

Long polling remains an opt-in local profile only and must not be enabled when a webhook is configured.

### 14.2 User-created bots

Each user-created bot has:

- its own Telegram bot ID/username and credential source;
- an unguessable webhook routing key;
- a distinct Telegram webhook secret;
- a workspace owner;
- an active published flow pointer;
- isolated conversations, inbox/outbox records, rate limits and status.

Production ingress is:

- POST /webhooks/telegram/bots/{webhookKey}
- lookup by hashed/random webhook key
- constant-time comparison of the Telegram secret header
- no Telegram bot token in URL, path, logs or frontend

### 14.3 Credential onboarding

Bring-your-own token flow:

1. send token over authenticated HTTPS;
2. keep plaintext only inside the request/use-case scope;
3. call getMe to verify and obtain bot identity;
4. reject a token already bound to another workspace;
5. encrypt with AES-256-GCM using envelope encryption;
6. store ciphertext, nonce, key version and non-secret bot metadata;
7. generate webhook key/secret and call setWebhook;
8. return only masked identity and status, never the token.

Managed Bots opportunity:

Telegram currently supports Bot Management Mode, managed-bot creation links and getManagedBotToken. Run a 0.5–1.5 day Phase 0 spike. If verified, make this the preferred onboarding because the management bot can guide creation and obtain/rotate the managed token; preserve bring-your-own token for existing or non-managed bots. Both routes terminate at the same BotCredential use case and encrypted storage.

Official references:

- [Telegram Mini App initData validation](https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app)
- [Telegram Bot API webhooks and secret_token](https://core.telegram.org/bots/api#setwebhook)
- [Telegram Managed Bots](https://core.telegram.org/bots/features#managed-bots)

### 14.4 Telegram Update Execution Sequence

~~~mermaid
sequenceDiagram
    participant T as Telegram
    participant I as Webhook ingress
    participant D as PostgreSQL
    participant R as Runtime worker
    participant O as Telegram outbox

    T->>I: Update + secret header
    I->>D: Insert inbox (botId, updateId)
    D-->>I: Inserted or duplicate
    I-->>T: 200 OK
    R->>D: Claim inbox and lock conversation
    R->>D: Load pinned executable version
    R->>R: Interpret bounded transitions
    R->>D: Save state + outbound command
    O->>D: Claim outbound command
    O->>T: Bot API send/edit request
    O->>D: Mark sent or schedule retry
~~~

## 15. REST and Communication Architecture

### 15.1 Communication boundary decisions

| Source | Target | Communication needed? | Recommended mechanism | Reason |
|---|---|---|---|---|
| Telegram Mini App | Backend | Yes | HTTPS REST/JSON | Separate process and trust boundary; request/response authoring operations. |
| Management Telegram bot adapter | Backend modules | Yes | In-process application calls | Same JVM; REST would add no isolation or ownership. |
| Backend authoring | Flow runtime | Yes | In-process application/event call | Same JVM and database transaction boundary in MVP. |
| Telegram | Management bot | Yes | HTTPS webhook in production; long polling local only | Fast production delivery and one platform token. |
| Telegram | User-created runtime | Yes | HTTPS per-bot routed webhook | Dynamic multi-bot ingress and durable acceptance. |
| Runtime | PostgreSQL | Yes | Direct SQL/JPA/JdbcClient | Runtime owns its state and work records. |
| Runtime | External integrations | When a node requires it | HTTPS client through an allowlisted integration adapter | No generic integration node in first MVP. |
| Future split backend | Future runtime service | Maybe later | Queue for update/command work; REST only for control/read operations | Avoid synchronous REST on every update after separation. |

### 15.2 Core REST API

| Method | Endpoint | Purpose | Request | Response | Auth | Idempotency |
|---|---|---|---|---|---|---|
| POST | /api/v1/auth/telegram/session | Verify initData and create short session | initData | access token, expiry, user, default workspace | Telegram initData | Same valid initData may return/rotate a session |
| GET | /api/v1/me | Current user and memberships | — | user + workspaces | Bearer | Safe |
| GET | /api/v1/workspaces | List accessible workspaces | — | page of workspaces | Bearer | Safe |
| POST | /api/v1/workspaces | Create workspace if enabled | name | workspace | Bearer | Idempotency-Key |
| GET | /api/v1/workspaces/{workspaceId}/bots | List bots | filters/page | bot summaries | Bearer + membership | Safe |
| POST | /api/v1/workspaces/{workspaceId}/bots | Create bot record | name | bot | Bearer + owner/editor | Idempotency-Key |
| GET | /api/v1/bots/{botId} | Bot details/status | — | bot without secret | Bearer + tenant | Safe |
| PATCH | /api/v1/bots/{botId} | Rename/update safe metadata | patch + revision | updated bot | Bearer + tenant | If-Match |
| PUT | /api/v1/bots/{botId}/telegram-credential | Verify and attach/replace token | token | masked bot identity + webhook status | Bearer + owner | Idempotency-Key; never echo token |
| DELETE | /api/v1/bots/{botId}/telegram-credential | Disconnect and remove encrypted credential | confirmation | 204 | Bearer + owner | Idempotency-Key |
| GET | /api/v1/bots/{botId}/flows/draft | Load canonical draft | — | FlowDraft + revision + ETag | Bearer + tenant | Safe |
| PUT | /api/v1/bots/{botId}/flows/draft | Save complete draft | FlowDraft | saved draft metadata | Bearer + tenant | If-Match revision |
| POST | /api/v1/bots/{botId}/flows/draft/validate | Authoritative validation | optional draft or current revision | errors/warnings | Bearer + tenant | Safe by payload hash |
| POST | /api/v1/bots/{botId}/flows/publish | Validate, compile and activate new immutable version | expected draft revision, note | FlowVersion + checksum | Bearer + owner/editor | Idempotency-Key |
| GET | /api/v1/bots/{botId}/flows/versions | List versions | page | version summaries | Bearer + tenant | Safe |
| GET | /api/v1/bots/{botId}/flows/versions/{versionId} | View immutable version | — | editor snapshot + metadata | Bearer + tenant | Safe |
| POST | /api/v1/bots/{botId}/flows/versions/{versionId}/activate | Roll back/activate an existing version | reason | active version status | Bearer + owner/editor | Idempotency-Key |
| GET | /api/v1/bots/{botId}/runtime-status | Health, last update/error and backlog summary | — | sanitized status | Bearer + tenant | Safe |
| GET | /api/v1/templates | List supported templates | filters | versioned template summaries | Bearer | Safe |
| GET | /actuator/health | Process/liveness health | — | health | Public/sanitized or platform probe | Safe |

Telegram webhook endpoints are not part of the Mini App REST API and are absent from the generated client.

### 15.3 API policies

- OpenAPI 3.1 is canonical and reviewed like code.
- Request/response DTOs are not JPA entities.
- Errors use application/problem+json aligned with RFC 9457: type, title, status, detail, instance, code, traceId and optional fieldErrors/entityReferences.
- URI major version is /api/v1. Additive fields are backward compatible. Breaking changes require /api/v2 or an agreed migration window.
- Use If-Match/ETag for mutable Bot metadata and FlowDraft revision. A conflict returns 412 with current revision metadata.
- Use Idempotency-Key on create, credential mutation, publish and activation. Store key + actor + route + request hash + response for a bounded TTL.
- Every tenant-scoped use case receives authorized workspace context; repositories require workspace_id/bot_id together where possible.
- Client validation improves UX; backend validation is authoritative; runtime accepts only compiled published versions and still checks supported schema/type versions.
- CORS allows only exact production and local development origins. Wildcard origins are forbidden with authorization headers.
- Bearer sessions are kept in memory and refreshed by revalidating initData; do not persist access tokens in localStorage.
- With Authorization headers rather than ambient cookies, CSRF protection is not required for API mutations. If the architecture later moves to cookies, SameSite and CSRF tokens become mandatory.

### 15.4 Telegram initData

The authentication endpoint must:

- accept the raw Telegram.WebApp.initData string;
- reconstruct and verify the official HMAC data-check string with the management bot secret;
- compare hashes in constant time;
- reject invalid, missing and expired auth_date values using a configurable short age, default five minutes;
- never trust initDataUnsafe by itself;
- map the verified Telegram user ID to User;
- issue a short-lived platform access token/session;
- avoid logging raw initData because it can contain personal data and a valid hash.

## 16. Flow Domain Model

### 16.1 Model strategy comparison

| Strategy | Benefits | Problems | Decision |
|---|---|---|---|
| Persist Drawflow export directly | Lowest prototype effort | Couples backend/runtime to UI library IDs, ports, module shape and upgrade behavior | Reject |
| One custom domain schema for editor and runtime | Stable contract and simpler than Drawflow | Runtime receives editor-only layout and authoring details; execution normalization happens repeatedly | Acceptable but not selected |
| Separate editor model and executable runtime model | Stable UI-independent draft plus validated, compact immutable execution snapshot | Requires a publish compiler/mapping | **Select** |

The frontend and backend share the versioned FlowDraft contract. Publish validates and compiles it into ExecutableFlowVersion. React Flow’s internal shape never crosses the API.

### 16.2 Core entities

| Entity | Purpose | Key rules |
|---|---|---|
| Bot | Tenant-owned Telegram bot configuration | One active published version pointer; secret stored separately |
| Flow | Stable logical flow identity for a bot | Owns one mutable draft and many versions |
| FlowVersion | Immutable published editor and executable snapshots | Monotonic version number and checksum |
| PublishedVersion | Bot pointer to currently active FlowVersion | Activation is audited; old versions remain |
| Node | Stable graph step | UUID, NodeType, typeVersion, config and optional position |
| Edge | Directed link between ports | UUID and stable source/target port IDs |
| Port | Typed input/output endpoint | Stable UUID; direction, kind and cardinality |
| Button | User-visible action on SendMessage | Stable UUID, order, label and stable output port ID |
| NodeType | Registry definition and compiler/executor | Backend is canonical; frontend receives supported registry metadata/build types |
| Template | Versioned seed FlowDraft | Copy-on-use; template updates do not mutate user drafts |
| Conversation | Bot + Telegram chat/user execution instance | Pinned FlowVersion, lifecycle and optimistic version |
| ConversationState | Current wait/node/variables/action context | Durable JSONB plus indexed relational keys |

### 16.3 Illustrative FlowDraft v1

~~~json
{
  "schemaVersion": 1,
  "flowId": "018f...uuid",
  "revision": 17,
  "startNodeId": "018f...start",
  "nodes": [
    {
      "id": "018f...message",
      "type": "SEND_MESSAGE",
      "typeVersion": 1,
      "position": { "x": 320, "y": 140 },
      "config": {
        "text": "Choose an option",
        "buttons": [
          {
            "id": "018f...button",
            "label": "Help",
            "outputPortId": "018f...port",
            "order": 0
          }
        ]
      },
      "ports": [
        { "id": "018f...input", "direction": "IN", "kind": "CONTROL" },
        { "id": "018f...port", "direction": "OUT", "kind": "BUTTON" }
      ]
    }
  ],
  "edges": [
    {
      "id": "018f...edge",
      "source": { "nodeId": "018f...message", "portId": "018f...port" },
      "target": { "nodeId": "018f...target", "portId": "018f...targetInput" }
    }
  ]
}
~~~

ExecutableFlowVersion removes layout-only fields, resolves node/port maps, normalizes defaults, records schema/compiler/node-registry versions and is addressed by checksum. It is produced only by the backend.

### 16.4 Stable IDs and button/edge behavior

- Generate UUIDv7 or random UUID client-side for new editor entities; backend validates uniqueness.
- Node ID never changes when label/config changes.
- Button ID and its outputPortId survive label edits and reorder.
- Reorder changes order only; existing edge remains attached to the same outputPortId.
- Deleting an unconnected button removes its port.
- Deleting a connected button is blocked by default and returns the affected edge IDs. After explicit confirmation, one command removes the button, port and connected edges atomically.
- Unknown/removed port references are publish errors.
- React Flow handle IDs equal stable domain port IDs only inside the adapter.

### 16.5 Validation

Validation is layered:

1. JSON Schema: shape, required fields, limits and types.
2. Registry validation: supported NodeType/typeVersion and node-specific configuration.
3. Graph validation: one Start, valid ports, edge cardinality, unique IDs and valid references.
4. Reachability: all enabled nodes must be reachable; orphan nodes are draft warnings and publish errors by default.
5. Branch validation: required buttons/outputs have exactly the allowed connections.
6. Cycle validation: cycles are allowed only when every possible cycle contains a suspending/user-input node. Automatic-only cycles are rejected.
7. Safety limits: maximum nodes/edges, message/button length, transition budget and callback-data constraints.
8. Compilation: create deterministic ExecutableFlowVersion and checksum.

The runtime also enforces a maximum automatic-transition count per update even for validated flows.

### 16.6 Draft, publish, rollback and compatibility

- FlowDraft is mutable and has an integer revision used for optimistic locking.
- Publish reads an expected revision, validates, compiles and inserts a new immutable version in one transaction.
- Published versions are never edited or deleted while referenced by a Conversation.
- New conversations use Bot.active_flow_version_id.
- Existing conversations remain pinned to their starting version until terminal/reset; publication cannot invalidate their current node/action.
- Rollback changes the active pointer to an older compatible immutable version; it does not rewrite conversation history.
- schemaVersion and per-node typeVersion drive migration.
- The runtime supports all versions still referenced by live conversations. A retirement job may archive a compiler/type version only when no active conversations depend on it.
- Draft read adapters can migrate older schema versions to the current editor model; original published bytes/checksum remain immutable.

### 16.7 Flow Save/Publish Sequence

~~~mermaid
sequenceDiagram
    participant U as Mini App
    participant A as REST API
    participant F as Flow module
    participant D as PostgreSQL

    U->>A: PUT draft + If-Match revision
    A->>F: Save authorized FlowDraft
    F->>F: Schema and basic graph validation
    F->>D: Update draft where revision matches
    D-->>U: New revision and ETag
    U->>A: POST publish + Idempotency-Key
    A->>F: Publish expected revision
    F->>F: Full validation and compile
    F->>D: Insert immutable version + activate
    D-->>U: Version, checksum and warnings
~~~

## 17. Data Architecture and Ownership

### 17.1 Data ownership

| Data | Canonical owner | Storage | Who changes it | Who reads it | Versioning |
|---|---|---|---|---|---|
| User | identity module | users | Auth/user use cases | API, audit | Row version/audit |
| Workspace | workspace module | workspaces, memberships | Workspace use cases | All authorized modules | Row version/audit |
| Bot | bot module | bots | Bot use cases | Flow/runtime/API | Row version + audit |
| Bot token | bot credential module | bot_credentials ciphertext | Credential use case only | Telegram adapter through narrow decrypt port | key_version + rotation audit |
| Draft flow | flow module | flow_drafts JSONB | Flow authoring use cases | API/publisher | revision + schemaVersion |
| Published flow | flow module | flow_versions JSONB | Publish only | Runtime, API | Immutable version/checksum |
| Active version pointer | flow/bot use case | bots.active_flow_version_id | Publish/activate use cases | Runtime | Audit event |
| Conversation state | runtime module | conversations JSONB + indexed keys | Runtime transition transaction | Runtime/status API | pinned flow version + row version |
| Update inbox | runtime module | telegram_update_inbox | Webhook ingress/worker | Worker/ops | Unique bot_id + update_id |
| Outbound command | jobs/runtime | telegram_outbox | Runtime transaction/worker | Worker/ops | status + attempt |
| Template | template module | templates + template_versions | Admin/release process | API/flow copy | Immutable template version |
| Audit | audit module | audit_events | Trusted application use cases | Authorized admin/ops | Append-only |

### 17.2 Storage model

- Use relational columns for identity, tenant ownership, lifecycle, status, foreign keys and queryable timestamps.
- Use JSONB for atomic FlowDraft, immutable editor snapshot, immutable executable snapshot and flexible ConversationState.
- Add workspace_id to tenant-owned aggregate roots and composite uniqueness/foreign-key rules where practical.
- Do not normalize Node and Edge into mutable row graphs for MVP; publication and version reads are aggregate operations.
- Index Bot webhook key hash, active version, Conversation (bot_id, telegram_chat_id), inbox claim status, outbox next_attempt_at and active conversation version references.
- Use Flyway for every schema change. Hibernate ddl-auto is validate in non-test environments.

### 17.3 Data ownership model

~~~mermaid
erDiagram
    USER ||--o{ MEMBERSHIP : has
    WORKSPACE ||--o{ MEMBERSHIP : contains
    WORKSPACE ||--o{ BOT : owns
    BOT ||--|| FLOW : configures
    FLOW ||--o{ FLOW_VERSION : publishes
    BOT ||--o{ CONVERSATION : serves
    FLOW_VERSION ||--o{ CONVERSATION : pins
    BOT ||--o{ UPDATE_INBOX : receives
    CONVERSATION ||--o{ OUTBOX_COMMAND : emits
~~~

## 18. Security Architecture

### 18.1 Primary threats and controls

| Threat | Control |
|---|---|
| Forged Mini App identity | Verify raw initData HMAC, auth_date and hash in constant time; short-lived session. |
| Cross-tenant resource access | Workspace membership at use-case boundary; tenant-scoped repositories; negative integration tests. |
| Bot token disclosure | HTTPS, memory-only frontend submission, AES-GCM envelope encryption, redaction, no response echo and rotation. |
| Forged Telegram webhook | Random routing key plus per-bot X-Telegram-Bot-Api-Secret-Token verification. |
| Duplicate/replayed updates | Unique (bot_id, update_id) inbox and idempotent handlers. |
| Client-side injection | React escaping, no raw HTML from flow text, CSP and dependency controls. |
| Malicious/invalid flow | Server schema/registry/graph validation, immutable compile and transition budget. |
| Noisy tenant or bot | Per-bot/workspace limits, bounded jobs, circuit breaker and dead-letter state. |
| Secret leakage in logs/errors | Central redaction filter, structured safe fields, no raw initData/token/update body by default. |
| Lost/corrupted data | Managed PostgreSQL or tested backup, PITR where available, restore drill. |
| Compromised dependency | Dependabot/Renovate, SBOM, dependency scanning and supported versions. |

### 18.2 Bot secret storage

Use envelope encryption:

- generate a random data-encryption key or derive an application data key under a KMS-managed key-encryption key;
- encrypt token with AES-256-GCM and authenticated context containing bot_id/workspace_id;
- persist ciphertext, nonce/tag, algorithm and key_version;
- keep the production master/KMS credential outside the database and repository;
- restrict decrypt operations to the Telegram credential adapter;
- audit verification, replacement, rotation and deletion without token values.

For local development only, a 32-byte master key can come from an ignored environment file. Production should use the selected cloud secret/KMS facility. Database encryption at rest is additive and does not replace application-level token encryption.

The current repository documentation states that a token was previously committed. Treat it as compromised: revoke/rotate it before any further use. Removing it from the current file or rewriting history cannot make the old token secret again.

### 18.3 Authorization and privacy

- Default role model: Workspace OWNER and EDITOR; VIEWER may be added when sharing is in scope.
- The first MVP UI can create one default owner workspace while the backend keeps membership checks explicit.
- Avoid collecting or logging message text unless required for the product; define retention before production.
- Conversation content/variables require a retention/delete policy and user-facing privacy statement.
- Audit credential, membership, publish, activate/rollback and destructive changes.
- Rate limit auth attempts, bot-credential verification, publish and webhook intake.
- Generic outbound HTTP nodes are out of scope; when added, use destination allowlists, DNS/IP revalidation and SSRF defenses.

## 19. Deployment Architecture

### 19.1 MVP topology

~~~mermaid
flowchart TB
    Client["Telegram WebView / browser"]
    Edge["TLS edge / reverse proxy"]
    Static["Static frontend hosting"]
    App["Java application container"]
    Db["PostgreSQL with persistent volume/backups"]
    Tg["Telegram Bot API"]

    Client --> Edge
    Edge --> Static
    Edge --> App
    Tg --> Edge
    App --> Tg
    App --> Db
~~~

Recommended production:

- Static frontend on CDN/static hosting.
- Java container on a simple managed container/app platform.
- Managed PostgreSQL if budget permits.
- One public HTTPS origin is preferable: / serves static UI and /api plus /webhooks route to Java. This reduces CORS and operational mistakes without requiring the same repository or process.
- Run Flyway as a release step or controlled application startup before serving traffic.
- One Java instance is acceptable until availability/load triggers justify two.

Local Docker Compose:

- frontend static/dev service;
- Java application;
- PostgreSQL;
- optional local observability profile only when needed.

No Kubernetes, Kafka, Redis, service mesh or separate runtime image is required.

### 19.2 Environment and delivery

- Separate local, test/staging and production configuration.
- Secrets come from ignored local env or platform secret manager; never image build arguments or repository files.
- CI builds/tests both repositories independently.
- Backend CI validates migrations, architecture boundaries, OpenAPI and JSON Schemas, builds an SBOM and container image.
- Frontend CI pins/generates the API client, runs unit/component/E2E tests and builds immutable static assets.
- Production deploy uses immutable image/artifact versions and records backend commit, frontend commit, contract version and migration version.
- Rollback must not roll database schema backward destructively; migrations use expand/contract where compatibility matters.

## 20. Observability

### 20.1 Logs

Use structured JSON logs with:

- timestamp, level, service/version and environment;
- traceId/requestId;
- workspaceId/botId/conversationId as internal identifiers where needed;
- Telegram update_id and FlowVersion checksum;
- operation, duration, outcome and retry attempt;
- no bot token, webhook secret, raw initData or full update/message body by default.

Create explicit events for webhook rejected, duplicate update, inbox delay, flow validation failed, publish completed, interpreter failure, outbox retry/dead-letter and credential revoked.

### 20.2 Metrics

Minimum metrics:

- HTTP/webhook request rate, latency and error rate;
- accepted, duplicate, rejected and pending updates;
- inbox processing delay and failure count;
- active conversations and transition latency;
- outbox pending, send latency, retries and dead letters;
- Telegram API error codes by safe category;
- flow validation/publish count and failure reason;
- database pool usage/query latency;
- JVM CPU, memory, GC and thread pool;
- per-bot quotas internally, with carefully controlled metric cardinality.

### 20.3 Health and operator visibility

- Liveness checks process health only.
- Readiness checks database connectivity and required configuration; do not call Telegram on every probe.
- A sanitized authenticated runtime-status API exposes last successful update, webhook configuration state, active version and recent error category to the bot owner.
- Alerts for sustained webhook 5xx, inbox delay, outbox dead letters, database exhaustion and credential failure.
- Distributed tracing is not required while all backend modules are in one process; preserve trace IDs and add OpenTelemetry when a real service boundary appears.

## 21. Testing Strategy

### 21.1 Test layers

| Layer | Frontend | Backend/runtime | Required focus |
|---|---|---|---|
| Unit | Store commands, domain adapters, form rules | Domain validators, compiler, interpreter transitions, crypto metadata handling | Fast deterministic logic |
| Component | Graph nodes, forms, conflict/error UI, Telegram adapter wrapper | Spring module/use-case slices, webhook parsing | Boundary behavior without full deployment |
| Persistence integration | — | Testcontainers PostgreSQL, Flyway, tenant-scoped repositories, inbox/outbox claiming | Real schema and transaction semantics |
| Contract | Generated OpenAPI client compile, JSON Schema fixtures | OpenAPI validation, schema backward compatibility, problem responses | Prevent frontend/backend drift |
| Telegram adapter | Mock browser Telegram object | WireMock/mock HTTP server for getMe, setWebhook and sendMessage | No real tokens in CI |
| End-to-end | Playwright Mini App flow | Full Java + PostgreSQL test deployment | Auth → draft → publish → update → reply |
| Security | XSS and auth-token handling | Forged/expired initData, webhook secret, IDOR/cross-tenant, redaction | Negative paths are release gates |
| Reliability | Save conflict/recovery | Duplicate/out-of-order updates, worker crash, retry/dead-letter, restart | Durable behavior |
| Performance | Large allowed graph interaction | Webhook acceptance and interpreter/load baseline | Verify triggers, not speculative scale |

### 21.2 Runtime test cases

Minimum interpreter suite:

- Start selects the configured first executable node.
- SendMessage without buttons emits one message and follows its default edge if present.
- SendMessage with buttons suspends and creates stable opaque action tokens.
- A valid button callback resumes the correct Conversation and branch.
- A stale/wrong-user/wrong-bot action token is rejected safely.
- Duplicate update_id does not repeat the state transition.
- New conversations select the active version; existing conversations remain pinned.
- Rollback changes only new conversations unless an explicit reset occurs.
- Automatic-only cycles fail publish; user-suspending cycles respect transition limits.
- A worker restart does not lose accepted inbox updates or queued outbound commands.
- Cross-workspace Bot/Flow IDs return not found/forbidden without disclosing existence.

### 21.3 Quality gates

A change cannot release when:

- migrations do not apply from the previous supported schema;
- OpenAPI/generated client or JSON Schema fixtures are out of sync;
- module-boundary tests fail;
- tenant-isolation, initData or webhook-secret negative tests fail;
- the golden vertical slice fails;
- a secret scanner detects a credential;
- container health/readiness does not become healthy in Compose;
- dependency scanning finds an unaccepted critical vulnerability.

## 22. Architecture Evolution

### 22.1 Stage 1 — MVP

- Static React Mini App.
- One Spring Boot modular-monolith instance.
- One PostgreSQL database.
- Management and user-bot webhooks in the same app.
- Flow authoring and shared runtime in the same process.
- PostgreSQL inbox/outbox with in-process workers.
- No Redis, Kafka, Kubernetes or Python.
- Managed Bots preferred if the spike succeeds; bring-your-own token fallback.

### 22.2 Stage 2 — Early traction

Without splitting services:

- run two Java instances if availability is required;
- use database work claiming such as FOR UPDATE SKIP LOCKED;
- ensure all jobs are safe under multiple instances;
- add immutable flow cache keyed by version/checksum;
- introduce per-bot/workspace quotas and operator tooling;
- move PostgreSQL to managed HA/PITR if not already;
- add read indexes/partitioning only from query evidence;
- formalize SLOs and on-call/error-budget review.

### 22.3 Stage 3 — Scale

Possible changes after triggers:

- separate lightweight webhook ingress from execution workers;
- extract runtime into an independently deployable service/worker using a durable queue;
- keep control-plane REST in the management backend;
- add Redis only for measured cache/coordination/rate-limit needs;
- add a managed queue before considering Kafka;
- partition conversations/inbox by bot/workspace/time;
- consider stronger tenant/process isolation for high-risk integrations;
- use Kubernetes only when several independently scaled workloads and team operations justify it.

### 22.4 Change triggers

| Future change | Do not do before | Concrete trigger | Next action |
|---|---|---|---|
| Second Java instance | Production availability is required | Paid users require recovery from a single process failure, or monthly availability target cannot tolerate restart time | Make workers multi-instance-safe; deploy two replicas behind the edge |
| Split webhook ingress | One app accepts updates within target latency | Sustained above 200 updates/s or runtime deployments cause webhook errors/backlog | Create stateless ingress that only verifies and enqueues |
| Extract runtime service | Management and runtime need the same scaling/release cadence | Sustained above 50 updates/s for 15 minutes with runtime above 70% app CPU, or runtime releases repeatedly block management releases, or a separate team owns runtime | Define queue contract; move runtime/inbox workers, not authoring REST |
| Add managed queue | PostgreSQL inbox meets delay/reliability target | Inbox backlog above 10,000 or p95 processing delay above 5 seconds for 30 minutes after query/index tuning | Evaluate managed SQS/Pub/Sub/RabbitMQ; preserve idempotency |
| Add Redis | PostgreSQL and in-memory immutable cache are sufficient | p95 conversation/version lookup above 50 ms under more than 500 concurrent active conversations, or distributed rate limiting is required | Add narrow cache/rate-limit use with DB as source of truth |
| Database read replica/cache | Primary DB is healthy | DB CPU above 60% for 30 minutes and read p95 above 100 ms after index/query tuning | Route safe read models/status queries to replica/cache |
| Partition inbox/outbox | Tables remain maintainable | More than 100 million retained rows or vacuum/index maintenance misses the operations window | Time partition and enforce retention |
| Per-bot process isolation | Logical isolation is sufficient | One tenant’s integration repeatedly causes shared-runtime incidents, or a contractual/regulatory isolation requirement appears | Add isolated worker pool/tier for selected bots |
| Kubernetes | Simple managed containers meet needs | At least 3 independently scaled application workloads, more than 5 running app containers, and a team of at least 4 shares operations | Evaluate managed Kubernetes against simpler platform |
| Kafka | A log/replay ecosystem is genuinely needed | Multiple independent consumers require long retention/replay and managed queue cannot meet throughput/governance | Architecture review and explicit ADR |
| Third runtime repository | Runtime is an independent deployable/team | Runtime service is extracted and independently owned/released | Split history/contracts deliberately; do not pre-create |
| Node.js backend | Java backend cannot meet a measured requirement | A concrete server capability is only sustainably available in Node and outweighs another service | New ADR; otherwise never add it |
| Python service | A Python-only product capability is validated | A mature Python-only library creates measured product value and cannot be isolated as an offline job | Define unique responsibility, contract, failure and cost before approval |

## 23. Migration Plan

### 23.1 Current-component decisions

| Current component | Repository | Decision | Target location | Reason |
|---|---|---|---|---|
| Vanilla JavaScript | bot-builder-ui | Rewrite | React/TypeScript feature modules | Globals, inline scripts and duplicated state are not a safe production base. |
| Drawflow | bot-builder-ui | Replace | React Flow adapter | Stable typed handles/nodes and active ecosystem; library format remains internal. |
| localStorage | bot-builder-ui | Replace | REST/PostgreSQL; optional recovery cache | It cannot be canonical or store secrets/status. |
| Flask example | bot-builder-ui | Remove after reference extraction | Backend contract tests/fixtures | It generates fake tokens and has no auth, DB or runtime. |
| Python requirements | bot-builder-ui | Remove | None | No production Python. |
| JSON export | bot-builder-ui | Refactor | FlowDraft v1 and ExecutableFlowVersion fixtures | Current export is unversioned and inconsistent with the fixture/runtime needs. |
| Java Telegram handlers | BotConstructor | Refactor | telegram-management adapter + application use cases | Current bot role is valid; handlers are state/concurrency/error fragile. |
| Long polling | BotConstructor | Keep temporarily | Local development profile only | Useful locally; production uses webhooks. |
| In-memory sessions | BotConstructor | Replace | PostgreSQL Conversation/platform sessions | Current state is unrelated, non-durable and non-scalable. |
| PostgreSQL/JPA | BotConstructor | Keep and Refactor | Product schema, Flyway, scoped repositories | Correct core database choice; current model is insufficient. |
| Java 23 preview | BotConstructor | Replace | Java 25 LTS without preview | Production support and repeatable tooling. |
| Existing Docker configuration | BotConstructor | Refactor | Working multi-stage image + Compose app/static/DB | App service is commented and artifact name/config are inconsistent. |
| telegrambots 5.7.1 | BotConstructor | Replace/upgrade behind adapter | telegram-runtime and telegram-management outbound adapter | Current version predates required current API and should not leak into domain. |
| docs/ARCHITECTURE.md | BotConstructor | Rewrite | Sanitized current module map | Current paths/resources are stale; system SAD lives privately elsewhere. |
| UserData model | BotConstructor | Rewrite | User/Workspace/Membership/Bot aggregates | Existing fields do not express tenants, bots or secure credentials. |
| main-old.js and unused pseudo-modules | bot-builder-ui | Remove after inventory | Git history and behavior tests | Avoid parallel implementations during rewrite. |

### 23.2 Migration approach

Do not attempt a large synchronized rewrite with no working path. Build a strangler-style vertical slice:

1. freeze current prototype as a behavior reference;
2. define FlowDraft v1, OpenAPI and golden fixtures;
3. build backend auth/bot/draft/publish APIs against PostgreSQL;
4. build a minimal React editor for only the vertical-slice node types;
5. implement webhook/runtime/conversation;
6. run the slice end to end;
7. port only confirmed UX behaviors and then delete prototype code;
8. import existing localStorage flows only through an explicit one-time converter if real user data exists; otherwise do not spend time on migration compatibility.

No application-code rewrite is performed by this architecture task.

## 24. Implementation Plan

Estimates are focused developer-days and intentionally expressed as ranges. They are planning inputs, not commitments; product unknowns and Telegram/hosting setup can change them.

| ID | Phase | Repository | Task | Dependencies | Result | Estimate | Acceptance criteria |
|---|---|---|---|---|---|---|---|
| P0-01 | 0 Architecture foundation | Both/docs | Approve stack, boundaries and core ADRs | SAD review | Signed baseline | 0.5–1 d | ADR-001 to ADR-007 accepted; no Python/Node backend ambiguity |
| P0-02 | 0 Architecture foundation | BotConstructor | Create contract directories and ownership rules | P0-01 | Contract skeleton | 0.5–1 d | OpenAPI/Schema paths, versioning and CI validation commands documented |
| P0-03 | 0 Architecture foundation | BotConstructor | Define FlowDraft v1, ExecutableFlow v1 and node registry | P0-01 | JSON Schemas + golden examples | 2–4 d | Start/SendMessage/button ports, IDs, cycles and deletion semantics are testable |
| P0-04 | 0 Architecture foundation | BotConstructor | Define OpenAPI v1 skeleton and error model | P0-02, P0-03 | Reviewable API contract | 2–3 d | Auth/bots/draft/validate/publish/version/status paths validate |
| P0-05 | 0 Architecture foundation | BotConstructor | Spike Telegram Managed Bots and current Bot API adapter | P0-01 | Go/no-go note and fallback | 0.5–1.5 d | Managed creation/token/webhook tested with a non-production bot or BYO fallback confirmed |
| P0-06 | 0 Architecture foundation | Both | Define canonical frontend state and Drawflow conversion inventory | P0-03 | Migration mapping | 1–2 d | DOM/React Flow excluded from domain state; each prototype behavior mapped or dropped |
| P1-01 | 1 Frontend foundation | bot-builder-ui | Scaffold React/TypeScript/Vite, lint/format/test | P0-01 | Buildable frontend | 1–2 d | Strict TypeScript build, Vitest and CI pass |
| P1-02 | 1 Frontend foundation | bot-builder-ui | Generate API/types and add Telegram typed adapter | P0-04, P1-01 | Typed gateways | 2–4 d | Pinned contract generates cleanly; mock/non-Telegram browser mode works |
| P1-03 | 1 Frontend foundation | bot-builder-ui | Implement normalized FlowDraft store and commands | P0-03, P1-01 | Canonical editor state | 3–5 d | Add/update/delete/reorder/connect commands have unit tests and undo-safe IDs |
| P1-04 | 1 Frontend foundation | bot-builder-ui | Implement React Flow canvas and core nodes | P1-03 | Visual Start/SendMessage graph | 4–7 d | Stable node/port IDs; update does not recreate domain node; mobile canvas usable |
| P1-05 | 1 Frontend foundation | bot-builder-ui | Implement node/button forms and client validation | P1-03, P1-04 | Typed editing UX | 4–6 d | Delete/reorder semantics preserve or explicitly remove edges; no raw HTML |
| P1-06 | 1 Frontend foundation | bot-builder-ui | Implement auth bootstrap, bots, draft save/conflict UI | P1-02, P1-03, P2-05 | Connected authoring UI | 3–5 d | initData session works; save uses ETag; 412 conflict is visible/recoverable |
| P1-07 | 1 Frontend foundation | bot-builder-ui | Add component/E2E tests and crash-recovery cache | P1-04, P1-06 | Frontend quality gate | 3–5 d | Core edit/save/publish journey passes; cache has TTL and contains no secret |
| P2-01 | 2 Backend foundation | BotConstructor | Upgrade/scaffold Java 25 Boot 4.1 modular monolith and tests | P0-01 | Healthy backend skeleton | 2–4 d | No preview; architecture test; Testcontainers context; supported dependencies |
| P2-02 | 2 Backend foundation | BotConstructor | Add Flyway baseline and User/Workspace/Membership/Bot schema | P2-01 | Versioned tenant schema | 2–4 d | Clean migration and upgrade test; Hibernate validate; composite tenant constraints |
| P2-03 | 2 Backend foundation | BotConstructor | Implement initData verification and platform session | P2-01, P2-02 | Auth endpoint | 2–4 d | Official vectors/negative cases pass; expired/forged data rejected; no raw logging |
| P2-04 | 2 Backend foundation | BotConstructor | Implement bot APIs and tenant authorization | P2-02, P2-03, P0-04 | Bot management control plane | 3–5 d | Cross-tenant tests fail closed; idempotent create; sanitized responses |
| P2-05 | 2 Backend foundation | BotConstructor | Implement credential verification, encryption and webhook setup | P2-04, P0-05 | Secure connected bot | 3–6 d | getMe verified; token encrypted/not echoed/logged; setWebhook secret configured |
| P2-06 | 2 Backend foundation | BotConstructor | Implement draft persistence with optimistic locking | P2-02, P0-03, P0-04 | Canonical drafts | 3–5 d | GET/PUT revision and 412 behavior pass; JSON Schema validation runs |
| P2-07 | 2 Backend foundation | BotConstructor | Implement full graph validation, compile, publish and versions | P2-06, P0-03 | Immutable executable versions | 5–8 d | Deterministic checksum; invalid graph rejected; activation/rollback audited |
| P2-08 | 2 Backend foundation | BotConstructor | Refactor management bot to webhook/stateless use cases | P2-03, P2-04 | Platform bot adapter | 3–5 d | /start opens Mini App; status route works; production long polling disabled |
| P3-01 | 3 Runtime vertical slice | BotConstructor | Implement interpreter and node executors | P2-07 | Executable Start/SendMessage/button engine | 5–8 d | Deterministic transition tests, suspension and transition budget pass |
| P3-02 | 3 Runtime vertical slice | BotConstructor | Implement per-bot webhook routing and durable inbox | P2-05, P3-01 | Reliable update acceptance | 4–7 d | Secret verified; duplicate update unique; 200 only after durable insert |
| P3-03 | 3 Runtime vertical slice | BotConstructor | Implement Conversation/State, version pinning and locking | P3-01, P3-02 | Durable continuation | 4–7 d | Restart-safe; concurrent updates serialized; new/old version rules pass |
| P3-04 | 3 Runtime vertical slice | BotConstructor | Implement Telegram outbox, retry and dead-letter | P3-03 | Reliable outbound delivery | 4–7 d | Backoff, terminal error, metrics and redacted logs verified |
| P3-05 | 3 Runtime vertical slice | Both | Complete golden end-to-end slice | P1-06, P2-08, P3-04 | Working MVP backbone | 4–7 d | Management bot → Mini App → save → token → publish → Telegram branch → resume |
| P4-01 | 4 Deployment | BotConstructor | Replace Dockerfile and Compose topology | P2-01, P3-05 | One-command local environment | 2–4 d | Static/app/DB become healthy; migrations run; no embedded credentials |
| P4-02 | 4 Deployment | Both | CI for tests, contracts, security and images | P1-07, P3-05 | Repeatable build | 3–5 d | Required quality gates run on every PR; artifacts are versioned |
| P4-03 | 4 Deployment | Both | Configure staging/prod TLS, domains, secrets and DB | P4-01, hosting decision | Deployable environments | 2–5 d | Telegram accepts both webhooks; KMS/secret manager and backups configured |
| P4-04 | 4 Deployment | BotConstructor | Add logs, metrics, health and basic alerts | P3-04, P4-03 | Observable deployment | 2–4 d | Runtime failures/backlog visible; probes and alerts tested |
| P5-01 | 5 MVP hardening | Both | Security hardening and tenant/secret audit | P3-05, P4-02 | Security release gate | 3–6 d | IDOR, initData, webhook, XSS, redaction and rate-limit tests pass |
| P5-02 | 5 MVP hardening | BotConstructor | Backup/restore, retry/reconciliation and retention | P4-03, P4-04 | Recovery runbook | 2–4 d | Restore drill succeeds; stuck inbox/outbox reconciled; retention scheduled |
| P5-03 | 5 MVP hardening | Both | Load/fault/E2E regression baseline | P5-01, P5-02 | Capacity baseline | 3–5 d | Target load meets webhook/processing latency; restart and Telegram failures tested |
| P5-04 | 5 MVP hardening | Both/docs | Release acceptance and operator/user documentation | P5-03 | MVP release candidate | 2–4 d | Vertical slice, runbook, known limitations and rollback procedure approved |

### 24.1 First task

Start with **P0-01: approve ADRs and boundaries**. The first code-producing task is P0-03/P0-04 contract work, not framework scaffolding. Building UI or controllers before stable IDs, button/port semantics and publication rules would recreate the present coupling.

### 24.2 Critical path

Contract decisions are the critical dependency:

P0-01 → P0-03 → P0-04 → P2-01/P1-01 → P2-03/P2-04 → P2-05/P2-06/P1-03 → P2-07/P1-04/P1-06 → P3-01 → P3-02 → P3-03 → P3-04 → P3-05 → P4-01/P4-03 → P5.

For one developer, frontend and backend tracks are sequenced in practice even where the table permits parallel work. The estimated full plan is roughly 87–148 focused developer-days; the golden vertical slice should be targeted earlier by deferring polish and non-critical screens.

### 24.3 First vertical slice

The first vertical slice is exactly:

1. management bot opens Mini App;
2. backend verifies initData;
3. owner/default workspace and Bot exist;
4. user connects a verified bot credential;
5. React editor creates Start → SendMessage with two buttons → two SendMessage branches;
6. draft saves with a revision;
7. publish creates immutable version 1;
8. user-bot webhook is accepted and deduplicated;
9. /start sends the first message/buttons;
10. button callback resumes the pinned conversation and sends the selected branch;
11. status/log/metrics identify success or failure;
12. version 2 can publish while an existing version-1 conversation remains valid.

### 24.4 Do not build for MVP

- Payment, Broadcast, generic HTTP, arbitrary code, variables and advanced conditions.
- Generated code or a deployable per bot.
- Separate management/runtime service, Python service or Node backend.
- Kafka, Redis, Kubernetes, service mesh or distributed tracing.
- Full billing engine, roles beyond the minimal owner/editor model, collaboration and analytics.
- Multiple graph libraries or a compatibility layer for Drawflow at runtime.
- Import of historical localStorage data unless real users need it.
- Exactly-once outbound-message claims that Telegram cannot guarantee.

### 24.5 Decisions that block implementation

| Decision | Blocks | Default in this SAD |
|---|---|---|
| Stable node/button/port semantics | Schema, editor and interpreter | Stable UUIDs; button owns stable output port |
| Connected-button deletion | Editor command and validation | Block, then explicit atomic delete of button/port/edges |
| MVP cycles | Validator/interpreter | Allow only cycles containing a suspending node; transition budget |
| Auth/session transport | API client/security | Short bearer session kept in memory |
| Managed Bots vs BYO token | Onboarding UI only | Managed preferred after spike; BYO fallback always supported |
| Hosting/KMS/domain | Production deployment | Simple managed container + managed PostgreSQL + provider secret/KMS |

## 25. ADR Register

| ADR | Decision | Alternatives | Reason | Consequences | Revisit trigger |
|---|---|---|---|---|---|
| ADR-001 | TypeScript frontend + Java backend/runtime; no production Python | TS end-to-end; Python end-to-end; Java+Python | Best solo maintainability, current expertise and reuse without frontend compromise | Two necessary languages; generated contracts | Owner/team backend expertise changes or Java blocks measured product need |
| ADR-002 | React/TypeScript frontend | Vue, Svelte, vanilla JS | Graph/UI ecosystem, hiring and test maturity | Prototype rewrite | A clear Vue/Svelte owner decision before Phase 1 |
| ADR-003 | React Flow behind adapter | Drawflow, Vue Flow, Rete | Stable typed custom graph model and project activity | Migration cost; library adapter required | React Flow lacks a proven required editor behavior |
| ADR-004 | REST only Mini App → Java | GraphQL; server-rendered pages; internal REST | Clear browser/process boundary and OpenAPI generation | API versioning/CORS/session work | Realtime collaboration or query shape creates measured REST pain |
| ADR-005 | Keep two repositories | Monorepo; third runtime repo | Existing boundaries map to artifacts/public-private needs | Cross-repo contract release process | Atomic change pain dominates or runtime becomes independently owned |
| ADR-006 | One Java modular monolith | Microservices; single unstructured package | Minimum operations with explicit split path | Modules share deployment/database | Independent scaling/team/release triggers in Stage 3 |
| ADR-007 | Shared multi-tenant interpreter | Source generation; per-bot container; serverless | Lowest cost and operational load; instant version activation | Logical isolation must be engineered | Contractual hard isolation or repeated noisy-tenant incidents |
| ADR-008 | PostgreSQL as primary DB and work queue | MongoDB; Redis; Kafka | Transactions, JSONB, relational tenancy and existing familiarity | Inbox/outbox tuning/retention needed | Measured queue or scale triggers |
| ADR-009 | Separate FlowDraft and ExecutableFlowVersion | Drawflow export; one shared model | UI-independent contract and publish-time validation/normalization | Compiler and two schemas | Compiler provides no value or becomes disproportionate |
| ADR-010 | Production webhooks; local long polling only | Long polling everywhere | Dynamic bots, fast delivery and standard production topology | TLS/routing/secrets required | Telegram platform contract changes |
| ADR-011 | PostgreSQL Conversation pinned to FlowVersion | In-memory state; always use current version | Restart safety and stable behavior across publication | Retain old runtimes while conversations live | Product requires forced migration with explicit semantics |
| ADR-012 | Envelope-encrypted bot tokens | Plain DB; frontend storage; one env token per bot | Tenant secret protection and rotation | KMS/crypto operations and audit | Managed Bots eliminate persistent token need or secret platform offers safer native model |
| ADR-013 | Static frontend + one Java app + PostgreSQL | Java serves embedded frontend; Kubernetes; multiple services | Minimum dynamic processes and independent static delivery | Two artifacts and edge routing | Deployment platform makes a combined artifact materially simpler |
| ADR-014 | PostgreSQL inbox/outbox with in-process workers | Synchronous webhook execution; Kafka/queue | Fast durable acceptance and retry without infrastructure | At-least-once outbound and worker logic | Queue/backlog trigger crossed |
| ADR-015 | Managed Bots preferred after spike; BYO token fallback | BYO only; Managed only | Better onboarding while retaining universal path | Two credential-source UX paths | Telegram availability/adoption makes one route clearly unnecessary |

## 26. Risks

| ID | Risk | Probability | Impact | Mitigation / owner action |
|---|---|---:|---:|---|
| R-01 | Product scope expands before core runtime works | High | Critical | Freeze vertical slice; new nodes require explicit scope/ADR. |
| R-02 | ICP/value remains unvalidated despite technical build | High | High | Run discovery/landing/user tests in parallel; define stop criteria outside this SAD. |
| R-03 | Graph/button semantics change after frontend/runtime implementation | Medium | Critical | Approve FlowDraft v1 golden fixtures before Phase 1/2. |
| R-04 | React rewrite loses useful prototype behavior | Medium | Medium | Inventory behaviors and convert them into acceptance tests before deletion. |
| R-05 | Users distrust token submission | Medium | High | Prefer Managed Bots after spike; transparent security copy and BYO fallback. |
| R-06 | Managed Bots are too new/limited for target users | Medium | Medium | Time-box spike; do not make architecture dependent on it. |
| R-07 | Cross-tenant data/secret access defect | Medium | Critical | Tenant-scoped APIs/repositories, composite keys, negative integration tests and audits. |
| R-08 | Shared runtime noisy tenant or poison update | Medium | High | Quotas, bounded transitions, work isolation, retries and dead-letter status. |
| R-09 | Duplicate outbound message after crash | Low/Medium | Medium | Document at-least-once boundary, dedupe inbound, outbox reconciliation and UX-safe messages. |
| R-10 | PostgreSQL inbox/outbox grows or contends | Medium after traction | High | Retention, indexes, claim batches, metrics and queue trigger. |
| R-11 | One developer becomes the delivery/operations bottleneck | High | High | Modular boundaries, runbooks, generated contracts, CI and strict MVP scope. |
| R-12 | Public backend repository reveals operational security detail | Medium | High | Private canonical SAD; sanitized public architecture docs; secret scanning. |
| R-13 | Old Telegram Java dependency misses current Bot API fields | High | High | Minimal direct HTTP adapter/current dependency behind port; contract fixtures. |
| R-14 | Hosting/KMS decision is delayed | Medium | High for release | Choose provider before P4-03; keep crypto port provider-neutral. |
| R-15 | Conversation/version retention grows indefinitely | Medium | Medium | Terminal/reset model, retention policy and references-before-archive checks. |
| R-16 | Telegram API/client behavior changes | Medium | High | Adapter isolation, official docs/changelog review and staging bot smoke tests. |

## 27. Open Questions

### 27.1 Critical product/architecture questions

| Question | Recommended default | Must decide by | Blocks now? |
|---|---|---|---|
| Is user-bot execution a release gate? | Yes; otherwise this is only an editor prototype | Before P0-01 approval | Yes, product scope |
| Which exact nodes are MVP? | Start and SendMessage with inline-button branches; add WaitForText only after slice | Before P0-03 | Yes, schema |
| Managed Bots or manual token first? | Managed preferred if P0-05 succeeds; BYO always available | Before onboarding UI P1-06 | No backend architecture block |
| Are arbitrary cycles allowed? | Only cycles containing a suspending user action; reject automatic cycles | Before P0-03 | Yes, validator semantics |
| What happens to connected edges when a button is deleted? | Block and require explicit atomic deletion | Before P0-03 | Yes, editor/schema |
| What personal/message data is retained and for how long? | Minimize content; define 30-day default for terminal conversations unless product/legal need differs | Before production P4-03 | No foundation block; production block |
| Which hosting/KMS/backup provider? | Simple managed container + managed PostgreSQL + native secrets/KMS | Before P4-03 | Production only |
| Is public OpenAPI/flow schema acceptable? | Yes; contracts are not secrets. Keep threat/operations details private. | Before P0-02 | Minor |

### 27.2 Important but non-blocking defaults

- One owner/default workspace in initial UI; backend supports more.
- Multiple bots per workspace.
- Inline keyboard buttons only in first runtime slice.
- Existing conversations stay pinned; no forced migration.
- Templates are optional seed data after blank-flow vertical slice.
- English may be the first interface language; localization architecture uses keys, but translations are product scope.
- p95 targets should be established from a baseline; initial design goal is webhook durable acceptance under 500 ms and normal transition start under 2 seconds, excluding Telegram delivery.
- Billing, payment and broadcast remain disabled modules until separate product decisions.

## 28. Immediate Next Steps

1. Approve ADR-001 through ADR-015 or record explicit objections.
2. Revoke/rotate any Telegram bot token that ever appeared in repository history.
3. Create contracts/openapi and contracts/flow/v1 in BotConstructor without implementing controllers.
4. Produce three golden FlowDraft fixtures: valid button branch, invalid orphan/port, and safe user-suspending cycle.
5. Run the Telegram Managed Bots/current Bot API spike and choose preferred onboarding.
6. Scaffold Java 25/Spring Boot 4.1 with Flyway, tests and enforced module boundaries.
7. Scaffold React/TypeScript and generate types/client from the pinned contracts.
8. Implement initData auth and the owner/default workspace before editor persistence.
9. Implement draft/publish and only then connect the React Flow editor.
10. Deliver the golden Telegram runtime vertical slice before adding templates, WaitForText or visual polish.

### Source notes

- Actual repository facts are tied to the commit hashes in this document, not to moving branch names.
- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Oracle Java SE support roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)
- [Telegram Mini Apps](https://core.telegram.org/bots/webapps)
- [Telegram Bot API](https://core.telegram.org/bots/api)
- [Telegram Bot Features — Managed Bots](https://core.telegram.org/bots/features#managed-bots)

---

**Final decision:** build the MVP as a React/TypeScript static Mini App plus one Java modular-monolith backend/runtime with PostgreSQL. Use REST only at the browser boundary, webhooks at the Telegram boundary, and in-process calls inside Java. Do not deploy Python, a Node backend or a separate runtime service until a measured trigger proves that the simpler architecture is insufficient.

## Implementation decision record — BE-01 to BE-03 (2026-08-29)

The implemented backend baseline is Java 21 LTS and Spring Boot 3.5.13, without preview language/runtime flags. This supersedes aspirational Java 25/Boot 4 references for the current repository implementation: Java 21 was explicitly required by the approved backlog execution request, Boot 3.4 had ended open-source support, and Boot 3.5 is the supported 3.x line that avoids an unnecessary major migration.

The single `constructor-service` application is organized as package modules `identity`, `workspace`, `bot`, `flow`, `runtime`, `telegram`, and `audit`. Core modules cannot depend on Telegram framework or adapter classes; runtime may use stable bot/flow contracts; Telegram is an inbound/outbound adapter and may invoke application contracts. ArchUnit enforces these rules and cycle freedom.

Configuration is immutable and type-safe under `app.telegram.management`, `app.telegram.media`, and `app.web`. Profiles are selected externally: `local` is PostgreSQL-backed and Telegram-off by default, `test` has no database or Telegram side effects, and `prod` has no secret defaults and requires management Telegram, datasource, and public URL values. Validation errors intentionally report only a sanitized configuration defect.

## Implementation decision record — DB-01 and DB-02 (2026-08-29)

Flyway is the only database schema owner; Hibernate uses validation only. Migrations are immutable, versioned SQL under `db/migration`, with automatic baselining disabled. The initial V1 migration creates the minimal tenant-aware user, workspace, membership, and bot schema. Flow persistence is deferred because CON-01 is not present.

New entity identifiers are UUIDs, consistently separate from Telegram numeric identifiers. Cross-module references are scalar IDs backed by database foreign keys rather than ORM navigation. PostgreSQL `TIMESTAMP WITH TIME ZONE` and Java `Instant` define the UTC timestamp policy. Workspace ownership, membership tenant links, bot ownership, uniqueness, valid roles, names, positive Telegram IDs, and optimistic versions are enforced in the database.

The prototype `user_data` model remains explicitly legacy (Variant B). It is used only by the current management Telegram create flow and is not the canonical platform identity. This avoids destructive assumptions about an inaccessible existing local database; future identity/auth work must migrate verified legacy data before removing it.
