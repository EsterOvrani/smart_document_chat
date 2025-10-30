package com.example.backend.chat.model;

import com.example.backend.user.model.User;
import com.example.backend.document.model.Document;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Chat Entity
 *
 * Represents a conversation between user and the AI system.
 * Each chat contains:
 * - Title (chat name)
 * - List of documents (PDFs uploaded by user)
 * - List of messages (questions and answers)
 * - Link to the user who created it
 *
 * Example:
 * Chat: "Mortgage Contract Analysis"
 *   ├─ Documents: [contract.pdf, appendix_a.pdf]
 *   └─ Messages: [question1, answer1, question2, answer2]
 */
@Entity
@Table(name = "chats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Chat {

    /**
     * Unique identifier for the chat
     * Generated automatically by PostgreSQL
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Chat title (name given by user)
     * Example: "Mortgage Contract Analysis", "Questions about regulations"
     */
    @Column(nullable = false, length = 255)
    private String title;

    /**
     * User who created the chat
     *
     * Many-to-One relationship:
     * - Many chats belong to one user
     * - One user can create 10 chats
     *
     * @ManyToOne - defines the relationship
     * @JoinColumn - creates "user_id" column in chats table
     * FetchType.LAZY - doesn't load User automatically (only when needed)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * List of documents in the chat
     *
     * One-to-Many relationship:
     * - One chat contains many documents
     *
     * mappedBy = "chat":
     * - Says that Document has a field named "chat"
     * - This field holds the relationship
     *
     * cascade = ALL:
     * - If chat is deleted → all its documents are also deleted
     *
     * orphanRemoval = true:
     * - If document is removed from list → it's deleted from DB
     */
    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Document> documents = new ArrayList<>();

    /**
     * List of messages in the chat (questions and answers)
     * One-to-Many relationship: one chat = many messages
     */
    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Message> messages = new ArrayList<>();

    /**
     * Chat status - where is it in the process?
     *
     * CREATING - User is creating the chat (uploading files)
     * PROCESSING - System is processing documents (embeddings)
     * READY - Ready! Can ask questions
     * FAILED - Something went wrong during processing
     *
     * @Enumerated(EnumType.STRING) - stores "READY" not 2
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatStatus status = ChatStatus.CREATING;

    /**
     * Collection name in Qdrant (Vector Database)
     *
     * Each chat gets its own unique collection!
     * Example: "chat_1_user_5"
     *
     * Why? So questions in chat 1 don't search documents from chat 2
     */
    @Column(name = "vector_collection_name")
    private String vectorCollectionName;

    /**
     * How many documents are still pending processing?
     *
     * When uploading 3 files: pendingDocuments = 3
     * File 1 finished: pendingDocuments = 2
     * File 2 finished: pendingDocuments = 1
     * File 3 finished: pendingDocuments = 0 → status = READY!
     */
    @Column(name = "pending_documents")
    private Integer pendingDocuments = 0;

    /**
     * Is the chat active (not deleted)?
     *
     * We don't actually delete from DB (Soft Delete)
     * Just change active to false
     */
    @Column(nullable = false)
    private Boolean active = true;

    /**
     * When the chat was created
     * updatable = false - cannot be changed after creation
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * When the chat was last updated
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * When the last activity occurred in the chat
     * Updated every time a question is sent
     */
    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    /**
     * Error message if there was a problem during processing
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    // ==================== Lifecycle Callbacks ====================
    // Functions that run automatically at certain times

    /**
     * Runs automatically before first save to DB
     * @PrePersist = before INSERT
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.lastActivityAt = LocalDateTime.now();

        if (this.status == null) {
            this.status = ChatStatus.CREATING;
        }
    }

    /**
     * Runs automatically before every update
     * @PreUpdate = before UPDATE
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ==================== Helper Methods ====================
    // Helper functions for managing the chat

    /**
     * Add document to chat
     * Also increments pendingDocuments by 1
     */
    public void addDocument(Document document) {
        documents.add(document);
        document.setChat(this);
        this.pendingDocuments++;
    }

    /**
     * Add message to chat
     * Also updates lastActivityAt
     */
    public void addMessage(Message message) {
        messages.add(message);
        message.setChat(this);
        this.lastActivityAt = LocalDateTime.now();
    }

    /**
     * One document finished processing - decrement by 1
     * If we reached 0 → status = READY!
     */
    public void decrementPendingDocuments() {
        if (this.pendingDocuments > 0) {
            this.pendingDocuments--;
        }

        if (this.pendingDocuments == 0 && this.status == ChatStatus.PROCESSING) {
            this.status = ChatStatus.READY;
        }
    }

    /**
     * Is the chat ready for questions?
     * Needs: status = READY + active = true
     */
    public boolean isReady() {
        return this.status == ChatStatus.READY && this.active;
    }

    /**
     * How many documents in the chat?
     */
    public int getDocumentCount() {
        return documents != null ? documents.size() : 0;
    }

    /**
     * How many messages in the chat?
     */
    public int getMessageCount() {
        return messages != null ? messages.size() : 0;
    }

    // ==================== Enum ====================

    /**
     * Possible chat statuses
     */
    public enum ChatStatus {
        CREATING,      // In creation process (uploading files)
        PROCESSING,    // Processing documents (embeddings)
        READY,         // Ready for questions!
        FAILED         // Failed processing
    }
}