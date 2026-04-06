package com.gearshow.backend.showcase.adapter.out.storage.s3;

import com.gearshow.backend.showcase.adapter.out.storage.s3.exception.S3PresignFailedException;
import com.gearshow.backend.showcase.application.port.out.PresignedUrlPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

/**
 * AWS S3 Presigned URL 생성 어댑터.
 * 클라이언트가 서버를 거치지 않고 S3에 직접 업로드할 수 있도록 PUT 서명 URL을 발급한다.
 * 발급된 URL은 {@value #EXPIRATION_MINUTES}분 후 만료된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3PresignedUrlAdapter implements PresignedUrlPort {

    /** Presigned URL 만료 시간 (분) */
    private static final int EXPIRATION_MINUTES = 10;

    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    /**
     * S3 PUT용 Presigned URL을 생성한다.
     * Content-Type을 서명에 포함시켜 클라이언트가 지정한 타입 외의 파일 업로드를 방지한다.
     */
    @Override
    public String generatePutUrl(String s3Key, String contentType) {
        try {
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(EXPIRATION_MINUTES))
                    .putObjectRequest(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(s3Key)
                            .contentType(contentType)
                            .build())
                    .build();

            String presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
            log.debug("Presigned URL 생성 완료: key={}", s3Key);
            return presignedUrl;
        } catch (Exception e) {
            log.error("Presigned URL 생성 실패: key={}", s3Key, e);
            throw new S3PresignFailedException();
        }
    }
}
