package ru.warehouse.auth.dto;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank String login, @NotBlank String password) {}

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {}

    public record UserResponse(UUID id, String login, String fullName, String email, String phone,
                               boolean active, List<String> roles, OffsetDateTime createdAt) {}

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9._@-]{3,100}$", message = "3–100 символов: латиница, цифры, . _ @ -") String login,
            @NotBlank String password,
            @NotBlank @Size(max = 255) String fullName,
            @Email @Size(max = 255) String email,
            @Size(max = 50) String phone,
            @NotEmpty Set<String> roles) {}

    /** PATCH: null = «не менять». Если передан roles, набор ролей заменяется целиком. */
    public record UpdateUserRequest(
            @Pattern(regexp = ".*\\S.*", message = "не может быть пустым") @Size(max = 255) String fullName,
            @Email @Size(max = 255) String email,
            @Size(max = 50) String phone,
            Boolean active,
            Set<String> roles) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    public record ResetPasswordRequest(@NotBlank String newPassword) {}

    public record RoleResponse(String code, String name) {}

    /** Краткие данные работника склада для назначений. */
    public record WorkerInfo(UUID id, String login, String fullName) {}
}