package com.example.backend.document.model;

import com.example.backend.chat.model.Chat;
import com.example.backend.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity של מסמך (Document)
 * 
 * מייצג קובץ PDF שהמשתמש העלה.
 * כל מסמך:
 * - שייך לשיחה ספציפית
 * - נשמר ב-MinIO (אחסון קבצים)
 * - מעובד לוקטורים ב-Qdrant
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * השיחה שהמסמך שייך אליה
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    /**
     * המשתמש שהעלה את המסמך
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * שם הקובץ המקורי (כמו שהמשתמש העלה)
     * לדוגמה: "חוזה_משכנתא.pdf"
     */
    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    /**
     * נתיב הקובץ ב-MinIO
     * לדוגמה: "users/5/chats/1/doc_12345.pdf"
     */
    @Column(name = "file_path", nullable = false)
    private String filePath;

    /**
     * סוג הקובץ
     */
    @Column(name = "file_type")
    private String fileType = "pdf";

    /**
     * גודל הקובץ בבתים
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * Hash של התוכן (למניעת כפילויות)
     */
    @Column(name = "content_hash")
    private String contentHash;

    /**
     * סטטוס העיבוד
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    /**
     * אחוז התקדמות (0-100)
     */
    @Column(name = "processing_progress")
    private Integer processingProgress = 0;

    /**
     * כמה תווים במסמך (אחרי עיבוד)
     */
    @Column(name = "character_count")
    private Integer characterCount;

    /**
     * כמה chunks נוצרו (חלוקה לחתיכות קטנות)
     */
    @Column(name = "chunk_count")
    private Integer chunkCount;

    /**
     * האם המסמך פעיל
     */
    @Column(nullable = false)
    private Boolean active = true;

    /**
     * הודעת שגיאה אם נכשל
     */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * עדכון לסטטוס מעבד
     */
    public void startProcessing() {
        this.processingStatus = ProcessingStatus.PROCESSING;
        this.processingProgress = 0;
    }

    /**
     * עדכון סטטוס להצלחה
     */
    public void markAsCompleted(int characterCount, int chunkCount) {
        this.processingStatus = ProcessingStatus.COMPLETED;
        this.processingProgress = 100;
        this.characterCount = characterCount;
        this.chunkCount = chunkCount;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * עדכון לכישלון
     */
    public void markAsFailed(String errorMessage) {
        this.processingStatus = ProcessingStatus.FAILED;
        this.errorMessage = errorMessage;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * האם המסמך מעובד?
     */
    public boolean isProcessed() {
        return this.processingStatus == ProcessingStatus.COMPLETED;
    }

    /**
     * האם בתהליך עיבוד?
     */
    public boolean isProcessing() {
        return this.processingStatus == ProcessingStatus.PROCESSING;
    }

    /**
     * האם נכשל?
     */
    public boolean hasFailed() {
        return this.processingStatus == ProcessingStatus.FAILED;
    }

    /**
     * סטטוסים אפשריים
     */
    public enum ProcessingStatus {
        PENDING,      // ממתין לעיבוד
        PROCESSING,   // בעיבוד
        COMPLETED,    // הושלם
        FAILED        // נכשל
    }
}