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

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    /*
    * Фильтр выполняется на каждом запросе.
    * Наследуем OncePerRequestFilter — гарантия, что сработает один раз за запрос.
     * */
    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        // Нас интересует только схема "Bearer <token>".
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7); // отрезаем "Bearer "
            try {
                // тут же валидация подписи/срока
                Long userId = tokenProvider.getUserId(token);

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
        filterChain.doFilter(request, response);
    }
}
