package com.example.backend.auth.service;

import com.example.backend.auth.dto.LoginUserDto;
import com.example.backend.auth.dto.RegisterUserDto;
import com.example.backend.auth.dto.VerifyUserDto;
import com.example.backend.user.model.User;
import com.example.backend.user.repository.UserRepository;
import com.example.backend.common.infrastructure.email.EmailService;
import com.example.backend.config.TestConfig;
import com.example.backend.common.exception.AuthenticationException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.common.exception.DuplicateResourceException;
import com.example.backend.common.exception.ValidationException; 

import jakarta.mail.MessagingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    public AuthenticationService(
            UserRepository userRepository,
            AuthenticationManager authenticationManager,
            PasswordEncoder passwordEncoder,
            EmailService emailService
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Autowired
    private TestConfig testConfig; 
    
    public User signup(RegisterUserDto input) {
        User user = new User();
        user.setUsername(input.getUsername());
        user.setEmail(input.getEmail());
        user.setPassword(passwordEncoder.encode(input.getPassword()));
        user.setFirstName(input.getFirstName());
        user.setLastName(input.getLastName());

        // ⭐ Test Mode Logic
        if (testConfig.isBypassEmailVerification()) {
            user.setEnabled(true); // מיד מאומת!
            user.setVerificationCode(null);
            user.setVerificationCodeExpiresAt(null);
            log.warn("🔶 TEST MODE: User automatically verified!");
        } else {
            user.setVerificationCode(
                testConfig.isTestModeEnabled() 
                    ? testConfig.getFixedVerificationCode() // קוד קבוע לבדיקות
                    : generateVerificationCode() // קוד רנדומלי
            );
            user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(15));
            user.setEnabled(false);
            sendVerificationEmail(user);
        }

        return userRepository.save(user);
    }
    public User authenticate(LoginUserDto input) {
        User user = userRepository.findByEmail(input.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("משתמש", input.getEmail()));

        if (!user.isEnabled()) {
            throw AuthenticationException.userNotVerified();
        }
        
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        input.getEmail(),
                        input.getPassword()
                )
        );

        return user;
    }

    public void verifyUser(VerifyUserDto input) {
        User user = userRepository.findByEmail(input.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("משתמש", input.getEmail()));
        
        // ⭐ Test Mode: קוד תמיד נכון
        if (testConfig.isTestModeEnabled() && 
            input.getVerificationCode().equals(testConfig.getFixedVerificationCode())) {
            user.setEnabled(true);
            user.setVerificationCode(null);
            user.setVerificationCodeExpiresAt(null);
            userRepository.save(user);
            log.warn("🔶 TEST MODE: Verification bypassed with fixed code!");
            return;
        }
        
        // Regular verification logic
        if (user.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ValidationException("verificationCode", "קוד האימות פג תוקף");
        }
        
        if (!user.getVerificationCode().equals(input.getVerificationCode())) {
            throw new ValidationException("verificationCode", "קוד אימות שגוי");
        }
        
        user.setEnabled(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiresAt(null);
        userRepository.save(user);
    }

    public void resendVerificationCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("משתמש", email));
        
        if (user.isEnabled()) {
            throw new ValidationException("email", "החשבון כבר מאומת");
        }
        
        user.setVerificationCode(generateVerificationCode());
        user.setVerificationCodeExpiresAt(LocalDateTime.now().plusHours(1));
        sendVerificationEmail(user);
        userRepository.save(user);
    }

    public boolean isEmailVerified(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("משתמש", email));
        return user.isEnabled();
    }

    private void sendVerificationEmail(User user) {
        String subject = "Account Verification";
        String verificationCode = user.getVerificationCode();
        
        try {
            emailService.sendVerificationEmail(user.getEmail(), subject, verificationCode);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
    
    private String generateVerificationCode() {
        Random random = new Random();
        int code = random.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    public boolean usernameExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    public boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}