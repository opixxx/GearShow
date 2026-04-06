package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.UploadFileType;
import com.gearshow.backend.showcase.application.port.in.GeneratePresignedUrlUseCase;
import com.gearshow.backend.showcase.application.port.out.PresignedUrlPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Presigned URL 생성 유스케이스 구현체.
 * 파일 유형과 쇼케이스 ID에 따라 S3 키 경로를 결정하고 PUT 서명 URL을 발급한다.
 */
@Service
@RequiredArgsConstructor
public class GeneratePresignedUrlService implements GeneratePresignedUrlUseCase {

    private final PresignedUrlPort presignedUrlPort;

    /**
     * 쇼케이스 등록 시 사용할 Presigned URL 목록을 생성한다.
     * SHOWCASE_IMAGE → "showcases/{uuid}.ext"
     * MODEL_SOURCE   → "showcases/model-source/{uuid}.ext"
     */
    @Override
    public List<PresignedUrlResult> generate(List<FileUploadRequest> files) {
        return files.stream()
                .map(file -> toResult(file, resolveDirectory(file.type())))
                .toList();
    }

    /**
     * 기존 쇼케이스에 이미지를 추가할 때 사용할 Presigned URL 목록을 생성한다.
     * → "showcases/{showcaseId}/{uuid}.ext"
     */
    @Override
    public List<PresignedUrlResult> generateForShowcase(Long showcaseId, List<FileUploadRequest> files) {
        String directory = "showcases/" + showcaseId;
        return files.stream()
                .map(file -> toResult(file, directory))
                .toList();
    }

    private PresignedUrlResult toResult(FileUploadRequest file, String directory) {
        String s3Key = generateKey(directory, file.filename());
        String presignedUrl = presignedUrlPort.generatePutUrl(s3Key, file.contentType());
        return new PresignedUrlResult(presignedUrl, s3Key, file.type());
    }

    /**
     * 파일 유형에 따라 S3 저장 디렉터리를 결정한다.
     */
    private String resolveDirectory(UploadFileType type) {
        return switch (type) {
            case SHOWCASE_IMAGE -> "showcases";
            case MODEL_SOURCE -> "showcases/model-source";
        };
    }

    /**
     * UUID 기반의 고유한 S3 키를 생성한다.
     * 파일명 충돌 방지를 위해 원본 파일명 대신 UUID를 사용한다.
     */
    private String generateKey(String directory, String filename) {
        String extension = extractExtension(filename);
        return directory + "/" + UUID.randomUUID() + extension;
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
