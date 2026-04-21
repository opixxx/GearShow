package com.gearshow.backend.user.application.service;

import com.gearshow.backend.user.application.dto.UserProfileResult;
import com.gearshow.backend.user.application.port.out.UserPort;
import com.gearshow.backend.user.domain.model.User;
import com.gearshow.backend.user.domain.vo.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GetUserProfilesServiceTest {

    @InjectMocks
    private GetUserProfilesService service;

    @Mock
    private UserPort userPort;

    private User user(Long id, String nickname, String profileImageUrl) {
        Instant fixed = Instant.parse("2026-04-22T00:00:00Z");
        return User.builder()
                .id(id)
                .nickname(nickname)
                .profileImageUrl(profileImageUrl)
                .phoneVerified(false)
                .status(UserStatus.ACTIVE)
                .createdAt(fixed)
                .updatedAt(fixed)
                .build();
    }

    @Test
    @DisplayName("빈 컬렉션은 단락 — 포트를 호출하지 않고 빈 리스트 반환")
    void getProfiles_emptyIds_shortCircuits() {
        // when
        List<UserProfileResult> result = service.getProfiles(List.of());

        // then
        assertThat(result).isEmpty();
        verify(userPort, never()).findAllByIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("null 컬렉션도 안전하게 빈 리스트를 반환한다")
    void getProfiles_nullIds_returnsEmpty() {
        // when
        List<UserProfileResult> result = service.getProfiles(null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("복수 ID 조회를 단일 findAllByIds 호출로 위임하고 UserProfileResult 로 매핑한다")
    void getProfiles_delegatesToBatchPortAndMaps() {
        // given
        List<Long> ids = List.of(1L, 2L);
        given(userPort.findAllByIds(ids)).willReturn(List.of(
                user(1L, "alice", "img/a.jpg"),
                user(2L, "bob", null)));

        // when
        List<UserProfileResult> result = service.getProfiles(ids);

        // then: 포트는 정확히 1회만 호출
        verify(userPort).findAllByIds(ids);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserProfileResult::userId)
                .containsExactlyInAnyOrder(1L, 2L);

        UserProfileResult alice = result.stream()
                .filter(r -> r.userId().equals(1L)).findFirst().orElseThrow();
        assertThat(alice.nickname()).isEqualTo("alice");
        assertThat(alice.profileImageUrl()).isEqualTo("img/a.jpg");

        UserProfileResult bob = result.stream()
                .filter(r -> r.userId().equals(2L)).findFirst().orElseThrow();
        assertThat(bob.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 ID 는 포트 결과에서 누락되고 서비스 결과도 그만큼 짧다")
    void getProfiles_missingIds_areOmitted() {
        // given
        given(userPort.findAllByIds(List.of(1L, 999L))).willReturn(List.of(
                user(1L, "alice", null)));

        // when
        List<UserProfileResult> result = service.getProfiles(List.of(1L, 999L));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(1L);
    }
}
