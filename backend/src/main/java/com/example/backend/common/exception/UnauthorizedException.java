package com.example.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * נזרק כאשר משתמש מנסה לגשת למשאב שאין לו הרשאה אליו
 */
public class UnauthorizedException extends BaseException {
    
    private static final String ERROR_CODE = "UNAUTHORIZED";
    
    public UnauthorizedException(String message) {
        super(message, HttpStatus.FORBIDDEN, ERROR_CODE);
    }
    
    public UnauthorizedException() {
        super("אין לך הרשאה לבצע פעולה זו", HttpStatus.FORBIDDEN, ERROR_CODE);
    }
    
    public UnauthorizedException(String resourceName, Long resourceId) {
        super(
            String.format("אין לך הרשאה לגשת ל%s עם ID %d", resourceName, resourceId),
            HttpStatus.FORBIDDEN,
            ERROR_CODE
        );
    }
}