package com.gearshow.backend.showcase.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ContentHash} VO 검증 테스트.
 */
class ContentHashTest {

    @Nested
    @DisplayName("유효한 값")
    class Valid {

        @Test
        @DisplayName("소문자 SHA-256 hex 64자를 허용한다")
        void accepts_valid_sha256_hex() {
            // given: SHA-256 hex 64자
            String hex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

            // when
            ContentHash hash = ContentHash.of(hex);

            // then
            assertThat(hash.value()).isEqualTo(hex);
        }
    }

    @Nested
    @DisplayName("유효하지 않은 값")
    class Invalid {

        @Test
        @DisplayName("null 을 거부한다")
        void rejects_null() {
            assertThatThrownBy(() -> ContentHash.of(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("63자 문자열을 거부한다")
        void rejects_too_short() {
            String tooShort = "a".repeat(63);

            assertThatThrownBy(() -> ContentHash.of(tooShort))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("64자");
        }

        @Test
        @DisplayName("65자 문자열을 거부한다")
        void rejects_too_long() {
            String tooLong = "a".repeat(65);

            assertThatThrownBy(() -> ContentHash.of(tooLong))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("64자");
        }

        @Test
        @DisplayName("대문자를 포함하면 거부한다")
        void rejects_uppercase() {
            String upper = "A".repeat(64);

            assertThatThrownBy(() -> ContentHash.of(upper))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("비-hex 문자를 거부한다")
        void rejects_non_hex_chars() {
            String withG = "g".repeat(64);

            assertThatThrownBy(() -> ContentHash.of(withG))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
