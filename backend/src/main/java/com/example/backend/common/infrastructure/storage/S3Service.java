package com.example.backend.common.infrastructure.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.backend.common.exception.ExternalServiceException;
import com.example.backend.common.exception.FileProcessingException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Service
@Slf4j
public class S3Service {

    @Value("${aws.s3.access-key}")
    private String accessKey;

    @Value("${aws.s3.secret-key}")
    private String secretKey;

    @Value("${aws.s3.region:eu-west-1}")
    private String region;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.endpoint:}")
    private String customEndpoint;

    private S3Client s3Client;
    private S3Presigner presigner;

    @PostConstruct
    public void init() {
        try {
            log.info("========================================");
            log.info("🔵 Initializing AWS S3 Service...");
            log.info("========================================");

            // Validation - check environment variables
            validateConfiguration();

            log.info("✅ Configuration validated successfully");
            log.info("Region: {}", region);
            log.info("Bucket: {}", bucketName);
            log.info("Access Key: {}***", accessKey.substring(0, Math.min(4, accessKey.length())));

            if (customEndpoint != null && !customEndpoint.isEmpty()) {
                log.info("Custom Endpoint: {}", customEndpoint);
            }

            // Create credentials
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

            // Build S3Client
            var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));

            // If custom endpoint exists (LocalStack, DigitalOcean Spaces, etc.)
            if (customEndpoint != null && !customEndpoint.isEmpty()) {
                log.info("Using custom endpoint: {}", customEndpoint);
                builder.endpointOverride(URI.create(customEndpoint));
            }

            s3Client = builder.build();

            // Create presigner for temporary URLs
            var presignerBuilder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));

            if (customEndpoint != null && !customEndpoint.isEmpty()) {
                presignerBuilder.endpointOverride(URI.create(customEndpoint));
            }

            presigner = presignerBuilder.build();

            // Create bucket if it doesn't exist
            createBucketIfNotExists();

            log.info("========================================");
            log.info("✅ AWS S3 Service initialized successfully!");
            log.info("========================================");

        } catch (Exception e) {
            log.error("========================================");
            log.error("❌ FAILED to initialize AWS S3 Service");
            log.error("========================================");
            log.error("Error details:", e);
            throw ExternalServiceException.storageServiceError("נכשל באתחול S3 client: " + e.getMessage());
        }
    }

    /**
     * Validate configuration
     */
    private void validateConfiguration() {
        List<String> errors = new ArrayList<>();

        if (accessKey == null || accessKey.isEmpty() || accessKey.equals("your-aws-access-key")) {
            errors.add("AWS_ACCESS_KEY_ID is not configured or contains placeholder value!");
        }

        if (secretKey == null || secretKey.isEmpty() || secretKey.equals("your-aws-secret-key")) {
            errors.add("AWS_SECRET_ACCESS_KEY is not configured or contains placeholder value!");
        }

        if (bucketName == null || bucketName.isEmpty()) {
            errors.add("AWS_S3_BUCKET is not configured!");
        }

        if (region == null || region.isEmpty()) {
            errors.add("AWS_REGION is not configured!");
        }

        if (!errors.isEmpty()) {
            log.error("❌ S3 Configuration errors found:");
            errors.forEach(error -> log.error("   - {}", error));
            log.error("");
            log.error("💡 Please check your .env file and ensure all AWS credentials are set correctly.");
            log.error("💡 Example .env file:");
            log.error("   AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE");
            log.error("   AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            log.error("   AWS_REGION=eu-west-1");
            log.error("   AWS_S3_BUCKET=my-bucket-name");

            throw new IllegalStateException("S3 configuration is invalid. Missing or incorrect AWS credentials.");
        }
    }

    @PreDestroy
    public void cleanup() {
        if (s3Client != null) {
            s3Client.close();
        }
        if (presigner != null) {
            presigner.close();
        }
        log.info("S3 client closed");
    }

    private void createBucketIfNotExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                .bucket(bucketName)
                .build());

            log.info("✅ Bucket already exists: {}", bucketName);

        } catch (NoSuchBucketException e) {
            log.info("📦 Creating bucket: {}", bucketName);

            s3Client.createBucket(CreateBucketRequest.builder()
                .bucket(bucketName)
                .build());

            log.info("✅ Bucket created successfully: {}", bucketName);

        } catch (Exception e) {
            log.error("❌ Failed to check/create bucket: {}", bucketName, e);
            throw ExternalServiceException.storageServiceError("נכשל ביצירת S3 bucket: " + e.getMessage());
        }
    }

    public void uploadFile(
            InputStream inputStream,
            String objectKey,
            String contentType,
            long size) {

        try {
            log.info("📤 Uploading file to S3: {}", objectKey);

            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(size)
                    .build(),
                RequestBody.fromInputStream(inputStream, size)
            );

            log.info("✅ File uploaded successfully: {}", objectKey);

        } catch (Exception e) {
            log.error("❌ Failed to upload file: {}", objectKey, e);
            throw FileProcessingException.uploadFailed(objectKey);
        }
    }

    public InputStream downloadFile(String objectKey) {
        try {
            log.info("📥 Downloading file from S3: {}", objectKey);

            var response = s3Client.getObject(
                GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build()
            );

            log.info("✅ File downloaded successfully: {}", objectKey);
            return response;

        } catch (Exception e) {
            log.error("❌ Failed to download file: {}", objectKey, e);
            throw ExternalServiceException.storageServiceError("נכשל בהורדת קובץ מ-S3: " + objectKey);
        }
    }

    public void deleteFile(String objectKey) {
        try {
            log.info("🗑️ Deleting file from S3: {}", objectKey);

            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build()
            );

            log.info("✅ File deleted successfully: {}", objectKey);

        } catch (Exception e) {
            log.error("❌ Failed to delete file: {}", objectKey, e);
            throw ExternalServiceException.storageServiceError("Failed to delete file from S3" + e);
        }
    }

    public boolean fileExists(String objectKey) {
        try {
            s3Client.headObject(
                HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build()
            );
            return true;

        } catch (NoSuchKeyException e) {
            return false;

        } catch (Exception e) {
            log.error("❌ Failed to check if file exists: {}", objectKey, e);
            throw ExternalServiceException.storageServiceError("S3: Failed to check file existence" + e);
        }
    }

    public FileInfo getFileInfo(String objectKey) {
        try {
            var response = s3Client.headObject(
                HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build()
            );

            FileInfo info = new FileInfo();
            info.setObjectName(objectKey);
            info.setSize(response.contentLength());
            info.setContentType(response.contentType());
            info.setLastModified(response.lastModified());
            info.setETag(response.eTag());

            return info;

        } catch (Exception e) {
            log.error("❌ Failed to get file info: {}", objectKey, e);
            throw ExternalServiceException.storageServiceError("Failed to get file info from S3" + e);
        }
    }

    public List<String> listFiles(String prefix) {
        try {
            log.info("📋 Listing files with prefix: {}", prefix);

            List<String> files = new ArrayList<>();

            ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

            ListObjectsV2Response response;
            do {
                response = s3Client.listObjectsV2(request);

                for (S3Object s3Object : response.contents()) {
                    if (!s3Object.key().endsWith("/")) {
                        files.add(s3Object.key());
                    }
                }

                request = request.toBuilder()
                    .continuationToken(response.nextContinuationToken())
                    .build();

            } while (response.isTruncated());

            log.info("✅ Found {} files with prefix: {}", files.size(), prefix);
            return files;

        } catch (Exception e) {
            log.error("❌ Failed to list files with prefix: {}", prefix, e);
            throw ExternalServiceException.storageServiceError("Failed to list files from S3" + e);
        }
    }

    public void deleteFolder(String prefix) {
        try {
            log.info("🗑️ Deleting folder: {}", prefix);

            List<String> files = listFiles(prefix);

            if (files.isEmpty()) {
                log.info("📂 No files to delete in folder: {}", prefix);
                return;
            }

            List<ObjectIdentifier> toDelete = new ArrayList<>();

            for (String key : files) {
                toDelete.add(ObjectIdentifier.builder().key(key).build());

                if (toDelete.size() == 1000) {
                    deleteObjects(toDelete);
                    toDelete.clear();
                }
            }

            if (!toDelete.isEmpty()) {
                deleteObjects(toDelete);
            }

            log.info("✅ Deleted {} files from folder: {}", files.size(), prefix);

        } catch (Exception e) {
            log.error("❌ Failed to delete folder: {}", prefix, e);
            throw ExternalServiceException.storageServiceError("Failed to delete folder from S3" + e);
        }
    }

    private void deleteObjects(List<ObjectIdentifier> objects) {
        s3Client.deleteObjects(
            DeleteObjectsRequest.builder()
                .bucket(bucketName)
                .delete(Delete.builder().objects(objects).build())
                .build()
        );
    }

    public String getPresignedUrl(String objectKey, int expirySeconds) {
        try {
            log.info("🔗 Generating presigned URL for: {} (expiry: {}s)",
                objectKey, expirySeconds);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .getObjectRequest(getObjectRequest)
                .build();

            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
            String url = presignedRequest.url().toString();

            log.info("✅ Presigned URL generated successfully");
            return url;

        } catch (Exception e) {
            log.error("❌ Failed to generate presigned URL: {}", objectKey, e);
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }

    public void copyFile(String sourceKey, String destinationKey) {
        try {
            log.info("📋 Copying file from {} to {}", sourceKey, destinationKey);

            s3Client.copyObject(
                CopyObjectRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(sourceKey)
                    .destinationBucket(bucketName)
                    .destinationKey(destinationKey)
                    .build()
            );

            log.info("✅ File copied successfully");

        } catch (Exception e) {
            log.error("❌ Failed to copy file", e);
            throw ExternalServiceException.storageServiceError("Failed to copy file in S3" + e);
        }
    }

    public long getFolderSize(String prefix) {
        try {
            List<String> files = listFiles(prefix);
            long totalSize = 0;

            for (String key : files) {
                FileInfo info = getFileInfo(key);
                totalSize += info.getSize();
            }

            log.info("📊 Total size of folder {}: {} bytes", prefix, totalSize);
            return totalSize;

        } catch (Exception e) {
            log.error("❌ Failed to calculate folder size: {}", prefix, e);
            throw new RuntimeException("Failed to calculate folder size", e);
        }
    }

    @Data
    public static class FileInfo {
        private String objectName;
        private Long size;
        private String contentType;
        private Instant lastModified;
        private String eTag;

        public String getFormattedSize() {
            if (size == null) return "Unknown";

            if (size < 1024) {
                return size + " B";
            } else if (size < 1024 * 1024) {
                return String.format("%.2f KB", size / 1024.0);
            } else if (size < 1024 * 1024 * 1024) {
                return String.format("%.2f MB", size / (1024.0 * 1024.0));
            } else {
                return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
            }
        }
    }
}
