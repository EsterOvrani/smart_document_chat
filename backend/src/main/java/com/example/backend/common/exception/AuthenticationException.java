package com.example.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * נזרק כאשר יש בעיה באימות המשתמש
 */
public class AuthenticationException extends BaseException {
    
    private static final String ERROR_CODE = "AUTHENTICATION_FAILED";
    
    public AuthenticationException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, ERROR_CODE);
    }
    
    public AuthenticationException() {
        super("אימות נכשל. אנא התחבר מחדש", HttpStatus.UNAUTHORIZED, ERROR_CODE);
    }
    
    public static AuthenticationException invalidCredentials() {
        return new AuthenticationException("שם משתמש או סיסמה שגויים");
    }
    
    public static AuthenticationException tokenExpired() {
        return new AuthenticationException("תוקף הטוקן פג. אנא התחבר מחדש");
    }
    
    public static AuthenticationException userNotVerified() {
        return new AuthenticationException("חשבון לא מאומת. אנא אמת את האימייל שלך");
    }
}