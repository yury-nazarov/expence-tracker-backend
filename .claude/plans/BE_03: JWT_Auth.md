# BE_03: JWT Auth

План реализации аутентификации по JWT поверх пакета `user` (BE_02).
Подача — для джуна: объясняю **что** делать, **зачем** и **какие аннотации** за это отвечают.

---

## 0. Что вообще происходит (картина целиком)

JWT (JSON Web Token) — это подписанная сервером строка, в которой лежат данные о пользователе
(минимум — его id) и срок годности. Сервер не хранит сессий: он просто **подписывает** токен своим
секретом при логине и **проверяет подпись** на каждом следующем запросе. Если подпись валидна и токен
не протух — мы доверяем тому, что внутри.

Поток:

```
register/login  ──>  AuthService  ──>  JwtTokenProvider.generate(userId)  ──>  токен клиенту
                                                                                   │
любой защищённый запрос  ──>  JwtAuthenticationFilter  ──>  provider.parse(token)  │
                                   │ кладёт userId в SecurityContext               │
                                   ▼                                               │
                              Controller (уже знает, кто пользователь) <───────────┘
```

Ключевые принципы, которые задаёт промпт:
- **Stateless** — никаких HttpSession, авторизация только по токену из заголовка.
- **DTO ≠ Entity** — отдельные request/response, наружу `passwordHash` не отдаём (это уже соблюдено в `UserResponse`).
- **Переиспользуем `UserService`** для регистрации, не дублируем логику создания пользователя.

---

## Принятые решения «на твоё усмотрение»

| Вопрос | Решение | Почему |
|---|---|---|
| Библиотека JWT | **jjwt** (`io.jsonwebtoken`) 0.12.x | Де-факто стандарт, чистый fluent-API, активно поддерживается |
| Формат ответа login/register | **токен + профиль пользователя** (`AuthResponse`) | Фронту удобно: после логина сразу есть данные юзера, не нужен лишний запрос |
| Эндпоинт `GET /api/auth/me` | **делаем** | Наглядно демонстрирует, что фильтр реально положил юзера в `SecurityContext` |
| Где живёт инфраструктура безопасности | `JwtTokenProvider` и `JwtAuthenticationFilter` — в пакете `auth`; `SecurityConfig`, `GlobalExceptionHandler`, entry point — в `common` | Токен-логика принадлежит фиче `auth`, общеприменимые бины — в `common` |
| Где брать сущность для login | новый метод `UserRepository.findByEmail` | Для проверки пароля нужен сам `User` с `passwordHash` |

---

## Итоговая структура файлов

```
auth/
├── AuthController.java              # POST /register, POST /login, GET /me
├── AuthService.java                # бизнес-логика регистрации и входа
├── JwtTokenProvider.java           # генерация + парсинг/валидация токена
├── JwtAuthenticationFilter.java    # достаёт токен из заголовка на каждом запросе
└── dto/
    ├── LoginRequest.java           # { email, password }
    └── AuthResponse.java           # { token, user }
common/
├── SecurityConfig.java             # ОБНОВИТЬ: реальные правила + фильтр + stateless
├── GlobalExceptionHandler.java     # НОВЫЙ: маппинг исключений в HTTP-коды
└── RestAuthenticationEntryPoint.java # НОВЫЙ: 401 для неаутентифицированных
user/
└── UserRepository.java             # ДОБАВИТЬ: findByEmail(...)
```

Для register **переиспользуем существующий `CreateUserRequest`** (`name`, `email`, `password`) —
контракт у него ровно тот же, плодить идентичный `RegisterRequest` смысла нет (принцип DRY).
Промпт допускает отдельный request — если хочешь строго по букве, можно завести `auth/dto/RegisterRequest.java`
с теми же полями; на работу это не влияет.

---

## Шаг 1. Зависимости (`pom.xml`)

Добавить три артефакта jjwt. Почему три: `api` — интерфейсы для нашего кода (compile-time),
`impl` и `jackson` — реализация и JSON-сериализация, нужны только в рантайме (`scope=runtime`),
поэтому в наш код их классы не «протекают».

