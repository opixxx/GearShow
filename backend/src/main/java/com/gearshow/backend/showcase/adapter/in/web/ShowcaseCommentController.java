package com.gearshow.backend.showcase.adapter.in.web;

import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.showcase.adapter.in.web.dto.CommentIdResponse;
import com.gearshow.backend.showcase.adapter.in.web.dto.CreateCommentRequest;
import com.gearshow.backend.showcase.adapter.in.web.dto.UpdateCommentRequest;
import com.gearshow.backend.showcase.application.dto.CommentResult;
import com.gearshow.backend.showcase.application.port.in.CreateCommentUseCase;
import com.gearshow.backend.showcase.application.port.in.DeleteCommentUseCase;
import com.gearshow.backend.showcase.application.port.in.ListCommentsUseCase;
import com.gearshow.backend.showcase.application.port.in.UpdateCommentUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 쇼케이스 댓글 관련 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/showcases/{showcaseId}/comments")
@RequiredArgsConstructor
@Validated
public class ShowcaseCommentController {

    private final CreateCommentUseCase createCommentUseCase;
    private final ListCommentsUseCase listCommentsUseCase;
    private final UpdateCommentUseCase updateCommentUseCase;
    private final DeleteCommentUseCase deleteCommentUseCase;

    /**
     * 댓글 목록을 조회한다.
     */
    @GetMapping
    public ApiResponse<PageInfo<CommentResult>> list(
            @PathVariable Long showcaseId,
            @RequestParam(required = false) String pageToken,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            int size) {

        PageInfo<CommentResult> result = listCommentsUseCase.list(showcaseId, pageToken, size);

        return ApiResponse.of(200, "댓글 목록 조회 성공", result);
    }

    /**
     * 댓글을 작성한다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentIdResponse> create(
            Authentication authentication,
            @PathVariable Long showcaseId,
            @Valid @RequestBody CreateCommentRequest request) {

        Long authorId = (Long) authentication.getPrincipal();
        Long commentId = createCommentUseCase.create(showcaseId, authorId, request.content());

        return ApiResponse.of(201, "댓글 작성 성공", new CommentIdResponse(commentId));
    }

    /**
     * 댓글을 수정한다.
     */
    @PatchMapping("/{commentId}")
    public ApiResponse<CommentIdResponse> update(
            Authentication authentication,
            @PathVariable Long showcaseId,
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request) {

        Long authorId = (Long) authentication.getPrincipal();
        updateCommentUseCase.update(showcaseId, commentId, authorId, request.content());

        return ApiResponse.of(200, "댓글 수정 성공", new CommentIdResponse(commentId));
    }

    /**
     * 댓글을 삭제한다.
     */
    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> delete(
            Authentication authentication,
            @PathVariable Long showcaseId,
            @PathVariable Long commentId) {

        Long authorId = (Long) authentication.getPrincipal();
        deleteCommentUseCase.delete(showcaseId, commentId, authorId);

        return ApiResponse.of(200, "댓글 삭제 성공");
    }
}
