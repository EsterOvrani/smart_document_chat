package com.example.backend.auth.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.Data;


@Getter
@Setter
@Data
public class LoginUserDto {
    private String email;
    private String password;
}