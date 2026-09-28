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
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    private static final Duration RESET_TOKEN_VALIDITY = Duration.ofMinutes(10);

    @Transactional
    public void register(AuthDto.RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUsernameException(request.username());
        }
        User user = new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.securityQuestion(),
                passwordEncoder.encode(request.securityAnswer()));
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

    /** 비밀번호를 잊었을 때 본인 확인용 질문을 보여준다. 가입 때 등록해두지 않은 계정(V6 이전 가입자)은
     *  찾을 수 없는 사용자와 똑같이 취급한다 — 어느 쪽이든 이 흐름을 더 진행할 수 없는 건 같다. */
    public AuthDto.SecurityQuestionResponse getSecurityQuestion(String username) {
        User user = userRepository.findByUsername(username)
                .filter(u -> u.getSecurityQuestion() != null)
                .orElseThrow(() -> new UserNotFoundException(username));
        return new AuthDto.SecurityQuestionResponse(user.getSecurityQuestion());
    }

    /** 답변이 맞으면 짧게(10분)만 유효한 재설정 토큰을 발급한다. 로그인 시도 제한과 같은 방식으로
     *  아이디별 실패 횟수를 세어, 답변을 무차별 대입하는 걸 막는다. */
    @Transactional
    public AuthDto.ResetTokenResponse verifySecurityAnswer(AuthDto.VerifySecurityAnswerRequest request) {
        String lockKey = "reset:" + request.username();
        loginAttemptService.checkNotLocked(lockKey);

        User user = userRepository.findByUsername(request.username())
                .filter(u -> u.getSecurityQuestion() != null)
                .orElseThrow(() -> {
                    loginAttemptService.recordFailure(lockKey);
                    return new InvalidCredentialsException();
                });
        if (!passwordEncoder.matches(request.securityAnswer(), user.getSecurityAnswerHash())) {
            loginAttemptService.recordFailure(lockKey);
            throw new InvalidCredentialsException();
        }
        loginAttemptService.recordSuccess(lockKey);

        String tokenValue = jwtTokenProvider.generateRefreshToken();
        LocalDateTime expiresAt = LocalDateTime.now().plus(RESET_TOKEN_VALIDITY);
        passwordResetTokenRepository.save(new PasswordResetToken(tokenValue, user, expiresAt));
        return new AuthDto.ResetTokenResponse(tokenValue);
    }

    /** 새 비밀번호로 바꾸고, 비밀번호를 바꿀 때와 동일하게 모든 리프레시 토큰을 폐기한다. */
    @Transactional
    public void resetPassword(AuthDto.ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.resetToken())
                .orElseThrow(InvalidResetTokenException::new);
        if (!resetToken.isUsable()) {
            throw new InvalidResetTokenException();
        }
        resetToken.markUsed();

        User user = resetToken.getUser();
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
