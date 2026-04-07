package com.gearshow.backend.showcase.adapter.in.web;

import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.showcase.adapter.in.web.dto.CreateShowcaseRequest;
import com.gearshow.backend.showcase.adapter.in.web.dto.CreateShowcaseResponse;
import com.gearshow.backend.showcase.adapter.in.web.dto.ShowcaseDetailResponse;
import com.gearshow.backend.showcase.adapter.in.web.dto.ShowcaseIdResponse;
import com.gearshow.backend.showcase.adapter.in.web.dto.UpdateShowcaseRequest;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseDetailResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseListResult;
import com.gearshow.backend.showcase.application.port.in.CreateShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.in.DeleteShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.in.GetShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.in.ListShowcasesUseCase;
import com.gearshow.backend.showcase.application.port.in.UpdateShowcaseUseCase;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 쇼케이스 관련 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/showcases")
@RequiredArgsConstructor
@Validated
public class ShowcaseController {

    private final CreateShowcaseUseCase createShowcaseUseCase;
    private final GetShowcaseUseCase getShowcaseUseCase;
    private final ListShowcasesUseCase listShowcasesUseCase;
    private final UpdateShowcaseUseCase updateShowcaseUseCase;
    private final DeleteShowcaseUseCase deleteShowcaseUseCase;

    /**
     * 쇼케이스 목록을 조회한다.
     */
    @GetMapping
    public ApiResponse<PageInfo<ShowcaseListResult>> list(
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isForSale,
            @RequestParam(required = false) ConditionGrade conditionGrade,
            @RequestParam(required = false) String pageToken,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            int size) {

        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.list(
                pageToken, size, category, brand, keyword, isForSale, conditionGrade);

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
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateShowcaseResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateShowcaseRequest request) {

        Long ownerId = (Long) authentication.getPrincipal();

        CreateShowcaseResult result = createShowcaseUseCase.create(
                request.toCommand(ownerId),
                request.imageKeys(),
                request.modelSourceImageKeys() != null ? request.modelSourceImageKeys() : List.of());

        return ApiResponse.of(201, "쇼케이스 등록 성공", CreateShowcaseResponse.from(result));
    }

    /**
     * 쇼케이스를 수정한다.
     */
    @PatchMapping("/{showcaseId}")
    public ApiResponse<ShowcaseIdResponse> update(
            Authentication authentication,
            @PathVariable Long showcaseId,
            @Valid @RequestBody UpdateShowcaseRequest request) {

        Long ownerId = (Long) authentication.getPrincipal();
        updateShowcaseUseCase.update(showcaseId, ownerId, request.toCommand());

        return ApiResponse.of(200, "쇼케이스 수정 성공", new ShowcaseIdResponse(showcaseId));
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

}
