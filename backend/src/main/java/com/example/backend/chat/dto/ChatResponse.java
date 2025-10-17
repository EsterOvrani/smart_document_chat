package com.example.backend.chat.dto;

import com.example.backend.chat.model.Chat.ChatStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO לתגובה עם פרטי שיחה
 * 
 * מה Backend מחזיר ל-Frontend אחרי בקשה
 * 
 * דוגמה:
 * GET /api/chats/1
 * Response:
 * {
 *   "id": 1,
 *   "title": "ניתוח חוזה",
 *   "status": "READY",
 *   "documentCount": 3,
 *   "messageCount": 10,
 *   "userName": "John Doe",
 *   "createdAt": "2025-01-15T10:30:00"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder  // מאפשר: ChatResponse.builder().id(1).title("x").build()
public class ChatResponse {

    /**
     * מזהה השיחה
     */
    private Long id;

    /**
     * כותרת השיחה
     */
    private String title;

    /**
     * סטטוס השיחה
     * CREATING, PROCESSING, READY, FAILED
     */
    private ChatStatus status;

    /**
     * שם המשתמש שיצר (לא כל ה-User!)
     */
    private String userName;

    /**
     * מזהה המשתמש
     */
    private Long userId;

    /**
     * כמה מסמכים בשיחה?
     */
    private Integer documentCount;

    /**
     * כמה הודעות בשיחה?
     */
    private Integer messageCount;

    /**
     * כמה מסמכים עדיין מעבדים?
     */
    private Integer pendingDocuments;

    /**
     * שם הקולקשן ב-Qdrant
     */
    private String vectorCollectionName;

    /**
     * האם השיחה מוכנה לשאלות?
     */
    private Boolean isReady;

    /**
     * האם פעילה?
     */
    private Boolean active;

    /**
     * מתי נוצרה
     * @JsonFormat - מגדיר את פורמט התאריך ב-JSON
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * מתי עודכנה
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * פעילות אחרונה
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastActivityAt;

    /**
     * הודעת שגיאה (אם יש)
     */
    private String errorMessage;

    /**
     * רשימת מסמכים (אופציונלי - רק אם מבקשים)
     */
    private List<DocumentInfo> documents;

    /**
     * סטטיסטיקות נוספות (אופציונלי)
     */
    private ChatStatistics statistics;

    // ==================== Inner Classes ====================

    /**
     * מידע בסיסי על מסמך (לא כל ה-Document Entity!)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentInfo {
        private Long id;
        private String originalFileName;
        private String processingStatus;
        private Long fileSize;
        private Integer processingProgress;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime uploadedAt;
    }

    /**
     * סטטיסטיקות על השיחה
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatStatistics {
        private Integer totalDocuments;
        private Integer completedDocuments;
        private Integer failedDocuments;
        private Integer totalMessages;
        private Integer userMessages;
        private Integer assistantMessages;
        private Long totalTokensUsed;
        private Double averageResponseTime;
        private Double averageConfidence;
    }

    // ==================== Helper Methods ====================

    /**
     * האם יש שגיאה?
     */
    public boolean hasError() {
        return errorMessage != null && !errorMessage.isEmpty();
    }

    /**
     * האם בעיבוד?
     */
    public boolean isProcessing() {
        return status == ChatStatus.PROCESSING;
    }

    /**
     * האם נכשל?
     */
    public boolean isFailed() {
        return status == ChatStatus.FAILED;
    }

    /**
     * אחוז התקדמות (0-100)
     * מחושב לפי מסמכים שהסתיימו
     */
    public Integer getProgressPercentage() {
        if (documentCount == null || documentCount == 0) {
            return 0;
        }

        if (pendingDocuments == null) {
            return 100;
        }

        int completed = documentCount - pendingDocuments;
        return (completed * 100) / documentCount;
    }

    /**
     * תיאור קצר של הסטטוס
     */
    public String getStatusDescription() {
        return switch (status) {
            case CREATING -> "יוצר שיחה...";
            case PROCESSING -> String.format("מעבד מסמכים (%d/%d)", 
                documentCount - pendingDocuments, documentCount);
            case READY -> "מוכן לשאלות";
            case FAILED -> "נכשל בעיבוד";
        };
    }
}