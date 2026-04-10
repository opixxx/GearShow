package com.gearshow.backend.user.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileImageDeleteFailedExceptionTest {

    @Test
    @DisplayName("프로필 이미지 삭제 실패 예외는 올바른 ErrorCode로 매핑된다")
    void exception_mapsToCorrectErrorCode() {
        // Given & When
        ProfileImageDeleteFailedException exception = new ProfileImageDeleteFailedException();

        // Then
        assertThat(exception)
                .isInstanceOf(CustomException.class);
        assertThat(exception.getStatus())
                .isEqualTo(ErrorCode.USER_PROFILE_IMAGE_DELETE_FAILED.getStatus());
        assertThat(exception.getMessage())
                .isEqualTo(ErrorCode.USER_PROFILE_IMAGE_DELETE_FAILED.getMessage());
    }
}
