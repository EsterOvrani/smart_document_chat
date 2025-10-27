package com.example.backend.document.service;

import com.example.backend.chat.model.Chat;
import com.example.backend.chat.repository.ChatRepository;
import com.example.backend.document.dto.DocumentResponse;
import com.example.backend.document.mapper.DocumentMapper;
import com.example.backend.document.model.Document;
import com.example.backend.document.model.Document.ProcessingStatus;
import com.example.backend.document.repository.DocumentRepository;
import com.example.backend.shared.service.MinioService;
import com.example.backend.share.service.QdrantVectorService;
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

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ChatRepository chatRepository;
    private final DocumentMapper documentMapper;
    private final MinioService minioService;
    private final QdrantVectorService qdrantVectorService;
    private final EmbeddingModel embeddingModel; // Injected from QdrantConfig
    private final DocumentChunkingService chunkingService;

    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 200;

    @Async
    public void processDocument(MultipartFile file, Chat chat) {
        log.info("🔵 ========================================");
        log.info("🔵 processDocument() CALLED with LangChain4j!");
        log.info("🔵 File: {}", file.getOriginalFilename());
        log.info("🔵 Chat ID: {}", chat.getId());
        log.info("🔵 ========================================");

        Document document = null;
        String filePath = null;

        try {
            // 1. Upload to MinIO
            log.info("📍 Step 1: Uploading to MinIO...");
            filePath = generateFilePath(chat, file);
            minioService.uploadFile(
                file.getInputStream(),
                filePath,
                file.getContentType(),
                file.getSize()
            );
            log.info("✅ File uploaded to MinIO successfully");

            // 2. Create Document Entity
            log.info("📍 Step 2: Creating Document entity...");
            document = createDocumentEntity(file, chat, filePath);
            document = documentRepository.save(document);
            log.info("✅ Document saved with ID: {}", document.getId());

            // 3. Validate File
            validateFile(file);
            document.startProcessing();
            document = documentRepository.save(document);

            // 4. Parse PDF using LangChain4j
            log.info("📍 Step 4: Parsing PDF with LangChain4j...");
            DocumentParser parser = new ApachePdfBoxDocumentParser();
            dev.langchain4j.data.document.Document langchainDoc = parser.parse(file.getInputStream());
            String text = langchainDoc.text();
            
            int characterCount = text.length();
            document.setCharacterCount(characterCount);
            log.info("✅ Extracted {} characters from PDF", characterCount);

            // 5. Split into chunks using LangChain4j
            log.info("📍 Step 5: Splitting into chunks...");
            // ⭐ שימוש ב-DocumentChunkingService
            List<TextSegment> segments = chunkingService.chunkDocument(
                text, 
                document.getOriginalFileName(),
                document.getId()
            );
            
            int chunkCount = segments.size();
            document.setChunkCount(chunkCount);
            log.info("✅ Split into {} chunks", chunkCount);

            // 6. Store in Qdrant using LangChain4j EmbeddingStore
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
                // Create embedding using model
                Embedding embedding = embeddingModel.embed(segment).content();
                
                // Add metadata to segment
                segment.metadata().put("document_id", document.getId().toString());
                segment.metadata().put("document_name", document.getOriginalFileName());
                segment.metadata().put("chunk_index", String.valueOf(processed));
                
                // Store in Qdrant
                embeddingStore.add(embedding, segment);
                
                processed++;
                int progress = (processed * 100) / segments.size();
                document.setProcessingProgress(progress);
                documentRepository.save(document);
                
                log.debug("Processed chunk {}/{}", processed, segments.size());
            }

            // 7. Mark as Completed
            document.markAsCompleted(characterCount, chunkCount);
            documentRepository.save(document);
            
            // Update chat status
            updateChatStatus(chat.getId());
            
            log.info("✅ Document {} processed successfully", document.getId());

        } catch (Exception e) {
            log.error("🔴 EXCEPTION in processDocument()!", e);
            
            if (document != null) {
                document.markAsFailed(e.getMessage());
                documentRepository.save(document);
            }
            
            markChatAsFailed(chat.getId(), "נכשל בעיבוד מסמך: " + file.getOriginalFilename());
            
            // Cleanup MinIO if needed
            if (filePath != null) {
                try {
                    minioService.deleteFile(filePath);
                    log.info("Cleaned up file from MinIO: {}", filePath);
                } catch (Exception cleanupError) {
                    log.warn("Failed to cleanup file from MinIO: {}", filePath, cleanupError);
                }
            }
        }
    }

    // ==================== Helper Methods ====================

    private String generateFilePath(Chat chat, MultipartFile file) {
        return String.format("users/%d/chats/%d/%s_%s",
            chat.getUser().getId(),
            chat.getId(),
            System.currentTimeMillis(),
            file.getOriginalFilename()
        );
    }

    private Document createDocumentEntity(MultipartFile file, Chat chat, String filePath) {
        Document document = new Document();
        document.setOriginalFileName(file.getOriginalFilename());
        document.setFileType("pdf");
        document.setFileSize(file.getSize());
        document.setFilePath(filePath);
        document.setProcessingStatus(ProcessingStatus.PENDING);
        document.setProcessingProgress(0);
        document.setChat(chat);
        document.setUser(chat.getUser());
        document.setActive(true);

        try {
            byte[] fileBytes = file.getBytes();
            String hash = calculateHash(fileBytes);
            document.setContentHash(hash);
        } catch (IOException e) {
            log.warn("Failed to calculate hash for file: {}", file.getOriginalFilename());
        }

        return document;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("הקובץ ריק");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("הקובץ חייב להיות PDF");
        }

        if (file.getSize() > 50 * 1024 * 1024) {
            throw new IllegalArgumentException("הקובץ גדול מ-50MB");
        }
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
            minioService.deleteFile(document.getFilePath());
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