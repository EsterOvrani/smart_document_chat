package com.example.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * נזרק כאשר יש בעיה בעיבוד קבצים (העלאה, קריאה, מחיקה)
 */
public class FileProcessingException extends BaseException {
    
    private static final String ERROR_CODE = "FILE_PROCESSING_ERROR";
    
    public FileProcessingException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, ERROR_CODE);
    }
    
    public FileProcessingException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR, ERROR_CODE);
    }
    
    public static FileProcessingException uploadFailed(String filename) {
        return new FileProcessingException("נכשל העלאת הקובץ: " + filename);
    }
    
    public static FileProcessingException invalidFileType(String filename, String allowedTypes) {
        return new FileProcessingException(
            String.format("סוג קובץ לא נתמך: %s. סוגי קבצים מותרים: %s", filename, allowedTypes)
        );
    }
    
    public static FileProcessingException fileTooLarge(String filename, long maxSize) {
        return new FileProcessingException(
            String.format("הקובץ %s גדול מדי. גודל מקסימלי: %d MB", filename, maxSize / (1024 * 1024))
        );
    }
}