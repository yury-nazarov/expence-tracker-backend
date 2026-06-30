package expence_tracker.backend.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Instant;

@Component
public class JwtTokenProvider {
    /*
    * Достает токен из заголовка на каждом этапе
    * */

    private final SecretKey key;            // ключ для подписи/проверки
    private final long expirationMs;        // срок жизни токена

    // @Value подставляет значения из application.properties в конструктор.
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ){
        // Keys.hmacShaKeyFor превращает строку-секрет в криптографический ключ.

        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // Кладём в токен id пользователя (subject) и сроки. subject в JWT — всегда строка.
    public String generateToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))                        // Кто это
                .issuedAt(Date.from(now))                               // Когда выдан
                .expiration(Date.from(now.plusMillis(expirationMs)))    // До когда годен
                .signWith(key)                                          // Подписываем ключем
                .compact();                                             // Собираем в строку
    }

    // Парсим токен и достаём userId. Если подпись неверна или токен протух —
    // parseSignedClaims бросит исключение (JwtException), его поймает фильтр.
    public Long getUserId(String token) {
        String subject = Jwts.parser()
                .verifyWith(key)                // Проверяет подпись ключем
                .build()
                .parseSignedClaims(token)       // парсит + валидирует, в т.ч. срок
                .getPayload()
                .getSubject();
        return Long.valueOf(subject);
    }


}
