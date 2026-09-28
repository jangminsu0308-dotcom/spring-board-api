package com.example.demo.auth;

import com.example.demo.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @InjectMocks
    private AuthService authService;

    private User user;
    private RefreshToken refreshToken;

    @BeforeEach
    void setUp() {
        user = new User("writer", "encoded-password", "가장 좋아하는 음식은?", "encoded-answer");
        ReflectionTestUtils.setField(user, "id", 1L);

        refreshToken = new RefreshToken("refresh-token", user, LocalDateTime.now().plusDays(7));
        ReflectionTestUtils.setField(refreshToken, "id", 1L);
    }

    @Test
    void register_아이디가_중복되지_않으면_비밀번호를_암호화해_저장한다() {
        when(userRepository.existsByUsername("writer")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

        when(passwordEncoder.encode("답변")).thenReturn("encoded-answer");

        authService.register(new AuthDto.RegisterRequest("writer", "password123", "질문", "답변"));

        verify(userRepository).save(argThat(u ->
                u.getUsername().equals("writer") && u.getPassword().equals("encoded-password")));
    }

    @Test
    void register_아이디가_중복되면_예외를_던지고_저장하지_않는다() {
        when(userRepository.existsByUsername("writer")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new AuthDto.RegisterRequest("writer", "password123", "질문", "답변")))
                .isInstanceOf(DuplicateUsernameException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_아이디와_비밀번호가_일치하면_토큰_쌍을_발급한다() {
        when(userRepository.findByUsername("writer")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken("writer")).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshValidityMs()).thenReturn(604800000L);

        AuthDto.TokenResponse response = authService.login(new AuthDto.LoginRequest("writer", "password123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.username()).isEqualTo("writer");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(loginAttemptService).recordSuccess("writer");
    }

    @Test
    void login_존재하지_않는_아이디면_예외를_던지고_실패를_기록한다() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new AuthDto.LoginRequest("unknown", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(loginAttemptService).recordFailure("unknown");
    }

    @Test
    void login_비밀번호가_틀리면_예외를_던지고_실패를_기록한다() {
        when(userRepository.findByUsername("writer")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new AuthDto.LoginRequest("writer", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(loginAttemptService).recordFailure("writer");
    }

    @Test
    void login_잠겨있으면_비밀번호_검증_없이_바로_예외를_던진다() {
        doThrow(new TooManyLoginAttemptsException(60)).when(loginAttemptService).checkNotLocked("writer");

        assertThatThrownBy(() -> authService.login(new AuthDto.LoginRequest("writer", "password123")))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        verify(userRepository, never()).findByUsername(any());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void refresh_유효하면_토큰_쌍을_재발급하고_기존_리프레시_토큰을_폐기한다() {
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(refreshToken));
        when(jwtTokenProvider.createAccessToken("writer")).thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("new-refresh-token");
        when(jwtTokenProvider.getRefreshValidityMs()).thenReturn(604800000L);

        AuthDto.TokenResponse response = authService.refresh(new AuthDto.RefreshRequest("refresh-token"));

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(refreshToken.isUsable()).isFalse();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refresh_존재하지_않으면_예외를_던진다() {
        when(refreshTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new AuthDto.RefreshRequest("unknown")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_만료됐으면_예외를_던진다() {
        RefreshToken expired = new RefreshToken("expired-token", user, LocalDateTime.now().minusDays(1));
        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(new AuthDto.RefreshRequest("expired-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_이미_폐기됐으면_예외를_던진다() {
        refreshToken.revoke();
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> authService.refresh(new AuthDto.RefreshRequest("refresh-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logout_존재하는_토큰이면_폐기한다() {
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(refreshToken));

        authService.logout(new AuthDto.LogoutRequest("refresh-token"));

        assertThat(refreshToken.isUsable()).isFalse();
    }

    @Test
    void logout_존재하지_않아도_예외를_던지지_않는다() {
        when(refreshTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        authService.logout(new AuthDto.LogoutRequest("unknown"));
    }

    @Test
    void changePassword_현재_비밀번호가_맞으면_변경하고_기존_리프레시_토큰을_전부_폐기한다() {
        when(userRepository.findByUsername("writer")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(passwordEncoder.encode("newpassword123")).thenReturn("new-encoded-password");
        when(refreshTokenRepository.findByUserAndRevokedFalse(user)).thenReturn(java.util.List.of(refreshToken));

        authService.changePassword("writer", new AuthDto.ChangePasswordRequest("password123", "newpassword123"));

        assertThat(user.getPassword()).isEqualTo("new-encoded-password");
        assertThat(refreshToken.isUsable()).isFalse();
    }

    @Test
    void changePassword_현재_비밀번호가_틀리면_예외를_던지고_변경하지_않는다() {
        when(userRepository.findByUsername("writer")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword("writer", new AuthDto.ChangePasswordRequest("wrong", "newpassword123")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(user.getPassword()).isEqualTo("encoded-password");
        verify(refreshTokenRepository, never()).findByUserAndRevokedFalse(any());
    }

    @Test
    void changePassword_인증된_사용자가_없으면_예외를_던진다() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword("ghost", new AuthDto.ChangePasswordRequest("password123", "newpassword123")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getSecurityQuestion_보안질문이_등록된_사용자면_질문을_반환한다() {
        when(userRepository.findByUsername("writer")).thenReturn(Optional.of(user));

        AuthDto.SecurityQuestionResponse response = authService.getSecurityQuestion("writer");

        assertThat(response.securityQuestion()).isEqualTo("가장 좋아하는 음식은?");
    }

    @Test
    void getSecurityQuestion_존재하지_않는_사용자면_예외를_던진다() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getSecurityQuestion("ghost"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getSecurityQuestion_보안질문이_없는_사용자면_예외를_던진다() {
        User legacyUser = new User("legacy", "encoded-password");
        when(userRepository.findByUsername("legacy")).thenReturn(Optional.of(legacyUser));

        assertThatThrownBy(() -> authService.getSecurityQuestion("legacy"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void verifySecurityAnswer_답변이_일치하면_재설정_토큰을_발급한다() {
        when(userRepository.findByUsername("writer")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("답변", "encoded-answer")).thenReturn(true);
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("reset-token");

        AuthDto.ResetTokenResponse response = authService.verifySecurityAnswer(
                new AuthDto.VerifySecurityAnswerRequest("writer", "답변"));

        assertThat(response.resetToken()).isEqualTo("reset-token");
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(loginAttemptService).recordSuccess("reset:writer");
    }

    @Test
    void verifySecurityAnswer_답변이_틀리면_예외를_던지고_실패를_기록한다() {
        when(userRepository.findByUsername("writer")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-answer")).thenReturn(false);

        assertThatThrownBy(() -> authService.verifySecurityAnswer(
                new AuthDto.VerifySecurityAnswerRequest("writer", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(loginAttemptService).recordFailure("reset:writer");
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void verifySecurityAnswer_존재하지_않는_사용자면_예외를_던지고_실패를_기록한다() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifySecurityAnswer(
                new AuthDto.VerifySecurityAnswerRequest("ghost", "답변")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(loginAttemptService).recordFailure("reset:ghost");
    }

    @Test
    void verifySecurityAnswer_잠겨있으면_답변_검증_없이_바로_예외를_던진다() {
        doThrow(new TooManyLoginAttemptsException(60)).when(loginAttemptService).checkNotLocked("reset:writer");

        assertThatThrownBy(() -> authService.verifySecurityAnswer(
                new AuthDto.VerifySecurityAnswerRequest("writer", "답변")))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        verify(userRepository, never()).findByUsername(any());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void resetPassword_토큰이_유효하면_비밀번호를_변경하고_기존_리프레시_토큰을_전부_폐기한다() {
        PasswordResetToken resetToken = new PasswordResetToken("reset-token", user, LocalDateTime.now().plusMinutes(10));
        when(passwordResetTokenRepository.findByToken("reset-token")).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("newpassword123")).thenReturn("new-encoded-password");
        when(refreshTokenRepository.findByUserAndRevokedFalse(user)).thenReturn(java.util.List.of(refreshToken));

        authService.resetPassword(new AuthDto.ResetPasswordRequest("reset-token", "newpassword123"));

        assertThat(user.getPassword()).isEqualTo("new-encoded-password");
        assertThat(resetToken.isUsable()).isFalse();
        assertThat(refreshToken.isUsable()).isFalse();
    }

    @Test
    void resetPassword_토큰이_존재하지_않으면_예외를_던진다() {
        when(passwordResetTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(new AuthDto.ResetPasswordRequest("unknown", "newpassword123")))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void resetPassword_토큰이_만료됐으면_예외를_던진다() {
        PasswordResetToken expired = new PasswordResetToken("expired-token", user, LocalDateTime.now().minusMinutes(1));
        when(passwordResetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.resetPassword(new AuthDto.ResetPasswordRequest("expired-token", "newpassword123")))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void resetPassword_토큰이_이미_사용됐으면_예외를_던진다() {
        PasswordResetToken used = new PasswordResetToken("used-token", user, LocalDateTime.now().plusMinutes(10));
        used.markUsed();
        when(passwordResetTokenRepository.findByToken("used-token")).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> authService.resetPassword(new AuthDto.ResetPasswordRequest("used-token", "newpassword123")))
                .isInstanceOf(InvalidResetTokenException.class);
    }
}
