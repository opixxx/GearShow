package com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto;

/**
 * Tripo Task 생성 응답.
 */
public record TripoTaskResponse(
        int code,
        TripoTaskData data
) {
    public record TripoTaskData(String task_id) {}
}
