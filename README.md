# BotConstructor

Spring Boot service for a Telegram bot constructor. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the codebase map.

## Local development with Docker

### Prerequisites

- Docker Desktop or Docker Engine with Docker Compose v2
- A Telegram bot token and username created through BotFather
- Free host port 5433 (PostgreSQL), or a custom value in `.env`

### Start PostgreSQL only

The Compose file starts PostgreSQL only, so the Spring Boot application can be launched and debugged from the IDE:

```bash
docker compose up -d postgres
docker compose ps
docker compose logs -f postgres
```

Run the application locally with the `local` profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The defaults match Compose: host `localhost`, port `5433`, database `bot_constructor`, username `bot_constructor`, and password `local_dev_password`. You can override them with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. Hibernate uses `ddl-auto=update` by default; to recreate the schema for a local run, set `SPRING_JPA_HIBERNATE_DDL_AUTO=create`.

If the PostgreSQL volume was initialized with different `POSTGRES_*` values, changing `.env` does not change an existing database user or database name. Recreate the local volume only when its data can be deleted:

```bash
docker compose down --volumes
docker compose up -d postgres
```

Stop containers while retaining database data:

```bash
docker compose down
```

### Connect to PostgreSQL

From an IDE or DBeaver on the host, use:

- Host: `localhost`
- Port: `POSTGRES_PORT` from `.env` (default `5433`)
- Database, username, and password: the corresponding `POSTGRES_*` values from `.env`

There is no Flyway or Liquibase setup. For local development, Hibernate uses `ddl-auto=update`, preserving existing tables and creating missing schema objects. The database is persisted in the `postgres_data` named volume; this is not a production migration strategy.

### Troubleshooting

- PostgreSQL is unhealthy: inspect `docker compose logs postgres` and check whether the host port is already occupied.
- The application cannot connect to the database: verify that the local profile uses `localhost`, while a containerized application would use the Compose hostname `postgres`.
