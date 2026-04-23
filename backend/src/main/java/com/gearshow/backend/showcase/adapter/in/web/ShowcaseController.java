package com.gearshow.backend.showcase.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;
import com.gearshow.backend.platform.idempotency.application.dto.ApiIdempotencyAcquireResult;
import com.gearshow.backend.platform.idempotency.application.port.in.AcquireApiIdempotencyUseCase;
import com.gearshow.backend.showcase.adapter.in.web.dto.CreateShowcaseRequest;
import com.gearshow.backend.showcase.adapter.in.web.dto.ShowcaseDetailResponse;
import com.gearshow.backend.showcase.adapter.in.web.dto.UpdateShowcaseRequest;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseDetailResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseListResult;
import com.gearshow.backend.showcase.application.port.in.CreateShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.in.DeleteShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.in.GetShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.in.ListShowcasesUseCase;
import com.gearshow.backend.showcase.application.port.in.UpdateShowcaseUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 쇼케이스 관련 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/showcases")
@RequiredArgsConstructor
@Validated
public class ShowcaseController {

    /** 멱등성 키 응답 캐싱 TTL. Stripe 표준(24h) 준수. */
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final CreateShowcaseUseCase createShowcaseUseCase;
    private final GetShowcaseUseCase getShowcaseUseCase;
    private final ListShowcasesUseCase listShowcasesUseCase;
    private final UpdateShowcaseUseCase updateShowcaseUseCase;
    private final DeleteShowcaseUseCase deleteShowcaseUseCase;
    private final AcquireApiIdempotencyUseCase apiIdempotencyUseCase;
    private final ObjectMapper objectMapper;

    /**
     * 쇼케이스 목록을 조회한다 (최신순).
     */
    @GetMapping
    public ApiResponse<PageInfo<ShowcaseListResult>> list(
            @RequestParam(required = false) String pageToken,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            int size) {

        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list(pageToken, size);

        return ApiResponse.of(200, "쇼케이스 목록 조회 성공", result);
    }

    /**
     * 쇼케이스 상세를 조회한다.
     */
    @GetMapping("/{showcaseId}")
    public ApiResponse<ShowcaseDetailResponse> getDetail(
            @PathVariable Long showcaseId) {

        ShowcaseDetailResult result = getShowcaseUseCase.getShowcase(showcaseId);

        return ApiResponse.of(200, "쇼케이스 조회 성공",
                ShowcaseDetailResponse.from(result));
    }

    /**
     * 쇼케이스를 등록한다.
     * 클라이언트가 Presigned URL로 S3에 이미지를 직접 업로드한 후 S3 키 목록을 전달한다.
     *
     * <p><b>멱등성</b>: {@code Idempotency-Key} 헤더가 제공되면 같은 키로 재도달한 요청은
     * 캐싱된 응답을 반환한다 (ADR-011 ①). 헤더 누락 시 기존 동작을 유지한다 (Phase 1 초기).</p>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> create(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateShowcaseRequest request) {

        Long ownerId = (Long) authentication.getPrincipal();

        if (idempotencyKey != null) {
            ApiIdempotencyAcquireResult acquired =
                    apiIdempotencyUseCase.acquire(idempotencyKey, ownerId, IDEMPOTENCY_TTL);
            if (acquired instanceof ApiIdempotencyAcquireResult.Cached cached) {
                return deserializeCachedResponse(cached.responseBody());
            }
        }

        CreateShowcaseResult result = createShowcaseUseCase.create(
                request.toCommand(ownerId),
                request.imageKeys(),
                request.modelSourceImageKeys() != null ? request.modelSourceImageKeys() : List.of());

        ApiResponse<Map<String, Object>> response = ApiResponse.of(201, "쇼케이스 등록 성공",
                Map.of("showcaseId", result.showcaseId(),
                        "model3dStatus", result.model3dStatus() != null
                                ? result.model3dStatus().name() : "null"));

        if (idempotencyKey != null) {
            apiIdempotencyUseCase.markDone(
                    idempotencyKey, 201, serializeResponse(response));
        }
        return response;
    }

    /**
     * 쇼케이스를 수정한다.
     */
    @PatchMapping("/{showcaseId}")
    public ApiResponse<Map<String, Long>> update(
            Authentication authentication,
            @PathVariable Long showcaseId,
            @Valid @RequestBody UpdateShowcaseRequest request) {

        Long ownerId = (Long) authentication.getPrincipal();
        updateShowcaseUseCase.update(showcaseId, ownerId, request.toCommand());

        return ApiResponse.of(200, "쇼케이스 수정 성공",
                Map.of("showcaseId", showcaseId));
    }

    /**
     * 쇼케이스를 삭제한다 (소프트 삭제).
     */
    @DeleteMapping("/{showcaseId}")
    public ApiResponse<Void> delete(
            Authentication authentication,
            @PathVariable Long showcaseId) {

        Long ownerId = (Long) authentication.getPrincipal();
        deleteShowcaseUseCase.delete(showcaseId, ownerId);

        return ApiResponse.of(200, "쇼케이스 삭제 성공");
    }

    private String serializeResponse(ApiResponse<Map<String, Object>> response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.IDEMPOTENCY_RESPONSE_SERIALIZATION_FAILED, e);
        }
    }

    private ApiResponse<Map<String, Object>> deserializeCachedResponse(String body) {
        try {
            return objectMapper.readValue(body,
                    new TypeReference<ApiResponse<Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.IDEMPOTENCY_RESPONSE_SERIALIZATION_FAILED, e);
        }
    }
}
