package com.example.backend.shared.service;

import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Service לניהול אחסון קבצים ב-MinIO
 * 
 * MinIO = S3-compatible object storage
 * 
 * תפקידים:
 * - העלאת קבצים
 * - הורדת קבצים
 * - מחיקת קבצים
 * - ניהול buckets
 */
@Service
@Slf4j
public class MinioService {

    // ==================== Configuration ====================
    
    @Value("${minio.url:http://localhost:9000}")
    private String minioUrl;

    @Value("${minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${minio.secret-key:minioadmin123}")
    private String secretKey;

    @Value("${minio.bucket-name:smart-document-chat}")
    private String bucketName;

    private MinioClient minioClient;

    // ==================== Initialization ====================

    /**
     * אתחול חיבור ל-MinIO
     */
    @PostConstruct
    public void init() {
        try {
            log.info("Connecting to MinIO at: {}", minioUrl);

            // יצירת client
            minioClient = MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(accessKey, secretKey)
                .build();

            // יצירת bucket אם לא קיים
            createBucketIfNotExists();

            log.info("Successfully connected to MinIO");

        } catch (Exception e) {
            log.error("Failed to connect to MinIO", e);
            throw new RuntimeException("Failed to initialize MinIO client", e);
        }
    }

    /**
     * יצירת bucket אם לא קיים
     */
    private void createBucketIfNotExists() {
        try {
            boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build()
            );

            if (!exists) {
                log.info("Creating bucket: {}", bucketName);
                minioClient.makeBucket(
                    MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build()
                );
                log.info("Bucket created successfully: {}", bucketName);
            } else {
                log.info("Bucket already exists: {}", bucketName);
            }

        } catch (Exception e) {
            log.error("Failed to create bucket: {}", bucketName, e);
            throw new RuntimeException("Failed to create MinIO bucket", e);
        }
    }

    // ==================== File Operations ====================

    /**
     * העלאת קובץ ל-MinIO
     * 
     * @param inputStream - תוכן הקובץ
     * @param objectName - נתיב הקובץ (למשל: "users/5/chats/1/doc.pdf")
     * @param contentType - סוג הקובץ (למשל: "application/pdf")
     * @param size - גודל הקובץ
     */
    public void uploadFile(
            InputStream inputStream,
            String objectName,
            String contentType,
            long size) {

        try {
            log.info("Uploading file to MinIO: {}", objectName);

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, size, -1)  // -1 = unknown part size
                    .contentType(contentType)
                    .build()
            );

            log.info("File uploaded successfully: {}", objectName);

        } catch (Exception e) {
            log.error("Failed to upload file: {}", objectName, e);
            throw new RuntimeException("Failed to upload file to MinIO", e);
        }
    }

    /**
     * הורדת קובץ מ-MinIO
     * 
     * @param objectName - נתיב הקובץ
     * @return InputStream של הקובץ
     */
    public InputStream downloadFile(String objectName) {
        try {
            log.info("Downloading file from MinIO: {}", objectName);

            InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build()
            );

            log.info("File downloaded successfully: {}", objectName);
            return stream;

        } catch (Exception e) {
            log.error("Failed to download file: {}", objectName, e);
            throw new RuntimeException("Failed to download file from MinIO", e);
        }
    }

    /**
     * מחיקת קובץ מ-MinIO
     * 
     * @param objectName - נתיב הקובץ
     */
    public void deleteFile(String objectName) {
        try {
            log.info("Deleting file from MinIO: {}", objectName);

            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build()
            );

            log.info("File deleted successfully: {}", objectName);

        } catch (Exception e) {
            log.error("Failed to delete file: {}", objectName, e);
            throw new RuntimeException("Failed to delete file from MinIO", e);
        }
    }

    /**
     * בדיקה אם קובץ קיים
     */
    public boolean fileExists(String objectName) {
        try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build()
            );
            return true;

        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return false;
            }
            log.error("Failed to check if file exists: {}", objectName, e);
            throw new RuntimeException("Failed to check file existence", e);
            
        } catch (Exception e) {
            log.error("Failed to check if file exists: {}", objectName, e);
            throw new RuntimeException("Failed to check file existence", e);
        }
    }

    /**
     * קבלת מידע על קובץ
     */
    public FileInfo getFileInfo(String objectName) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build()
            );

            FileInfo info = new FileInfo();
            info.setObjectName(objectName);
            info.setSize(stat.size());
            info.setContentType(stat.contentType());
            info.setLastModified(stat.lastModified().toInstant());
            info.setETag(stat.etag());

            return info;

        } catch (Exception e) {
            log.error("Failed to get file info: {}", objectName, e);
            throw new RuntimeException("Failed to get file info from MinIO", e);
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
            log.info("Listing files with prefix: {}", prefix);

            List<String> files = new ArrayList<>();

            Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .recursive(true)
                    .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir()) {  // רק קבצים, לא תיקיות
                    files.add(item.objectName());
                }
            }

            log.info("Found {} files with prefix: {}", files.size(), prefix);
            return files;

        } catch (Exception e) {
            log.error("Failed to list files with prefix: {}", prefix, e);
            throw new RuntimeException("Failed to list files from MinIO", e);
        }
    }

    /**
     * מחיקת כל הקבצים בתיקייה
     * 
     * @param prefix - תחילת הנתיב
     */
    public void deleteFolder(String prefix) {
        try {
            log.info("Deleting folder: {}", prefix);

            List<String> files = listFiles(prefix);
            
            for (String file : files) {
                deleteFile(file);
            }

            log.info("Deleted {} files from folder: {}", files.size(), prefix);

        } catch (Exception e) {
            log.error("Failed to delete folder: {}", prefix, e);
            throw new RuntimeException("Failed to delete folder from MinIO", e);
        }
    }

    /**
     * קבלת URL זמני לקובץ (לצפייה/הורדה)
     * 
     * @param objectName - נתיב הקובץ
     * @param expirySeconds - כמה זמן ה-URL תקף (בשניות)
     * @return URL זמני
     */
    public String getPresignedUrl(String objectName, int expirySeconds) {
        try {
            log.info("Generating presigned URL for: {} (expiry: {}s)", 
                objectName, expirySeconds);

            String url = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(io.minio.http.Method.GET)
                    .bucket(bucketName)
                    .object(objectName)
                    .expiry(expirySeconds)
                    .build()
            );

            log.info("Presigned URL generated successfully");
            return url;

        } catch (Exception e) {
            log.error("Failed to generate presigned URL: {}", objectName, e);
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }

    /**
     * העתקת קובץ
     */
    public void copyFile(String sourceObjectName, String destinationObjectName) {
        try {
            log.info("Copying file from {} to {}", sourceObjectName, destinationObjectName);

            minioClient.copyObject(
                CopyObjectArgs.builder()
                    .bucket(bucketName)
                    .object(destinationObjectName)
                    .source(
                        CopySource.builder()
                            .bucket(bucketName)
                            .object(sourceObjectName)
                            .build()
                    )
                    .build()
            );

            log.info("File copied successfully");

        } catch (Exception e) {
            log.error("Failed to copy file", e);
            throw new RuntimeException("Failed to copy file in MinIO", e);
        }
    }

    /**
     * קבלת גודל כולל של תיקייה
     */
    public long getFolderSize(String prefix) {
        try {
            List<String> files = listFiles(prefix);
            long totalSize = 0;

            for (String file : files) {
                FileInfo info = getFileInfo(file);
                totalSize += info.getSize();
            }

            log.info("Total size of folder {}: {} bytes", prefix, totalSize);
            return totalSize;

        } catch (Exception e) {
            log.error("Failed to calculate folder size: {}", prefix, e);
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
        private java.time.Instant lastModified;
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