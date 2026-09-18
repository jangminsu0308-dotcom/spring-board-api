package com.example.demo.auth;

import com.example.demo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public void register(AuthDto.RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUsernameException(request.username());
        }
        User user = new User(request.username(), passwordEncoder.encode(request.password()));
        userRepository.save(user);
    }

    @Transactional
    public AuthDto.TokenResponse login(AuthDto.LoginRequest request) {
        loginAttemptService.checkNotLocked(request.username());

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> {
                    loginAttemptService.recordFailure(request.username());
                    return new InvalidCredentialsException();
                });
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            loginAttemptService.recordFailure(request.username());
            throw new InvalidCredentialsException();
        }
        loginAttemptService.recordSuccess(request.username());
        return issueTokens(user);
    }

    /** 액세스 토큰이 만료됐을 때, 리프레시 토큰으로 재로그인 없이 새 토큰 쌍을 발급한다. 사용된 리프레시 토큰은 즉시 폐기(회전)한다. */
    @Transactional
    public AuthDto.TokenResponse refresh(AuthDto.RefreshRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!refreshToken.isUsable()) {
            throw new InvalidRefreshTokenException();
        }
        refreshToken.revoke();
        return issueTokens(refreshToken.getUser());
    }

    @Transactional
    public void logout(AuthDto.LogoutRequest request) {
        refreshTokenRepository.findByToken(request.refreshToken())
                .ifPresent(RefreshToken::revoke);
    }

    /** 비밀번호를 바꾸면 유출됐을 수 있는 기존 리프레시 토큰을 전부 폐기한다 — 바꾸는 순간 모든 기기에서 다시 로그인해야 한다. */
    @Transactional
    public void changePassword(String username, AuthDto.ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("인증된 사용자를 찾을 수 없습니다: " + username));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.findByUserAndRevokedFalse(user)
                .forEach(RefreshToken::revoke);
    }

    private AuthDto.TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getUsername());
        String refreshTokenValue = jwtTokenProvider.generateRefreshToken();
        LocalDateTime expiresAt = LocalDateTime.now().plus(Duration.ofMillis(jwtTokenProvider.getRefreshValidityMs()));
        refreshTokenRepository.save(new RefreshToken(refreshTokenValue, user, expiresAt));
        return new AuthDto.TokenResponse(accessToken, refreshTokenValue, user.getUsername());
    }
}
