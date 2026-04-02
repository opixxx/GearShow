package com.gearshow.backend.showcase.application.port.out;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 이미지 저장소 Outbound Port.
 * S3 등 외부 저장소에 이미지를 업로드/삭제한다.
 */
public interface ImageStoragePort {

    /**
     * 이미지 파일을 업로드하고 URL을 반환한다.
     *
     * @param directory 저장 디렉터리 경로 (예: "showcases/1")
     * @param file      업로드할 파일
     * @return 업로드된 이미지 URL
     */
    String upload(String directory, MultipartFile file);

    /**
     * 여러 이미지 파일을 업로드하고 URL 목록을 반환한다.
     *
     * @param directory 저장 디렉터리 경로
     * @param files     업로드할 파일 목록
     * @return 업로드된 이미지 URL 목록
     */
    List<String> uploadAll(String directory, List<MultipartFile> files);

    /**
     * 이미지를 삭제한다.
     *
     * @param imageUrl 삭제할 이미지 URL
     */
    void delete(String imageUrl);
}
