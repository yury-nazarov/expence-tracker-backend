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

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    // Регистрация: создаём юзера через UserService, затем выдаём токен.
    public AuthResponse register(CreateUserRequest request) {
        UserResponse user = userService.create(request);
        String token = tokenProvider.generateToken(user.getId());
        return AuthResponse.builder().token(token).user(user).build();
    }

    // Вход: находим по email, сверяем пароль через matches(), при успехе — токен.
    public AuthResponse login(LoginRequest request) {
        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // matches()
        // 1. Извлекает из сохранённого хеша соль (salt) и параметры алгоритма.
        // 2. Повторно прогоняет введённый пароль через тот же алгоритм с той же солью.
        // 3. Сравнивает результат.
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        String token = tokenProvider.generateToken(user.getId());
        // TODO: Обсудить в ревью.
        //  У нас уже есть объект пользователя.
        //  Делать второй запрос в БД через мапер считаю избыточным
        return AuthResponse.builder()
                .token(token)
                .user(UserResponse.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .createdAt(user.getCreatedAt())
                        .build())
                .build();

    }
}
