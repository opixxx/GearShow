package com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TripoTaskStatusResponseTest {

    @Nested
    @DisplayName("isTerminal")
    class IsTerminal {

        @ParameterizedTest
        @ValueSource(strings = {"SUCCESS", "FAILED", "CANCELLED", "BANNED", "EXPIRED",
                "success", "failed", "cancelled", "banned", "expired"})
        @DisplayName("종료 상태이면 true를 반환한다")
        void isTerminal_terminalStatus_returnsTrue(String status) {
            // Given
            TripoTaskStatusResponse response = createResponse(status);

            // When & Then
            assertThat(response.isTerminal()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"RUNNING", "QUEUED", "PENDING"})
        @DisplayName("진행 중 상태이면 false를 반환한다")
        void isTerminal_runningStatus_returnsFalse(String status) {
            // Given
            TripoTaskStatusResponse response = createResponse(status);

            // When & Then
            assertThat(response.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("data가 null이면 false를 반환한다")
        void isTerminal_nullData_returnsFalse() {
            // Given
            TripoTaskStatusResponse response = new TripoTaskStatusResponse(0, null);

            // When & Then
            assertThat(response.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("status가 null이면 false를 반환한다")
        void isTerminal_nullStatus_returnsFalse() {
            // Given
            TripoTaskStatusResponse response = createResponse(null);

            // When & Then
            assertThat(response.isTerminal()).isFalse();
        }
    }

    @Nested
    @DisplayName("isSuccess")
    class IsSuccess {

        @Test
        @DisplayName("SUCCESS 상태이면 true를 반환한다")
        void isSuccess_successStatus_returnsTrue() {
            // Given
            TripoTaskStatusResponse response = createResponse("success");

            // When & Then
            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("FAILED 상태이면 false를 반환한다")
        void isSuccess_failedStatus_returnsFalse() {
            // Given
            TripoTaskStatusResponse response = createResponse("FAILED");

            // When & Then
            assertThat(response.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("data가 null이면 false를 반환한다")
        void isSuccess_nullData_returnsFalse() {
            // Given
            TripoTaskStatusResponse response = new TripoTaskStatusResponse(0, null);

            // When & Then
            assertThat(response.isSuccess()).isFalse();
        }
    }

    private TripoTaskStatusResponse createResponse(String status) {
        var data = new TripoTaskStatusResponse.TripoTaskStatusData(
                "task-123", "multiview_to_model", status, 100, null);
        return new TripoTaskStatusResponse(0, data);
    }
}
