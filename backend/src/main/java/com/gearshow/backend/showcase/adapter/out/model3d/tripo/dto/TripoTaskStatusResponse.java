package com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Tripo Task 상태 조회 응답.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TripoTaskStatusResponse(
        int code,
        TripoTaskStatusData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TripoTaskStatusData(
            String task_id,
            String type,
            String status,
            int progress,
            TripoOutput output
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TripoOutput(
            String model,
            String pbr_model,
            String rendered_image
    ) {}

    /** Task가 완료(성공 또는 실패) 상태인지 확인한다. */
    public boolean isTerminal() {
        String status = data != null ? data.status() : null;
        if (status == null) return false;
        String upper = status.toUpperCase();
        return "SUCCESS".equals(upper) || "FAILED".equals(upper)
                || "CANCELLED".equals(upper) || "BANNED".equals(upper) || "EXPIRED".equals(upper);
    }

    /** Task가 성공 상태인지 확인한다. */
    public boolean isSuccess() {
        return data != null && data.status() != null
                && "SUCCESS".equalsIgnoreCase(data.status());
    }
}
