package com.example.backend.document.service;

import com.example.backend.chat.model.Chat;
import com.example.backend.chat.repository.ChatRepository;
import com.example.backend.document.dto.DocumentResponse;
import com.example.backend.document.mapper.DocumentMapper;
import com.example.backend.document.model.Document;
import com.example.backend.document.model.Document.ProcessingStatus;
import com.example.backend.document.repository.DocumentRepository;
import com.example.backend.common.infrastructure.storage.S3Service;
import com.example.backend.common.infrastructure.vectordb.QdrantVectorService;
import com.example.backend.user.model.User;
import com.example.backend.common.infrastructure.document.DocumentChunkingService;
import com.example.backend.document.model.Document;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.common.exception.ValidationException;
import com.example.backend.common.exception.UnauthorizedException;
import com.example.backend.common.exception.FileProcessingException;
import com.example.backend.common.exception.ExternalServiceException;

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
     * Process document - entry point
     * Note: Not @Async here - delegates work to internal async function
     */
    public void processDocument(MultipartFile file, Chat chat) {
        log.info("🔵 ========================================");
        log.info("🔵 processDocument() CALLED - preparing file for async processing");
        log.info("🔵 File: {}", file.getOriginalFilename());
        log.info("🔵 File size: {}", file.getSize());
        log.info("🔵 Chat ID: {}", chat.getId());
        log.info("🔵 ========================================");
        
        try {
            // Step 1: Read file content to memory before async processing
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();
            long fileSize = file.getSize();
            
            log.info("✅ File read to memory: {} bytes", fileBytes.length);
            
            // Step 2: Pass data to async function
            processDocumentAsync(fileBytes, originalFilename, contentType, fileSize, chat);
            
        } catch (IOException e) {
            log.error("❌ Failed to read file to memory: {}", file.getOriginalFilename(), e);
            throw FileProcessingException.uploadFailed(file.getOriginalFilename());
        }
    }

    /**
     * Actual document processing - runs in separate thread
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
            // Step 1: Upload to S3 - create InputStream from bytes
            log.info("📍 Step 1: Uploading to MinIO...");
            filePath = generateFilePath(chat, originalFilename);
            
            s3Service.uploadFile(
                new ByteArrayInputStream(fileBytes),
                filePath,
                contentType,
                fileSize
            );
            log.info("✅ File uploaded to MinIO successfully");

            // Step 2: Create Document Entity
            log.info("📍 Step 2: Creating Document entity...");
            document = createDocumentEntity(originalFilename, fileSize, chat, filePath, fileBytes);
            document = documentRepository.save(document);
            log.info("✅ Document entity saved with ID: {} and size: {}", document.getId(), fileSize);

            // Step 3: Validate (check bytes, not MultipartFile)
            validateFile(originalFilename, fileBytes);
            document.startProcessing();
            document = documentRepository.save(document);

            // Step 4: Parse PDF using LangChain4j - create InputStream from bytes
            log.info("📍 Step 4: Parsing PDF with LangChain4j...");
            DocumentParser parser = new ApachePdfBoxDocumentParser();
            
            dev.langchain4j.data.document.Document langchainDoc =
                parser.parse(new ByteArrayInputStream(fileBytes));
                
            String text = langchainDoc.text();
            
            int characterCount = text.length();
            document.setCharacterCount(characterCount);
            log.info("✅ Extracted {} characters from PDF", characterCount);

            // Step 5: Split into chunks
            log.info("📍 Step 5: Splitting into chunks...");
            List<TextSegment> segments = chunkingService.chunkDocument(
                text, 
                originalFilename,
                document.getId()
            );
            
            int chunkCount = segments.size();
            document.setChunkCount(chunkCount);
            log.info("✅ Split into {} chunks", chunkCount);

            // Step 6: Store in Qdrant
            log.info("📍 Step 6: Storing in Qdrant...");
            String collectionName = chat.getVectorCollectionName();
            EmbeddingStore<TextSegment> embeddingStore = 
                qdrantVectorService.getEmbeddingStoreForCollection(collectionName);

            if (embeddingStore == null) {
                log.error("❌ No embedding store found for collection: {}", collectionName);
                throw ExternalServiceException.vectorDbError("לא נמצא אחסון וקטורי עבור הקולקשן: " + collectionName);

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

            // Step 7: Mark as Completed
            document.markAsCompleted(characterCount, chunkCount);
            documentRepository.save(document);
            
            updateChatStatus(chat.getId());
            
            log.info("✅ Document {} processed successfully", document.getId());

        } catch (FileProcessingException e) {
            // שגיאת עיבוד קובץ ספציפית
            log.error("🔴 File processing error: {}", e.getMessage());
            if (document != null) {
                document.markAsFailed(e.getMessage());
                documentRepository.save(document);
            }
            markChatAsFailed(chat.getId(), "נכשל בעיבוד מסמך: " + originalFilename);
            cleanupFile(filePath);
            throw e;
            
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
            cleanupFile(filePath);
            
            throw FileProcessingException.uploadFailed(originalFilename);
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
     * Validate file using bytes, not MultipartFile
     */
    private void validateFile(String filename, byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new ValidationException("file", "הקובץ ריק");
        }

        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw FileProcessingException.invalidFileType(filename, "PDF");
        }

        if (fileBytes.length > 50 * 1024 * 1024) {
            throw FileProcessingException.fileTooLarge(filename, 50L * 1024 * 1024);
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
            .orElseThrow(() -> new ResourceNotFoundException("שיחה", chatId));
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
            .orElseThrow(() -> new ResourceNotFoundException("שיחה", chatId));

        chat.setStatus(Chat.ChatStatus.FAILED);
        chat.setErrorMessage(errorMessage);
        
        chatRepository.save(chat);
    }

    private void cleanupFile(String filePath) {
        if (filePath != null) {
            try {
                s3Service.deleteFile(filePath);
                log.info("Cleaned up file from MinIO: {}", filePath);
            } catch (Exception cleanupError) {
                log.warn("Failed to cleanup file from MinIO: {}", filePath, cleanupError);
            }
        }
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

        Document document = documentRepository.findByIdAndActiveTrue(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("מסמך", documentId));
        
        if (!document.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("מסמך", documentId);
        }

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

        Document document = documentRepository.findByIdAndActiveTrue(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("מסמך", documentId));
        
        if (!document.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("מסמך", documentId);
        }

        document.setActive(false);
        documentRepository.save(document);

        // Delete from MinIO
        try {
            s3Service.deleteFile(document.getFilePath());
            log.info("Deleted file from MinIO: {}", document.getFilePath());
        } catch (Exception e) {
            log.warn("Failed to delete file from MinIO", e);
            throw ExternalServiceException.storageServiceError("נכשל במחיקת הקובץ מהאחסון");
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
     * Delete all documents for a chat (by chat ID)
     * Deletes only from DB, not files from S3
     *
     * @param chatId - chat identifier
     * @param user - user (for permission check)
     * @return number of documents deleted
     */
    @Transactional
    public int deleteAllDocumentsByChat(Long chatId, User user) {
        try {
            log.info("🗑️ Deleting all documents for chat: {}", chatId);

            // Create Chat entity with ID (for query)
            Chat chat = new Chat();
            chat.setId(chatId);

            // Get all documents
            List<Document> documents = documentRepository
                .findByChatAndActiveTrueOrderByCreatedAtDesc(chat);

            if (documents.isEmpty()) {
                log.info("📂 No documents found to delete for chat: {}", chatId);
                return 0;
            }

            // Permission check - ensure all documents belong to user
            boolean unauthorized = documents.stream()
                .anyMatch(doc -> !doc.getUser().getId().equals(user.getId()));
                            
            if (unauthorized) {
                throw new UnauthorizedException("אין הרשאה למחוק מסמכים של משתמש אחר");
            }

            int count = documents.size();

            // Delete from DB
            documentRepository.deleteAll(documents);

            log.info("✅ Deleted {} document entities for chat: {}", count, chatId);
            return count;

        } catch (UnauthorizedException e) {
            log.error("❌ Authorization violation: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("❌ Failed to delete documents for chat: {}", chatId, e);
            throw new ResourceNotFoundException("נכשל במחיקת המסמכים");
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