package com.example.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO לבקשה לשאול שאלה
 * 
 * מה המשתמש שולח כששואל שאלה:
 * - השאלה עצמה
 * - (אופציונלי) הקשר נוסף
 * 
 * דוגמה:
 * POST /api/chats/1/ask
 * {
 *   "question": "מה הריבית בחוזה?"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AskQuestionRequest {

    /**
     * השאלה
     * 
     * @NotBlank - חייבת להיות שאלה (לא ריקה)
     * @Size - בין 1 ל-2000 תווים
     * 
     * למה מקסימום 2000?
     * - שאלות ארוכות = יותר tokens = יותר כסף
     * - רוב השאלות קצרות בכל מקרה
     */
    @NotBlank(message = "השאלה היא שדה חובה")
    @Size(min = 1, max = 2000, message = "השאלה חייבת להיות בין 1 ל-2000 תווים")
    private String question;

    /**
     * האם לכלול את כל ההקשר?
     * 
     * true = שלח את כל ההיסטוריה ל-AI
     * false = רק 5 ההודעות האחרונות (ברירת מחדל)
     * 
     * למה false כברירת מחדל?
     * כי זה חוסך tokens (= כסף!)
     */
    private Boolean includeFullContext = false;

    /**
     * כמה הודעות היסטוריה לכלול?
     * 
     * ברירת מחדל: 5
     * מקסימום: 20
     */
    private Integer contextMessageCount = 5;

    /**
     * האם לחפש במסמכים ספציפיים?
     * אם ריק - חפש בכולם
     */
    private java.util.List<Long> documentIds;

    // ==================== Validation Methods ====================

    /**
     * האם השאלה תקינה?
     */
    public boolean isValid() {
        return question != null && 
               !question.trim().isEmpty() && 
               question.length() <= 2000;
    }

    /**
     * נירמול השאלה (ניקוי רווחים מיותרים)
     */
    public String getNormalizedQuestion() {
        if (question == null) {
            return "";
        }
        return question.trim().replaceAll("\\s+", " ");
    }

    /**
     * כמה מילים בשאלה?
     */
    public int getWordCount() {
        if (question == null || question.isEmpty()) {
            return 0;
        }
        return getNormalizedQuestion().split("\\s+").length;
    }

    /**
     * האם זו שאלה קצרה? (עד 5 מילים)
     */
    public boolean isShortQuestion() {
        return getWordCount() <= 5;
    }

    /**
     * האם זו שאלה ארוכה? (מעל 50 מילים)
     */
    public boolean isLongQuestion() {
        return getWordCount() > 50;
    }

    /**
     * בדיקת context count תקין
     */
    public void validateContextCount() {
        if (contextMessageCount == null || contextMessageCount < 0) {
            contextMessageCount = 5;
        }
        if (contextMessageCount > 20) {
            contextMessageCount = 20;
        }
    }
}