# Backend — CLAUDE.md

Инструкции для Claude Code при работе с `backend/`.

## Stack

- **Java 21** LTS
- **Spring Boot 3.5.x** (spring-boot-starter-web, spring-boot-starter-data-jpa)
- **Hibernate 6** (через Spring Data JPA)
- **Maven 3.9** (wrapper `mvnw` включён в репо — локальный Maven не нужен)

## Команды

```bash
./mvnw spring-boot:run          # запуск dev-сервера → http://localhost:8080
./mvnw package -DskipTests      # сборка fat JAR → target/
./mvnw test                     # запуск тестов
```

## База данных

Локальный PostgreSQL поднимается через `docker-compose.yml` в корне монорепо:

```bash
docker compose up -d    # запуск PostgreSQL на порту 5432
docker compose down     # остановка
```

Credentials: `postgresql://postgres:postgres@localhost:5432/expense_tracker`

**Переменные окружения:**

| Переменная    | Дефолт                                              | Описание     |
|---------------|-----------------------------------------------------|--------------|
| `DATABASE_URL`| `jdbc:postgresql://localhost:5432/expense_tracker`  | JDBC URL     |
| `DB_USER`     | `postgres`                                          | Логин БД     |
| `DB_PASSWORD` | `postgres`                                          | Пароль БД    |

## Структура пакетов

Feature-based архитектура. Корневой пакет: `expence_tracker.backend`.

```
expence_tracker.backend/
├── user/
│   ├── UserController.java
│   ├── UserService.java
│   ├── UserRepository.java
│   ├── User.java              # Entity
│   └── UserDto.java           # DTO (отдельно от Entity)
├── auth/
│   ├── AuthController.java
│   ├── AuthService.java
│   └── JwtTokenProvider.java
├── common/                    # shared-компоненты (утилиты, exception-handler и т.п.)
└── BackendApplication.java
```

Каждая фича — отдельный пакет со своими слоями `controller → service → repository → entity`.  
Общие компоненты выносить в `common/`.

## Ключевые архитектурные решения

- **Feature-based**: один пакет на фичу, внутри — полный вертикальный срез.
- **DTO отделены от Entity**: никогда не возвращать Entity напрямую из контроллера.
- **ORM**: Spring Data JPA + Hibernate 6. Репозитории наследуют `JpaRepository<T, ID>`. Сложные запросы — через `@Query` (JPQL).
- **Boilerplate**: Lombok (`@Data`, `@Builder`, `@RequiredArgsConstructor`) для Entity и DTO.
- **Schema management**: только Flyway создаёт/меняет схему; Hibernate — только `ddl-auto: validate`.

## Планы реализации

Все планы сохранять в `.claude/plans/`.

**Формат имени:** `BE_<NN>: FeatureName.md`

Примеры:
```
BE_01: Project.md
BE_02: User.md
BE_03: JWT_Auth.md
```

**Правило моделей:**
- Подготовка плана → модель **Opus**
- Реализация плана → модель **Sonnet**

## Стиль работы

Это учебный проект. При объяснении решений и планов — подача как для джуна, который уже базово понимает Java/Spring и архитектуру: объясняй ключевые моменты, но не углубляйся в детали без запроса.

