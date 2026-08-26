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

- **PostgreSQL:** datasource параметризований через `SPRING_DATASOURCE_*`; локальний запуск IDE використовує `localhost:5433/bot_constructor`, а контейнеризований застосунок — `postgres:5432/bot_constructor`. PostgreSQL Compose запускається окремо, Hibernate default `ddl-auto=update`; таблиця `user_data`. Міграцій немає (`application.yml`, `application-local.yml`, `docker-compose.yml`, `tg/model/UserData.java`, `tg/data/repo/UserRepository.java`).
- **Telegram Bot API:** long polling для input, `DefaultAbsSender` для output; token/username беруться з `bot.*` (`tg/config/TelegramBotConnection.java`, `tg/service/impl/HelpBotSender.java`, `application.yml`).
- **Kafka topics:** не визначені. Compose для локальної розробки запускає лише PostgreSQL; застосунок запускається окремо з IDE і підключається до `localhost` (`docker-compose.yml`, `application-local.yml`).
- **Assets:** video, MOV і GIF лежать у `constructor-service/src/main/resources/assets/`; startup вимагає `video.mp4`, активний animation flow використовує GIF, а MOV не потрапляє в Docker build context (`ConstructorApplication.java`, `TelegramMessageService.java`, `constructor-service/.dockerignore`).

## Security, observability та помилки

- Authentication/authorization у Spring відсутні: немає Spring Security dependency або security config. Бізнес-обмеження є лише для `FREE` plan у create flow (`pom.xml`, `tg/handler/CreateBtnHandler.java`). Telegram user/chat IDs приймаються з updates без додаткової перевірки.
- Bot token і datasource credentials читаються з environment variables; Compose вимагає `BOT_TOKEN` і `BOT_USERNAME`, а `.env` ігнорується Git (`application.yml`, `docker-compose.yml`, `.env.example`, `.gitignore`). Раніше закомічений token слід вважати скомпрометованим і ротувати.
- Observability обмежена SLF4J startup/error logs, `System.out` для unmatched update та увімкненим Hibernate SQL logging; Actuator/metrics/tracing dependencies відсутні (`TelegramBotConnection.java`, `BotUpdateDispatcher.java`, `TelegramMessageService.java`, `application.yml`, `pom.xml`).
- Telegram registration і outbound failures перехоплюються та логуються без retry/propagation (`TelegramBotConnection.java`, `TelegramMessageService.java`). `@ControllerAdvice` обробляє `NotFoundFileException`, але exception не кидається поточним кодом і advice орієнтований на MVC (`tg/exceptionHandler/`).

## Build, test і local run

Для host build потрібні JDK 23, Maven і доступний PostgreSQL згідно з config (`pom.xml`, `application.yml`). З `constructor-service/`:

```text
mvn clean package
mvn test
mvn spring-boot:run
```

Compiler використовує Java 23 `--enable-preview`; Spring Boot Maven plugin створює executable `target/app.jar`. Test sources і test dependency відсутні, тому фактичного test suite немає (`pom.xml`, відсутній `src/test/`). Docker local run: виконати `docker compose up -d postgres`, а застосунок запускати з IDE або `mvn spring-boot:run -Dspring-boot.run.profiles=local` (`README.md`, `docker-compose.yml`, `application-local.yml`).

## Архітектурні обмеження

- Один deployable Maven-модуль; routing базується на Spring DI списку handlers і string prefixes (`pom.xml`, `BotUpdateDispatcher.java`, `tg/service/input/`).
- Сесії локальні для JVM, не переживають restart і не діляться між instances (`tg/service/state/UserSessionService.java`).
- Schema локально оновлюється Hibernate через `ddl-auto=update`; versioned migrations відсутні (`application.yml`, `docker-compose.yml`).
- `sendVideo` закоментований; startup все одно вимагає MP4, а animation sender не перевіряє null stream (`ConstructorApplication.java`, `TelegramMessageService.java`).

## Відомі прогалини та непідтверджені припущення

- Немає тестів, API contract, CI, migrations, application health endpoint або deployment manifests (`pom.xml`, repository tree). PostgreSQL має лише container healthcheck (`docker-compose.yml`).
- Edit, Back, Slide і довільний text/state flow не мають активних concrete handlers; `EditBtnHandler` порожній і не зареєстрований (`tg/handler/`, `tg/service/state/`).
- Новий default user у `UserService` не зберігається; життєвий цикл створення ботів і оновлення `countOfBots` не реалізовані (`tg/data/UserService.java`, `tg/handler/CreateBtnHandler.java`).
- Потрібність HTTP starter, production topology та очікувані Kafka/scheduled flows не підтверджені кодом (`pom.xml`, `constructor-service/src/main/java/`).
