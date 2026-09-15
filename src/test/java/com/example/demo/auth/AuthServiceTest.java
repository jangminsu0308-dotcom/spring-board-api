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
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("writer", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void register_아이디가_중복되지_않으면_비밀번호를_암호화해_저장한다() {
        when(userRepository.existsByUsername("writer")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

        authService.register(new AuthDto.RegisterRequest("writer", "password123"));

        verify(userRepository).save(argThat(u ->
                u.getUsername().equals("writer") && u.getPassword().equals("encoded-password")));
    }

    @Test
    void register_아이디가_중복되면_예외를_던지고_저장하지_않는다() {
        when(userRepository.existsByUsername("writer")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new AuthDto.RegisterRequest("writer", "password123")))
                .isInstanceOf(DuplicateUsernameException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_아이디와_비밀번호가_일치하면_토큰을_발급한다() {
        when(userRepository.findByUsername("writer")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.createToken("writer")).thenReturn("jwt-token");

        AuthDto.TokenResponse response = authService.login(new AuthDto.LoginRequest("writer", "password123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("writer");
    }

    @Test
    void login_존재하지_않는_아이디면_예외를_던진다() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new AuthDto.LoginRequest("unknown", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_비밀번호가_틀리면_예외를_던진다() {
        when(userRepository.findByUsername("writer")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new AuthDto.LoginRequest("writer", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
