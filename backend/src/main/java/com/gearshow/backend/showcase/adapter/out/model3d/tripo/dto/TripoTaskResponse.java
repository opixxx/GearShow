package com.gearshow.backend.showcase.adapter.out.model3d.tripo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Tripo Task 생성 응답.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TripoTaskResponse(
        int code,
        TripoTaskData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TripoTaskData(String task_id) {}
}
