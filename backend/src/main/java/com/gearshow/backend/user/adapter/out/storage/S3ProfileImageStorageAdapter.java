package com.gearshow.backend.user.adapter.out.storage;

import com.gearshow.backend.user.application.exception.ProfileImageDeleteFailedException;
import com.gearshow.backend.user.application.exception.ProfileImageUploadFailedException;
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
 * 모든 S3 호출 실패는 도메인 친화 예외로 래핑한다.
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
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(content));
            log.info("프로필 이미지 업로드 완료: key={}, size={}bytes", s3Key, content.length);
        } catch (Exception e) {
            log.error("프로필 이미지 업로드 실패: key={}", s3Key, e);
            throw new ProfileImageUploadFailedException();
        }
    }

    @Override
    public void delete(String s3Key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(request);
            log.info("프로필 이미지 삭제 완료: key={}", s3Key);
        } catch (Exception e) {
            log.error("프로필 이미지 삭제 실패: key={}", s3Key, e);
            throw new ProfileImageDeleteFailedException();
        }
    }

    @Override
    public String toUrl(String s3Key) {
        return normalizedCdnPrefix() + s3Key;
    }

    @Override
    public String extractKey(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String prefix = normalizedCdnPrefix();
        if (!imageUrl.startsWith(prefix)) {
            log.warn("프로필 이미지 URL이 CDN prefix와 일치하지 않아 키 추출 불가: url={}", imageUrl);
            return null;
        }
        String key = imageUrl.substring(prefix.length());
        return key.isBlank() ? null : key;
    }

    /**
     * CDN URL prefix를 끝에 슬래시 한 개로 정규화한다.
     * 환경변수가 슬래시로 끝나든 아니든 동일한 결과를 보장한다.
     */
    private String normalizedCdnPrefix() {
        if (cdnUrl.endsWith("/")) {
            return cdnUrl;
        }
        return cdnUrl + "/";
    }
}
