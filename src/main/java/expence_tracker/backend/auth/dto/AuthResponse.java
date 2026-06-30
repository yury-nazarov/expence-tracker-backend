package expence_tracker.backend.auth.dto;


import expence_tracker.backend.user.dto.UserResponse;
import lombok.Builder;
import lombok.Data;

@Data // А нужен ли он с учетом билдера?
@Builder
public class AuthResponse {
    private String token;
    private UserResponse user;

}
