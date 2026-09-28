package com.example.demo.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDto {

    public record RegisterRequest(
        @NotBlank(message = "아이디는 필수입니다")
        @Size(min = 4, max = 50, message = "아이디는 4~50자여야 합니다")
        String username,

        @NotBlank(message = "비밀번호는 필수입니다")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
        String password,

        @NotBlank(message = "보안 질문은 필수입니다")
        String securityQuestion,

        @NotBlank(message = "보안 질문 답변은 필수입니다")
        String securityAnswer
    ) {}

    public record LoginRequest(
        @NotBlank(message = "아이디는 필수입니다")
        String username,

        @NotBlank(message = "비밀번호는 필수입니다")
        String password
    ) {}

    public record RefreshRequest(
        @NotBlank(message = "리프레시 토큰은 필수입니다")
        String refreshToken
    ) {}

    public record LogoutRequest(
        @NotBlank(message = "리프레시 토큰은 필수입니다")
        String refreshToken
    ) {}

    public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호는 필수입니다")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수입니다")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
        String newPassword
    ) {}

    public record TokenResponse(
        String accessToken,
        String refreshToken,
        String username
    ) {}

    public record SecurityQuestionResponse(
        String securityQuestion
    ) {}

    public record VerifySecurityAnswerRequest(
        @NotBlank(message = "아이디는 필수입니다")
        String username,

        @NotBlank(message = "답변은 필수입니다")
        String securityAnswer
    ) {}

    public record ResetTokenResponse(
        String resetToken
    ) {}

    public record ResetPasswordRequest(
        @NotBlank(message = "재설정 토큰은 필수입니다")
        String resetToken,

        @NotBlank(message = "새 비밀번호는 필수입니다")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
        String newPassword
    ) {}
}
