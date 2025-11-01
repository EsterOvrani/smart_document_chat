package com.example.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * מבנה אחיד לכל תגובות השגיאה במערכת
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    /**
     * האם הפעולה הצליחה
     */
    private boolean success;
    
    /**
     * קוד השגיאה (לזיהוי בצד לקוח)
     */
    private String errorCode;
    
    /**
     * הודעת השגיאה (בעברית)
     */
    private String message;
    
    /**
     * פרטים נוספים על השגיאה
     */
    private String details;
    
    /**
     * זמן התרחשות השגיאה
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    /**
     * הנתיב שבו התרחשה השגיאה
     */
    private String path;
    
    /**
     * שגיאות ולידציה לפי שדה (אם קיימות)
     */
    private Map<String, String> fieldErrors;
    
    /**
     * יוצר תגובת שגיאה פשוטה
     */
    public static ErrorResponse of(String errorCode, String message) {
        return ErrorResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * יוצר תגובת שגיאה עם פרטים
     */
    public static ErrorResponse of(String errorCode, String message, String details) {
        return ErrorResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * יוצר תגובת שגיאה עם שגיאות ולידציה
     */
    public static ErrorResponse withFieldErrors(
            String errorCode, 
            String message, 
            Map<String, String> fieldErrors) {
        return ErrorResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .fieldErrors(fieldErrors)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
