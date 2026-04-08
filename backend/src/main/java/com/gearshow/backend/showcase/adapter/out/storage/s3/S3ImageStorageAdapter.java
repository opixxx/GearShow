package com.gearshow.backend.showcase.adapter.out.storage.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gearshow.backend.showcase.adapter.out.storage.s3.exception.S3DownloadFailedException;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * AWS S3 이미지 저장소 어댑터.
 * 이미지 업로드는 Presigned URL을 통해 클라이언트가 직접 수행하므로,
 * 이 어댑터는 URL 변환, 존재 여부 확인, 삭제 기능만 제공한다.
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

    /**
     * S3 키를 DB에 저장할 URL로 변환한다.
     * CDN 도입 시 설정값(cdn-url)만 변경하면 자동 적용된다.
     */
    @Override
    public String toUrl(String s3Key) {
        return cdnUrl + "/" + s3Key;
    }

    /**
     * S3 키가 실제 존재하는 객체인지 HeadObject로 확인한다.
     * 클라이언트가 Presigned URL을 통해 실제로 업로드했는지 검증할 때 사용한다.
     *
     * @return true: 객체 존재, false: 객체 없음
     */
    @Override
    public boolean exists(String s3Key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("S3 객체 존재 여부 확인 중 오류 발생: key={}", s3Key, e);
            return false;
        }
    }

    /**
     * S3에서 이미지 파일을 삭제한다.
     * imageUrl에서 CDN 접두어를 제거하여 S3 키를 추출한다.
     */
    @Override
    public void delete(String imageUrl) {
        String key = extractKeyFromUrl(imageUrl);
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
        log.info("S3 이미지 삭제 완료: key={}", key);
    }

    /**
     * S3에서 이미지 바이트 데이터를 다운로드한다.
     */
    @Override
    public byte[] download(String imageUrl) {
        String key = extractKeyFromUrl(imageUrl);
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build())
                    .asByteArray();
        } catch (Exception e) {
            log.error("S3 이미지 다운로드 실패: key={}", key, e);
            throw new S3DownloadFailedException();
        }
    }

    /**
     * 바이트 데이터를 S3에 업로드하고 CDN URL을 반환한다.
     */
    @Override
    public String upload(String s3Key, byte[] data, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(s3Key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));
        log.info("S3 업로드 완료: key={}, size={}bytes", s3Key, data.length);
        return toUrl(s3Key);
    }

    /**
     * CDN URL에서 S3 키를 추출한다.
     * 예: "https://cdn.example.com/showcases/images/uuid.jpg" → "showcases/images/uuid.jpg"
     */
    private String extractKeyFromUrl(String imageUrl) {
        return imageUrl.replace(cdnUrl + "/", "");
    }
}
