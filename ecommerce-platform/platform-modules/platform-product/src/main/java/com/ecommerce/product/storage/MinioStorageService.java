package com.ecommerce.product.storage;

import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MinioStorageService {
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");
    private static final String READ_POLICY = """
            {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}
            """;

    private final MinioClient client;
    private final String endpoint;
    private final String bucket;

    public MinioStorageService(
            @Value("${minio.endpoint}") String endpoint,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey,
            @Value("${minio.bucket:ecommerce}") String bucket) {
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.bucket = bucket;
        this.client = MinioClient.builder()
                .endpoint(this.endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    public UploadResult uploadImage(MultipartFile file) {
        validate(file);
        String contentType = file.getContentType();
        String objectName = "products/" + LocalDate.now() + "/"
                + UUID.randomUUID() + "." + EXTENSIONS.get(contentType);

        try (InputStream inputStream = file.getInputStream()) {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
            return new UploadResult(endpoint + "/" + bucket + "/" + objectName,
                    objectName, contentType, file.getSize());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "图片上传失败，请检查 MinIO 服务");
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ErrorCode.BAD_REQUEST, "图片大小不能超过 5MB");
        }
        if (!StringUtils.hasText(file.getContentType())
                || !ALLOWED_TYPES.contains(file.getContentType().toLowerCase())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "只支持 JPG、PNG、WEBP、GIF 图片");
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        client.setBucketPolicy(SetBucketPolicyArgs.builder()
                .bucket(bucket)
                .config(READ_POLICY.formatted(bucket))
                .build());
    }

    public record UploadResult(String url, String objectName, String contentType, long size) {
    }
}
