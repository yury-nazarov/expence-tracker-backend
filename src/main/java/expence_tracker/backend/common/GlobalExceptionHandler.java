package expence_tracker.backend.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice // глобальный перехватчик исключений из всех контроллеров;
public class GlobalExceptionHandler {

    // Неверный логин/пароль → 401
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handlerBadCredential(BadCredentialsException e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.builder()
                        .message("error")
                        .detailMessage(e.getMessage()) // TODO: Может быть потенциально опасная информация
                        .errorTime(LocalDateTime.now())
                        .build());
    }

    // Бизнес-ошибки из сервисов ("email занят", "не найден") → 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .message("error")
                        .detailMessage(e.getMessage())
                        .errorTime(LocalDateTime.now())
                        .build());
    }

    // Ошибки валидации @Valid (@NotBlank, @Email...) → 400 с деталями по полям
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .message("validation error")
                        .detailMessage(e.getMessage())
                        .errorTime(LocalDateTime.now())
                        .build());

    }

}
