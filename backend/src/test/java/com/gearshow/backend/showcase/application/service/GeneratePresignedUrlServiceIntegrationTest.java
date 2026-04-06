package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.UploadFileType;
import com.gearshow.backend.showcase.application.port.in.GeneratePresignedUrlUseCase;
import com.gearshow.backend.showcase.application.port.in.GeneratePresignedUrlUseCase.FileUploadRequest;
import com.gearshow.backend.showcase.application.port.in.GeneratePresignedUrlUseCase.PresignedUrlResult;
import com.gearshow.backend.support.TestInfraConfig;
import com.gearshow.backend.support.TestOAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GeneratePresignedUrlService 통합 테스트.
 *
 * <p>검증 대상:
 * <ul>
 *   <li>{@link GeneratePresignedUrlUseCase#generate} - 파일 유형별 S3 키 경로 규칙</li>
 *   <li>{@link GeneratePresignedUrlUseCase#generateForShowcase} - 쇼케이스 ID 기반 경로 규칙</li>
 *   <li>{@link CreateShowcaseFacade} - S3 키 미존재 시 {@link InvalidImageKeyException} 발생</li>
 * </ul>
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
@Transactional
class GeneratePresignedUrlServiceIntegrationTest {

    @Autowired
    private GeneratePresignedUrlUseCase generatePresignedUrlUseCase;

    // ===== Helper =====

    /**
     * 단일 FileUploadRequest를 생성한다.
     */
    private FileUploadRequest fileRequest(String filename, UploadFileType type) {
        return new FileUploadRequest("image/jpeg", filename, type);
    }

    // ===== generate() =====

    @Nested
    @DisplayName("generate() - 쇼케이스 등록용 Presigned URL 생성")
    class Generate {

        @Test
        @DisplayName("요청한 파일 개수만큼 결과가 반환된다")
        void generate_returnsCorrectCount() {
            // Given - SHOWCASE_IMAGE 3개, MODEL_SOURCE 1개 혼합 요청
            List<FileUploadRequest> files = List.of(
                    fileRequest("front.jpg", UploadFileType.SHOWCASE_IMAGE),
                    fileRequest("back.jpg", UploadFileType.SHOWCASE_IMAGE),
                    fileRequest("side.jpg", UploadFileType.SHOWCASE_IMAGE),
                    fileRequest("source.jpg", UploadFileType.MODEL_SOURCE)
            );

            // When
            List<PresignedUrlResult> results = generatePresignedUrlUseCase.generate(files);

            // Then
            assertThat(results).hasSize(4);
        }

        @Test
        @DisplayName("SHOWCASE_IMAGE 유형의 S3 키는 'showcases/' 접두사를 가진다")
        void generate_showcaseImageType_keyStartsWithShowcases() {
            // Given
            List<FileUploadRequest> files = List.of(
                    fileRequest("photo.jpg", UploadFileType.SHOWCASE_IMAGE)
            );

            // When
            List<PresignedUrlResult> results = generatePresignedUrlUseCase.generate(files);

            // Then - 경로 규칙: showcases/{uuid}.ext  (model-source 하위가 아님)
            String s3Key = results.get(0).s3Key();
            assertThat(s3Key).startsWith("showcases/");
            assertThat(s3Key).doesNotContain("model-source");
        }

        @Test
        @DisplayName("MODEL_SOURCE 유형의 S3 키는 'showcases/model-source/' 접두사를 가진다")
        void generate_modelSourceType_keyStartsWithModelSource() {
            // Given
            List<FileUploadRequest> files = List.of(
                    fileRequest("source.jpg", UploadFileType.MODEL_SOURCE)
            );

            // When
            List<PresignedUrlResult> results = generatePresignedUrlUseCase.generate(files);

            // Then - 경로 규칙: showcases/model-source/{uuid}.ext
            String s3Key = results.get(0).s3Key();
            assertThat(s3Key).startsWith("showcases/model-source/");
        }

        @Test
        @DisplayName("S3 키는 원본 파일명의 확장자를 유지한다")
        void generate_keyPreservesExtension() {
            // Given
            List<FileUploadRequest> files = List.of(
                    fileRequest("image.png", UploadFileType.SHOWCASE_IMAGE)
            );

            // When
            List<PresignedUrlResult> results = generatePresignedUrlUseCase.generate(files);

            // Then
            assertThat(results.get(0).s3Key()).endsWith(".png");
        }

        @Test
        @DisplayName("파일명에 확장자가 없으면 S3 키도 확장자 없이 생성된다")
        void generate_noExtension_keyHasNoExtension() {
            // Given
            List<FileUploadRequest> files = List.of(
                    fileRequest("imagefile", UploadFileType.SHOWCASE_IMAGE)
            );

            // When
            List<PresignedUrlResult> results = generatePresignedUrlUseCase.generate(files);

            // Then - "showcases/{uuid}" 형태여야 하며, UUID 부분에 '.'이 없어야 한다
            String keyBody = results.get(0).s3Key()
                    .replaceFirst("^showcases/", ""); // 경로 접두사 제거
            assertThat(keyBody).doesNotContain(".");
        }

        @Test
        @DisplayName("동일 파일명을 여러 번 요청해도 S3 키는 모두 다르다 (UUID 중복 없음)")
        void generate_multipleFiles_keysAreUnique() {
            // Given - 같은 파일명 3개 요청
            List<FileUploadRequest> files = IntStream.range(0, 3)
                    .mapToObj(i -> fileRequest("same.jpg", UploadFileType.SHOWCASE_IMAGE))
                    .toList();

            // When
            List<PresignedUrlResult> results = generatePresignedUrlUseCase.generate(files);

            // Then
            long distinctKeyCount = results.stream()
                    .map(PresignedUrlResult::s3Key)
                    .distinct()
                    .count();
            assertThat(distinctKeyCount).isEqualTo(3);
        }

        @Test
        @DisplayName("결과의 type 필드는 요청 시 전달한 유형과 동일하다")
        void generate_resultTypeMatchesRequest() {
            // Given
            List<FileUploadRequest> files = List.of(
                    fileRequest("img.jpg", UploadFileType.SHOWCASE_IMAGE),
                    fileRequest("src.jpg", UploadFileType.MODEL_SOURCE)
            );

            // When
            List<PresignedUrlResult> results = generatePresignedUrlUseCase.generate(files);

            // Then - 요청 순서와 동일하게 type이 매핑되어야 한다
            assertThat(results.get(0).type()).isEqualTo(UploadFileType.SHOWCASE_IMAGE);
            assertThat(results.get(1).type()).isEqualTo(UploadFileType.MODEL_SOURCE);
        }

        @Test
        @DisplayName("결과의 presignedUrl 필드는 null이 아니고 비어있지 않다")
        void generate_presignedUrlIsNotBlank() {
            // Given
            List<FileUploadRequest> files = List.of(
                    fileRequest("photo.jpg", UploadFileType.SHOWCASE_IMAGE)
            );

            // When
            List<PresignedUrlResult> results = generatePresignedUrlUseCase.generate(files);

            // Then
            assertThat(results.get(0).presignedUrl()).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("결과의 s3Key 필드는 null이 아니고 비어있지 않다")
        void generate_s3KeyIsNotBlank() {
            // Given
            List<FileUploadRequest> files = List.of(
                    fileRequest("photo.jpg", UploadFileType.SHOWCASE_IMAGE)
            );

            // When
            List<PresignedUrlResult> results = generatePresignedUrlUseCase.generate(files);

            // Then
            assertThat(results.get(0).s3Key()).isNotNull().isNotBlank();
        }
    }

    // ===== generateForShowcase() =====

    @Nested
    @DisplayName("generateForShowcase() - 기존 쇼케이스 이미지 추가용 Presigned URL 생성")
    class GenerateForShowcase {

        @Test
        @DisplayName("요청한 파일 개수만큼 결과가 반환된다")
        void generateForShowcase_returnsCorrectCount() {
            // Given
            Long showcaseId = 42L;
            List<FileUploadRequest> files = List.of(
                    fileRequest("a.jpg", UploadFileType.SHOWCASE_IMAGE),
                    fileRequest("b.jpg", UploadFileType.SHOWCASE_IMAGE)
            );

            // When
            List<PresignedUrlResult> results =
                    generatePresignedUrlUseCase.generateForShowcase(showcaseId, files);

            // Then
            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("S3 키 경로에 쇼케이스 ID가 포함된다")
        void generateForShowcase_keyContainsShowcaseId() {
            // Given
            Long showcaseId = 123L;
            List<FileUploadRequest> files = List.of(
                    fileRequest("photo.jpg", UploadFileType.SHOWCASE_IMAGE)
            );

            // When
            List<PresignedUrlResult> results =
                    generatePresignedUrlUseCase.generateForShowcase(showcaseId, files);

            // Then - 경로 규칙: showcases/{showcaseId}/{uuid}.ext
            String s3Key = results.get(0).s3Key();
            assertThat(s3Key).startsWith("showcases/" + showcaseId + "/");
        }

        @Test
        @DisplayName("쇼케이스 ID가 다르면 S3 키 경로 접두사가 다르다")
        void generateForShowcase_differentShowcaseIds_haveDifferentPrefixes() {
            // Given
            List<FileUploadRequest> files = List.of(
                    fileRequest("img.jpg", UploadFileType.SHOWCASE_IMAGE)
            );

            // When
            List<PresignedUrlResult> resultsA =
                    generatePresignedUrlUseCase.generateForShowcase(10L, files);
            List<PresignedUrlResult> resultsB =
                    generatePresignedUrlUseCase.generateForShowcase(20L, files);

            // Then
            assertThat(resultsA.get(0).s3Key()).startsWith("showcases/10/");
            assertThat(resultsB.get(0).s3Key()).startsWith("showcases/20/");
        }

        @Test
        @DisplayName("generateForShowcase() 결과의 presignedUrl은 null이 아니다")
        void generateForShowcase_presignedUrlIsNotNull() {
            // Given
            Long showcaseId = 1L;
            List<FileUploadRequest> files = List.of(
                    fileRequest("img.jpg", UploadFileType.SHOWCASE_IMAGE)
            );

            // When
            List<PresignedUrlResult> results =
                    generatePresignedUrlUseCase.generateForShowcase(showcaseId, files);

            // Then
            assertThat(results.get(0).presignedUrl()).isNotNull().isNotBlank();
        }
    }

}
