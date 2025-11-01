package com.example.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * נזרק כאשר משאב לא נמצא במערכת (שיחה, מסמך, משתמש וכו')
 */
public class ResourceNotFoundException extends BaseException {
    
    private static final String ERROR_CODE = "RESOURCE_NOT_FOUND";
    
    public ResourceNotFoundException(String resourceName, Long id) {
        super(
            String.format("%s עם ID %d לא נמצא", resourceName, id),
            HttpStatus.NOT_FOUND,
            ERROR_CODE
        );
    }
    
    public ResourceNotFoundException(String resourceName, String identifier) {
        super(
            String.format("%s '%s' לא נמצא", resourceName, identifier),
            HttpStatus.NOT_FOUND,
            ERROR_CODE
        );
    }
    
    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}