package com.example.backend.chat.service;

import com.example.backend.chat.dto.*;
import com.example.backend.chat.mapper.ChatMapper;
import com.example.backend.chat.model.Chat;
import com.example.backend.chat.model.Chat.ChatStatus;
import com.example.backend.chat.repository.ChatRepository;
import com.example.backend.document.service.DocumentService;
import com.example.backend.common.infrastructure.vectordb.QdrantVectorService;
import com.example.backend.user.model.User;
import com.example.backend.document.model.Document;
import com.example.backend.document.repository.DocumentRepository;
import com.example.backend.chat.model.Message;
import com.example.backend.chat.repository.MessageRepository;
import com.example.backend.common.infrastructure.storage.S3Service;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.common.exception.ValidationException;
import com.example.backend.common.exception.UnauthorizedException;
import com.example.backend.common.exception.ExternalServiceException;
import com.example.backend.common.exception.FileProcessingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


import java.util.stream.Collectors;
import java.time.LocalDateTime;

import java.util.List;

/**
 * Service for managing chats
 * Uses QdrantVectorService for vector operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChatService {

    // ==================== Dependencies ====================

    private final ChatRepository chatRepository;
    private final ChatMapper chatMapper;
    private final DocumentService documentService;
    private final QdrantVectorService qdrantVectorService;
    private final DocumentRepository documentRepository;
    private final MessageRepository messageRepository;
    private final S3Service s3Service;
    

    // ==================== Create Chat ====================

    /**
     * Create a new chat with documents
     */
    public ChatResponse createChat(CreateChatRequest request, User user) {
        log.info("========================================");
        log.info("🚀 Creating new chat for user: {} with title: {}", 
            user.getUsername(), request.getTitle());
        log.info("📦 Number of files in request: {}", request.getFiles().size());
        
        for (int i = 0; i < request.getFiles().size(); i++) {
            MultipartFile file = request.getFiles().get(i);
            log.info("📄 File {}: name={}, size={}, contentType={}", 
                i + 1, 
                file.getOriginalFilename(), 
                file.getSize(), 
                file.getContentType());
        }
        log.info("========================================");

        // ==================== Validation ====================

        validateCreateChatRequest(request);
        validateUser(user);

        // ==================== Create Chat Entity ====================

        Chat chat = new Chat();
        chat.setTitle(request.getTitle());
        chat.setUser(user);
        chat.setStatus(ChatStatus.CREATING);
        chat.setPendingDocuments(request.getFileCount());

        // ==================== Create Qdrant Collection using QdrantVectorService ====================

        String collectionName = qdrantVectorService.createNewCollectionForUpload(request.getTitle());
        chat.setVectorCollectionName(collectionName);

        chat = chatRepository.save(chat);
        log.info("✅ Chat created with ID: {} with collection: {}", chat.getId(), collectionName);

        // ==================== Upload Documents ====================

        try {
            chat.setStatus(ChatStatus.PROCESSING);
            chat = chatRepository.save(chat);
            log.info("✅ Chat status changed to PROCESSING");

            List<MultipartFile> files = request.getFiles();
            log.info("📤 Starting to process {} files", files.size());

            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                log.info("📄 Processing file {}/{}: {}", i + 1, files.size(), file.getOriginalFilename());

                try {
                    documentService.processDocument(file, chat);
                    log.info("✅ File queued for processing");
                } catch (Exception e) {
                    log.error("❌ FAILED to process file: {}", 
                        file.getOriginalFilename(), e);
                }
            }

            log.info("✅ All files queued for processing for chat: {}", chat.getId());

        } catch (Exception e) {
            log.error("❌ Failed to process documents for chat: {}", chat.getId(), e);
            chat.setStatus(ChatStatus.FAILED);
            chat.setErrorMessage("נכשל בעיבוד מסמכים: " + e.getMessage());
            chatRepository.save(chat);
            throw FileProcessingException.uploadFailed("מסמכי השיחה");
        }

        // ==================== Return Response ====================

        log.info("🎉 createChat completed for chat ID: {}", chat.getId());
        return chatMapper.toResponse(chat);
    }

    // ==================== Get Chats ====================

    /**
     * Get all chats for a user
     */
    public ChatListResponse getAllChats(User user) {
        log.info("Getting all chats for user: {}", user.getUsername());

        validateUser(user);

        List<Chat> chats = chatRepository
            .findByUserAndActiveTrueOrderByLastActivityAtDesc(user);

        List<ChatListResponse.ChatSummary> summaries = 
            chatMapper.toChatSummaryList(chats);

        return ChatListResponse.builder()
            .chats(summaries)
            .totalCount(chats.size())
            .readyCount(countByStatus(chats, ChatStatus.READY))
            .processingCount(countByStatus(chats, ChatStatus.PROCESSING))
            .failedCount(countByStatus(chats, ChatStatus.FAILED))
            .build();
    }

    /**
     * Get a specific chat
     */
    public ChatResponse getChat(Long chatId, User user) {
        log.info("Getting chat: {} for user: {}", chatId, user.getUsername());

        Chat chat = findChatByIdAndUser(chatId, user);
        
        ChatResponse response = chatMapper.toResponse(chat);
        
        if (chat.isReady()) {
            response.setDocuments(
                chatMapper.toDocumentInfoList(chat.getDocuments())
            );
        }

        return response;
    }

    /**
     * Search chats
     */
    public ChatListResponse searchChats(String searchTerm, User user) {
        log.info("Searching chats with term: {} for user: {}", searchTerm, user.getUsername());

        validateUser(user);

        List<Chat> chats = chatRepository
            .findByUserAndTitleContainingIgnoreCaseAndActiveTrue(user, searchTerm);

        List<ChatListResponse.ChatSummary> summaries = 
            chatMapper.toChatSummaryList(chats);

        return ChatListResponse.builder()
            .chats(summaries)
            .totalCount(chats.size())
            .build();
    }

    // ==================== Update Chat ====================

    /**
     * Update chat title
     */
    public ChatResponse updateChatTitle(Long chatId, String newTitle, User user) {
        log.info("Updating chat: {} title to: {}", chatId, newTitle);

        Chat chat = findChatByIdAndUser(chatId, user);
        
        chat.setTitle(newTitle);
        chat = chatRepository.save(chat);

        return chatMapper.toResponse(chat);
    }

    /**
     * Update chat status
     */
    public void updateChatStatus(Long chatId) {
        log.info("Updating status for chat: {}", chatId);

        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new ResourceNotFoundException("שיחה", chatId));

        chat.decrementPendingDocuments();

        if (chat.getPendingDocuments() == 0) {
            chat.setStatus(ChatStatus.READY);
            log.info("Chat {} is now READY", chatId);
        }

        chatRepository.save(chat);
    }

    /**
     * Mark chat as failed
     */
    public void markChatAsFailed(Long chatId, String errorMessage) {
        log.error("Marking chat: {} as FAILED. Error: {}", chatId, errorMessage);

        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new ResourceNotFoundException("שיחה", chatId));

        chat.setStatus(ChatStatus.FAILED);
        chat.setErrorMessage(errorMessage);
        
        chatRepository.save(chat);
    }

    // ==================== Delete Chat ====================

    /**
     * Delete entire chat
     */
    public void deleteChat(Long chatId, User user) {
        log.info("========================================");
        log.info("🗑️ Starting FULL deletion of chat: {}", chatId);
        log.info("========================================");

        Chat chat = findChatByIdAndUser(chatId, user);
        String collectionName = chat.getVectorCollectionName();
        
        int deletedFiles = 0;
        int deletedDocuments = 0;
        int deletedMessages = 0;

        try {
            // ==================== 1. Delete Qdrant Collection ====================
            if (collectionName != null && !collectionName.isEmpty()) {
                try {
                    log.info("📍 Step 1: Deleting Qdrant collection");
                    qdrantVectorService.deleteCollection(collectionName);
                    log.info("✅ Qdrant collection deleted");
                } catch (Exception e) {
                    log.error("❌ Failed to delete Qdrant collection", e);
                }
            }

            // ==================== 2. Delete Files from S3 ====================
            try {
                log.info("📍 Step 2: Deleting files from MinIO");
                String folderPath = String.format("users/%d/chats/%d/", user.getId(), chatId);
                s3Service.deleteFolder(folderPath);
                log.info("✅ Files deleted from MinIO");
            } catch (Exception e) {
                log.error("❌ Failed to delete files from MinIO", e);
            }

            // ==================== 3. Delete Documents from DB ====================
            try {
                log.info("📍 Step 3: Deleting Document entities via DocumentService");
                deletedDocuments = documentService.deleteAllDocumentsByChat(chatId, user);
                log.info("✅ Deleted {} documents", deletedDocuments);
            } catch (Exception e) {
                log.error("❌ Failed to delete documents", e);
                throw e;
            }

            // ==================== 4. Delete Messages from DB ====================
            try {
                log.info("📍 Step 4: Deleting Message entities");
                List<Message> messages = messageRepository.findByChatOrderByCreatedAtAsc(chat);
                deletedMessages = messages.size();
                messageRepository.deleteAll(messages);
                log.info("✅ Deleted {} messages", deletedMessages);
            } catch (Exception e) {
                log.error("❌ Failed to delete messages", e);
                throw e;
            }

            // ==================== 5. Delete Chat from DB ====================
            try {
                log.info("📍 Step 5: Deleting Chat entity");
                chatRepository.delete(chat);
                log.info("✅ Chat deleted");
            } catch (Exception e) {
                log.error("❌ Failed to delete chat", e);
                throw e;
            }

            // ==================== Summary ====================
            log.info("========================================");
            log.info("✅ FULL DELETION COMPLETED for chat: {}", chatId);
            log.info("📊 Summary:");
            log.info("   - Qdrant collection: {}", collectionName != null ? "Deleted" : "N/A");
            log.info("   - Files from MinIO: Deleted");
            log.info("   - Document entities: {}", deletedDocuments);
            log.info("   - Message entities: {}", deletedMessages);
            log.info("   - Chat entity: Deleted");
            log.info("========================================");

        } catch (Exception e) {
            log.error("========================================");
            log.error("❌ CRITICAL ERROR during deletion", e);
            log.error("========================================");
            throw ExternalServiceException.storageServiceError("נכשל במחיקת השיחה: " + e.getMessage());

        }
    }
        
    // ==================== Helper Methods ====================

    /**
     * Find chat by ID with permission check
     */
    private Chat findChatByIdAndUser(Long chatId, User user) {
        Chat chat = chatRepository.findByIdAndActiveTrue(chatId)
            .orElseThrow(() -> new ResourceNotFoundException("שיחה", chatId));
        
        if (!chat.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("שיחה", chatId);
        }
        
        return chat;
    }

    /**
     * Count chats by status
     */
    private int countByStatus(List<Chat> chats, ChatStatus status) {
        return (int) chats.stream()
            .filter(chat -> chat.getStatus() == status)
            .count();
    }

    /**
     * Validate create chat request
     */
    private void validateCreateChatRequest(CreateChatRequest request) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new ValidationException("title", "כותרת השיחה היא שדה חובה");
        }

        if (request.getFiles() == null || request.getFiles().isEmpty()) {
            throw new ValidationException("files", "חייב להעלות לפחות קובץ אחד");
        }

        if (!request.validateFileTypes()) {
            throw FileProcessingException.invalidFileType(
                "קבצים", 
                "PDF"
            );
        }

        if (request.hasEmptyFiles()) {
            throw new ValidationException("files", "אחד או יותר מהקבצים ריקים");
        }

        if (request.hasOversizedFiles()) {
            throw FileProcessingException.fileTooLarge(
                "אחד מהקבצים", 
                50L * 1024 * 1024
            );
        }
    }

    /**
     * Validate user
     */
    private void validateUser(User user) {
        if (user == null) {
            throw new ValidationException("user", "משתמש לא תקין");
        }

        if (!user.isEnabled()) {
            throw new UnauthorizedException("משתמש לא מאומת");
        }
    }

    // ==================== Statistics ====================

    /**
     * Get user chat statistics
     */
    public ChatListResponse.GeneralStatistics getUserStatistics(User user) {
        log.info("Getting statistics for user: {}", user.getUsername());

        List<Chat> chats = chatRepository.findByUserAndActiveTrueOrderByLastActivityAtDesc(user);

        int totalDocuments = chats.stream()
            .mapToInt(Chat::getDocumentCount)
            .sum();

        int totalMessages = chats.stream()
            .mapToInt(Chat::getMessageCount)
            .sum();

        double avgMessages = chats.isEmpty() ? 0.0 : 
            (double) totalMessages / chats.size();

        double avgDocuments = chats.isEmpty() ? 0.0 : 
            (double) totalDocuments / chats.size();

        return ChatListResponse.GeneralStatistics.builder()
            .totalDocuments(totalDocuments)
            .totalMessages(totalMessages)
            .averageMessagesPerChat(avgMessages)
            .averageDocumentsPerChat(avgDocuments)
            .build();
    }

    // ==================== Processing Status ====================
    /**
     * Get detailed processing status for a chat
     */
    public ProcessingStatusResponse getProcessingStatus(Long chatId, User user) {
        log.info("Getting processing status for chat: {}", chatId);

        Chat chat = findChatByIdAndUser(chatId, user);
        
        List<Document> documents = documentRepository
            .findByChatAndActiveTrueOrderByCreatedAtDesc(chat);

        int totalDocs = documents.size();
        int completedDocs = (int) documents.stream()
            .filter(doc -> doc.getProcessingStatus() == Document.ProcessingStatus.COMPLETED)
            .count();
        int processingDocs = (int) documents.stream()
            .filter(doc -> doc.getProcessingStatus() == Document.ProcessingStatus.PROCESSING)
            .count();
        int failedDocs = (int) documents.stream()
            .filter(doc -> doc.getProcessingStatus() == Document.ProcessingStatus.FAILED)
            .count();

        int overallProgress = totalDocs > 0 
            ? (completedDocs * 100) / totalDocs 
            : 0;

        Document currentProcessingDoc = documents.stream()
            .filter(doc -> doc.getProcessingStatus() == Document.ProcessingStatus.PROCESSING)
            .findFirst()
            .orElse(null);

        int remainingDocs = totalDocs - completedDocs - failedDocs;
        long estimatedTimeRemaining = remainingDocs * 30L;

        LocalDateTime startTime = chat.getCreatedAt();
        long elapsedSeconds = startTime != null 
            ? java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds()
            : 0L;

        ProcessingStatusResponse.ProcessingStatusResponseBuilder responseBuilder = 
            ProcessingStatusResponse.builder()
                .status(chat.getStatus().name())
                .isReady(chat.isReady())
                .overallProgress(overallProgress)
                .totalDocuments(totalDocs)
                .completedDocuments(completedDocs)
                .processingDocuments(processingDocs)
                .failedDocuments(failedDocs)
                .estimatedTimeRemaining(estimatedTimeRemaining)
                .errorMessage(chat.getErrorMessage())
                .processingStartedAt(startTime)
                .elapsedTimeSeconds(elapsedSeconds);

        if (currentProcessingDoc != null) {
            ProcessingStatusResponse.CurrentDocument currentDoc = 
                ProcessingStatusResponse.CurrentDocument.builder()
                    .id(currentProcessingDoc.getId())
                    .name(currentProcessingDoc.getOriginalFileName())
                    .progress(currentProcessingDoc.getProcessingProgress() != null 
                        ? currentProcessingDoc.getProcessingProgress() 
                        : 0)
                    .stage(getProcessingStage(currentProcessingDoc.getProcessingProgress()))
                    .fileSize(currentProcessingDoc.getFileSize())
                    .fileSizeFormatted(formatFileSize(currentProcessingDoc.getFileSize()))
                    .build();
            
            responseBuilder.currentDocument(currentDoc);
        }

        List<ProcessingStatusResponse.DocumentStatus> docStatuses = documents.stream()
            .map(doc -> {
                Long processingTime = null;
                if (doc.getCreatedAt() != null && doc.getProcessedAt() != null) {
                    processingTime = java.time.Duration
                        .between(doc.getCreatedAt(), doc.getProcessedAt())
                        .getSeconds();
                }

                return ProcessingStatusResponse.DocumentStatus.builder()
                    .id(doc.getId())
                    .name(doc.getOriginalFileName())
                    .status(doc.getProcessingStatus().name())
                    .progress(doc.getProcessingProgress() != null ? doc.getProcessingProgress() : 0)
                    .fileSize(doc.getFileSize())
                    .fileSizeFormatted(formatFileSize(doc.getFileSize()))
                    .errorMessage(doc.getErrorMessage())
                    .startedAt(doc.getCreatedAt())
                    .completedAt(doc.getProcessedAt())
                    .processingTimeSeconds(processingTime)
                    .build();
            })
            .collect(Collectors.toList());

        responseBuilder.documents(docStatuses);

        return responseBuilder.build();
    }

    /**
     * Determine processing stage based on progress percentage
     */
    private String getProcessingStage(Integer progress) {
        if (progress == null || progress < 10) {
            return "UPLOADING";
        } else if (progress < 30) {
            return "EXTRACTING_TEXT";
        } else if (progress < 80) {
            return "CREATING_EMBEDDINGS";
        } else if (progress < 100) {
            return "STORING";
        } else {
            return "COMPLETED";
        }
    }

    /**
     * Format file size to human-readable string
     */
    private String formatFileSize(Long bytes) {
        if (bytes == null || bytes == 0) {
            return "0 B";
        }

        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}