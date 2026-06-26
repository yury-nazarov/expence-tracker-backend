# expense-tracker: backend

Java/Spring Backend трекера расходов.

## Стек

| Проект | Технологии                                            |
|---|-------------------------------------------------------|
| [backend/](./backend/) | Java 21, Spring Boot 3.5, Spring Data JPA, PostgreSQL |

## Архитектура

```
backend/    →   Spring Boot REST API   →   порт 8080
                    │
            PostgreSQL 16 (Docker)     →   порт 5432
```

Backend построен по пакетам: user, auth... eth. Которые в свою очередь содержат слои: `controller → service → repository → entity`.

## Запуск для разработки

### 1. База данных

```bash
docker compose up -d
```

### 2. Backend

```bash
cd backend
./mvnw spring-boot:run
```

- API: `http://localhost:8080`

Переменные окружения (значения по умолчанию подходят для локального запуска):

| Переменная | По умолчанию |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/expense_tracker` |
| `DB_USER` | `postgres` |
| `DB_PASSWORD` | `postgres` |

## Остановка

```bash
docker compose down
```

## Манифес

- `feature` - фичи которые идут от разработчиков или владельца продукта