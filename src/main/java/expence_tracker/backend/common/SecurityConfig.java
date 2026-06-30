package expence_tracker.backend.common;

import expence_tracker.backend.auth.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@EnableWebSecurity
public class SecurityConfig {
    // Spring Security по умолчанию закрывает всё.
    // Пока нет JWT — открываем доступ.
    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            RestAuthenticationEntryPoint authEntryPoint
            ) throws Exception {
        http

        /* Отключаем CSRF защиту
        * CSRF (Cross-Site Request Forgery) — атака, при которой чужой сайт заставляет браузеp пользователя втихаря
        * отправить запрос на наш сервер, используя уже сохранённую cookie-сессию.
        * Защита от неё (CSRF-токен) имеет смысл, когда аутентификация держится на cookie/сессии.
        * Наш API будет stateless — без серверной сессии, авторизация пойдёт через JWT-токен в заголовке Authorization.
        * Токен из заголовка чужой сайт автоматически не подставит (в отличие от cookie), поэтому классический CSRF тут
        * неприменим, и защиту отключают, чтобы она не мешала POST-запросам.
        * **/
            .csrf(AbstractHttpConfigurer::disable) // для stateless REST CSRF не нужен
            // Stateless: сервер не создаёт и не хранит HttpSession.
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Авторизация
            .authorizeHttpRequests(auth -> auth
                    // регистрация и логин — открыт
                    .requestMatchers("/api/auth/**").permitAll()
                    // Swagger оставляем доступным, иначе документацию не открыть без токена:
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    // всё остальное — только с токеном
                    .anyRequest().authenticated()
            )
            // 401 для неаутентифицированных запросов:
            .exceptionHandling(eh -> eh.authenticationEntryPoint(authEntryPoint))
            // Наш фильтр должен отработать ДО стандартного — он кладёт юзера в контекст:
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        // билдер собирает все накопленные настройки в готовый объект SecurityFilterChain, который и
        //  становится бином.
        return http.build();
    }

    // BCrypt — алгоритм хеширования с солью; бин внедряется в UserService
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
