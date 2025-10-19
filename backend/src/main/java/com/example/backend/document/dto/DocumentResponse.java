package com.example.backend.document.dto;

import com.example.backend.document.model.Document.ProcessingStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResponse {

    private Long id;
    private String originalFileName;
    private String fileType;
    private Long fileSize;
    private String fileSizeFormatted;
    
    // ✅ נוסף!
    private String filePath;
    
    private ProcessingStatus processingStatus;
    private Integer processingProgress;
    private Integer characterCount;
    private Integer chunkCount;
    private Long chatId;
    private String chatTitle;
    private Boolean active;
    private String errorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime processedAt;

    private ProcessingStatistics statistics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingStatistics {
        private Long processingTimeMs;
        private String processingTimeFormatted;
        private Integer embeddingsCount;
        private Double estimatedCost;
    }

    public boolean isProcessed() {
        return processingStatus == ProcessingStatus.COMPLETED;
    }

    public boolean isProcessing() {
        return processingStatus == ProcessingStatus.PROCESSING;
    }

    public boolean hasFailed() {
        return processingStatus == ProcessingStatus.FAILED;
    }

    public boolean isPending() {
        return processingStatus == ProcessingStatus.PENDING;
    }

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

    public String getStatusDescription() {
        return switch (processingStatus) {
            case PENDING -> "ממתין לעיבוד";
            case PROCESSING -> String.format("מעבד... (%d%%)", processingProgress);
            case COMPLETED -> "הושלם בהצלחה";
            case FAILED -> "נכשל: " + (errorMessage != null ? errorMessage : "שגיאה לא ידועה");
        };
    }

    public boolean isLargeFile() {
        return fileSize != null && fileSize > 10 * 1024 * 1024;
    }

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

    public Double getEstimatedEmbeddingCost() {
        if (characterCount == null) {
            return 0.0;
        }

        int estimatedTokens = characterCount / 4;
        return estimatedTokens * 0.00000013;
    }
}