package com.example.backend.common.exception;

import org.springframework.http.HttpStatus;

public class TimeoutException extends BaseException {
    
    private static final String ERROR_CODE = "TIMEOUT";
    
    public TimeoutException(String operation) {
        super(
            String.format("פג זמן ההמתנה לפעולה: %s", operation),
            HttpStatus.REQUEST_TIMEOUT,
            ERROR_CODE
        );
    }
    
    public TimeoutException(String operation, long timeoutSeconds) {
        super(
            String.format("פג זמן ההמתנה לפעולה: %s (לאחר %d שניות)", operation, timeoutSeconds),
            HttpStatus.REQUEST_TIMEOUT,
            ERROR_CODE
        );
    }
}