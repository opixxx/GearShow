package com.gearshow.backend.support;

import com.gearshow.backend.showcase.application.dto.UploadFile;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;

/**
 * 테스트용 인프라 설정.
 * S3, Kafka, 3D 모델 클라이언트 등 외부 연동을 Mock으로 대체한다.
 */
@TestConfiguration
public class TestInfraConfig {

    /**
     * 테스트용 S3Client Mock.
     */
    @Bean
    @Primary
    public S3Client testS3Client() {
        return mock(S3Client.class);
    }

    /**
     * 테스트용 이미지 저장소 Mock.
     * 실제 S3 업로드 대신 가짜 URL을 반환한다.
     */
    @Bean
    @Primary
    public ImageStoragePort testImageStoragePort() {
        return new ImageStoragePort() {
            @Override
            public String upload(String directory, UploadFile file) {
                return "https://test-cdn.gearshow.com/" + directory + "/" + UUID.randomUUID() + ".jpg";
            }

            @Override
            public List<String> uploadAll(String directory, List<UploadFile> files) {
                List<String> urls = new ArrayList<>();
                for (UploadFile file : files) {
                    urls.add(upload(directory, file));
                }
                return urls;
            }

            @Override
            public void delete(String imageUrl) {
                // 테스트에서는 삭제 무시
            }
        };
    }

    /**
     * 테스트용 Kafka Producer Mock.
     * 실제 Kafka 발행 대신 로그만 남긴다.
     */
    @Bean
    @Primary
    public ModelGenerationPort testModelGenerationPort() {
        return mock(ModelGenerationPort.class);
    }

    /**
     * 테스트용 3D 모델 생성 클라이언트 Mock.
     * 실제 외부 3D 생성 API 호출 대신 Mock을 사용한다.
     */
    @Bean
    @Primary
    public ModelGenerationClient testModelGenerationClient() {
        return mock(ModelGenerationClient.class);
    }
}
