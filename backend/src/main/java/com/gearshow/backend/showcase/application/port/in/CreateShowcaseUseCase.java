package com.gearshow.backend.showcase.application.port.in;

import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseResult;

import java.util.List;

/**
 * 쇼케이스 등록 유스케이스.
 */
public interface CreateShowcaseUseCase {

    /**
     * 새로운 쇼케이스를 등록한다.
     * 이미지는 클라이언트가 Presigned URL로 S3에 직접 업로드하고,
     * 서버는 S3 키 목록을 전달받아 존재 여부를 검증한 후 DB에 저장한다.
     *
     * @param command              등록 커맨드
     * @param imageKeys            쇼케이스 이미지 S3 키 목록 (최소 1개)
     * @param modelSourceImageKeys 3D 모델 소스 이미지 S3 키 목록 (없으면 빈 리스트)
     * @return 등록 결과
     */
    CreateShowcaseResult create(CreateShowcaseCommand command,
                                 List<String> imageKeys,
                                 List<String> modelSourceImageKeys);
}