```xml
<!-- JWT (jjwt): генерация и проверка токенов -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

---

## Шаг 2. Конфигурация (`application.properties`)

```properties
# JWT
# Секрет для HMAC-SHA256 ДОЛЖЕН быть >= 32 байт (256 бит), иначе jjwt бросит ошибку при старте.
# Дефолт — только для локалки; в проде задавать через переменную окружения JWT_SECRET.
jwt.secret=${JWT_SECRET:local-dev-secret-change-me-please-32bytes-minimum!!}
# Время жизни токена в миллисекундах (по умолчанию 1 час).
jwt.expiration-ms=${JWT_EXPIRATION_MS:3600000}
```

Синтаксис `${ИМЯ:дефолт}` — это подстановка Spring: берёт переменную окружения `ИМЯ`,
а если её нет — использует значение после двоеточия. Тот же приём, что уже применён для `DATABASE_URL`.

---

## Шаг 3. `UserRepository` — добавить `findByEmail`

Для входа нужно достать `User` целиком (с `passwordHash`), чтобы сверить пароль.
Новые импорты к уже существующим в файле (`@Query`, `@Param` там уже есть):

```java
import expence_tracker.backend.user.entity.User;   // уже импортирован в файле
import java.util.Optional;                          // уже импортирован в файле
```

```java
// Возвращаем Optional: пользователя с таким email может не быть.
// JPQL обращается к сущности User и её полю email (не к таблице/колонке).
@Query("SELECT u FROM User u WHERE u.email = :email")
Optional<User> findByEmail(@Param("email") String email);
```

> Можно было бы положиться на «магию» Spring Data (метод `findByEmail` без `@Query` Spring сгенерит сам
> по имени), но в проекте принято писать JPQL явно — придерживаемся стиля.

> ⚠️ **Ловушка (проверено на практике):** в `SELECT` должна стоять сама сущность — `SELECT u`,
> а не её поля. Тип в `SELECT` обязан совпадать с возвращаемым типом метода, и компилятор это НЕ ловит —
> `@Query` валидируется Hibernate только в рантайме:
> - `SELECT u` → `User` ✅
> - `SELECT u.email` → `String` → каст в `User` даёт `ClassCastException`
> - `SELECT u.id, u.name, ...` → `Object[]` → `ConverterNotFoundException` (`ArrayToObjectConverter`)
>
> Перечислять поля нужно только для осознанной проекции (DTO / интерфейс-проекция).

---

## Шаг 4. `JwtTokenProvider` (пакет `auth`)

Отдельный компонент-бин: умеет **создать** токен и **разобрать/проверить** его.

```java
package expence_tracker.backend.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
```

```java
@Component
public class JwtTokenProvider {

    private final SecretKey key;          // ключ для подписи/проверки
    private final long expirationMs;      // срок жизни токена

    // @Value подставляет значения из application.properties в конструктор.
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        // Keys.hmacShaKeyFor превращает строку-секрет в криптографический ключ.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // Кладём в токен id пользователя (subject) и сроки. subject в JWT — всегда строка.
    public String generateToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))         // кто это
                .issuedAt(Date.from(now))                // когда выдан
                .expiration(Date.from(now.plusMillis(expirationMs))) // до когда годен
                .signWith(key)                           // подпись нашим секретом
                .compact();                              // собрать в строку
    }

    // Парсим токен и достаём userId. Если подпись неверна или токен протух —
    // parseSignedClaims бросит исключение (JwtException), его поймает фильтр.
    public Long getUserId(String token) {
        String subject = Jwts.parser()
                .verifyWith(key)                         // проверить подпись тем же ключом
                .build()
                .parseSignedClaims(token)                // парсит + валидирует (в т.ч. срок)
                .getPayload()
                .getSubject();
        return Long.valueOf(subject);
    }
}
```

Аннотации: `@Component` — Spring создаёт бин и сможет внедрить его в фильтр/сервис.
`@Value` — инъекция конкретного свойства (в отличие от `@Autowired`, который тащит целый бин).

---

## Шаг 5. DTO (`auth/dto`)

`LoginRequest` — вход принимает только email и пароль:

```java
package expence_tracker.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
```

```java
@Data
public class LoginRequest {
    @NotBlank @Email
    private String email;
    @NotBlank
    private String password;   // тут НЕ ставим @Size(min=8): при логине длину не проверяем,
                               // иначе подскажем злоумышленнику правила пароля
}
```

`AuthResponse` — что отдаём после register/login:

```java
package expence_tracker.backend.auth.dto;

import expence_tracker.backend.user.dto.UserResponse;
import lombok.Builder;
import lombok.Data;
```

```java
@Data
@Builder
public class AuthResponse {
    private String token;        // сам JWT
    private UserResponse user;   // профиль (без passwordHash — он и так не входит в UserResponse)
}
```

---

## Шаг 6. `AuthService` (пакет `auth`)

Сердце фичи. Регистрацию **делегируем** в `UserService` (не дублируем создание),
для логина — достаём сущность и сверяем пароль.

```java
package expence_tracker.backend.auth;

