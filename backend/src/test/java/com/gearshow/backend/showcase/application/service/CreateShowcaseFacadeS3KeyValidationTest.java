package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.application.exception.InvalidImageKeyException;
import com.gearshow.backend.showcase.application.port.in.CreateShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.support.TestOAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CreateShowcaseFacade — S3 키 존재 여부 검증 통합 테스트.
 *
 * <p>S3에 업로드되지 않은 키로 쇼케이스 등록 시 {@link InvalidImageKeyException}이 발생함을 검증한다.
 * ImageStoragePort.exists()가 항상 false를 반환하도록 별도 설정을 사용한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, CreateShowcaseFacadeS3KeyValidationTest.NonExistingS3KeyConfig.class})
@Transactional
class CreateShowcaseFacadeS3KeyValidationTest {

    /**
     * S3 키가 항상 존재하지 않는 것으로 처리하는 ImageStoragePort Stub.
     */
    @TestConfiguration
    static class NonExistingS3KeyConfig {

        @Bean
        @Primary
        public ImageStoragePort nonExistingImageStoragePort() {
            return new ImageStoragePort() {
                @Override
                public String toUrl(String s3Key) {
                    return "https://test-cdn.gearshow.com/" + s3Key;
                }

                @Override
                public boolean exists(String s3Key) {
                    // 테스트에서 S3 업로드 미완료 상태를 시뮬레이션한다
                    return false;
                }

                @Override
                public void delete(String imageUrl) {
                    // 테스트에서는 삭제 무시
                }
            };
        }
    }

    @Autowired
    private CreateShowcaseUseCase createShowcaseUseCase;

    private CreateShowcaseCommand defaultCommand() {
        return new CreateShowcaseCommand(
                1L, null, Category.BOOTS, "Nike", null,
                "S3 키 검증 테스트", null, "270mm",
                ConditionGrade.A, 0, false, 0, false,
                null, null);
    }

    @Test
    @DisplayName("S3에 존재하지 않는 이미지 키로 쇼케이스를 등록하면 InvalidImageKeyException이 발생한다")
    void create_withNonExistingImageKey_throwsInvalidImageKeyException() {
        // Given
        CreateShowcaseCommand command = defaultCommand();
        List<String> imageKeys = List.of("showcases/images/not-uploaded.jpg");

        // When & Then
        List<String> emptyModelKeys = List.of();
        assertThatThrownBy(() ->
                createShowcaseUseCase.create(command, imageKeys, emptyModelKeys)
        ).isInstanceOf(InvalidImageKeyException.class);
    }

    @Test
    @DisplayName("여러 이미지 키 중 존재하지 않는 키가 있으면 InvalidImageKeyException이 발생한다")
    void create_withMultipleNonExistingKeys_throwsException() {
        // Given
        CreateShowcaseCommand command = defaultCommand();
        List<String> imageKeys = List.of(
                "showcases/images/first.jpg",
                "showcases/images/second.jpg",
                "showcases/images/third.jpg"
        );

        // When & Then
        List<String> emptyModelKeys = List.of();
        assertThatThrownBy(() ->
                createShowcaseUseCase.create(command, imageKeys, emptyModelKeys)
        ).isInstanceOf(InvalidImageKeyException.class);
    }
}
