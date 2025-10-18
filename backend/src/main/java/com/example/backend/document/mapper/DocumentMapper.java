package com.example.backend.document.mapper;

import com.example.backend.document.dto.DocumentResponse;
import com.example.backend.document.model.Document;
import org.mapstruct.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper להמרה בין Document Entity ל-DTOs
 */
@Mapper(
    componentModel = "spring",
    injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
public interface DocumentMapper {

    /**
     * המרה מ-Document Entity ל-DocumentResponse DTO
     */
    @Mapping(source = "chat.id", target = "chatId")
    @Mapping(source = "chat.title", target = "chatTitle")
    @Mapping(target = "fileSizeFormatted", expression = "java(formatFileSize(document.getFileSize()))")
    @Mapping(target = "statistics", ignore = true)  // נמלא בService
    DocumentResponse toResponse(Document document);

    /**
     * המרה של רשימה
     */
    List<DocumentResponse> toResponseList(List<Document> documents);

    // ==================== Helper Methods ====================

    /**
     * פורמט גודל קובץ קריא
     */
    default String formatFileSize(Long fileSize) {
        if (fileSize == null) {
            return "לא ידוע";
        }

        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.2f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * חישוב סטטיסטיקות עיבוד
     */
    default DocumentResponse.ProcessingStatistics buildStatistics(Document document) {
        if (document.getCreatedAt() == null || document.getProcessedAt() == null) {
            return null;
        }

        Duration duration = Duration.between(document.getCreatedAt(), document.getProcessedAt());
        long millis = duration.toMillis();

        return DocumentResponse.ProcessingStatistics.builder()
                .processingTimeMs(millis)
                .processingTimeFormatted(formatDuration(millis))
                .embeddingsCount(document.getChunkCount())
                .estimatedCost(calculateEmbeddingCost(document.getCharacterCount()))
                .build();
    }

    /**
     * פורמט משך זמן
     */
    default String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        }

        long seconds = millis / 1000;
        if (seconds < 60) {
            return seconds + " שניות";
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " דקות";
        }

        long hours = minutes / 60;
        return hours + " שעות";
    }

    /**
     * חישוב עלות embeddings
     * OpenAI text-embedding-3-large: ~$0.13 per 1M tokens
     */
    default Double calculateEmbeddingCost(Integer characterCount) {
        if (characterCount == null) {
            return 0.0;
        }

        // הערכה: 1 token ≈ 4 characters
        int estimatedTokens = characterCount / 4;
        return estimatedTokens * 0.00000013;
    }

    /**
     * עדכון Document מ-DTO
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDocumentFromResponse(DocumentResponse response, @MappingTarget Document document);

    /**
     * העשרת התגובה אחרי המרה
     */
    @AfterMapping
    default void enrichDocumentResponse(@MappingTarget DocumentResponse response, Document document) {
        // הוסף סטטיסטיקות אם המסמך הושלם
        if (document.isProcessed()) {
            response.setStatistics(buildStatistics(document));
        }

        // וודא שיש fileSizeFormatted
        if (response.getFileSizeFormatted() == null) {
            response.setFileSizeFormatted(formatFileSize(document.getFileSize()));
        }
    }
}