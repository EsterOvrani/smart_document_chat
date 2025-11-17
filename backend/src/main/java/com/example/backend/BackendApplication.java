package com.example.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import java.util.TimeZone;
import jakarta.annotation.PostConstruct;

@SpringBootApplication
@EnableAsync
public class BackendApplication {

    @PostConstruct
    public void init() {
        // ✅ הגדר את TimeZone לכל האפליקציה
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jerusalem"));
    }

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}