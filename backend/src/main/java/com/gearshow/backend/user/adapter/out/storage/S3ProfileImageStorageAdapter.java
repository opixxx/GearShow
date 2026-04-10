package com.gearshow.backend.user.adapter.out.storage;

import com.gearshow.backend.user.application.port.out.ProfileImageStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 프로필 이미지용 S3 저장소 어댑터.
 * 프로필 이미지의 업로드, 삭제, URL 변환을 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3ProfileImageStorageAdapter implements ProfileImageStoragePort {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.cdn-url}")
    private String cdnUrl;

    @Override
    public void upload(String s3Key, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(content));
        log.debug("프로필 이미지 업로드 완료: key={}", s3Key);
    }

    @Override
    public void delete(String s3Key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        s3Client.deleteObject(request);
        log.debug("프로필 이미지 삭제 완료: key={}", s3Key);
    }

    @Override
    public String toUrl(String s3Key) {
        return cdnUrl + "/" + s3Key;
    }

    @Override
    public String extractKey(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String prefix = cdnUrl + "/";
        if (imageUrl.startsWith(prefix)) {
            return imageUrl.substring(prefix.length());
        }
        return null;
    }
}