import expence_tracker.backend.auth.dto.AuthResponse;
import expence_tracker.backend.auth.dto.LoginRequest;
import expence_tracker.backend.user.UserRepository;
import expence_tracker.backend.user.UserService;
import expence_tracker.backend.user.dto.CreateUserRequest;
import expence_tracker.backend.user.dto.UserResponse;
import expence_tracker.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
```

```java
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;          // переиспользуем create()
    private final UserRepository userRepository;    // для findByEmail при логине
    private final PasswordEncoder passwordEncoder;  // тот же BCrypt-бин
    private final JwtTokenProvider tokenProvider;

    // Регистрация: создаём юзера через UserService, затем выдаём токен.
    public AuthResponse register(CreateUserRequest request) {
        UserResponse user = userService.create(request); // тут же отработает проверка "email занят"
        String token = tokenProvider.generateToken(user.getId());
        return AuthResponse.builder().token(token).user(user).build();
    }

    // Вход: находим по email, сверяем пароль через matches(), при успехе — токен.
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                // НЕ уточняем "нет такого email" vs "неверный пароль" — одинаковая ошибка,
                // чтобы не подсказывать, какие email зарегистрированы.
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // matches(сырой, хеш) — BCrypt сам достаёт соль из хеша и сравнивает.
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = tokenProvider.generateToken(user.getId());
        return AuthResponse.builder()
                .token(token)
                .user(/* собрать UserResponse из user */)
                .build();
    }
}
```

> **Деталь про маппинг.** В `UserService` метод `toDTO(User)` сейчас `private`. Для `login` нужен тот же
> маппинг `User → UserResponse`. Два варианта:
> 1. Сделать в `UserService` публичный метод (напр. `toResponse(User)` или `getByEmail`) и вызвать его — **рекомендую**, маппинг остаётся в одном месте.
> 2. Собрать `UserResponse.builder()...` прямо в `AuthService` — быстрее, но дублирует маппинг.
>
> `BadCredentialsException` — стандартное исключение Spring Security; в шаге 8 мапим его на **401**.

---

## Шаг 7. `JwtAuthenticationFilter` (пакет `auth`)

Фильтр выполняется **на каждом** запросе. Наследуем `OncePerRequestFilter` — гарантия, что
сработает один раз за запрос.

```java
package expence_tracker.backend.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
```

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        // Нас интересует только схема "Bearer <token>".
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7); // отрезаем "Bearer "
            try {
                Long userId = tokenProvider.getUserId(token); // тут же валидация подписи/срока

                // Создаём "аутентификацию": principal = userId, прав (authorities) пока нет.
                var authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, Collections.emptyList());

                // Кладём пользователя в контекст — дальше Spring Security считает запрос аутентифицированным.
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                // Токен битый/протух — просто НЕ аутентифицируем.
                // Решение "пускать или нет" примет SecurityConfig (вернёт 401 через entry point).
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response); // передаём запрос дальше по цепочке
    }
}
```

Почему principal — это `userId` (Long): в `/me` мы его прочитаем и достанем профиль.
Authorities (роли/права) пока пустые — ролей в проекте нет.

---

## Шаг 8. Обработка ошибок (`common`)

### 8a. `GlobalExceptionHandler` — НОВЫЙ (его в проекте нет!)

Сейчас `IllegalArgumentException` из `UserService` («Email already in use», «User not found»)
превращается в **500**. Заводим `@RestControllerAdvice`, чтобы маппить исключения в человеческие коды.

```java
package expence_tracker.backend.common;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
```

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Неверный логин/пароль → 401
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleBadCredentials(BadCredentialsException e) {
        return Map.of("error", e.getMessage());
    }

    // Бизнес-ошибки из сервисов ("email занят", "не найден") → 400
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalArgument(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }

    // Ошибки валидации @Valid (@NotBlank, @Email...) → 400 с деталями по полям
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));
        return errors;
    }
}
```

`@RestControllerAdvice` — глобальный перехватчик исключений из всех контроллеров;
`@ExceptionHandler(X.class)` — какой тип ловим; `@ResponseStatus` — какой HTTP-код вернуть.

> Замечание на будущее: «email занят» по-хорошему это **409 Conflict**, а «не найден» — **404**.
> Сейчас обе ситуации кидают одинаковый `IllegalArgumentException`, поэтому маппятся в 400.
> Разделить можно позже, заведя свои исключения (напр. `EmailAlreadyUsedException`) — выходит за рамки BE_03.

### 8b. `RestAuthenticationEntryPoint` — НОВЫЙ

Когда в защищённый эндпоинт пришли **без** валидного токена, по умолчанию Spring может ответить
не так, как мы хотим. Entry point гарантирует чистый **401**.

```java
package expence_tracker.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
```

```java
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }
}
```

---

## Шаг 9. `SecurityConfig` — ОБНОВИТЬ

Меняем временный `permitAll()` на реальные правила, включаем stateless и встраиваем JWT-фильтр.
`PasswordEncoder`-бин оставляем как есть.

Добавить к уже имеющимся импортам файла:

```java
import expence_tracker.backend.auth.JwtAuthenticationFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
// RestAuthenticationEntryPoint лежит в этом же пакете common — импорт не нужен
// (уже есть: HttpSecurity, EnableWebSecurity, AbstractHttpConfigurer, SecurityFilterChain,
//  BCryptPasswordEncoder, PasswordEncoder, Bean, Configuration)
```

```java
@Bean
public SecurityFilterChain filterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtFilter,                 // внедряем наш фильтр (он @Component)
        RestAuthenticationEntryPoint authEntryPoint) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)             // stateless REST — CSRF не нужен (как и было)
        // Stateless: сервер не создаёт и не хранит HttpSession.
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**").permitAll()   // регистрация и логин — открыты
            // Swagger оставляем доступным, иначе документацию не открыть без токена:
            .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
            .anyRequest().authenticated()                  // всё остальное — только с токеном
        )
        // 401 для неаутентифицированных запросов:
        .exceptionHandling(eh -> eh.authenticationEntryPoint(authEntryPoint))
        // Наш фильтр должен отработать ДО стандартного — он кладёт юзера в контекст:
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
}
```

Порядок важен: `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)` ставит наш фильтр
раньше штатного, чтобы к моменту проверки прав пользователь уже лежал в `SecurityContext`.

---

## Шаг 10. `AuthController` (пакет `auth`)

```java
package expence_tracker.backend.auth;

