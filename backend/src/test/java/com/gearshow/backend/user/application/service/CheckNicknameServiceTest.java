package com.gearshow.backend.user.application.service;

import com.gearshow.backend.user.application.port.out.UserPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 닉네임 중복 확인 서비스 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class CheckNicknameServiceTest {

    @InjectMocks
    private CheckNicknameService checkNicknameService;

    @Mock
    private UserPort userPort;

    @Test
    @DisplayName("존재하지 않는 닉네임이면 사용 가능하다")
    void isAvailable_nicknameNotExists_returnsTrue() {
        // Given
        given(userPort.existsByNickname("새닉네임")).willReturn(false);

        // When
        boolean result = checkNicknameService.isAvailable("새닉네임");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("이미 존재하는 닉네임이면 사용 불가하다")
    void isAvailable_nicknameExists_returnsFalse() {
        // Given
        given(userPort.existsByNickname("중복닉")).willReturn(true);

        // When
        boolean result = checkNicknameService.isAvailable("중복닉");

        // Then
        assertThat(result).isFalse();
    }
}
