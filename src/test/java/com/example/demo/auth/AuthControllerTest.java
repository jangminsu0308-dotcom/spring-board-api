package com.example.demo.auth;

import com.example.demo.security.JwtAuthenticationEntryPoint;
import com.example.demo.security.JwtTokenProvider;
import com.example.demo.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtTokenProvider.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @Test
    void 회원가입_성공시_201을_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthDto.RegisterRequest("writer", "password123"))))
                .andExpect(status().isCreated());
    }

    @Test
    void 회원가입시_아이디가_중복되면_409를_반환한다() throws Exception {
        doThrow(new DuplicateUsernameException("writer")).when(authService).register(any(AuthDto.RegisterRequest.class));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthDto.RegisterRequest("writer", "password123"))))
                .andExpect(status().isConflict());
    }

    @Test
    void 회원가입시_비밀번호가_너무_짧으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthDto.RegisterRequest("writer", "short"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 로그인_성공시_토큰_쌍을_반환한다() throws Exception {
        when(authService.login(any(AuthDto.LoginRequest.class)))
                .thenReturn(new AuthDto.TokenResponse("access-token", "refresh-token", "writer"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthDto.LoginRequest("writer", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.username").value("writer"));
    }

    @Test
    void 로그인_실패시_401을_반환한다() throws Exception {
        when(authService.login(any(AuthDto.LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthDto.LoginRequest("writer", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰_재발급_성공시_새_토큰_쌍을_반환한다() throws Exception {
        when(authService.refresh(any(AuthDto.RefreshRequest.class)))
                .thenReturn(new AuthDto.TokenResponse("new-access-token", "new-refresh-token", "writer"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthDto.RefreshRequest("refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    @Test
    void 토큰_재발급시_유효하지_않으면_401을_반환한다() throws Exception {
        when(authService.refresh(any(AuthDto.RefreshRequest.class)))
                .thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthDto.RefreshRequest("invalid"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그아웃시_204를_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthDto.LogoutRequest("refresh-token"))))
                .andExpect(status().isNoContent());

        verify(authService).logout(any(AuthDto.LogoutRequest.class));
    }
}
