package com.example.backend.document.model;

import com.example.backend.chat.model.Chat;
import com.example.backend.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Document Entity
 *
 * Represents a PDF file uploaded by the user.
 * Each document:
 * - Belongs to a specific chat
 * - Stored in S3 (file storage)
 * - Processed into vectors in Qdrant
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The chat this document belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    /**
     * The user who uploaded the document
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Original file name (as uploaded by user)
     * Example: "mortgage_contract.pdf"
     */
    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    /**
     * File path in S3
     * Example: "users/5/chats/1/doc_12345.pdf"
     */
    @Column(name = "file_path", nullable = false)
    private String filePath;

    /**
     * File type
     */
    @Column(name = "file_type")
    private String fileType = "pdf";

    /**
     * File size in bytes
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * Content hash (to prevent duplicates)
     */
    @Column(name = "content_hash")
    private String contentHash;

    /**
     * Processing status
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    /**
     * Progress percentage (0-100)
     */
    @Column(name = "processing_progress")
    private Integer processingProgress = 0;

    /**
     * How many characters in document (after processing)
     */
    @Column(name = "character_count")
    private Integer characterCount;

    /**
     * How many chunks were created (split into small pieces)
     */
    @Column(name = "chunk_count")
    private Integer chunkCount;

    /**
     * Is the document active
     */
    @Column(nullable = false)
    private Boolean active = true;

    /**
     * Error message if processing failed
     */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Update status to processing
     */
    public void startProcessing() {
        this.processingStatus = ProcessingStatus.PROCESSING;
        this.processingProgress = 0;
    }

    /**
     * Mark as successfully completed
     */
    public void markAsCompleted(int characterCount, int chunkCount) {
        this.processingStatus = ProcessingStatus.COMPLETED;
        this.processingProgress = 100;
        this.characterCount = characterCount;
        this.chunkCount = chunkCount;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * Mark as failed
     */
    public void markAsFailed(String errorMessage) {
        this.processingStatus = ProcessingStatus.FAILED;
        this.errorMessage = errorMessage;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * Is the document processed?
     */
    public boolean isProcessed() {
        return this.processingStatus == ProcessingStatus.COMPLETED;
    }

    /**
     * Is it currently processing?
     */
    public boolean isProcessing() {
        return this.processingStatus == ProcessingStatus.PROCESSING;
    }

    /**
     * Did it fail?
     */
    public boolean hasFailed() {
        return this.processingStatus == ProcessingStatus.FAILED;
    }

    /**
     * Possible processing statuses
     */
    public enum ProcessingStatus {
        PENDING,      // Waiting for processing
        PROCESSING,   // Currently processing
        COMPLETED,    // Completed successfully
        FAILED        // Failed
    }
}