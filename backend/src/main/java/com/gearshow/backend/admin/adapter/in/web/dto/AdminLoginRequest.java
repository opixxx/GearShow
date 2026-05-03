package com.gearshow.backend.admin.adapter.in.web.dto;

import com.gearshow.backend.admin.application.dto.LoginAdminCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 관리자 로그인 요청 DTO.
 */
public record AdminLoginRequest(
        @NotBlank(message = "email은 필수입니다")
        @Email(message = "유효한 이메일 형식이 아닙니다")
        String email,

        @NotBlank(message = "password는 필수입니다")
        String password
) {

    public LoginAdminCommand toCommand() {
        return new LoginAdminCommand(email, password);
    }
}
