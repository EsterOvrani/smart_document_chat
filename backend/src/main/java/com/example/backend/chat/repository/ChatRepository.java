package com.example.backend.chat.repository;

import com.example.backend.chat.model.Chat;
import com.example.backend.chat.model.Chat.ChatStatus;
import com.example.backend.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository לניהול שיחות (Chats)
 * 
 * ירש מ-JpaRepository שנותן לנו אוטומטית:
 * - save(chat) - שמירה
 * - findById(id) - חיפוש לפי ID
 * - findAll() - כל השיחות
 * - delete(chat) - מחיקה
 * - count() - ספירה
 * 
 * אנחנו מוסיפים פונקציות ספציפיות לצרכים שלנו
 */
@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    // ==================== חיפוש לפי משתמש ====================

    /**
     * כל השיחות של משתמש (פעילות בלבד)
     * ממוינות לפי פעילות אחרונה (החדש ביותר ראשון)
     * 
     * Spring מבין מהשם:
     * - findBy = SELECT * FROM chats WHERE
     * - User = user_id = ?
     * - AndActiveTrue = AND active = true
     * - OrderByLastActivityAtDesc = ORDER BY last_activity_at DESC
     */
    List<Chat> findByUserAndActiveTrueOrderByLastActivityAtDesc(User user);

    /**
     * כל השיחות של משתמש (כולל מחוקות)
     */
    List<Chat> findByUserOrderByCreatedAtDesc(User user);

    /**
     * שיחות של משתמש לפי סטטוס
     * לדוגמה: כל השיחות שעדיין מעבדות
     */
    List<Chat> findByUserAndStatusAndActiveTrue(User user, ChatStatus status);

    // ==================== חיפוש לפי ID ====================

    /**
     * חיפוש שיחה לפי ID (רק פעילה)
     * 
     * למה זה חשוב?
     * אם משתמש מנסה לגשת לשיחה מחוקה - לא ימצא אותה
     */
    Optional<Chat> findByIdAndActiveTrue(Long id);

    /**
     * חיפוש שיחה של משתמש ספציפי
     * 
     * למה זה חשוב?
     * בדיקת הרשאות! משתמש A לא יכול לגשת לשיחה של משתמש B
     */
    Optional<Chat> findByIdAndUserAndActiveTrue(Long id, User user);

    // ==================== חיפוש לפי כותרת ====================

    /**
     * חיפוש שיחות לפי חלק מהכותרת
     * 
     * Containing = LIKE %{title}%
     * IgnoreCase = לא רגיש לאותיות גדולות/קטנות
     * 
     * דוגמה: אם הכותרת היא "ניתוח חוזה משכנתא"
     * - "חוזה" ימצא ✅
     * - "משכנתא" ימצא ✅
     * - "חוזה" ימצא ✅ (IgnoreCase)
     */
    List<Chat> findByUserAndTitleContainingIgnoreCaseAndActiveTrue(
        User user, 
        String title
    );

    // ==================== חיפוש לפי תאריך ====================

    /**
     * שיחות שנוצרו אחרי תאריך מסוים
     * 
     * After = > (גדול מ)
     */
    List<Chat> findByUserAndCreatedAtAfterAndActiveTrue(
        User user, 
        LocalDateTime date
    );

    /**
     * שיחות שהיו פעילות לאחרונה
     * שימושי ל"המשך מאיפה שעצרת"
     */
    List<Chat> findByUserAndLastActivityAtAfterAndActiveTrue(
        User user, 
        LocalDateTime date
    );

    // ==================== ספירות ====================

    /**
     * כמה שיחות יש למשתמש? (פעילות בלבד)
     */
    long countByUserAndActiveTrue(User user);

    /**
     * כמה שיחות במצב PROCESSING?
     * שימושי לדעת אם יש עומס
     */
    long countByUserAndStatusAndActiveTrue(User user, ChatStatus status);

    // ==================== בדיקות קיום ====================

    /**
     * האם קיימת שיחה עם כותרת מסוימת?
     * שימושי למניעת כפילויות
     */
    boolean existsByUserAndTitleAndActiveTrue(User user, String title);

    // ==================== שאילתות מותאמות אישית (JPQL) ====================

    /**
     * שיחות עם לפחות X מסמכים
     * 
     * @Query - כותבים SQL מותאם אישית
     * JPQL = Java Persistence Query Language
     * 
     * למה לא SQL רגיל?
     * JPQL עובד עם Entities ולא עם טבלאות
     */
    @Query("SELECT c FROM Chat c WHERE c.user = :user " +
           "AND c.active = true " +
           "AND SIZE(c.documents) >= :minDocuments " +
           "ORDER BY c.lastActivityAt DESC")
    List<Chat> findChatsWithMinimumDocuments(
        @Param("user") User user,
        @Param("minDocuments") int minDocuments
    );

    /**
     * שיחות עם לפחות X הודעות
     * שימושי למצוא שיחות פעילות
     */
    @Query("SELECT c FROM Chat c WHERE c.user = :user " +
           "AND c.active = true " +
           "AND SIZE(c.messages) >= :minMessages " +
           "ORDER BY c.lastActivityAt DESC")
    List<Chat> findChatsWithMinimumMessages(
        @Param("user") User user,
        @Param("minMessages") int minMessages
    );

    /**
     * חיפוש מתקדם - שיחות לפי מספר תנאים
     * 
     * שימושי לפילטרים מורכבים:
     * - חיפוש בכותרת
     * - סטטוס מסוים
     * - טווח תאריכים
     */
    @Query("SELECT c FROM Chat c WHERE c.user = :user " +
           "AND c.active = true " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND (:searchTerm IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "AND (:fromDate IS NULL OR c.createdAt >= :fromDate) " +
           "AND (:toDate IS NULL OR c.createdAt <= :toDate) " +
           "ORDER BY c.lastActivityAt DESC")
    List<Chat> searchChats(
        @Param("user") User user,
        @Param("status") ChatStatus status,
        @Param("searchTerm") String searchTerm,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate
    );

    /**
     * סטטיסטיקות - כמה שיחות לכל סטטוס
     * 
     * מחזיר: {READY=5, PROCESSING=2, FAILED=1}
     */
    @Query("SELECT c.status, COUNT(c) FROM Chat c " +
           "WHERE c.user = :user AND c.active = true " +
           "GROUP BY c.status")
    List<Object[]> countChatsByStatus(@Param("user") User user);

    /**
     * מחיקה רכה (Soft Delete) של שיחות ישנות
     * 
     * @Modifying - אומר ל-Spring שזה UPDATE/DELETE
     * @Transactional - מבטיח שהפעולה תתבצע בטוח
     * 
     * למה לא למחוק באמת?
     * כי אולי נרצה לשחזר, או לראות היסטוריה
     */
    @Query("UPDATE Chat c SET c.active = false " +
           "WHERE c.user = :user " +
           "AND c.lastActivityAt < :date")
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    int softDeleteOldChats(
        @Param("user") User user,
        @Param("date") LocalDateTime date
    );
}