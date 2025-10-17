package com.example.backend.document.dto;

import com.example.backend.document.model.Document.ProcessingStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO לפרטי מסמך
 * 
 * מה Backend מחזיר כשמבקשים מידע על מסמך
 * 
 * דוגמה:
 * GET /api/documents/1
 * Response:
 * {
 *   "id": 1,
 *   "originalFileName": "חוזה.pdf",
 *   "fileSize": 2500000,
 *   "processingStatus": "COMPLETED",
 *   "processingProgress": 100,
 *   "characterCount": 15000,
 *   "chunkCount": 12
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResponse {

    /**
     * מזהה המסמך
     */
    private Long id;

    /**
     * שם הקובץ המקורי
     */
    private String originalFileName;

    /**
     * סוג הקובץ (pdf)
     */
    private String fileType;

    /**
     * גודל הקובץ בבתים
     */
    private Long fileSize;

    /**
     * גודל הקובץ בפורמט קריא (למשל "2.5 MB")
     */
    private String fileSizeFormatted;

    /**
     * סטטוס העיבוד
     */
    private ProcessingStatus processingStatus;

    /**
     * אחוז התקדמות (0-100)
     */
    private Integer processingProgress;

    /**
     * כמה תווים במסמך
     */
    private Integer characterCount;

    /**
     * כמה chunks נוצרו
     */
    private Integer chunkCount;

    /**
     * מזהה השיחה שהמסמך שייך אליה
     */
    private Long chatId;

    /**
     * כותרת השיחה
     */
    private String chatTitle;

    /**
     * האם המסמך פעיל
     */
    private Boolean active;

    /**
     * הודעת שגיאה (אם נכשל)
     */
    private String errorMessage;

    /**
     * מתי הועלה
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * מתי הסתיים העיבוד
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime processedAt;

    /**
     * סטטיסטיקות עיבוד
     */
    private ProcessingStatistics statistics;

    // ==================== Inner Classes ====================

    /**
     * סטטיסטיקות על העיבוד
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingStatistics {
        /**
         * זמן עיבוד (במילישניות)
         */
        private Long processingTimeMs;

        /**
         * זמן עיבוד בפורמט קריא ("2.5 שניות")
         */
        private String processingTimeFormatted;

        /**
         * כמה embeddings נוצרו
         */
        private Integer embeddingsCount;

        /**
         * משוערת עלות (בדולרים)
         */
        private Double estimatedCost;
    }

    // ==================== Helper Methods ====================

    /**
     * האם המסמך מעובד?
     */
    public boolean isProcessed() {
        return processingStatus == ProcessingStatus.COMPLETED;
    }

    /**
     * האם בעיבוד?
     */
    public boolean isProcessing() {
        return processingStatus == ProcessingStatus.PROCESSING;
    }

    /**
     * האם נכשל?
     */
    public boolean hasFailed() {
        return processingStatus == ProcessingStatus.FAILED;
    }

    /**
     * האם ממתין?
     */
    public boolean isPending() {
        return processingStatus == ProcessingStatus.PENDING;
    }

    /**
     * פורמט גודל קובץ קריא
     */
    public String getFormattedFileSize() {
        if (fileSize == null) {
            return "לא ידוע";
        }

        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.2f KB", fileSize / 1024.0);
        } else {
            return String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    /**
     * תיאור הסטטוס
     */
    public String getStatusDescription() {
        return switch (processingStatus) {
            case PENDING -> "ממתין לעיבוד";
            case PROCESSING -> String.format("מעבד... (%d%%)", processingProgress);
            case COMPLETED -> "הושלם בהצלחה";
            case FAILED -> "נכשל: " + (errorMessage != null ? errorMessage : "שגיאה לא ידועה");
        };
    }

    /**
     * האם המסמך גדול? (מעל 10MB)
     */
    public boolean isLargeFile() {
        return fileSize != null && fileSize > 10 * 1024 * 1024;
    }

    /**
     * כמה זמן לקח לעבד?
     */
    public String getProcessingDuration() {
        if (processedAt == null || createdAt == null) {
            return "לא ידוע";
        }

        long seconds = java.time.Duration.between(createdAt, processedAt).getSeconds();

        if (seconds < 60) {
            return seconds + " שניות";
        } else if (seconds < 3600) {
            return (seconds / 60) + " דקות";
        } else {
            return (seconds / 3600) + " שעות";
        }
    }

    /**
     * משוערת עלות embeddings
     * OpenAI text-embedding-3-large: ~$0.13 per 1M tokens
     */
    public Double getEstimatedEmbeddingCost() {
        if (characterCount == null) {
            return 0.0;
        }

        // הערכה גסה: 1 token ≈ 4 characters
        int estimatedTokens = characterCount / 4;
        return estimatedTokens * 0.00000013; // $0.13 per 1M tokens
    }
}