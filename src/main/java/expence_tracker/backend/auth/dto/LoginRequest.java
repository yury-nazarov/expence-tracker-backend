package expence_tracker.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank
    private String email;

    // Не указываем ограничения, что бы злоумышленик не узнал длину пароля
    @NotBlank
    private String password;
}
