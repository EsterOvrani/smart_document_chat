package com.example.backend.chat.service;

import com.example.backend.chat.dto.*;
import com.example.backend.chat.mapper.ChatMapper;
import com.example.backend.chat.model.Chat;
import com.example.backend.chat.model.Chat.ChatStatus;
import com.example.backend.chat.repository.ChatRepository;
import com.example.backend.document.service.DocumentService;
import com.example.backend.shared.service.QdrantVectorService;
import com.example.backend.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.backend.document.model.Document;
import com.example.backend.document.repository.DocumentRepository;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

import java.util.List;

/**
 * Service לניהול שיחות
 * עדכון לשימוש ב-QdrantVectorService במקום QdrantService
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
    private final QdrantVectorService qdrantVectorService; // שינוי!
    private final DocumentRepository documentRepository;

    // ==================== Create Chat ====================

    /**
     * יצירת שיחה חדשה עם מסמכים
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
        
        // שימוש ב-QdrantVectorService במקום QdrantService
        String collectionName = qdrantVectorService.createNewCollectionForUpload(request.getFileCount());
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
            throw new RuntimeException("נכשל בעיבוד מסמכים", e);
        }

        // ==================== Return Response ====================

        log.info("🎉 createChat completed for chat ID: {}", chat.getId());
        return chatMapper.toResponse(chat);
    }

    // ==================== Get Chats ====================

    /**
     * קבלת כל השיחות של משתמש
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
     * קבלת שיחה ספציפית
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
     * חיפוש שיחות
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
     * עדכון כותרת שיחה
     */
    public ChatResponse updateChatTitle(Long chatId, String newTitle, User user) {
        log.info("Updating chat: {} title to: {}", chatId, newTitle);

        Chat chat = findChatByIdAndUser(chatId, user);
        
        chat.setTitle(newTitle);
        chat = chatRepository.save(chat);

        return chatMapper.toResponse(chat);
    }

    /**
     * עדכון סטטוס שיחה
     */
    public void updateChatStatus(Long chatId) {
        log.info("Updating status for chat: {}", chatId);

        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new RuntimeException("שיחה לא נמצאה"));

        chat.decrementPendingDocuments();

        if (chat.getPendingDocuments() == 0) {
            chat.setStatus(ChatStatus.READY);
            log.info("Chat {} is now READY", chatId);
        }

        chatRepository.save(chat);
    }

    /**
     * סימון שיחה כנכשלת
     */
    public void markChatAsFailed(Long chatId, String errorMessage) {
        log.error("Marking chat: {} as FAILED. Error: {}", chatId, errorMessage);

        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new RuntimeException("שיחה לא נמצאה"));

        chat.setStatus(ChatStatus.FAILED);
        chat.setErrorMessage(errorMessage);
        
        chatRepository.save(chat);
    }

    // ==================== Delete Chat ====================

    /**
     * מחיקת שיחה (Soft Delete)
     */
    public void deleteChat(Long chatId, User user) {
        log.info("Deleting chat: {} for user: {}", chatId, user.getUsername());

        Chat chat = findChatByIdAndUser(chatId, user);

        // Soft delete - רק מסמן כלא פעיל
        chat.setActive(false);
        chatRepository.save(chat);

        // אופציונלי: הסרת קולקשין מ-cache
        try {
            qdrantVectorService.removeCollectionFromCache(chat.getVectorCollectionName());
            log.info("Collection removed from cache: {}", chat.getVectorCollectionName());
        } catch (Exception e) {
            log.warn("Failed to remove collection from cache: {}", 
                chat.getVectorCollectionName(), e);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * מציאת שיחה לפי ID + בדיקת הרשאות
     */
    private Chat findChatByIdAndUser(Long chatId, User user) {
        return chatRepository.findByIdAndUserAndActiveTrue(chatId, user)
            .orElseThrow(() -> new RuntimeException("שיחה לא נמצאה או אין הרשאה"));
    }

    /**
     * ספירת שיחות לפי סטטוס
     */
    private int countByStatus(List<Chat> chats, ChatStatus status) {
        return (int) chats.stream()
            .filter(chat -> chat.getStatus() == status)
            .count();
    }

    /**
     * בדיקת תקינות בקשה
     */
    private void validateCreateChatRequest(CreateChatRequest request) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("כותרת השיחה היא שדה חובה");
        }

        if (request.getFiles() == null || request.getFiles().isEmpty()) {
            throw new IllegalArgumentException("חייב להעלות לפחות קובץ אחד");
        }

        if (!request.validateFileTypes()) {
            throw new IllegalArgumentException("ניתן להעלות רק קבצי PDF");
        }

        if (request.hasEmptyFiles()) {
            throw new IllegalArgumentException("אחד או יותר מהקבצים ריקים");
        }

        if (request.hasOversizedFiles()) {
            throw new IllegalArgumentException("אחד או יותר מהקבצים גדולים מ-50MB");
        }
    }

    /**
     * בדיקת תקינות משתמש
     */
    private void validateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("משתמש לא תקין");
        }

        if (!user.isEnabled()) {
            throw new SecurityException("משתמש לא מאומת");
        }
    }

    // ==================== Statistics ====================

    /**
     * סטטיסטיקות על שיחות המשתמש
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
     * קבלת סטטוס עיבוד מפורט של שיחה
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
     * קביעת שלב העיבוד לפי אחוז ההתקדמות
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
     * פורמט גודל קובץ קריא
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