// backend/src/main/java/com/example/backend/chat/dto/ProcessingStatusResponse.java
package com.example.backend.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO לסטטוס עיבוד מפורט של שיחה
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessingStatusResponse {

    /**
     * סטטוס כללי
     */
    private String status; // PROCESSING, READY, FAILED
    
    /**
     * האם מוכן?
     */
    private Boolean isReady;
    
    /**
     * אחוז התקדמות כולל (0-100)
     */
    private Integer overallProgress;
    
    /**
     * כמה מסמכים סה"כ
     */
    private Integer totalDocuments;
    
    /**
     * כמה מסמכים הסתיימו
     */
    private Integer completedDocuments;
    
    /**
     * כמה מסמכים עדיין מעבדים
     */
    private Integer processingDocuments;
    
    /**
     * כמה מסמכים נכשלו
     */
    private Integer failedDocuments;
    
    /**
     * זמן משוער שנותר (בשניות)
     */
    private Long estimatedTimeRemaining;
    
    /**
     * המסמך הנוכחי שמעבדים
     */
    private CurrentDocument currentDocument;
    
    /**
     * רשימת כל המסמכים והסטטוס שלהם
     */
    private List<DocumentStatus> documents;
    
    /**
     * הודעת שגיאה (אם יש)
     */
    private String errorMessage;
    
    /**
     * מתי התחיל העיבוד
     */
    private LocalDateTime processingStartedAt;
    
    /**
     * כמה זמן עבר מאז התחלת העיבוד (בשניות)
     */
    private Long elapsedTimeSeconds;

    // ==================== Inner Classes ====================

    /**
     * מידע על המסמך שמעבדים כרגע
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentDocument {
        private Long id;
        private String name;
        private Integer progress; // 0-100
        private String stage; // UPLOADING, EXTRACTING_TEXT, CREATING_EMBEDDINGS, STORING
        private Long fileSize;
        private String fileSizeFormatted;
    }

    /**
     * סטטוס של מסמך בודד
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentStatus {
        private Long id;
        private String name;
        private String status; // PENDING, PROCESSING, COMPLETED, FAILED
        private Integer progress; // 0-100
        private Long fileSize;
        private String fileSizeFormatted;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private Long processingTimeSeconds;
    }
}