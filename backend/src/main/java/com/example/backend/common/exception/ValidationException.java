package com.example.backend.common.exception;

import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.HashMap;

/**
 * נזרק כאשר יש בעיית ולידציה בנתונים
 */
public class ValidationException extends BaseException {
    
    private static final String ERROR_CODE = "VALIDATION_ERROR";
    private final Map<String, String> fieldErrors;
    
    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
        this.fieldErrors = new HashMap<>();
    }
    
    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
        this.fieldErrors = fieldErrors != null ? fieldErrors : new HashMap<>();
    }
    
    public ValidationException(String field, String error) {
        super("שגיאת ולידציה", HttpStatus.BAD_REQUEST, ERROR_CODE);
        this.fieldErrors = new HashMap<>();
        this.fieldErrors.put(field, error);
    }
    
    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
    
    public boolean hasFieldErrors() {
        return !fieldErrors.isEmpty();
    }
}