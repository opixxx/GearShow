package com.gearshow.backend.user.adapter.in.web;

import com.gearshow.backend.user.application.dto.MyProfileResult;
import com.gearshow.backend.user.application.dto.UserProfileResult;
import com.gearshow.backend.user.application.port.in.*;
import com.gearshow.backend.user.domain.vo.UserStatus;
import com.gearshow.backend.user.infrastructure.config.SecurityConfig;
import com.gearshow.backend.user.infrastructure.security.JwtAuthenticationFilter;
import com.gearshow.backend.user.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController 통합 테스트.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckNicknameUseCase checkNicknameUseCase;

    @MockitoBean
    private GetMyProfileUseCase getMyProfileUseCase;

    @MockitoBean
    private GetUserProfileUseCase getUserProfileUseCase;

    @MockitoBean
    private UpdateProfileUseCase updateProfileUseCase;

    @MockitoBean
    private WithdrawUseCase withdrawUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Nested
    @DisplayName("닉네임 중복 확인")
    class CheckNickname {

        @Test
        @DisplayName("사용 가능한 닉네임이면 available=true를 반환한다")
        void checkNickname_available_returnsTrue() throws Exception {
            // Given
            given(checkNicknameUseCase.isAvailable("테스트닉")).willReturn(true);

            // When & Then
            mockMvc.perform(get("/api/v1/users/nicknames/check")
                            .param("nickname", "테스트닉"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value("테스트닉"))
                    .andExpect(jsonPath("$.data.available").value(true));
        }

        @Test
        @DisplayName("이미 사용 중인 닉네임이면 available=false를 반환한다")
        void checkNickname_duplicate_returnsFalse() throws Exception {
            // Given
            given(checkNicknameUseCase.isAvailable("중복닉")).willReturn(false);

            // When & Then
            mockMvc.perform(get("/api/v1/users/nicknames/check")
                            .param("nickname", "중복닉"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.available").value(false));
        }
    }

    @Nested
    @DisplayName("사용자 프로필 조회")
    class GetProfile {

        @Test
        @DisplayName("공개 프로필을 조회한다")
        void getUserProfile_success() throws Exception {
            // Given
            given(getUserProfileUseCase.getUserProfile(1L))
                    .willReturn(new UserProfileResult(1L, "축구매니아", "https://img.com/1.jpg"));

            // When & Then
            mockMvc.perform(get("/api/v1/users/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value("축구매니아"));
        }

        @Test
        @DisplayName("인증된 사용자가 내 프로필을 조회한다")
        void getMyProfile_authenticated_success() throws Exception {
            // Given
            given(jwtTokenProvider.validateToken("valid-token")).willReturn(true);
            given(jwtTokenProvider.getUserId("valid-token")).willReturn(1L);
            given(getMyProfileUseCase.getMyProfile(1L))
                    .willReturn(new MyProfileResult(1L, "축구매니아", null, null, false,
                            UserStatus.ACTIVE, Instant.now()));

            // When & Then
            mockMvc.perform(get("/api/v1/users/me")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value("축구매니아"));
        }

        @Test
        @DisplayName("인증 없이 내 프로필 조회하면 401을 반환한다")
        void getMyProfile_unauthenticated_returns401() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("회원 탈퇴")
    class Withdraw {

        @Test
        @DisplayName("인증된 사용자가 탈퇴한다")
        void withdraw_authenticated_success() throws Exception {
            // Given
            given(jwtTokenProvider.validateToken("valid-token")).willReturn(true);
            given(jwtTokenProvider.getUserId("valid-token")).willReturn(1L);
            willDoNothing().given(withdrawUseCase).withdraw(1L);

            // When & Then
            mockMvc.perform(delete("/api/v1/users/me")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk());
        }
    }
}
