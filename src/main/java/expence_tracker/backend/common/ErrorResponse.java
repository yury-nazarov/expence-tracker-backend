package expence_tracker.backend.common;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ErrorResponse {
    String message;
    String detailMessage;
    LocalDateTime errorTime;
}
