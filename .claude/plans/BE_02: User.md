# BE_02: User — план реализации

> Реализация в режиме «ментор тренирует джуна»: код пишет джун по шагам, ментор объясняет.

## Context

В API нужна основа для авторизации (фича из `feature/BE_02: User.md`). Для этого создаём пакет
`user` с полным вертикальным срезом `Controller → Service → Repository → Entity`. Модель хранит
имя, email и хеш пароля + служебные поля (`id`, `created_at`, `updated_at`).

Сейчас в проекте «голый» Spring Boot: только `BackendApplication`, пустой `application.properties`,
нет драйвера PostgreSQL, нет Flyway, нет Lombok, нет миграций. Поэтому фича включает и
инфраструктурную подготовку. Сам JWT — отдельная фича BE_03; здесь готовим только модель и
временно открытый Security-контур.

---

## Справочник по аннотациям (читать один раз, дальше по коду понятно)

**Lombok** (генерирует boilerplate на этапе компиляции — меньше ручного кода):
- `@Data` — разом генерирует геттеры, сеттеры, `toString()`, `equals()`, `hashCode()` и
  конструктор для `final`-полей. Удобно для DTO и Entity.
- `@Builder` — добавляет fluent-билдер: `User.builder().name("...").email("...").build()`.
  Читаемее, чем конструктор с кучей аргументов, и не зависит от их порядка.
- `@NoArgsConstructor` — пустой конструктор `User()`. **Обязателен для JPA**: Hibernate создаёт
  объект через него, а потом заполняет поля.
- `@AllArgsConstructor` — конструктор со всеми полями. Нужен, чтобы `@Builder` мог собрать объект.
- `@RequiredArgsConstructor` — конструктор только для `final`-полей. Основа constructor-injection
  в Spring: все зависимости объявляем `private final`, Spring подставит их сам.

**JPA / Hibernate** (маппинг Java-объект ↔ строка таблицы):
- `@Entity` — помечает класс как сущность БД (одна строка = один объект).
- `@Table(name = "users")` — имя таблицы (`user` — зарезервированное слово в PostgreSQL).
- `@Id` — первичный ключ.
- `@GeneratedValue(strategy = IDENTITY)` — значение генерирует БД (наш `BIGSERIAL`).
- `@Column(...)` — настройки колонки: имя, `nullable`, `unique`, `length`, `updatable`.
- `@PrePersist` / `@PreUpdate` — хуки: метод вызывается перед вставкой / обновлением. Используем
  для авто-заполнения `createdAt` / `updatedAt`.

**Spring Web / Validation:**
- `@RestController` — контроллер, где каждый метод возвращает тело ответа (JSON), а не имя view.
- `@RequestMapping("/api/users")` — общий префикс пути для всех методов.
- `@PostMapping` / `@GetMapping` — привязка метода к HTTP-глаголу и пути.
- `@RequestBody` — десериализовать JSON тела запроса в объект.
- `@PathVariable` — взять часть URL (`/{id}`) как аргумент.
- `@Valid` — запустить Bean Validation для входящего DTO (проверить `@NotBlank`, `@Email` и т.п.).
- `@ResponseStatus(CREATED)` — какой HTTP-код вернуть при успехе (201 для создания).
- `@Service` — бизнес-слой; делает класс Spring-бином.

**Bean Validation** (на полях DTO): `@NotBlank` (не пустая строка), `@Email` (формат email),
`@Size(min/max)` (длина строки).

---

## Шаг 1 — Зависимости в `backend/pom.xml`

Добавить в блок `<dependencies>`:

```xml
<!-- Драйвер PostgreSQL: без него приложение не подключится к БД -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Flyway: версионирование и применение миграций схемы для PostgreSQL -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>

<!-- Lombok: генерация геттеров/сеттеров/билдеров; optional — не тянется транзитивно -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>

<!-- Spring Security: даёт BCryptPasswordEncoder для хеширования паролей -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Bean Validation: аннотации @NotBlank/@Email/@Size на DTO -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- springdoc: автогенерация Swagger UI и OpenAPI JSON -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.9</version>
</dependency>
```

> `spring-boot-starter-security` нужен ради `BCryptPasswordEncoder` (хеш пароля). Бонусом — фундамент
> под JWT в BE_03.

---

## Шаг 2 — `backend/src/main/resources/application.properties`

```properties
spring.application.name=backend

# Подключение к БД (значения берутся из env, иначе — дефолты для локалки)
spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/expense_tracker}
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}

# Hibernate только проверяет соответствие схемы entity (схему ведёт Flyway)
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# Flyway применяет миграции при старте
spring.flyway.enabled=true
```

> `ddl-auto=validate` — наш принцип из CLAUDE.md: схему создаёт только Flyway, Hibernate её не трогает.

---

## Шаг 3 — Миграция `backend/src/main/resources/db/migration/V1__create_users_table.sql`

```sql
CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,           -- автоинкрементный BIGINT
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,       -- UNIQUE: один email = один аккаунт
    password_hash VARCHAR(255) NOT NULL,              -- BCrypt-хеш, не сам пароль
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);
```

> Имя файла строго `V{version}__{description}.sql` — иначе Flyway его не подхватит.

---

## Шаг 4 — Entity `backend/src/main/java/expence_tracker/backend/user/User.java`

