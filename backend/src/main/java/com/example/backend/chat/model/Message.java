package com.example.backend.chat.model;

import com.example.backend.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Message Entity
 *
 * Represents a message in a chat conversation - can be:
 * - User question (USER)
 * - AI response (ASSISTANT)
 * - System message (SYSTEM)
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The chat this message belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    @JsonIgnore
    private Chat chat;

    /**
     * Message content
     * Example USER: "What is the interest rate in the contract?"
     * Example ASSISTANT: "The interest rate is 3.5%"
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Sender role
     * USER = user asked
     * ASSISTANT = AI answered
     * SYSTEM = system message
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    /**
     * The user who sent the message (only if role=USER)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    /**
     * Sources of information for the answer
     * Example: ["contract.pdf - page 3", "appendix.pdf - page 1"]
     * Stored as JSON string
     */
    @Column(columnDefinition = "TEXT")
    private String sources;

    /**
     * Confidence score in the answer (0.0 - 1.0)
     * Example: 0.95 = AI is 95% confident the answer is correct
     */
    @Column(name = "confidence_score")
    private Double confidenceScore;

    /**
     * Number of tokens used (for OpenAI costs)
     */
    @Column(name = "tokens_used")
    private Integer tokensUsed;

    /**
     * Response time in milliseconds
     */
    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    /**
     * Parent message ID (for conversation threading)
     * Allows the AI to remember context
     */
    @Column(name = "parent_message_id")
    private Long parentMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Check if this is a user message
     */
    public boolean isUserMessage() {
        return this.role == MessageRole.USER;
    }

    /**
     * Check if this is an AI message
     */
    public boolean isAssistantMessage() {
        return this.role == MessageRole.ASSISTANT;
    }

    /**
     * Convert sources from JSON string to list
     * Uses simple comma-separated format for now
     */
    public List<String> getSourcesList() {
        if (sources == null || sources.isEmpty()) {
            return new ArrayList<>();
        }
        return List.of(sources.split(","));
    }

    /**
     * Convert list to JSON string
     * Uses simple comma-separated format for now
     */
    public void setSourcesList(List<String> sourcesList) {
        if (sourcesList == null || sourcesList.isEmpty()) {
            this.sources = null;
        } else {
            this.sources = String.join(",", sourcesList);
        }
    }

    /**
     * Possible message roles
     */
    public enum MessageRole {
        USER,       // User question
        ASSISTANT,  // AI answer
        SYSTEM      // System message
    }
}