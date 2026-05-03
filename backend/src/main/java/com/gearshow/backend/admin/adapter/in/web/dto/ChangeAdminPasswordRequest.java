package com.gearshow.backend.admin.adapter.in.web.dto;

import com.gearshow.backend.admin.application.dto.ChangeAdminPasswordCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 관리자 비밀번호 변경 요청 DTO.
 *
 * <p>{@link #toString()} 은 password 들을 마스킹한다 — 우발적 로그 직렬화 시 평문 유출 차단.</p>
 */
public record ChangeAdminPasswordRequest(
        @NotBlank(message = "현재 비밀번호는 필수입니다")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수입니다")
        @Size(min = 8, max = 100, message = "새 비밀번호는 8자 이상 100자 이하여야 합니다")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "새 비밀번호는 영문과 숫자를 모두 포함해야 합니다")
        String newPassword
) {

    public ChangeAdminPasswordCommand toCommand(Long adminId) {
        return new ChangeAdminPasswordCommand(adminId, currentPassword, newPassword);
    }

    @Override
    @SuppressWarnings("java:S2068") // password=*** 는 credential 이 아니라 마스크 표시 문자열
    public String toString() {
        return "ChangeAdminPasswordRequest[currentPassword=***, newPassword=***]";
    }
}
