package com.gearshow.backend.showcase.application.port.out;

/**
 * 이미지 저장소 Outbound Port.
 * 업로드는 Presigned URL을 통해 클라이언트가 직접 수행하므로 이 포트에 포함하지 않는다.
 */
public interface ImageStoragePort {

    /**
     * S3 키를 DB에 저장할 URL로 변환한다.
     * CDN 도입 시 설정값만 변경하면 자동 적용된다.
     *
     * @param s3Key S3 객체 키 (예: "showcases/images/uuid.jpg")
     * @return 이미지 URL
     */
    String toUrl(String s3Key);

    /**
     * S3 키가 실제 존재하는 객체인지 확인한다.
     * 클라이언트가 Presigned URL로 실제로 업로드했는지 검증할 때 사용한다.
     *
     * @param s3Key S3 객체 키
     * @return 객체 존재 여부
     */
    boolean exists(String s3Key);

    /**
     * 이미지를 삭제한다.
     *
     * @param imageUrl 삭제할 이미지 URL
     */
    void delete(String imageUrl);
}
