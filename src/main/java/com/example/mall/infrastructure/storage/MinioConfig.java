package com.example.mall.infrastructure.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(
            @Value("${mall.minio.endpoint}") String endpoint,
            @Value("${mall.minio.access-key}") String accessKey,
            @Value("${mall.minio.secret-key}") String secretKey,
            @Value("${mall.minio.bucket}") String bucket) {
        MinioClient client =
                MinioClient.builder()
                        .endpoint(endpoint)
                        .credentials(accessKey, secretKey)
                        .build();
        try {
            boolean exists =
                    client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            // bootstrap is best-effort; first real upload will surface meaningful error.
            // Don't fail context startup so we can run tests without a live MinIO.
        }
        return client;
    }
}
