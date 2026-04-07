package com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto;

/**
 * Tripo Task 상태 조회 응답.
 */
public record TripoTaskStatusResponse(
        int code,
        TripoTaskStatusData data
) {
    public record TripoTaskStatusData(
            String task_id,
            String status,
            int progress,
            TripoOutput output
    ) {}

    public record TripoOutput(
            String model,
            String pbr_model,
            String rendered_image
    ) {}

    /** Task가 완료(성공 또는 실패) 상태인지 확인한다. */
    public boolean isTerminal() {
        String status = data != null ? data.status() : null;
        return "success".equals(status) || "failed".equals(status) || "cancelled".equals(status);
    }

    /** Task가 성공 상태인지 확인한다. */
    public boolean isSuccess() {
        return data != null && "success".equals(data.status());
    }
}
