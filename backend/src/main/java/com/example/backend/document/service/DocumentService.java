package com.example.backend.document.service;

import com.example.backend.chat.model.Chat;
import com.example.backend.chat.repository.ChatRepository;
import com.example.backend.document.dto.DocumentResponse;
import com.example.backend.document.mapper.DocumentMapper;
import com.example.backend.document.model.Document;
import com.example.backend.document.model.Document.ProcessingStatus;
import com.example.backend.document.repository.DocumentRepository;
import com.example.backend.shared.service.S3Service;
import com.example.backend.shared.service.QdrantVectorService;
import com.example.backend.user.model.User;
import com.example.backend.shared.service.DocumentChunkingService;
import com.example.backend.document.model.Document;

// LangChain4j imports
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.scheduling.annotation.Async;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import java.io.ByteArrayInputStream;


@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ChatRepository chatRepository;
    private final DocumentMapper documentMapper;
    private final S3Service s3Service;
    private final QdrantVectorService qdrantVectorService;
    private final EmbeddingModel embeddingModel; // Injected from QdrantConfig
    private final DocumentChunkingService chunkingService;

    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 200;

    /**
     * עיבוד מסמך - נקודת כניסה
     * ⚠️ לא @Async כאן! נעביר את העבודה לפונקציה פנימית
     */
    public void processDocument(MultipartFile file, Chat chat) {
        log.info("🔵 ========================================");
        log.info("🔵 processDocument() CALLED - preparing file for async processing");
        log.info("🔵 File: {}", file.getOriginalFilename());
        log.info("🔵 File size: {}", file.getSize());
        log.info("🔵 Chat ID: {}", chat.getId());
        log.info("🔵 ========================================");

        try {
            // ✅ שלב 1: קרא את תוכן הקובץ לזיכרון לפני ה-Async
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();
            long fileSize = file.getSize();
            
            log.info("✅ File read to memory: {} bytes", fileBytes.length);
            
            // ✅ שלב 2: העבר את הנתונים לפונקציה Async
            processDocumentAsync(fileBytes, originalFilename, contentType, fileSize, chat);
            
        } catch (IOException e) {
            log.error("❌ Failed to read file to memory: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to read file", e);
        }
    }

    /**
     * עיבוד מסמך בפועל - רץ בתהליך נפרד
     */
    @Async
    public void processDocumentAsync(
            byte[] fileBytes,
            String originalFilename, 
            String contentType,
            long fileSize,
            Chat chat) {
        
        log.info("🔵 ========================================");
        log.info("🔵 processDocumentAsync() STARTED with LangChain4j!");
        log.info("🔵 File: {}", originalFilename);
        log.info("🔵 File bytes: {}", fileBytes.length);
        log.info("🔵 Chat ID: {}", chat.getId());
        log.info("🔵 ========================================");

        Document document = null;
        String filePath = null;

        try {
            // ✅ 1. Upload to MinIO - יצור InputStream מה-bytes
            log.info("📍 Step 1: Uploading to MinIO...");
            filePath = generateFilePath(chat, originalFilename);
            
            s3Service.uploadFile(
                new ByteArrayInputStream(fileBytes),  // ✅ יצור InputStream מה-bytes
                filePath,
                contentType,
                fileSize
            );
            log.info("✅ File uploaded to MinIO successfully");

            // ✅ 2. Create Document Entity
            log.info("📍 Step 2: Creating Document entity...");
            document = createDocumentEntity(originalFilename, fileSize, chat, filePath, fileBytes);
            document = documentRepository.save(document);
            log.info("✅ Document entity saved with ID: {} and size: {}", document.getId(), fileSize);

            // ✅ 3. Validate (בדיקה על ה-bytes, לא על MultipartFile)
            validateFile(originalFilename, fileBytes);
            document.startProcessing();
            document = documentRepository.save(document);

            // ✅ 4. Parse PDF using LangChain4j - יצור InputStream מה-bytes
            log.info("📍 Step 4: Parsing PDF with LangChain4j...");
            DocumentParser parser = new ApachePdfBoxDocumentParser();
            
            dev.langchain4j.data.document.Document langchainDoc = 
                parser.parse(new ByteArrayInputStream(fileBytes));  // ✅ יצור InputStream מה-bytes
                
            String text = langchainDoc.text();
            
            int characterCount = text.length();
            document.setCharacterCount(characterCount);
            log.info("✅ Extracted {} characters from PDF", characterCount);

            // ✅ 5. Split into chunks
            log.info("📍 Step 5: Splitting into chunks...");
            List<TextSegment> segments = chunkingService.chunkDocument(
                text, 
                originalFilename,
                document.getId()
            );
            
            int chunkCount = segments.size();
            document.setChunkCount(chunkCount);
            log.info("✅ Split into {} chunks", chunkCount);

            // ✅ 6. Store in Qdrant
            log.info("📍 Step 6: Storing in Qdrant...");
            String collectionName = chat.getVectorCollectionName();
            EmbeddingStore<TextSegment> embeddingStore = 
                qdrantVectorService.getEmbeddingStoreForCollection(collectionName);

            if (embeddingStore == null) {
                log.error("❌ No embedding store found for collection: {}", collectionName);
                throw new RuntimeException("No embedding store for collection: " + collectionName);
            }

            // Create embeddings and store
            int processed = 0;
            for (TextSegment segment : segments) {
                Embedding embedding = embeddingModel.embed(segment).content();
                
                segment.metadata().put("document_id", document.getId().toString());
                segment.metadata().put("document_name", originalFilename);
                segment.metadata().put("chunk_index", String.valueOf(processed));
                
                embeddingStore.add(embedding, segment);
                
                processed++;
                int progress = (processed * 100) / segments.size();
                document.setProcessingProgress(progress);
                documentRepository.save(document);
                
                log.debug("Processed chunk {}/{}", processed, segments.size());
            }

            // ✅ 7. Mark as Completed
            document.markAsCompleted(characterCount, chunkCount);
            documentRepository.save(document);
            
            updateChatStatus(chat.getId());
            
            log.info("✅ Document {} processed successfully", document.getId());

        } catch (Exception e) {
            log.error("🔴 EXCEPTION in processDocumentAsync()!", e);
            log.error("🔴 Exception type: {}", e.getClass().getName());
            log.error("🔴 Exception message: {}", e.getMessage());
            log.error("🔴 File name: {}", originalFilename);
            log.error("🔴 File size (reported): {}", fileSize);
            log.error("🔴 File bytes length: {}", fileBytes.length);
            
            if (document != null) {
                document.markAsFailed(e.getMessage());
                documentRepository.save(document);
            }
            
            markChatAsFailed(chat.getId(), "נכשל בעיבוד מסמך: " + originalFilename);
            
            if (filePath != null) {
                try {
                    s3Service.deleteFile(filePath);
                    log.info("Cleaned up file from MinIO: {}", filePath);
                } catch (Exception cleanupError) {
                    log.warn("Failed to cleanup file from MinIO: {}", filePath, cleanupError);
                }
            }
        }
    }

