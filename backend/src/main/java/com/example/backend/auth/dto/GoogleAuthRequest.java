package com.example.backend.auth.dto;

import lombok.Data;

@Data
public class GoogleAuthRequest {
    private String credential; // Google ID token
}