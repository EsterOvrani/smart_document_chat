package com.example.backend.chat.model;

import com.example.backend.user.model.User;
import com.example.backend.document.model.Document;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity של שיחה (Chat)
 * 
 * מייצג שיחה בין משתמש למערכת ה-AI.
 * כל שיחה מכילה:
 * - כותרת (שם השיחה)
 * - רשימת מסמכים (PDFs שהמשתמש העלה)
 * - רשימת הודעות (שאלות ותשובות)
 * - קישור למשתמש שיצר אותה
 * 
 * דוגמה:
 * Chat: "ניתוח חוזה משכנתא"
 *   ├─ Documents: [חוזה.pdf, נספח_א.pdf]
 *   └─ Messages: [שאלה1, תשובה1, שאלה2, תשובה2]
 */
@Entity
@Table(name = "chats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Chat {

    /**
     * מזהה ייחודי של השיחה
     * נוצר אוטומטית על ידי PostgreSQL
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * כותרת השיחה (שם שהמשתמש נתן)
     * לדוגמה: "ניתוח חוזה משכנתא", "שאלות על תקנון"
     */
    @Column(nullable = false, length = 255)
    private String title;

    /**
     * המשתמש שיצר את השיחה
     * 
     * קשר Many-to-One:
     * - הרבה שיחות (Many) שייכות למשתמש אחד (One)
     * - משתמש אחד יכול ליצור 10 שיחות
     * 
     * @ManyToOne - מגדיר את הקשר
     * @JoinColumn - יוצר עמודה "user_id" בטבלת chats
     * FetchType.LAZY - לא טוען את ה-User אוטומטית (רק כשצריך)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * רשימת המסמכים בשיחה
     * 
     * קשר One-to-Many:
     * - שיחה אחת (One) מכילה הרבה מסמכים (Many)
     * 
     * mappedBy = "chat":
     * - אומר ש-Document יש שדה בשם "chat"
     * - השדה הזה מחזיק את הקשר
     * 
     * cascade = ALL:
     * - אם נמחק שיחה → נמחק גם את כל המסמכים שלה
     * 
     * orphanRemoval = true:
     * - אם נסיר מסמך מהרשימה → הוא יימחק מה-DB
     */
    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Document> documents = new ArrayList<>();

    /**
     * רשימת ההודעות בשיחה (שאלות ותשובות)
     * קשר One-to-Many: שיחה אחת = הרבה הודעות
     */
    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Message> messages = new ArrayList<>();

    /**
     * סטטוס השיחה - איפה היא בתהליך?
     * 
     * CREATING - המשתמש יוצר את השיחה (מעלה קבצים)
     * PROCESSING - המערכת מעבדת את המסמכים (embeddings)
     * READY - מוכן! אפשר לשאול שאלות
     * FAILED - משהו השתבש בעיבוד
     * 
     * @Enumerated(EnumType.STRING) - שומר "READY" ולא 2
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatStatus status = ChatStatus.CREATING;

    /**
     * שם הקולקשן ב-Qdrant (Vector Database)
     * 
     * כל שיחה מקבלת קולקשן ייחודי משלה!
     * לדוגמה: "chat_1_user_5"
     * 
     * למה? כדי ששאלות בשיחה 1 לא יחפשו במסמכים של שיחה 2
     */
    @Column(name = "vector_collection_name")
    private String vectorCollectionName;

    /**
     * כמה מסמכים עדיין ממתינים לעיבוד?
     * 
     * כשמעלים 3 קבצים: pendingDocuments = 3
     * קובץ 1 הסתיים: pendingDocuments = 2
     * קובץ 2 הסתיים: pendingDocuments = 1
     * קובץ 3 הסתיים: pendingDocuments = 0 → status = READY!
     */
    @Column(name = "pending_documents")
    private Integer pendingDocuments = 0;

    /**
     * האם השיחה פעילה (לא נמחקה)?
     * 
     * אנחנו לא באמת מוחקים מה-DB (Soft Delete)
     * רק משנים את active ל-false
     */
    @Column(nullable = false)
    private Boolean active = true;

    /**
     * מתי השיחה נוצרה
     * updatable = false - לא ניתן לשנות אחרי יצירה
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * מתי השיחה עודכנה לאחרונה
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * מתי הפעילות האחרונה בשיחה
     * מתעדכן כל פעם ששולחים שאלה
     */
    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    /**
     * הודעת שגיאה אם הייתה בעיה בעיבוד
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    // ==================== Lifecycle Callbacks ====================
    // פונקציות שרצות אוטומטית בזמנים מסוימים

    /**
     * רץ אוטומטית לפני שמירה ראשונה ב-DB
     * @PrePersist = לפני INSERT
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.lastActivityAt = LocalDateTime.now();
        
        if (this.status == null) {
            this.status = ChatStatus.CREATING;
        }
    }

    /**
     * רץ אוטומטית לפני כל עדכון
     * @PreUpdate = לפני UPDATE
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ==================== Helper Methods ====================
    // פונקציות עזר לניהול השיחה

    /**
     * הוספת מסמך לשיחה
     * גם מגדיל את pendingDocuments ב-1
     */
    public void addDocument(Document document) {
        documents.add(document);
        document.setChat(this);
        this.pendingDocuments++;
    }

    /**
     * הוספת הודעה לשיחה
     * גם מעדכן את lastActivityAt
     */
    public void addMessage(Message message) {
        messages.add(message);
        message.setChat(this);
        this.lastActivityAt = LocalDateTime.now();
    }

    /**
     * מסמך אחד סיים עיבוד - מפחית ב-1
     * אם הגענו ל-0 ← status = READY!
     */
    public void decrementPendingDocuments() {
        if (this.pendingDocuments > 0) {
            this.pendingDocuments--;
        }
        
        if (this.pendingDocuments == 0 && this.status == ChatStatus.PROCESSING) {
            this.status = ChatStatus.READY;
        }
    }

    /**
     * האם השיחה מוכנה לשאלות?
     * צריך: status = READY + active = true
     */
    public boolean isReady() {
        return this.status == ChatStatus.READY && this.active;
    }

    /**
     * כמה מסמכים בשיחה?
     */
    public int getDocumentCount() {
        return documents != null ? documents.size() : 0;
    }

    /**
     * כמה הודעות בשיחה?
     */
    public int getMessageCount() {
        return messages != null ? messages.size() : 0;
    }

    // ==================== Enum ====================

    /**
     * סטטוסים אפשריים של שיחה
     */
    public enum ChatStatus {
        CREATING,      // בתהליך יצירה (מעלה קבצים)
        PROCESSING,    // מעבד מסמכים (embeddings)
        READY,         // מוכן לשאלות!
        FAILED         // נכשל בעיבוד
    }
}