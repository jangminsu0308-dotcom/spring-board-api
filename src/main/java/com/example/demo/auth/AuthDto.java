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
        String password
    ) {}

    public record LoginRequest(
        @NotBlank(message = "아이디는 필수입니다")
        String username,

        @NotBlank(message = "비밀번호는 필수입니다")
        String password
    ) {}

    public record TokenResponse(
        String token,
        String username
    ) {}
}
