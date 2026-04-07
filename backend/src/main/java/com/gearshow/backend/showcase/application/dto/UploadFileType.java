package com.gearshow.backend.showcase.application.dto;

/**
 * 업로드 파일 유형.
 * Presigned URL 발급 시 파일의 용도를 구분하기 위해 사용한다.
 */
public enum UploadFileType {

    /** 쇼케이스 이미지 (상품 사진) */
    SHOWCASE_IMAGE,

    /** 3D 모델 소스 이미지 (앞/뒤/좌/우 각도 사진) */
    MODEL_SOURCE
}
