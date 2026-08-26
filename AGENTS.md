# Repository Guidelines

## Codex Navigation

Start with [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) to identify the relevant module and flow. Then read only the files needed for the current task; do not scan the entire repository without a concrete need. Update the architecture map whenever a change affects repository structure, integrations, persistence, entry points, or a major flow.

## Project Structure & Module Organization

This repository contains a Spring Boot Telegram bot in `constructor-service/`. Java sources live under `constructor-service/src/main/java/org/demchenko`; bot handlers, services, persistence classes, state management, configuration, and models are grouped into packages below `org.demchenko.tg_bot`. Runtime configuration is in `src/main/resources/application.yml`, and media sent by the bot belongs in `src/main/resources/assets/`. Add tests under `constructor-service/src/test/java`, mirroring the production package structure.

The root `docker-compose.yml` describes additional services, but its referenced build directories are not currently present. Treat it as incomplete until those paths are reconciled.

## Build, Test, and Development Commands

Run Maven commands from the service directory:

- `cd constructor-service`
- `mvn clean package`: compile with Java 23 preview support and build the JAR.
- `mvn spring-boot:run`: launch the application locally on the configured Spring port.
- `mvn test`: run the test suite when tests and a test dependency are added.

A local PostgreSQL database must match the datasource settings (`localhost:5433/bot_constructor`, user `bot_constructor`, password `local_dev_password` by default) before the application starts successfully.

## Coding Style & Naming Conventions

Use four-space indentation and standard Java conventions: `PascalCase` for types, `camelCase` for methods and fields, and lowercase package names. Keep classes focused by role: commands/buttons in `handler`, integration logic in `service`, repositories in `data/repo`, and configuration in `config`. Follow the existing Spring stereotype and constructor-injection pattern; Lombok's `@RequiredArgsConstructor` is preferred for required dependencies. No formatter or linter is configured, so match nearby code and organize imports consistently.

## Testing Guidelines

No tests or coverage threshold currently exist. Add `spring-boot-starter-test` before introducing JUnit 5 tests. Name unit tests `*Test.java` and broader Spring context tests `*IntegrationTest.java`. Mock Telegram API boundaries and cover handler routing, state transitions, and persistence behavior. Run `mvn test` before submitting changes.

## Commit & Pull Request Guidelines

History uses short, imperative, lowercase summaries such as `created new methods for send video and animation`. Keep each commit narrowly scoped and describe the observable change. Pull requests should include a concise summary, testing performed, configuration or schema impacts, and linked issues. Include screenshots or sample bot interactions for user-visible flow changes.

## Security & Configuration

Never commit bot tokens, passwords, or production endpoints. The credentials currently tracked in `application.yml` should be rotated and replaced with environment-variable placeholders. Avoid logging Telegram updates or user data unless values are redacted.
