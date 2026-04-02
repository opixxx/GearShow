package com.gearshow.backend.showcase.adapter.out.storage.s3;

import com.gearshow.backend.showcase.adapter.out.storage.s3.exception.S3UploadFailedException;
import com.gearshow.backend.showcase.application.dto.UploadFile;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AWS S3 이미지 저장소 어댑터.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3ImageStorageAdapter implements ImageStoragePort {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.cdn-url}")
    private String cdnUrl;

    @Override
    public String upload(String directory, UploadFile file) {
        String key = generateKey(directory, file.originalFilename());
        putObject(key, file);
        return cdnUrl + "/" + key;
    }

    @Override
    public List<String> uploadAll(String directory, List<UploadFile> files) {
        List<String> urls = new ArrayList<>();
        for (UploadFile file : files) {
            urls.add(upload(directory, file));
        }
        return urls;
    }

    @Override
    public void delete(String imageUrl) {
        String key = extractKeyFromUrl(imageUrl);
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
        log.info("S3 이미지 삭제 완료: {}", key);
    }

    private void putObject(String key, UploadFile file) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.contentType())
                            .build(),
                    RequestBody.fromInputStream(file.inputStream(), file.size()));
            log.info("S3 이미지 업로드 완료: {}", key);
        } catch (Exception e) {
            log.error("S3 이미지 업로드 실패: {}", key, e);
            throw new S3UploadFailedException();
        }
    }

    /**
     * 고유한 S3 키를 생성한다.
     * 파일명 충돌 방지를 위해 UUID를 사용한다.
     */
    private String generateKey(String directory, String originalFilename) {
        String extension = extractExtension(originalFilename);
        return directory + "/" + UUID.randomUUID() + extension;
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    /**
     * CDN URL에서 S3 키를 추출한다.
     */
    private String extractKeyFromUrl(String imageUrl) {
        return imageUrl.replace(cdnUrl + "/", "");
    }
}
