package com.example.backend.auth.controller;

import com.example.backend.auth.dto.LoginUserDto;
import com.example.backend.auth.dto.RegisterUserDto;
import com.example.backend.auth.dto.VerifyUserDto;
import com.example.backend.auth.service.AuthenticationService;
import com.example.backend.auth.service.JwtService;
import com.example.backend.user.model.User;
import com.example.backend.shared.responses.LoginResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RequestMapping("/auth")
@RestController
public class AuthenticationController {
    private final JwtService jwtService;
    private final AuthenticationService authenticationService;

    public AuthenticationController(JwtService jwtService, AuthenticationService authenticationService) {
        this.jwtService = jwtService;
        this.authenticationService = authenticationService;
    }

    @PostMapping({"/signup", "/register"})
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterUserDto registerUserDto) {
        try {
            User registeredUser = authenticationService.signup(registerUserDto);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User registered successfully. Please check your email for verification code.");
            response.put("user", Map.of(
                "id", registeredUser.getId(),
                "username", registeredUser.getUsername(),
                "email", registeredUser.getEmail()
            ));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> authenticate(@RequestBody LoginUserDto loginUserDto){
        try {
            User authenticatedUser = authenticationService.authenticate(loginUserDto);
            String jwtToken = jwtService.generateToken(authenticatedUser);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("token", jwtToken);
            response.put("expiresIn", jwtService.getExpirationTime());
            response.put("user", Map.of(
                "username", authenticatedUser.getUsername(),
                "email", authenticatedUser.getEmail(),
                "fullName", authenticatedUser.getFirstName() + " " + authenticatedUser.getLastName()
            ));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyUser(@RequestBody VerifyUserDto verifyUserDto) {
        try {
            authenticationService.verifyUser(verifyUserDto);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Account verified successfully");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<String> verifyUserByLink(
            @RequestParam String email, 
            @RequestParam String code) {
        try {
            VerifyUserDto verifyUserDto = new VerifyUserDto();
            verifyUserDto.setEmail(email);
            verifyUserDto.setVerificationCode(code);
            
            authenticationService.verifyUser(verifyUserDto);
            
            return ResponseEntity.status(302)
                    .header("Location", "/login?verified=true")
                    .build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(302)
                    .header("Location", "/login?verified=false&error=" + e.getMessage())
                    .build();
        }
    }

    @PostMapping("/resend")
    public ResponseEntity<Map<String, Object>> resendVerificationCode(@RequestParam String email) {
        try {
            authenticationService.resendVerificationCode(email);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Verification code sent");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/check-username/{username}")
    public ResponseEntity<Map<String, Object>> checkUsername(@PathVariable String username) {
        Map<String, Object> response = new HashMap<>();
        boolean exists = authenticationService.usernameExists(username);
        response.put("available", !exists);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/check-email/{email}")
    public ResponseEntity<Map<String, Object>> checkEmail(@PathVariable String email) {
        Map<String, Object> response = new HashMap<>();
        boolean exists = authenticationService.emailExists(email);
        response.put("available", !exists);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/check-verified/{email}")
    public ResponseEntity<Map<String, Object>> checkIfVerified(@PathVariable String email) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean verified = authenticationService.isEmailVerified(email);
            response.put("verified", verified);
            response.put("email", email);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("verified", false);
            response.put("error", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> checkStatus() {
        Map<String, Object> response = new HashMap<>();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated() && 
            !authentication.getPrincipal().equals("anonymousUser")) {
            User user = (User) authentication.getPrincipal();
            response.put("success", true);
            response.put("authenticated", true);
            
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("username", user.getUsername());
            userInfo.put("email", user.getEmail());
            userInfo.put("fullName", user.getFirstName() + " " + user.getLastName());
            response.put("user", userInfo);
        } else {
            response.put("success", true);
            response.put("authenticated", false);
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        SecurityContextHolder.clearContext();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }
}