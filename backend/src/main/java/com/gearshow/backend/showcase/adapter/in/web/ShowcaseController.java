package com.gearshow.backend.showcase.adapter.in.web;

import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.showcase.adapter.in.web.dto.CreateShowcaseRequest;
import com.gearshow.backend.showcase.adapter.in.web.dto.ShowcaseDetailResponse;
import com.gearshow.backend.showcase.adapter.in.web.dto.UpdateShowcaseRequest;
import com.gearshow.backend.showcase.adapter.in.web.dto.UploadFileMapper;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    private final CreateShowcaseUseCase createShowcaseUseCase;
    private final GetShowcaseUseCase getShowcaseUseCase;
    private final ListShowcasesUseCase listShowcasesUseCase;
    private final UpdateShowcaseUseCase updateShowcaseUseCase;
    private final DeleteShowcaseUseCase deleteShowcaseUseCase;

    /**
     * 쇼케이스 목록을 조회한다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageInfo<ShowcaseListResult>>> list(
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

        return ResponseEntity.ok(
                ApiResponse.of(200, "쇼케이스 목록 조회 성공", result));
    }

    /**
     * 쇼케이스 상세를 조회한다.
     */
    @GetMapping("/{showcaseId}")
    public ResponseEntity<ApiResponse<ShowcaseDetailResponse>> getDetail(
            @PathVariable Long showcaseId) {

        ShowcaseDetailResult result = getShowcaseUseCase.getShowcase(showcaseId);

        return ResponseEntity.ok(
                ApiResponse.of(200, "쇼케이스 조회 성공",
                        ShowcaseDetailResponse.from(result)));
    }

    /**
     * 쇼케이스를 등록한다.
     * 이미지는 multipart/form-data로 업로드한다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            Authentication authentication,
            @Valid @ModelAttribute CreateShowcaseRequest request,
            @RequestParam("images") List<MultipartFile> images,
            @RequestParam(value = "modelSourceImages", required = false) List<MultipartFile> modelSourceImages) {

        Long ownerId = (Long) authentication.getPrincipal();
        List<MultipartFile> safeModelSourceImages = modelSourceImages != null
                ? modelSourceImages : List.of();

        CreateShowcaseResult result = createShowcaseUseCase.create(
                request.toCommand(ownerId, !safeModelSourceImages.isEmpty()),
                UploadFileMapper.toUploadFiles(images),
                UploadFileMapper.toUploadFiles(safeModelSourceImages));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(201, "쇼케이스 등록 성공",
                        Map.of("showcaseId", result.showcaseId(),
                                "model3dStatus", result.model3dStatus() != null
                                        ? result.model3dStatus().name() : "null")));
    }

    /**
     * 쇼케이스를 수정한다.
     */
    @PatchMapping("/{showcaseId}")
    public ResponseEntity<ApiResponse<Map<String, Long>>> update(
            Authentication authentication,
            @PathVariable Long showcaseId,
            @Valid @RequestBody UpdateShowcaseRequest request) {

        Long ownerId = (Long) authentication.getPrincipal();
        updateShowcaseUseCase.update(showcaseId, ownerId, request.toCommand());

        return ResponseEntity.ok(
                ApiResponse.of(200, "쇼케이스 수정 성공",
                        Map.of("showcaseId", showcaseId)));
    }

    /**
     * 쇼케이스를 삭제한다 (소프트 삭제).
     */
    @DeleteMapping("/{showcaseId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            Authentication authentication,
            @PathVariable Long showcaseId) {

        Long ownerId = (Long) authentication.getPrincipal();
        deleteShowcaseUseCase.delete(showcaseId, ownerId);

        return ResponseEntity.ok(
                ApiResponse.of(200, "쇼케이스 삭제 성공"));
    }

}
