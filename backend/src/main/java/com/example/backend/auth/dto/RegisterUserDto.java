package com.example.backend.auth.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterUserDto {
    private String email;
    private String password;
    private String username;
    private String firstName;  // ← הוסף
    private String lastName;   // ← הוסף
}