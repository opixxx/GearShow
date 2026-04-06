package com.gearshow.backend.showcase.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * AWS S3 클라이언트 설정.
 * S3Client(일반 API)와 S3Presigner(Presigned URL 발급) 두 가지 빈을 등록한다.
 */
@Configuration
public class S3Config {

    @Value("${cloud.aws.s3.region}")
    private String region;

    @Value("${cloud.aws.s3.access-key}")
    private String accessKey;

    @Value("${cloud.aws.s3.secret-key}")
    private String secretKey;

    @Value("${cloud.aws.s3.endpoint:#{null}}")
    private String endpoint;

    /**
     * S3 일반 API 클라이언트.
     * 객체 삭제, 존재 여부 확인(headObject) 등에 사용된다.
     */
    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));

        // LocalStack 등 로컬 테스트용 엔드포인트 설정
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true);
        }

        return builder.build();
    }

    /**
     * S3 Presigned URL 생성 클라이언트.
     * 클라이언트가 서버를 거치지 않고 S3에 직접 업로드하도록 PUT 서명 URL을 발급한다.
     */
    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));

        // LocalStack 등 로컬 테스트용 엔드포인트 설정
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }
}
