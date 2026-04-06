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
     */
<<<<<<< HEAD
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Map<String, Object>> requestGeneration(
=======
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> requestGeneration(
>>>>>>> 7205c15 (feat: S3 Presigned URL 업로드로 전환)
            Authentication authentication,
            @PathVariable Long showcaseId,
            @Valid @RequestBody RequestModelGenerationRequest request) {

        Long ownerId = (Long) authentication.getPrincipal();

        ModelGenerationResult result = requestModelGenerationUseCase.requestRetry(
                showcaseId, ownerId, request.modelSourceImageKeys());

        return ApiResponse.of(202, "3D 모델 생성 요청 완료",
                Map.of("showcase3dModelId", result.showcase3dModelId(),
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
