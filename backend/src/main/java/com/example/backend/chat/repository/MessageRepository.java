package com.example.backend.chat.repository;

import com.example.backend.chat.model.Chat;
import com.example.backend.chat.model.Message;
import com.example.backend.chat.model.Message.MessageRole;
import com.example.backend.user.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository לניהול הודעות (Messages)
 * 
 * מטפל בהיסטוריית השיחה:
 * - שמירת שאלות ותשובות
 * - קבלת הקשר לשאלות
 * - מעקב אחרי שימוש (tokens, זמני תגובה)
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // ==================== חיפוש לפי שיחה ====================

    /**
     * כל ההודעות בשיחה
     * ממוין לפי זמן (מהישן לחדש)
     * 
     * למה מהישן לחדש?
     * כדי להציג כמו WhatsApp - ההודעה הראשונה למעלה
     */
    List<Message> findByChatOrderByCreatedAtAsc(Chat chat);

    /**
     * X הודעות אחרונות בשיחה
     * 
     * למה זה שימושי?
     * לשלוח ל-AI רק את ההקשר האחרון (לא כל ההיסטוריה)
     * 
     * דוגמה:
     * Pageable.ofSize(10) - רק 10 הודעות אחרונות
     */
    List<Message> findByChatOrderByCreatedAtDesc(Chat chat, Pageable pageable);

    /**
     * ספירה - כמה הודעות בשיחה?
     */
    long countByChat(Chat chat);

    /**
     * כמה הודעות של משתמש? (USER role)
     */
    long countByChatAndRole(Chat chat, MessageRole role);

    // ==================== חיפוש לפי תפקיד (Role) ====================

    /**
     * רק שאלות של המשתמש
     */
    List<Message> findByChatAndRoleOrderByCreatedAtAsc(Chat chat, MessageRole role);

    /**
     * ההודעה האחרונה של המשתמש
     * שימושי לדעת מה השאלה האחרונה
     */
    Optional<Message> findFirstByChatAndRoleOrderByCreatedAtDesc(
        Chat chat, 
        MessageRole role
    );

    // ==================== חיפוש לפי תוכן ====================

    /**
     * חיפוש הודעות לפי תוכן
     * 
     * למה זה שימושי?
     * "מה שאלתי אתמול על הריבית?"
     */
    @Query("SELECT m FROM Message m WHERE m.chat = :chat " +
           "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "ORDER BY m.createdAt ASC")
    List<Message> searchMessagesByContent(
        @Param("chat") Chat chat,
        @Param("searchTerm") String searchTerm
    );

    // ==================== חיפוש לפי תאריך ====================

    /**
     * הודעות מהיום האחרון
     */
    List<Message> findByChatAndCreatedAtAfterOrderByCreatedAtAsc(
        Chat chat, 
        LocalDateTime date
    );

    /**
     * הודעות בטווח תאריכים
     */
    @Query("SELECT m FROM Message m WHERE m.chat = :chat " +
           "AND m.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY m.createdAt ASC")
    List<Message> findMessagesBetweenDates(
        @Param("chat") Chat chat,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    // ==================== הקשר (Context) ====================

    /**
     * קבלת X הודעות אחרונות לפני הודעה מסוימת
     * 
     * למה זה חשוב?
     * כשמשתמש שואל שאלה, ה-AI צריך את ההקשר:
     * 
     * משתמש: "מה הריבית?"
     * AI: "3.5%"
     * משתמש: "ומה היא היום?" ← צריך הקשר!
     * 
     * אז שולחים ל-AI את 5 ההודעות האחרונות
     */
    @Query("SELECT m FROM Message m WHERE m.chat = :chat " +
           "AND m.createdAt < :beforeDate " +
           "ORDER BY m.createdAt DESC")
    List<Message> findRecentContextMessages(
        @Param("chat") Chat chat,
        @Param("beforeDate") LocalDateTime beforeDate,
        Pageable pageable
    );

    /**
     * שרשור שיחה - מציאת כל ההודעות מהודעה X ואילך
     */
    @Query("SELECT m FROM Message m WHERE m.chat = :chat " +
           "AND m.parentMessageId = :parentId " +
           "ORDER BY m.createdAt ASC")
    List<Message> findMessageThread(
        @Param("chat") Chat chat, 
        @Param("parentId") Long parentId
    );

    // ==================== סטטיסטיקות ====================

    /**
     * סכום Tokens שהשתמשנו (עלויות OpenAI)
     * 
     * שימושי לדעת כמה כסף הוצאנו!
     */
    @Query("SELECT COALESCE(SUM(m.tokensUsed), 0) FROM Message m " +
           "WHERE m.chat = :chat AND m.role = 'ASSISTANT'")
    Long getTotalTokensUsed(@Param("chat") Chat chat);

    /**
     * זמן תגובה ממוצע
     * שימושי ל-monitoring
     */
    @Query("SELECT AVG(m.responseTimeMs) FROM Message m " +
           "WHERE m.chat = :chat AND m.role = 'ASSISTANT' " +
           "AND m.responseTimeMs IS NOT NULL")
    Double getAverageResponseTime(@Param("chat") Chat chat);

    /**
     * רמת ביטחון ממוצעת
     * כמה ה-AI בטוח בתשובות?
     */
    @Query("SELECT AVG(m.confidenceScore) FROM Message m " +
           "WHERE m.chat = :chat AND m.role = 'ASSISTANT' " +
           "AND m.confidenceScore IS NOT NULL")
    Double getAverageConfidenceScore(@Param("chat") Chat chat);

    /**
     * התשובות עם ביטחון נמוך ביותר
     * שימושי למציאת תשובות חלשות
     */
    @Query("SELECT m FROM Message m WHERE m.chat = :chat " +
           "AND m.role = 'ASSISTANT' " +
           "AND m.confidenceScore IS NOT NULL " +
           "ORDER BY m.confidenceScore ASC")
    List<Message> findLowConfidenceMessages(
        @Param("chat") Chat chat, 
        Pageable pageable
    );

    /**
     * הודעות עם זמן תגובה ארוך
     * למציאת בעיות ביצועים
     */
    @Query("SELECT m FROM Message m WHERE m.chat = :chat " +
           "AND m.role = 'ASSISTANT' " +
           "AND m.responseTimeMs > :thresholdMs " +
           "ORDER BY m.responseTimeMs DESC")
    List<Message> findSlowResponses(
        @Param("chat") Chat chat,
        @Param("thresholdMs") Long thresholdMs
    );

    // ==================== סטטיסטיקות לפי משתמש ====================

    /**
     * כמה שאלות שאל המשתמש בכל השיחות?
     */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.user = :user " +
           "AND m.role = 'USER'")
    long countUserQuestions(@Param("user") User user);

    /**
     * סכום Tokens של כל השיחות של המשתמש
     * כמה הוא עלה לנו בסך הכל?
     */
    @Query("SELECT COALESCE(SUM(m.tokensUsed), 0) FROM Message m " +
           "WHERE m.chat.user = :user AND m.role = 'ASSISTANT'")
    Long getTotalTokensUsedByUser(@Param("user") User user);

    // ==================== ניקוי ====================

    /**
     * מחיקת הודעות ישנות (אם רוצים לחסוך מקום)
     */
    @Query("DELETE FROM Message m WHERE m.chat = :chat " +
           "AND m.createdAt < :beforeDate")
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    int deleteOldMessages(
        @Param("chat") Chat chat,
        @Param("beforeDate") LocalDateTime beforeDate
    );

    /**
     * ספירת הודעות לפני מחיקה
     * כדי לדעת כמה נמחק
     */
    long countByChatAndCreatedAtBefore(Chat chat, LocalDateTime beforeDate);
}