import expence_tracker.backend.auth.dto.AuthResponse;
import expence_tracker.backend.auth.dto.LoginRequest;
import expence_tracker.backend.user.UserService;
import expence_tracker.backend.user.dto.CreateUserRequest;
import expence_tracker.backend.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
```

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;   // для /me

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@RequestBody @Valid CreateUserRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse login(@RequestBody @Valid LoginRequest request) {
        return authService.login(request);
    }

    // Текущий пользователь по токену. principal сюда положил наш фильтр (это userId).
    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        return userService.getById(userId);
    }
}
```

`@AuthenticationPrincipal` — достаёт principal из `SecurityContext` (то, что фильтр положил как principal,
то есть `userId`). Эндпоинт `/me` не входит в `/api/auth/**`-... — стоп, входит по префиксу, **но** он
защищён логикой: без валидного токена фильтр не положит principal, `userId` будет `null`.

> ⚠️ **Тонкость с `/me`:** мы открыли `/api/auth/**` через `permitAll()`, значит `/api/auth/me` тоже
> формально открыт, и Security не потребует токен. Тогда без токена `userId` придёт `null` → `getById(null)`
> упадёт. Варианты:
> 1. **Рекомендую:** сузить правило — `permitAll()` только для `/api/auth/register` и `/api/auth/login`,
>    а `/api/auth/me` оставить под `authenticated()`. Тогда без токена сразу 401.
> 2. Либо вынести `/me` на другой путь (напр. `/api/users/me`), не попадающий под `/api/auth/**`.
>
> Возьми вариант 1 — поправь правила в шаге 9:
> ```java
> .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
> .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
> .anyRequest().authenticated()
> ```

---

## Шаг 11. Проверка

1. Сборка: `./mvnw package -DskipTests` (или `./mvnw test`).
2. Поднять БД: `docker compose up -d`, запустить `./mvnw spring-boot:run`.
3. Ручная проверка (curl/Swagger):
   - `POST /api/auth/register` `{name,email,password}` → 201 + `{token, user}`.
   - `POST /api/auth/login` верные данные → 200 + токен; неверные → **401**.
   - `GET /api/auth/me` без заголовка → **401**; с `Authorization: Bearer <token>` → 200 + профиль.
   - Любой старый защищённый эндпоинт (`GET /api/user/list`) без токена → **401**, с токеном → 200.
   - Протухший/битый токен → **401**.
4. Проверить, что `passwordHash` не светится ни в одном ответе и в логах (`show-sql=false` уже стоит).

---

## Чек-лист реализации

- [ ] `pom.xml`: добавлены 3 зависимости jjwt
- [ ] `application.properties`: `jwt.secret`, `jwt.expiration-ms`
- [ ] `UserRepository.findByEmail`
- [ ] (опц.) `UserService` — публичный маппинг `User → UserResponse` для login
- [ ] `auth/JwtTokenProvider`
- [ ] `auth/dto/LoginRequest`, `auth/dto/AuthResponse`
- [ ] `auth/AuthService`
- [ ] `auth/JwtAuthenticationFilter`
- [ ] `common/GlobalExceptionHandler`
- [ ] `common/RestAuthenticationEntryPoint`
- [ ] `common/SecurityConfig` — stateless + правила + фильтр + entry point
- [ ] `auth/AuthController` (register/login/me)
- [ ] Проверка по шагу 11
</content>
</invoke>
