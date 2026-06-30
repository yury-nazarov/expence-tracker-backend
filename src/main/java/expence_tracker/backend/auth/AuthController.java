package expence_tracker.backend.auth;

import expence_tracker.backend.auth.dto.AuthResponse;
import expence_tracker.backend.auth.dto.LoginRequest;
import expence_tracker.backend.user.UserService;
import expence_tracker.backend.user.dto.CreateUserRequest;
import expence_tracker.backend.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestBody @Valid CreateUserRequest request
            ) {
        return ResponseEntity.ok().body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody @Valid LoginRequest request
            ) {
        return ResponseEntity.ok().body(authService.login(request));
    }
}
