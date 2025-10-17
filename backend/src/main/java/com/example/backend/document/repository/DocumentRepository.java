package com.example.backend.document.repository;

import com.example.backend.document.model.Document;
import com.example.backend.document.model.Document.ProcessingStatus;
import com.example.backend.chat.model.Chat;
import com.example.backend.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository לניהול מסמכים (Documents)
 * 
 * מטפל בכל הפעולות על טבלת documents:
 * - חיפוש מסמכים לפי שיחה
 * - חיפוש לפי סטטוס עיבוד
 * - מעקב אחרי מסמכים שנכשלו
 * - בדיקת כפילויות
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    // ==================== חיפוש לפי שיחה ====================

    /**
     * כל המסמכים של שיחה (פעילים בלבד)
     * ממוין לפי תאריך העלאה (החדש ביותר ראשון)
     */
    List<Document> findByChatAndActiveTrueOrderByCreatedAtDesc(Chat chat);

    /**
     * רק מסמכים שהסתיימו (COMPLETED)
     * 
     * למה זה חשוב?
     * כשרוצים לשאול שאלה, צריך רק מסמכים מעובדים!
     */
    List<Document> findByChatAndProcessingStatusAndActiveTrue(
        Chat chat, 
        ProcessingStatus status
    );

    /**
     * ספירה - כמה מסמכים בשיחה?
     */
    long countByChatAndActiveTrue(Chat chat);

    /**
     * כמה מסמכים עדיין מעבדים?
     */
    long countByChatAndProcessingStatusAndActiveTrue(
        Chat chat, 
        ProcessingStatus status
    );

    // ==================== חיפוש לפי משתמש ====================

    /**
     * כל המסמכים של משתמש (מכל השיחות)
     */
    List<Document> findByUserOrderByCreatedAtDesc(User user);

    /**
     * מסמכים של משתמש לפי סטטוס
     */
    List<Document> findByUserAndProcessingStatusAndActiveTrue(
        User user, 
        ProcessingStatus status
    );

    /**
     * ספירה - כמה מסמכים יש למשתמש בסטטוס מסוים?
     */
    long countByUserAndProcessingStatus(User user, ProcessingStatus status);

    // ==================== חיפוש לפי ID ====================

    /**
     * חיפוש מסמך לפי ID (פעיל בלבד)
     */
    Optional<Document> findByIdAndActiveTrue(Long id);

    /**
     * חיפוש מסמך של משתמש ספציפי
     * 
     * למה?
     * בדיקת הרשאות! משתמש לא יכול למחוק מסמך של מישהו אחר
     */
    Optional<Document> findByIdAndUserAndActiveTrue(Long id, User user);

    // ==================== בדיקת כפילויות ====================

    /**
     * בדיקה אם קובץ עם אותו שם כבר קיים בשיחה
     * 
     * למה זה חשוב?
     * למנוע העלאת אותו קובץ פעמיים
     */
    Optional<Document> findByChatAndOriginalFileNameAndActiveTrue(
        Chat chat, 
        String originalFileName
    );

    /**
     * בדיקה לפי Hash של התוכן
     * 
     * למה Hash?
     * אפילו אם שינו את השם, זה אותו תוכן!
     */
    Optional<Document> findByChatAndContentHashAndActiveTrue(
        Chat chat, 
        String contentHash
    );

    /**
     * האם קיים מסמך עם Hash זהה?
     */
    boolean existsByChatAndContentHashAndActiveTrue(
        Chat chat, 
        String contentHash
    );

    // ==================== חיפוש לפי תאריך ====================

    /**
     * מסמכים שהועלו לאחרונה
     */
    List<Document> findByChatAndCreatedAtAfterAndActiveTrue(
        Chat chat, 
        LocalDateTime date
    );

    /**
     * מסמכים שלא הסתיימו תוך זמן X
     * שימושי למציאת תקלות!
     */
    @Query("SELECT d FROM Document d WHERE d.processingStatus = 'PROCESSING' " +
           "AND d.createdAt < :timeoutDate " +
           "AND d.active = true")
    List<Document> findStuckDocuments(@Param("timeoutDate") LocalDateTime timeoutDate);

    // ==================== שאילתות סטטיסטיות ====================

    /**
     * סכום גודל כל המסמכים בשיחה (בבתים)
     * שימושי לדעת כמה מקום תופס
     */
    @Query("SELECT COALESCE(SUM(d.fileSize), 0) FROM Document d " +
           "WHERE d.chat = :chat AND d.active = true")
    Long getTotalFileSizeByChat(@Param("chat") Chat chat);

    /**
     * סכום תווים מעובדים בשיחה
     */
    @Query("SELECT COALESCE(SUM(d.characterCount), 0) FROM Document d " +
           "WHERE d.chat = :chat " +
           "AND d.processingStatus = 'COMPLETED' " +
           "AND d.active = true")
    Integer getTotalCharacterCountByChat(@Param("chat") Chat chat);

    /**
     * סכום chunks בשיחה
     * 
     * למה זה חשוב?
     * יותר chunks = יותר embeddings = יותר עלויות OpenAI
     */
    @Query("SELECT COALESCE(SUM(d.chunkCount), 0) FROM Document d " +
           "WHERE d.chat = :chat " +
           "AND d.processingStatus = 'COMPLETED' " +
           "AND d.active = true")
    Integer getTotalChunkCountByChat(@Param("chat") Chat chat);

    /**
     * מסמכים שנכשלו לאחרונה
     * שימושי ל-monitoring
     */
    @Query("SELECT d FROM Document d " +
           "WHERE d.processingStatus = 'FAILED' " +
           "AND d.active = true " +
           "AND d.processedAt > :since " +
           "ORDER BY d.processedAt DESC")
    List<Document> findRecentFailures(@Param("since") LocalDateTime since);

    /**
     * סטטיסטיקות לפי סטטוס
     * מחזיר: [(COMPLETED, 10), (PROCESSING, 2), (FAILED, 1)]
     */
    @Query("SELECT d.processingStatus, COUNT(d) FROM Document d " +
           "WHERE d.chat = :chat AND d.active = true " +
           "GROUP BY d.processingStatus")
    List<Object[]> countDocumentsByStatus(@Param("chat") Chat chat);

    /**
     * מסמכים הגדולים ביותר בשיחה
     * שימושי למציאת בעיות ביצועים
     */
    @Query("SELECT d FROM Document d " +
           "WHERE d.chat = :chat AND d.active = true " +
           "ORDER BY d.fileSize DESC")
    List<Document> findLargestDocuments(
        @Param("chat") Chat chat, 
        org.springframework.data.domain.Pageable pageable
    );

    // ==================== עדכונים ====================

    /**
     * עדכון סטטוס של כל המסמכים בשיחה
     * שימושי אם צריך לעצור עיבוד
     */
    @Query("UPDATE Document d SET d.processingStatus = :newStatus " +
           "WHERE d.chat = :chat " +
           "AND d.processingStatus = :currentStatus")
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    int updateStatusForChat(
        @Param("chat") Chat chat,
        @Param("currentStatus") ProcessingStatus currentStatus,
        @Param("newStatus") ProcessingStatus newStatus
    );

    /**
     * מחיקה רכה של מסמכים ישנים שנכשלו
     */
    @Query("UPDATE Document d SET d.active = false " +
           "WHERE d.processingStatus = 'FAILED' " +
           "AND d.processedAt < :date")
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    int cleanupOldFailedDocuments(@Param("date") LocalDateTime date);
}