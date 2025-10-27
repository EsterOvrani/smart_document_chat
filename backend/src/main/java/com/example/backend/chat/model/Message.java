package com.example.backend.chat.model;

import com.example.backend.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity של הודעה (Message)
 * 
 * מייצג הודעה בשיחה - יכול להיות:
 * - שאלה של המשתמש (USER)
 * - תשובה של ה-AI (ASSISTANT)
 * - הודעת מערכת (SYSTEM)
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * השיחה שההודעה שייכת אליה
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    @JsonIgnore
    private Chat chat;

    /**
     * תוכן ההודעה
     * אם USER: "מה הריבית בחוזה?"
     * אם ASSISTANT: "הריבית היא 3.5%"
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * תפקיד השולח
     * USER = המשתמש שאל
     * ASSISTANT = ה-AI ענה
     * SYSTEM = הודעת מערכת
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    /**
     * המשתמש ששלח (רק אם role=USER)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    /**
     * מקורות המידע שממנו הגיעה התשובה
     * לדוגמה: ["חוזה.pdf - עמוד 3", "נספח.pdf - עמוד 1"]
     * נשמר כJSON string
     */
    @Column(columnDefinition = "TEXT")
    private String sources;

    /**
     * רמת ביטחון בתשובה (0.0 - 1.0)
     * אם 0.95 = ה-AI בטוח ב-95% שהתשובה נכונה
     */
    @Column(name = "confidence_score")
    private Double confidenceScore;

    /**
     * כמה טוקנים התשובה השתמשה (לעלויות OpenAI)
     */
    @Column(name = "tokens_used")
    private Integer tokensUsed;

    /**
     * זמן תגובה במילישניות
     */
    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    /**
     * מזהה הודעה קודמת (לשרשור שיחה)
     * כך ה-AI יכול לזכור הקשר
     */
    @Column(name = "parent_message_id")
    private Long parentMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * האם זו הודעת משתמש?
     */
    public boolean isUserMessage() {
        return this.role == MessageRole.USER;
    }

    /**
     * האם זו הודעת AI?
     */
    public boolean isAssistantMessage() {
        return this.role == MessageRole.ASSISTANT;
    }

    /**
     * המרת sources מJSON string לרשימה
     */
    public List<String> getSourcesList() {
        if (sources == null || sources.isEmpty()) {
            return new ArrayList<>();
        }
        // TODO: להטמיע המרה מJSON
        return List.of(sources.split(","));
    }

    /**
     * המרת רשימה ל-JSON string
     */
    public void setSourcesList(List<String> sourcesList) {
        if (sourcesList == null || sourcesList.isEmpty()) {
            this.sources = null;
        } else {
            // TODO: להטמיע המרה לJSON
            this.sources = String.join(",", sourcesList);
        }
    }

    /**
     * תפקידים אפשריים
     */
    public enum MessageRole {
        USER,       // שאלה של משתמש
        ASSISTANT,  // תשובה של AI
        SYSTEM      // הודעת מערכת
    }
}