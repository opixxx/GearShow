package com.gearshow.backend.showcase.application.port.in;

import java.util.List;

/**
 * 쇼케이스 이미지 관리 유스케이스.
 */
public interface ManageShowcaseImageUseCase {

    /**
     * 이미지를 추가한다.
     * 이미지는 클라이언트가 Presigned URL로 S3에 직접 업로드하고,
     * 서버는 S3 키 목록을 전달받아 DB에 저장한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @param ownerId    요청자 ID
     * @param imageKeys  추가할 이미지 S3 키 목록
     * @return 추가된 이미지 ID 목록
     */
    List<Long> addImages(Long showcaseId, Long ownerId, List<String> imageKeys);

    /**
     * 이미지를 삭제한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @param imageId    이미지 ID
     * @param ownerId    요청자 ID
     */
    void deleteImage(Long showcaseId, Long imageId, Long ownerId);

    /**
     * 이미지 정렬 순서를 변경한다.
     *
     * @param showcaseId  쇼케이스 ID
     * @param ownerId     요청자 ID
     * @param imageOrders 이미지 정렬 순서 목록
     */
    void reorderImages(Long showcaseId, Long ownerId, List<ImageOrder> imageOrders);

    /**
     * 이미지 정렬 순서.
     */
    record ImageOrder(Long showcaseImageId, int sortOrder, boolean isPrimary) {}
}
