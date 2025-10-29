package com.example.backend.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

/**
 * Service לניהול אחסון קבצים ב-AWS S3
 * 
 * תפקידים:
 * - העלאת קבצים
 * - הורדת קבצים
 * - מחיקת קבצים
 * - ניהול buckets
 */
@Service
@Slf4j
public class S3Service {

    // ==================== Configuration ====================
    
    @Value("${aws.s3.access-key}")
    private String accessKey;

    @Value("${aws.s3.secret-key}")
    private String secretKey;

    @Value("${aws.s3.region:us-east-1}")
    private String region;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.endpoint:}")
    private String customEndpoint;

    private S3Client s3Client;
    private S3Presigner presigner;

    // ==================== Initialization ====================

    /**
     * אתחול חיבור ל-AWS S3
     */
    @PostConstruct
    public void init() {
        try {
            log.info("Connecting to AWS S3...");
            log.info("Region: {}", region);
            log.info("Bucket: {}", bucketName);

            // יצירת credentials
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

            // בניית S3Client
            var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));

            // אם יש endpoint מותאם (למשל DigitalOcean Spaces)
            if (customEndpoint != null && !customEndpoint.isEmpty()) {
                log.info("Using custom endpoint: {}", customEndpoint);
                builder.endpointOverride(URI.create(customEndpoint));
            }

            s3Client = builder.build();

            // יצירת presigner לURL זמני
            var presignerBuilder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));

            if (customEndpoint != null && !customEndpoint.isEmpty()) {
                presignerBuilder.endpointOverride(URI.create(customEndpoint));
            }

            presigner = presignerBuilder.build();

            // יצירת bucket אם לא קיים
            createBucketIfNotExists();

            log.info("✅ Successfully connected to AWS S3");

        } catch (Exception e) {
            log.error("❌ Failed to connect to AWS S3", e);
            throw new RuntimeException("Failed to initialize S3 client", e);
        }
    }

    /**
     * סגירת החיבור בסיום
     */
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

    /**
     * יצירת bucket אם לא קיים
     */
    private void createBucketIfNotExists() {
        try {
            // בדיקה אם ה-bucket קיים
            s3Client.headBucket(HeadBucketRequest.builder()
                .bucket(bucketName)
                .build());
            
            log.info("Bucket already exists: {}", bucketName);

        } catch (NoSuchBucketException e) {
            // Bucket לא קיים - ניצור אותו
            log.info("Creating bucket: {}", bucketName);
            
            s3Client.createBucket(CreateBucketRequest.builder()
                .bucket(bucketName)
                .build());
            
            log.info("✅ Bucket created successfully: {}", bucketName);

        } catch (Exception e) {
            log.error("❌ Failed to check/create bucket: {}", bucketName, e);
            throw new RuntimeException("Failed to create S3 bucket", e);
        }
    }

    // ==================== File Operations ====================

    /**
     * העלאת קובץ ל-S3
     * 
     * @param inputStream - תוכן הקובץ
     * @param objectKey - נתיב הקובץ (למשל: "users/5/chats/1/doc.pdf")
     * @param contentType - סוג הקובץ (למשל: "application/pdf")
     * @param size - גודל הקובץ
     */
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
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    /**
     * הורדת קובץ מ-S3
     * 
     * @param objectKey - נתיב הקובץ
     * @return InputStream של הקובץ
     */
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
            throw new RuntimeException("Failed to download file from S3", e);
        }
    }

    /**
     * מחיקת קובץ מ-S3
     * 
     * @param objectKey - נתיב הקובץ
     */
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
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }

    /**
     * בדיקה אם קובץ קיים
     */
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
            throw new RuntimeException("Failed to check file existence", e);
        }
    }

    /**
     * קבלת מידע על קובץ
     */
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
            throw new RuntimeException("Failed to get file info from S3", e);
        }
    }

    /**
     * רשימת קבצים בתיקייה
     * 
     * @param prefix - תחילת הנתיב (למשל: "users/5/")
     * @return רשימת קבצים
     */
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
                    // רק קבצים, לא "תיקיות" (keys שמסתיימים ב-/)
                    if (!s3Object.key().endsWith("/")) {
                        files.add(s3Object.key());
                    }
                }

                // המשך לדף הבא אם יש
                request = request.toBuilder()
                    .continuationToken(response.nextContinuationToken())
                    .build();

            } while (response.isTruncated());

            log.info("✅ Found {} files with prefix: {}", files.size(), prefix);
            return files;

        } catch (Exception e) {
            log.error("❌ Failed to list files with prefix: {}", prefix, e);
            throw new RuntimeException("Failed to list files from S3", e);
        }
    }

    /**
     * מחיקת כל הקבצים בתיקייה
     * 
     * @param prefix - תחילת הנתיב
     */
    public void deleteFolder(String prefix) {
        try {
            log.info("🗑️ Deleting folder: {}", prefix);

            List<String> files = listFiles(prefix);
            
            if (files.isEmpty()) {
                log.info("No files to delete in folder: {}", prefix);
                return;
            }

            // מחיקה ב-batch (עד 1000 קבצים בפעם)
            List<ObjectIdentifier> toDelete = new ArrayList<>();
            
            for (String key : files) {
                toDelete.add(ObjectIdentifier.builder().key(key).build());
                
                // כל 1000 קבצים - מחק
                if (toDelete.size() == 1000) {
                    deleteObjects(toDelete);
                    toDelete.clear();
                }
            }

            // מחק את השאר
            if (!toDelete.isEmpty()) {
                deleteObjects(toDelete);
            }

            log.info("✅ Deleted {} files from folder: {}", files.size(), prefix);

        } catch (Exception e) {
            log.error("❌ Failed to delete folder: {}", prefix, e);
            throw new RuntimeException("Failed to delete folder from S3", e);
        }
    }

    /**
     * מחיקת מספר קבצים בבת אחת
     */
    private void deleteObjects(List<ObjectIdentifier> objects) {
        s3Client.deleteObjects(
            DeleteObjectsRequest.builder()
                .bucket(bucketName)
                .delete(Delete.builder().objects(objects).build())
                .build()
        );
    }

    /**
     * קבלת URL זמני לקובץ (לצפייה/הורדה)
     * 
     * @param objectKey - נתיב הקובץ
     * @param expirySeconds - כמה זמן ה-URL תקף (בשניות)
     * @return URL זמני
     */
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

    /**
     * העתקת קובץ
     */
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
            throw new RuntimeException("Failed to copy file in S3", e);
        }
    }

    /**
     * קבלת גודל כולל של תיקייה
     */
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

    // ==================== Inner Classes ====================

    /**
     * מידע על קובץ
     */
    @Data
    public static class FileInfo {
        private String objectName;
        private Long size;
        private String contentType;
        private Instant lastModified;
        private String eTag;

        /**
         * גודל קריא (למשל: "2.5 MB")
         */
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