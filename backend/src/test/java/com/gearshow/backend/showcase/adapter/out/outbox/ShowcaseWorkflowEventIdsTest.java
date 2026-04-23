package com.gearshow.backend.showcase.adapter.out.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ShowcaseWorkflowEventIds} 단위 테스트.
 *
 * <p>ADR-011 ③: {@code event_id = SHA-256(idempotencyKey)} 결정적 파생 불변식 검증.
 * 패키지-프라이빗 유틸을 리플렉션으로 호출한다.</p>
 */
@DisplayName("ShowcaseWorkflowEventIds")
class ShowcaseWorkflowEventIdsTest {

    private static final Method DERIVE;

    static {
        try {
            DERIVE = ShowcaseWorkflowEventIds.class.getDeclaredMethod(
                    "deriveMessageId", String.class);
            DERIVE.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static String derive(String key) throws Exception {
        return (String) DERIVE.invoke(null, key);
    }

    @Nested
    @DisplayName("결정성")
    class Determinism {

        @Test
        @DisplayName("같은 입력은 항상 같은 해시를 생성한다")
        void sameInput_sameOutput() throws Exception {
            String key = "ab12-3456-test-key";
            assertThat(derive(key)).isEqualTo(derive(key));
        }

        @Test
        @DisplayName("다른 입력은 다른 해시를 생성한다")
        void differentInput_differentOutput() throws Exception {
            assertThat(derive("key-1")).isNotEqualTo(derive("key-2"));
        }

        @Test
        @DisplayName("RFC 4634 벡터: 빈 문자열 → 알려진 SHA-256 해시")
        void knownVector_asciiInput() throws Exception {
            // "abc" 의 SHA-256 hex lowercase
            String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
            assertThat(derive("abc")).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("출력 형식")
    class OutputFormat {

        @Test
        @DisplayName("출력은 64자 소문자 hex 문자열이다 (outbox.event_id VARCHAR(64) 와 일치)")
        void output_isLowerHex64Chars() throws Exception {
            String hash = derive("some-key");
            assertThat(hash).hasSize(64);
            assertThat(hash).matches("[0-9a-f]{64}");
        }
    }

    @Nested
    @DisplayName("입력 검증")
    class InputValidation {

        @Test
        @DisplayName("null 입력은 IllegalArgumentException 을 던진다")
        void nullInput_throws() {
            assertThatThrownBy(() -> derive(null))
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("빈 문자열은 IllegalArgumentException 을 던진다")
        void blankInput_throws() {
            assertThatThrownBy(() -> derive("   "))
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }
}