```java
package expence_tracker.backend.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity                       // строка таблицы = объект этого класса
@Table(name = "users")        // user — зарезервированное слово в PostgreSQL
@Data                         // геттеры/сеттеры/toString/equals/hashCode
@Builder                      // User.builder()....build()
@NoArgsConstructor            // пустой конструктор обязателен для Hibernate
@AllArgsConstructor           // нужен, чтобы работал @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // id генерит БД (BIGSERIAL)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;                          // только хеш, не пароль

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;                       // updatable=false — не меняется после вставки

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist                                            // перед INSERT
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate                                             // перед UPDATE
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

---

## Шаг 5 — Repository `.../user/UserRepository.java`

```java
package expence_tracker.backend.user;

import expence_tracker.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// JpaRepository<Entity, типКлюча> даёт save/findById/findAll/delete «из коробки».
public interface UserRepository extends JpaRepository<User, Long> {

    // Явный JPQL вместо derived-метода: запрос виден прямо в коде и не зависит от имени метода.
    // :email — именованный параметр, связывается через @Param.
    @Query("""
            SELECT u
            FROM User u
            WHERE u.email = :email
            """)
    Optional<User> findByEmail(@Param("email") String email);

    // COUNT(...) > 0 — проверка существования: считаем строки с таким email.
    @Query("""
            SELECT COUNT(u) > 0
            FROM User u
            WHERE u.email = :email
            """)
    boolean existsByEmail(@Param("email") String email);
}
```

> В JPQL обращаемся к **имени сущности** (`User`) и её **полям** (`u.email`), а не к именам
> таблицы/колонок БД. `@Param("email")` связывает аргумент метода с `:email` в запросе.

---

## Шаг 6 — DTO

**`.../user/CreateUserRequest.java`** — вход (что присылает клиент):

```java
package expence_tracker.backend.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank @Size(max = 100)
    private String name;

    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 8)            // принимаем сырой пароль, хешируем в сервисе
    private String password;
}
```

**`.../user/UserDto.java`** — выход (что отдаём клиенту, без пароля):

```java
package expence_tracker.backend.user;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class UserDto {
    private Long id;
    private String name;
    private String email;
    private LocalDateTime createdAt;     // passwordHash наружу не отдаём принципиально
}
```

> Принцип из CLAUDE.md: контроллер никогда не возвращает Entity напрямую — только DTO.

---

## Шаг 7 — Service `.../user/UserService.java`

```java
package expence_tracker.backend.user;

import expence_tracker.backend.user.dto.CreateUserRequest;
import expence_tracker.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor               // конструктор для final-полей → DI без @Autowired
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;     // бин из SecurityConfig (шаг 9)

    public UserDto create(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))  // хешируем
                .build();
        return toDto(userRepository.save(user));
    }

    public UserDto getById(Long id) {
        return userRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    public List<UserDto> getAll() {
        return userRepository.findAll().stream().map(this::toDto).toList();
    }

    // маппинг Entity → DTO в одном месте
    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
```

---

## Шаг 8 — Controller `.../user/UserController.java`

```java
package expence_tracker.backend.user;

import expence_tracker.backend.user.dto.CreateUserRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController                          // методы возвращают JSON-тело
@RequestMapping("/api/users")           // общий префикс пути
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)                       // 201 при создании
    public UserDto create(@RequestBody @Valid CreateUserRequest request) {
        return userService.create(request);                   // @Valid запускает проверки DTO
    }

    @GetMapping("/{id}")
    public UserDto getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @GetMapping
    public List<UserDto> getAll() {
        return userService.getAll();
    }
}
```

---

## Шаг 9 — `.../common/SecurityConfig.java` (временный, открытый контур)

```java
package expence_tracker.backend.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Spring Security по умолчанию закрывает всё. Пока нет JWT (BE_03) — открываем доступ.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)               // для stateless REST CSRF не нужен
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    // BCrypt — алгоритм хеширования с солью; бин внедряется в UserService
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

---

## Итоговая структура пакета

```
expence_tracker.backend/
├── user/
│   ├── User.java
│   ├── UserRepository.java
│   ├── CreateUserRequest.java
│   ├── UserDto.java
│   ├── UserService.java
│   └── UserController.java
├── common/
│   └── SecurityConfig.java
└── BackendApplication.java
```

---

## Verification

```bash
docker compose up -d              # из корня монорепо — PostgreSQL на 5432
cd backend && ./mvnw spring-boot:run
```

1. Старт без ошибок, в логах видно применение Flyway-миграции `V1`.
2. Swagger UI: `http://localhost:8080/swagger-ui.html` — видны 3 эндпоинта `/api/users`.
3. Создать пользователя:
   ```bash
   curl -i -X POST http://localhost:8080/api/users \
     -H 'Content-Type: application/json' \
     -d '{"name":"Test","email":"test@example.com","password":"secret12"}'
   ```
   Ожидаем `201 Created` и JSON **без** `passwordHash`.
4. `GET http://localhost:8080/api/users` — список с созданным пользователем.
5. (опц.) В БД проверить, что в `password_hash` лежит BCrypt-хеш (начинается с `$2`), а не сырой пароль.
6. `./mvnw test` — сборка и тесты зелёные.
```
