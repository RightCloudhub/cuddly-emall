package com.example.mall.infrastructure.storage;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Uploads product images under a {@code mall/} prefix in the shared MinIO bucket. */
@Service
public class ImageStorageService {

    private static final String PREFIX = "mall/products/";

    private final MinioClient minio;
    private final String bucket;
    private final String endpoint;

    public ImageStorageService(
            MinioClient minio,
            @Value("${mall.minio.bucket}") String bucket,
            @Value("${mall.minio.endpoint}") String endpoint) {
        this.minio = minio;
        this.bucket = bucket;
        this.endpoint = endpoint;
    }

    public String upload(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("only image uploads are allowed");
        }
        String key = PREFIX + UUID.randomUUID() + suffixFor(file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            minio.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .stream(in, file.getSize(), -1)
                            .contentType(contentType)
                            .build());
        } catch (MinioException | IOException | NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new ImageStorageException("upload failed", e);
        }
        return publicUrl(key);
    }

    private String publicUrl(String key) {
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return base + "/" + bucket + "/" + key;
    }

    private static String suffixFor(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }

    public static class ImageStorageException extends RuntimeException {
        ImageStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