// ==================== Helper Methods ====================

    private String generateFilePath(Chat chat, String originalFilename) {
        return String.format("users/%d/chats/%d/%s_%s",
            chat.getUser().getId(),
            chat.getId(),
            System.currentTimeMillis(),
            originalFilename
        );
    }

    private Document createDocumentEntity(
            String originalFilename, 
            long fileSize, 
            Chat chat, 
            String filePath, 
            byte[] fileBytes) {
        
        Document document = new Document();
        document.setOriginalFileName(originalFilename);
        document.setFileType("pdf");
        document.setFileSize(fileSize);
        document.setFilePath(filePath);
        document.setProcessingStatus(ProcessingStatus.PENDING);
        document.setProcessingProgress(0);
        document.setChat(chat);
        document.setUser(chat.getUser());
        document.setActive(true);

        try {
            String hash = calculateHash(fileBytes);
            document.setContentHash(hash);
        } catch (Exception e) {
            log.warn("Failed to calculate hash for file: {}", originalFilename);
        }

        return document;
    }

    /**
     * ✅ בדיקת תקינות על ה-bytes, לא על MultipartFile
     */
    private void validateFile(String filename, byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("הקובץ ריק");
        }

        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("הקובץ חייב להיות PDF");
        }

        if (fileBytes.length > 50 * 1024 * 1024) {
            throw new IllegalArgumentException("הקובץ גדול מ-50MB");
        }
        
        log.info("✅ File validation passed: {} bytes", fileBytes.length);
    }

    private String calculateHash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (Exception e) {
            log.error("Failed to calculate hash", e);
            return null;
        }
    }


    
    private void updateChatStatus(Long chatId) {
        log.info("Updating status for chat: {}", chatId);

        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new RuntimeException("שיחה לא נמצאה"));

        chat.decrementPendingDocuments();

        if (chat.getPendingDocuments() == 0) {
            chat.setStatus(Chat.ChatStatus.READY);
            log.info("Chat {} is now READY", chatId);
        }

        chatRepository.save(chat);
    }

    private void markChatAsFailed(Long chatId, String errorMessage) {
        log.error("Marking chat: {} as FAILED. Error: {}", chatId, errorMessage);

        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new RuntimeException("שיחה לא נמצאה"));

        chat.setStatus(Chat.ChatStatus.FAILED);
        chat.setErrorMessage(errorMessage);
        
        chatRepository.save(chat);
    }

    // ==================== Get Documents Methods ====================

    public List<DocumentResponse> getDocumentsByChat(Long chatId, User user) {
        log.info("Getting documents for chat: {}", chatId);

        Chat chat = new Chat();
        chat.setId(chatId);

        List<Document> documents = documentRepository
            .findByChatAndActiveTrueOrderByCreatedAtDesc(chat);

        return documentMapper.toResponseList(documents);
    }

    public DocumentResponse getDocument(Long documentId, User user) {
        log.info("Getting document: {}", documentId);

        Document document = documentRepository.findByIdAndUserAndActiveTrue(documentId, user)
            .orElseThrow(() -> new RuntimeException("מסמך לא נמצא או אין הרשאה"));

        return documentMapper.toResponse(document);
    }

    public List<DocumentResponse> getProcessedDocuments(Long chatId, User user) {
        log.info("Getting processed documents for chat: {}", chatId);

        Chat chat = new Chat();
        chat.setId(chatId);

        List<Document> documents = documentRepository
            .findByChatAndProcessingStatusAndActiveTrue(chat, ProcessingStatus.COMPLETED);

        return documentMapper.toResponseList(documents);
    }

    public void deleteDocument(Long documentId, User user) {
        log.info("Deleting document: {}", documentId);

        Document document = documentRepository.findByIdAndUserAndActiveTrue(documentId, user)
            .orElseThrow(() -> new RuntimeException("מסמך לא נמצא או אין הרשאה"));

        document.setActive(false);
        documentRepository.save(document);

        // Delete from MinIO
        try {
            s3Service.deleteFile(document.getFilePath());
            log.info("Deleted file from MinIO: {}", document.getFilePath());
        } catch (Exception e) {
            log.warn("Failed to delete file from MinIO", e);
        }
    }

    public DocumentStatistics getDocumentStatistics(Long chatId) {
        Chat chat = new Chat();
        chat.setId(chatId);

        long total = documentRepository.countByChatAndActiveTrue(chat);
        long completed = documentRepository.countByChatAndProcessingStatusAndActiveTrue(
            chat, ProcessingStatus.COMPLETED);
        long processing = documentRepository.countByChatAndProcessingStatusAndActiveTrue(
            chat, ProcessingStatus.PROCESSING);
        long failed = documentRepository.countByChatAndProcessingStatusAndActiveTrue(
            chat, ProcessingStatus.FAILED);

        Long totalSize = documentRepository.getTotalFileSizeByChat(chat);
        Integer totalChars = documentRepository.getTotalCharacterCountByChat(chat);
        Integer totalChunks = documentRepository.getTotalChunkCountByChat(chat);

        return DocumentStatistics.builder()
            .totalDocuments(total)
            .completedDocuments(completed)
            .processingDocuments(processing)
            .failedDocuments(failed)
            .totalFileSize(totalSize)
            .totalCharacters(totalChars)
            .totalChunks(totalChunks)
            .build();
    }

    /**
     * מחיקת כל המסמכים של שיחה (לפי chat ID)
     * מוחק רק מה-DB, לא קבצים מ-MinIO
     * 
     * @param chatId - מזהה השיחה
     * @param user - המשתמש (לבדיקת הרשאות)
     * @return כמה מסמכים נמחקו
     */
    @Transactional
    public int deleteAllDocumentsByChat(Long chatId, User user) {
        try {
            log.info("🗑️ Deleting all documents for chat: {}", chatId);

            // יצירת Chat entity עם ה-ID (לשאילתה)
            Chat chat = new Chat();
            chat.setId(chatId);

            // קבלת כל המסמכים
            List<Document> documents = documentRepository
                .findByChatAndActiveTrueOrderByCreatedAtDesc(chat);

            if (documents.isEmpty()) {
                log.info("📂 No documents found to delete for chat: {}", chatId);
                return 0;
            }

            // בדיקת הרשאות - שכל המסמכים שייכים למשתמש
            boolean unauthorized = documents.stream()
                .anyMatch(doc -> !doc.getUser().getId().equals(user.getId()));
            
            if (unauthorized) {
                throw new SecurityException("אין הרשאה למחוק מסמכים של משתמש אחר");
            }

            int count = documents.size();

            // מחיקה מה-DB
            documentRepository.deleteAll(documents);

            log.info("✅ Deleted {} document entities for chat: {}", count, chatId);
            return count;

        } catch (SecurityException e) {
            log.error("❌ Security violation: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("❌ Failed to delete documents for chat: {}", chatId, e);
            throw new RuntimeException("Failed to delete chat documents", e);
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class DocumentStatistics {
        private Long totalDocuments;
        private Long completedDocuments;
        private Long processingDocuments;
        private Long failedDocuments;
        private Long totalFileSize;
        private Integer totalCharacters;
        private Integer totalChunks;
    }
}