package com.gearshow.backend.user.adapter.in.web;

import com.gearshow.backend.user.application.dto.LoginResult;
import com.gearshow.backend.user.application.port.in.LoginUseCase;
import com.gearshow.backend.user.application.port.in.LogoutUseCase;
import com.gearshow.backend.user.application.port.in.RefreshTokenUseCase;
import com.gearshow.backend.user.infrastructure.config.SecurityConfig;
import com.gearshow.backend.user.infrastructure.security.JwtAuthenticationFilter;
import com.gearshow.backend.user.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 통합 테스트.
 * SecurityConfig를 import하여 실제 보안 설정(공개/보호 엔드포인트)을 검증한다.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginUseCase loginUseCase;

    @MockitoBean
    private LogoutUseCase logoutUseCase;

    @MockitoBean
    private RefreshTokenUseCase refreshTokenUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Nested
    @DisplayName("POST /api/v1/auth/login/{provider}")
    class Login {

        @Test
        @DisplayName("유효한 인가 코드로 로그인하면 200과 토큰을 반환한다")
        void login_withValidCode_returnsToken() throws Exception {
            // Given
            LoginResult result = new LoginResult("access-token", "refresh-token", "Bearer", 3600);
            given(loginUseCase.login(any())).willReturn(result);

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login/kakao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"authorizationCode\":\"valid-code\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
        }

        @Test
        @DisplayName("인가 코드가 비어있으면 400을 반환한다")
        void login_withEmptyCode_returns400() throws Exception {
            // Given & When & Then
            mockMvc.perform(post("/api/v1/auth/login/kakao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"authorizationCode\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("인가 코드 필드가 없으면 400을 반환한다")
        void login_withoutCode_returns400() throws Exception {
            // Given & When & Then
            mockMvc.perform(post("/api/v1/auth/login/kakao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class Refresh {

        @Test
        @DisplayName("유효한 Refresh Token으로 갱신하면 200과 새 토큰을 반환한다")
        void refresh_withValidToken_returnsNewToken() throws Exception {
            // Given
            LoginResult result = new LoginResult("new-access", "new-refresh", "Bearer", 3600);
            given(refreshTokenUseCase.refresh(any())).willReturn(result);

            // When & Then
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"valid-refresh-token\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("new-access"));
        }

        @Test
        @DisplayName("Refresh Token이 비어있으면 400을 반환한다")
        void refresh_withEmptyToken_returns400() throws Exception {
            // Given & When & Then
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class Logout {

        @Test
        @DisplayName("인증된 사용자가 로그아웃하면 200을 반환한다")
        void logout_withAuth_returns200() throws Exception {
            // Given
            given(jwtTokenProvider.validateToken(any())).willReturn(true);
            given(jwtTokenProvider.getUserId(any())).willReturn(1L);
            willDoNothing().given(logoutUseCase).logout(1L);

            // When & Then
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer valid-access-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("로그아웃 성공"));
        }

        @Test
        @DisplayName("인증 없이 로그아웃하면 401을 반환한다")
        void logout_withoutAuth_returns401() throws Exception {
            // Given & When & Then
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
