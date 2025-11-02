package com.example.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;

@Configuration
@Getter
public class TestConfig {
    
    @Value("${app.test-mode.enabled:false}")
    private boolean testModeEnabled;
    
    @Value("${app.test-mode.bypass-email-verification:false}")
    private boolean bypassEmailVerification;
    
    @Value("${app.test-mode.fixed-verification-code:999999}")
    private String fixedVerificationCode;
}