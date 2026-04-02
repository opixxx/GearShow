package com.gearshow.backend.showcase.adapter.in.web;

import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.showcase.adapter.in.web.dto.UploadFileMapper;
import com.gearshow.backend.showcase.application.dto.Model3dDetailResult;
import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.port.in.GetModel3dUseCase;
import com.gearshow.backend.showcase.application.port.in.RequestModelGenerationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> requestGeneration(
            Authentication authentication,
            @PathVariable Long showcaseId,
            @RequestParam("modelSourceImages") List<MultipartFile> modelSourceImages) {

        Long ownerId = (Long) authentication.getPrincipal();

        ModelGenerationResult result = requestModelGenerationUseCase.requestRetry(
                showcaseId, ownerId, UploadFileMapper.toUploadFiles(modelSourceImages));

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.of(202, "3D 모델 생성 요청 완료",
                        Map.of("showcase3dModelId", result.showcase3dModelId(),
                                "modelStatus", result.modelStatus().name())));
    }

    /**
     * 3D 모델 상태를 조회한다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Model3dDetailResult>> getModel3d(
            @PathVariable Long showcaseId) {

        Model3dDetailResult result = getModel3dUseCase.getModel3d(showcaseId);

        return ResponseEntity.ok(
                ApiResponse.of(200, "3D 모델 조회 성공", result));
    }
}
