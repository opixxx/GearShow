package com.gearshow.backend.user.application.port.out;

/**
 * 프로필 이미지 저장소 Outbound Port.
 * 프로필 이미지의 업로드, 삭제, URL 변환을 담당한다.
 */
public interface ProfileImageStoragePort {

    /**
     * 프로필 이미지를 업로드한다.
     *
     * @param s3Key       S3 객체 키 (예: "profiles/uuid.jpg")
     * @param content     파일 바이트 배열
     * @param contentType 파일 Content-Type (예: "image/jpeg")
     */
    void upload(String s3Key, byte[] content, String contentType);

    /**
     * 프로필 이미지를 삭제한다.
     *
     * @param s3Key S3 객체 키
     */
    void delete(String s3Key);

    /**
     * S3 키를 CDN URL로 변환한다.
     *
     * @param s3Key S3 객체 키
     * @return CDN URL
     */
    String toUrl(String s3Key);

    /**
     * CDN URL에서 S3 키를 추출한다.
     *
     * @param imageUrl CDN URL
     * @return S3 객체 키 (URL이 null이거나 비어있으면 null 반환)
     */
    String extractKey(String imageUrl);
}
