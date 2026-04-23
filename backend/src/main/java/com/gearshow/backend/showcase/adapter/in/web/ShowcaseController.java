package com.gearshow.backend.showcase.adapter.in.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.platform.idempotency.adapter.out.serialization.ApiResponseCodec;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 쇼케이스 관련 API 컨트롤러.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/showcases")
@RequiredArgsConstructor
@Validated
public class ShowcaseController {

    /** 멱등성 키 응답 캐싱 TTL. Stripe 표준(24h) 준수. */
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    /** 캐싱된 응답 역직렬화용 TypeReference (싱글턴 재사용). */
    private static final TypeReference<ApiResponse<Map<String, Object>>> CACHED_RESPONSE_TYPE =
            new TypeReference<>() {};

    private final CreateShowcaseUseCase createShowcaseUseCase;
    private final GetShowcaseUseCase getShowcaseUseCase;
    private final ListShowcasesUseCase listShowcasesUseCase;
    private final UpdateShowcaseUseCase updateShowcaseUseCase;
    private final DeleteShowcaseUseCase deleteShowcaseUseCase;
    private final AcquireApiIdempotencyUseCase apiIdempotencyUseCase;
    private final ApiResponseCodec apiResponseCodec;

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
     * <p><b>멱등성</b>: {@code Idempotency-Key} 헤더는 필수다 (ADR-011 ①). 같은 키로 재도달한
     * 요청은 캐싱된 응답을 그대로 반환하고, 비즈니스 로직 실패 시에는 보상 삭제로 동일 키
     * 재시도를 허용한다. 누락 시 400 {@code MISSING_REQUIRED_HEADER} 를 반환한다.</p>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateShowcaseRequest request) {

        Long ownerId = (Long) authentication.getPrincipal();

        ApiIdempotencyAcquireResult acquired =
                apiIdempotencyUseCase.acquire(idempotencyKey, ownerId, IDEMPOTENCY_TTL);
        if (acquired instanceof ApiIdempotencyAcquireResult.Cached cached) {
            return apiResponseCodec.decode(cached.responseBody(), CACHED_RESPONSE_TYPE);
        }

        ApiResponse<Map<String, Object>> response;
        try {
            CreateShowcaseResult result = createShowcaseUseCase.create(
                    request.toCommand(ownerId, idempotencyKey),
                    request.imageKeys(),
                    request.modelSourceImageKeys() != null ? request.modelSourceImageKeys() : List.of());

            // model3dStatus 는 3D 소스 이미지 없이 등록되거나 Deduped 경로에서 null 일 수 있으므로
            // HashMap 으로 JSON null 직렬화를 보장한다 (Map.of 는 null 값을 허용하지 않음).
            Map<String, Object> body = new HashMap<>();
            body.put("showcaseId", result.showcaseId());
            body.put("model3dStatus",
                    result.model3dStatus() != null ? result.model3dStatus().name() : null);
            response = ApiResponse.of(201, "쇼케이스 등록 성공", body);
        } catch (RuntimeException e) {
            // 비즈니스 실패 시 IN_PROGRESS 좀비 방지 — 보상 삭제로 동일 키 재시도 허용
            discardSilently(idempotencyKey);
            throw e;
        }

        cacheResponseSilently(idempotencyKey, response);
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

    /**
     * 응답 캐싱은 best-effort. 실패해도 비즈니스 응답은 그대로 반환한다.
     * (직렬화 or markDone 실패가 클라이언트에 500 으로 전파되지 않도록 방어)
     */
    private void cacheResponseSilently(String idempotencyKey, ApiResponse<Map<String, Object>> response) {
        try {
            String serialized = apiResponseCodec.encode(response);
            apiIdempotencyUseCase.markDone(idempotencyKey, 201, serialized);
        } catch (RuntimeException e) {
            log.warn("멱등성 응답 캐싱 실패 — 비즈니스 응답은 그대로 반환. key={}", idempotencyKey, e);
        }
    }

    /**
     * 보상 삭제도 best-effort. 실패해도 원래 예외를 우선 전파한다.
     */
    private void discardSilently(String idempotencyKey) {
        try {
            apiIdempotencyUseCase.discardOnFailure(idempotencyKey);
        } catch (RuntimeException e) {
            log.warn("멱등성 키 보상 삭제 실패 — 수동 정리 또는 TTL 대기 필요. key={}", idempotencyKey, e);
        }
    }
}
