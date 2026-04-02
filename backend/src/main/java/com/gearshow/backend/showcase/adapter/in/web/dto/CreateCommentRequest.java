package com.gearshow.backend.showcase.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 댓글 작성 요청 DTO.
 */
public record CreateCommentRequest(
        @NotBlank(message = "댓글 내용은 필수입니다")
        @Size(max = 500, message = "댓글은 500자 이내여야 합니다")
        String content
) {}
