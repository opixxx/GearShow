package com.gearshow.backend.showcase.adapter.in.web;

import com.gearshow.backend.common.dto.ApiResponse;
import com.gearshow.backend.common.dto.PageInfo;
import com.gearshow.backend.showcase.application.dto.ShowcaseListResult;
import com.gearshow.backend.showcase.application.port.in.ListShowcasesUseCase;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 쇼케이스 관련 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/users/me/showcases")
@RequiredArgsConstructor
@Validated
public class MyShowcaseController {

    private final ListShowcasesUseCase listShowcasesUseCase;

    /**
     * 내 쇼케이스 목록을 조회한다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageInfo<ShowcaseListResult>>> listMyShowcases(
            Authentication authentication,
            @RequestParam(required = false) ShowcaseStatus showcaseStatus,
            @RequestParam(required = false) String pageToken,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            int size) {

        Long ownerId = (Long) authentication.getPrincipal();
        PageInfo<ShowcaseListResult> result = listShowcasesUseCase.listByOwner(
                ownerId, pageToken, size, showcaseStatus);

        return ResponseEntity.ok(
                ApiResponse.of(200, "내 쇼케이스 목록 조회 성공", result));
    }
}
