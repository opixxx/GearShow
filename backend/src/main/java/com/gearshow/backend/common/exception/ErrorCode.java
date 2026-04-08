package com.gearshow.backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 전역 에러 코드를 통합 관리하는 열거형.
 * 각 도메인별 접두사로 구분하며, 메시지는 한글로 작성한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // USER
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다"),
    USER_DUPLICATE_NICKNAME(400, "이미 사용 중인 닉네임입니다"),
    USER_ALREADY_WITHDRAWN(400, "이미 탈퇴한 사용자입니다"),
    USER_SUSPENDED(403, "정지된 사용자입니다"),
    USER_HAS_ACTIVE_TRANSACTION(400, "진행 중인 거래가 있어 탈퇴할 수 없습니다"),
    USER_PHONE_NOT_VERIFIED(400, "휴대폰 인증이 필요합니다"),
    USER_INVALID_STATUS_TRANSITION(400, "유효하지 않은 사용자 상태 전이입니다"),

    // AUTH
    AUTH_ACCOUNT_NOT_FOUND(404, "인증 계정을 찾을 수 없습니다"),
    AUTH_DUPLICATE_PROVIDER(400, "이미 연동된 소셜 계정입니다"),
    AUTH_INVALID_CODE(400, "유효하지 않은 인가 코드입니다"),
    AUTH_EXPIRED_TOKEN(401, "토큰이 만료되었습니다"),
    AUTH_INVALID_TOKEN(401, "유효하지 않은 토큰입니다"),
    AUTH_UNSUPPORTED_PROVIDER(400, "지원하지 않는 소셜 로그인 제공자입니다"),

    // CATALOG
    CATALOG_ITEM_NOT_FOUND(404, "카탈로그 아이템을 찾을 수 없습니다"),
    CATALOG_ITEM_INACTIVE(400, "비활성화된 카탈로그 아이템입니다"),
    CATALOG_ITEM_DUPLICATE_MODEL_CODE(400, "동일 카테고리 내 중복된 모델 코드입니다"),
    CATALOG_ITEM_INVALID(400, "유효하지 않은 카탈로그 아이템입니다"),

    // SHOWCASE
    SHOWCASE_NOT_FOUND(404, "쇼케이스를 찾을 수 없습니다"),
    SHOWCASE_NOT_OWNER(403, "쇼케이스 소유자만 수정 또는 삭제할 수 있습니다"),
    SHOWCASE_INVALID(400, "유효하지 않은 쇼케이스입니다"),
    SHOWCASE_INVALID_STATUS_TRANSITION(400, "유효하지 않은 쇼케이스 상태 전이입니다"),
    SHOWCASE_MIN_IMAGE_REQUIRED(400, "최소 1개의 이미지가 필요합니다"),
    SHOWCASE_PRIMARY_IMAGE_REQUIRED(400, "대표 이미지가 반드시 1개 존재해야 합니다"),
    SHOWCASE_MODEL_ALREADY_GENERATING(400, "3D 모델이 이미 생성 중입니다"),
    SHOWCASE_MODEL_MIN_SOURCE_IMAGE_REQUIRED(400, "3D 모델 생성에는 최소 4장의 소스 이미지가 필요합니다"),
    SHOWCASE_IMAGE_NOT_BELONG(400, "해당 쇼케이스에 속하지 않는 이미지입니다"),
    SHOWCASE_IMAGE_NOT_FOUND(404, "쇼케이스 이미지를 찾을 수 없습니다"),
    SHOWCASE_IMAGE_REORDER_MISMATCH(400, "재정렬 요청 이미지 목록이 실제 이미지 목록과 일치하지 않습니다"),
    SHOWCASE_IMAGE_DUPLICATE_SORT_ORDER(400, "이미지 정렬 순서가 중복되었습니다"),
    SHOWCASE_COMMENT_NOT_FOUND(404, "댓글을 찾을 수 없습니다"),
    SHOWCASE_COMMENT_NOT_AUTHOR(403, "댓글 작성자만 수정 또는 삭제할 수 있습니다"),
    SHOWCASE_COMMENT_INVALID(400, "유효하지 않은 댓글입니다"),
    SHOWCASE_SPEC_SERIALIZATION_FAILED(500, "스펙 JSON 직렬화에 실패했습니다"),

    // STORAGE
    STORAGE_UPLOAD_FAILED(500, "이미지 업로드에 실패했습니다"),
    STORAGE_DOWNLOAD_FAILED(500, "이미지 다운로드에 실패했습니다"),
    STORAGE_FILE_READ_FAILED(500, "파일 스트림 읽기에 실패했습니다"),
    STORAGE_PRESIGN_FAILED(500, "Presigned URL 생성에 실패했습니다"),
    STORAGE_KEY_NOT_FOUND(400, "S3에 존재하지 않는 이미지 키입니다. 이미지를 먼저 업로드해 주세요"),

    // TRIPO (3D 모델 생성)
    TRIPO_UPLOAD_FAILED(500, "Tripo 이미지 업로드에 실패했습니다"),
    TRIPO_TASK_CREATION_FAILED(500, "Tripo 3D 모델 생성 요청에 실패했습니다"),
    TRIPO_TASK_STATUS_FAILED(500, "Tripo Task 상태 조회에 실패했습니다"),
    TRIPO_TASK_TIMEOUT(500, "Tripo 3D 모델 생성 시간이 초과되었습니다"),
    TRIPO_DOWNLOAD_FAILED(500, "Tripo 3D 모델 다운로드에 실패했습니다");

    private final int status;
    private final String message;
}
