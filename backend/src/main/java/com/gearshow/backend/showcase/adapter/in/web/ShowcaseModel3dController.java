package com.gearshow.backend.showcase.adapter.in.web;

import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.showcase.adapter.in.web.dto.RequestModelGenerationRequest;
import com.gearshow.backend.showcase.application.dto.Model3dDetailResult;
import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.port.in.GetModel3dUseCase;
import com.gearshow.backend.showcase.application.port.in.RequestModelGenerationUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 쇼케이스 3D 모델 관련 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/showcases/{showcaseId}/3d-model")
@RequiredArgsConstructor
public class ShowcaseModel3dController {

    private final RequestModelGenerationUseCase requestModelGenerationUseCase;
    private final GetModel3dUseCase getModel3dUseCase;

    /**
     * 3D 모델 생성을 재요청한다.
     * 클라이언트가 Presigned URL로 S3에 소스 이미지를 직접 업로드한 후 S3 키 목록을 전달한다.
     *
     * <p><b>멱등성</b>: {@code Idempotency-Key} 헤더는 필수다 (ADR-011 ①).
     * 재시도마다 클라이언트가 새 UUID 를 생성해 전송해야 하며, 서버는 이 값으로
     * 워크플로우 UNIQUE 식별자와 Outbox {@code event_id} 를 파생한다.</p>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Map<String, Object>> requestGeneration(
            Authentication authentication,
            @PathVariable Long showcaseId,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody RequestModelGenerationRequest request) {

        Long ownerId = (Long) authentication.getPrincipal();

        ModelGenerationResult result = requestModelGenerationUseCase.requestRetry(
                showcaseId, ownerId, idempotencyKey, request.modelSourceImageKeys());

        return ApiResponse.of(202, "3D 모델 생성 요청 완료",
                Map.of("workflowId", result.workflowId(),
                        "modelStatus", result.modelStatus().name()));
    }

    /**
     * 3D 모델 상태를 조회한다.
     */
    @GetMapping
    public ApiResponse<Model3dDetailResult> getModel3d(
            @PathVariable Long showcaseId) {

        Model3dDetailResult result = getModel3dUseCase.getModel3d(showcaseId);

        return ApiResponse.of(200, "3D 모델 조회 성공", result);
    }
}
