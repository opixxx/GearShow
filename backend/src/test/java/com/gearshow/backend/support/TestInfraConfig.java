package com.gearshow.backend.support;

import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationPort;
import com.gearshow.backend.showcase.application.port.out.PresignedUrlPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.S3Client;

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
     * 실제 S3 연동 없이 가짜 URL을 반환하고, 모든 키를 존재하는 것으로 처리한다.
     */
    @Bean
    @Primary
    public ImageStoragePort testImageStoragePort() {
        return new ImageStoragePort() {
            @Override
            public String toUrl(String s3Key) {
                return "https://test-cdn.gearshow.com/" + s3Key;
            }

            @Override
            public boolean exists(String s3Key) {
                // 테스트에서는 모든 키를 존재하는 것으로 처리
                return true;
            }

            @Override
            public void delete(String imageUrl) {
                // 테스트에서는 삭제 무시
            }

            @Override
            public byte[] download(String imageUrl) {
                // 테스트에서는 빈 바이트 반환
                return new byte[0];
            }

            @Override
            public String upload(String s3Key, byte[] data, String contentType) {
                return "https://test-cdn.gearshow.com/" + s3Key;
            }
        };
    }

    /**
     * 테스트용 Presigned URL Stub.
     * 실제 S3Presigner(AWS SDK) 없이 결정적인 가짜 URL을 반환한다.
     */
    @Bean
    @Primary
    public PresignedUrlPort testPresignedUrlPort() {
        return (s3Key, contentType) ->
                "https://test-bucket.s3.amazonaws.com/" + s3Key + "?X-Amz-Signature=test-sig";
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
