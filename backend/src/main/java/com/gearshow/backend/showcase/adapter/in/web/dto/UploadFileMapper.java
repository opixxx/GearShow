package com.gearshow.backend.showcase.adapter.in.web.dto;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;
import com.gearshow.backend.showcase.application.dto.UploadFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * MultipartFile → UploadFile 변환 유틸리티.
 * 컨트롤러(adapter) 계층에서 공통으로 사용한다.
 */
public final class UploadFileMapper {

    private UploadFileMapper() {
    }

    /**
     * MultipartFile 목록을 UploadFile 목록으로 변환한다.
     */
    public static List<UploadFile> toUploadFiles(List<MultipartFile> files) {
        return files.stream()
                .map(UploadFileMapper::toUploadFile)
                .toList();
    }

    /**
     * 단일 MultipartFile을 UploadFile로 변환한다.
     */
    public static UploadFile toUploadFile(MultipartFile file) {
        try {
            return new UploadFile(
                    file.getInputStream(),
                    file.getContentType(),
                    file.getSize(),
                    file.getOriginalFilename());
        } catch (IOException e) {
            throw new CustomException(ErrorCode.STORAGE_FILE_READ_FAILED);
        }
    }
}
