package com.gearshow.backend.showcase.application.port.out;

/**
 * Presigned URL 생성 Outbound Port.
 * S3 등 외부 저장소의 PUT용 Presigned URL을 발급한다.
 */
public interface PresignedUrlPort {

    /**
     * S3 PUT용 Presigned URL을 생성한다.
     * 클라이언트는 이 URL로 서버를 거치지 않고 S3에 직접 업로드한다.
     *
     * @param s3Key       S3 객체 키 (예: "showcases/images/uuid.jpg")
     * @param contentType 파일 Content-Type (예: "image/jpeg")
     * @return Presigned URL (만료: 10분)
     */
    String generatePutUrl(String s3Key, String contentType);
}
