package com.gearshow.backend.showcase.application.port.in;

import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 쇼케이스 등록 유스케이스.
 */
public interface CreateShowcaseUseCase {

    /**
     * 새로운 쇼케이스를 등록한다.
     *
     * @param command           등록 커맨드
     * @param images            쇼케이스 이미지 파일 목록
     * @param modelSourceImages 3D 모델 소스 이미지 파일 목록 (없으면 빈 리스트)
     * @return 등록 결과
     */
    CreateShowcaseResult create(CreateShowcaseCommand command,
                                 List<MultipartFile> images,
                                 List<MultipartFile> modelSourceImages);
}
