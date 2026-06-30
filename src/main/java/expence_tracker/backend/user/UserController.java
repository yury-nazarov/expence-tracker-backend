package expence_tracker.backend.user;

import expence_tracker.backend.user.dto.CreateUserRequest;
import expence_tracker.backend.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody @Valid CreateUserRequest request) {
        // @Valid запускает проверки DTO
        return userService.create(request);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse getById(@PathVariable Long id){
        return userService.getById(id);
    }

    @GetMapping("/list")
    @ResponseStatus(HttpStatus.OK)
    public List<UserResponse> getAll() {
        return userService.getAll();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok().body(userService.getById(userId));
    }
}
