package com.gearshow.backend.user.application.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateProfileCommandTest {

    @Nested
    @DisplayName("hasImage")
    class HasImage {

        @Test
        @DisplayName("이미지 바이트가 존재하면 true를 반환한다")
        void hasImage_withContent_returnsTrue() {
            // Given
            UpdateProfileCommand command = new UpdateProfileCommand(
                    "닉네임", new byte[]{1, 2, 3}, "image/jpeg", "photo.jpg");

            // When
            boolean result = command.hasImage();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("이미지가 null이면 false를 반환한다")
        void hasImage_nullContent_returnsFalse() {
            // Given
            UpdateProfileCommand command = new UpdateProfileCommand(
                    "닉네임", null, null, null);

            // When
            boolean result = command.hasImage();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("이미지 바이트가 빈 배열이면 false를 반환한다")
        void hasImage_emptyContent_returnsFalse() {
            // Given
            UpdateProfileCommand command = new UpdateProfileCommand(
                    "닉네임", new byte[]{}, "image/jpeg", "photo.jpg");

            // When
            boolean result = command.hasImage();

            // Then
            assertThat(result).isFalse();
        }
    }
}
