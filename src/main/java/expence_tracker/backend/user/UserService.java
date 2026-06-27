package expence_tracker.backend.user;

import expence_tracker.backend.user.dto.CreateUserRequest;
import expence_tracker.backend.user.dto.UserResponse;
import expence_tracker.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor // Конструктор для final-полей → DI без @Autowired
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;  // бин из SecurityConfig

    // Создание пользователя
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        return toDTO(userRepository.save(user));
    }

    public UserResponse getById(Long id) {
        return userRepository.findById(id)
                .map(item -> toDTO(item))
                .orElseThrow(() -> new IllegalArgumentException("User not found by id:" + id));
    }

    public List<UserResponse> getAll() {
        return userRepository.findAll().stream().map(item -> toDTO(item)).toList();
    }

    // маппинг Entity → DTO в одном месте
    private UserResponse toDTO(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
