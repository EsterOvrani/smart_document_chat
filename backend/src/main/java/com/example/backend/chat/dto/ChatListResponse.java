package com.example.backend.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO לרשימת שיחות
 * 
 * מה Backend מחזיר כשמבקשים רשימת שיחות
 * 
 * דוגמה:
 * GET /api/chats
 * Response:
 * {
 *   "chats": [...],
 *   "totalCount": 25,
 *   "readyCount": 20,
 *   "processingCount": 3,
 *   "failedCount": 2
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatListResponse {

    /**
     * רשימת השיחות
     * כל אחת היא ChatSummary (גרסה מקוצרת)
     */
    private List<ChatSummary> chats;

    /**
     * כמה שיחות בסך הכל?
     */
    private Integer totalCount;

    /**
     * כמה שיחות מוכנות?
     */
    private Integer readyCount;

    /**
     * כמה בעיבוד?
     */
    private Integer processingCount;

    /**
     * כמה נכשלו?
     */
    private Integer failedCount;

    /**
     * סטטיסטיקות כלליות
     */
    private GeneralStatistics statistics;

    // ==================== Inner Classes ====================

    /**
     * גרסה מקוצרת של שיחה (לרשימה)
     * לא שולחים את כל המסמכים וההודעות!
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatSummary {
        private Long id;
        private String title;
        private String status;
        private Integer documentCount;
        private Integer messageCount;
        private Integer pendingDocuments;
        private Boolean isReady;
        private String lastActivityAt;
        private String createdAt;
        
        /**
         * תצוגה מקוצרת של הודעה אחרונה
         */
        private String lastMessagePreview;
        
        /**
         * אחוז התקדמות (0-100)
         */
        private Integer progressPercentage;
    }

    /**
     * סטטיסטיקות כלליות על כל השיחות
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeneralStatistics {
        private Integer totalDocuments;
        private Integer totalMessages;
        private Long totalTokensUsed;
        private Double averageMessagesPerChat;
        private Double averageDocumentsPerChat;
    }

    // ==================== Helper Methods ====================

    /**
     * האם יש שיחות?
     */
    public boolean hasChats() {
        return chats != null && !chats.isEmpty();
    }

    /**
     * כמה שיחות בסך הכל?
     */
    public int getChatCount() {
        return chats != null ? chats.size() : 0;
    }

    /**
     * אחוז שיחות מוכנות
     */
    public Double getReadyPercentage() {
        if (totalCount == null || totalCount == 0) {
            return 0.0;
        }
        return (readyCount != null ? readyCount : 0) * 100.0 / totalCount;
    }

    /**
     * האם יש שיחות בעיבוד?
     */
    public boolean hasProcessingChats() {
        return processingCount != null && processingCount > 0;
    }

    /**
     * האם יש שיחות שנכשלו?
     */
    public boolean hasFailedChats() {
        return failedCount != null && failedCount > 0;
    }
}