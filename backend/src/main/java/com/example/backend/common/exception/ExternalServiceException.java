package com.example.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * נזרק כאשר יש בעיה בשירות חיצוני (AI, S3, Email וכו')
 */
public class ExternalServiceException extends BaseException {
    
    private static final String ERROR_CODE = "EXTERNAL_SERVICE_ERROR";
    
    public ExternalServiceException(String serviceName, String message) {
        super(
            String.format("שגיאה בשירות %s: %s", serviceName, message),
            HttpStatus.SERVICE_UNAVAILABLE,
            ERROR_CODE
        );
    }
    
    public ExternalServiceException(String serviceName, String message, Throwable cause) {
        super(
            String.format("שגיאה בשירות %s: %s", serviceName, message),
            cause,
            HttpStatus.SERVICE_UNAVAILABLE,
            ERROR_CODE
        );
    }
    
    public static ExternalServiceException aiServiceError(String message) {
        return new ExternalServiceException("AI", message);
    }
    
    public static ExternalServiceException storageServiceError(String message) {
        return new ExternalServiceException("אחסון", message);
    }
    
    public static ExternalServiceException emailServiceError(String message) {
        return new ExternalServiceException("דואר אלקטרוני", message);
    }
    
    public static ExternalServiceException vectorDbError(String message) {
        return new ExternalServiceException("Vector Database", message);
    }
}