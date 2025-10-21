// backend/src/main/java/com/example/backend/document/service/DocumentService.java
package com.example.backend.document.service;

import com.example.backend.chat.model.Chat;
import com.example.backend.chat.repository.ChatRepository;
import com.example.backend.document.dto.DocumentResponse;
import com.example.backend.document.mapper.DocumentMapper;
import com.example.backend.document.model.Document;
import com.example.backend.document.model.Document.ProcessingStatus;
import com.example.backend.document.repository.DocumentRepository;
import com.example.backend.shared.service.MinioService;
import com.example.backend.shared.service.OpenAIService;
import com.example.backend.shared.service.QdrantService;
import com.example.backend.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.scheduling.annotation.Async;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ChatRepository chatRepository; // ✅ שימוש ישיר ב-Repository במקום ChatService
    private final DocumentMapper documentMapper;
    private final MinioService minioService;
    private final OpenAIService openAIService;
    private final QdrantService qdrantService;

    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 200;

    @Async
    public void processDocument(MultipartFile file, Chat chat) {
        // ✅ הוסף logs מיד בהתחלה!
        log.info("🔵🔵🔵 ========================================");
        log.info("🔵 processDocument() CALLED!");
        log.info("🔵 Thread name: {}", Thread.currentThread().getName());
        log.info("🔵 File: {}", file.getOriginalFilename());
        log.info("🔵 File size: {}", file.getSize());
        log.info("🔵 Chat ID: {}", chat.getId());
        log.info("🔵 Chat title: {}", chat.getTitle());
        log.info("🔵🔵🔵 ========================================");

        Document document = null;
        String filePath = null;

        try {
            // ==================== 1. Upload to MinIO FIRST ====================
            log.info("📍 Step 1: Generating file path...");
            filePath = generateFilePath(chat, file);
            log.info("✅ File path generated: {}", filePath);

            log.info("📍 Step 2: Uploading to MinIO...");
            minioService.uploadFile(
                file.getInputStream(),
                filePath,
                file.getContentType(),
                file.getSize()
            );
            log.info("✅ File uploaded to MinIO successfully");

            // ==================== 2. Create Document Entity ====================
            log.info("📍 Step 3: Creating Document entity...");
            document = createDocumentEntity(file, chat, filePath);
            log.info("✅ Document entity created (not saved yet)");

            log.info("📍 Step 4: Saving Document to database...");
            document = documentRepository.save(document);
            log.info("✅ Document saved with ID: {}", document.getId());

            // ==================== 3. Validate File ====================
            validateFile(file);

            // ==================== 4. Start Processing ====================
            document.startProcessing();
            document = documentRepository.save(document);

            // ==================== 5. Extract Text from PDF ====================
            String text = extractTextFromPDF(file);
            
            int characterCount = text.length();
            document.setCharacterCount(characterCount);
            log.info("Extracted {} characters from PDF", characterCount);

            // ==================== 6. Split into Chunks ====================
            List<String> chunks = splitIntoChunks(text);
            int chunkCount = chunks.size();
            document.setChunkCount(chunkCount);
            log.info("Split into {} chunks", chunkCount);

            // ==================== 7. Create Embeddings ====================
            List<float[]> embeddings = createEmbeddings(chunks, document);
            log.info("Created {} embeddings", embeddings.size());

            // ==================== 8. Store in Qdrant ====================
            storeInQdrant(chat, document, chunks, embeddings);
            log.info("Stored embeddings in Qdrant");

            // ==================== 9. Mark as Completed ====================
            document.markAsCompleted(characterCount, chunkCount);
            documentRepository.save(document);

            // ✅ עדכן סטטוס השיחה ישירות דרך Repository
            updateChatStatus(chat.getId());

            log.info("Document {} processed successfully", document.getId());

        } catch (Exception e) {
            log.error("🔴🔴🔴 ========================================");
            log.error("🔴 EXCEPTION in processDocument()!");
            log.error("🔴 File: {}", file.getOriginalFilename());
            log.error("🔴 Error type: {}", e.getClass().getName());
            log.error("🔴 Error message: {}", e.getMessage());
            log.error("🔴🔴🔴 ========================================");
            e.printStackTrace();

            // עדכן הסטטוס של המסמך כנכשל
            if (document != null) {
                document.markAsFailed(e.getMessage());
                documentRepository.save(document);
            }

            // ✅ סמן שיחה כנכשלת ישירות דרך Repository
            markChatAsFailed(chat.getId(), "נכשל בעיבוד מסמך: " + file.getOriginalFilename());
            
            // נקה את הקובץ מ-MinIO אם הועלה
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

    /**
     * יצירת נתיב קובץ ב-MinIO
     */
    private String generateFilePath(Chat chat, MultipartFile file) {
        return String.format("users/%d/chats/%d/%s_%s",
            chat.getUser().getId(),
            chat.getId(),
            System.currentTimeMillis(),
            file.getOriginalFilename()
        );
    }

    /**
     * יצירת Document Entity עם file_path
     */
    private Document createDocumentEntity(MultipartFile file, Chat chat, String filePath) {
        Document document = new Document();
        document.setOriginalFileName(file.getOriginalFilename());
        document.setFileType("pdf");
        document.setFileSize(file.getSize());
        document.setFilePath(filePath); // ✅ הוסף את ה-filePath!
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

    /**
     * בדיקת תקינות קובץ
     */
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

    /**
     * פירסור PDF לטקסט
     */
    private String extractTextFromPDF(MultipartFile file) throws IOException {
        log.info("Extracting text from PDF: {}", file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream();
             PDDocument document = PDDocument.load(inputStream)) {

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            if (text == null || text.trim().isEmpty()) {
                throw new IOException("PDF לא מכיל טקסט");
            }

            return text.trim();
        }
    }

    /**
     * חלוקה ל-chunks
     */
    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            
            if (end < text.length()) {
                int lastPeriod = text.lastIndexOf('.', end);
                if (lastPeriod > start) {
                    end = lastPeriod + 1;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            start = end - CHUNK_OVERLAP;
            if (start >= text.length()) break;
        }

        log.info("Created {} chunks from text", chunks.size());
        return chunks;
    }

    /**
     * יצירת embeddings
     */
    private List<float[]> createEmbeddings(List<String> chunks, Document document) {
        List<float[]> embeddings = new ArrayList<>();

        int processed = 0;
        for (String chunk : chunks) {
            try {
                float[] embedding = openAIService.createEmbedding(chunk);
                embeddings.add(embedding);

                processed++;
                int progress = (processed * 80) / chunks.size();
                document.setProcessingProgress(progress);
                documentRepository.save(document);

            } catch (Exception e) {
                log.error("Failed to create embedding for chunk", e);
                throw new RuntimeException("נכשל ביצירת embedding", e);
            }
        }

        return embeddings;
    }

    /**
     * שמירה ב-Qdrant
     */
    private void storeInQdrant(Chat chat, Document document, 
                               List<String> chunks, List<float[]> embeddings) {
        
        String collectionName = chat.getVectorCollectionName();

        for (int i = 0; i < chunks.size(); i++) {
            try {
                qdrantService.upsertVector(
                    collectionName,
                    document.getId() + "_chunk_" + i,
                    embeddings.get(i),
                    chunks.get(i),
                    document.getId(),
                    document.getOriginalFileName()
                );

                int progress = 80 + ((i + 1) * 20) / chunks.size();
                document.setProcessingProgress(progress);
                documentRepository.save(document);

            } catch (Exception e) {
                log.error("Failed to store vector in Qdrant", e);
                throw new RuntimeException("נכשל בשמירת וקטור", e);
            }
        }
    }

    /**
     * עדכון סטטוס שיחה - ישירות דרך Repository
     */
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

    /**
     * סימון שיחה כנכשלת - ישירות דרך Repository
     */
    private void markChatAsFailed(Long chatId, String errorMessage) {
        log.error("Marking chat: {} as FAILED. Error: {}", chatId, errorMessage);

        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new RuntimeException("שיחה לא נמצאה"));

        chat.setStatus(Chat.ChatStatus.FAILED);
        chat.setErrorMessage(errorMessage);
        
        chatRepository.save(chat);
    }

    /**
     * חישוב SHA-256 hash
     */
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

    // ==================== Get Documents ====================

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

        try {
            String collectionName = document.getChat().getVectorCollectionName();
            qdrantService.deleteVectorsByDocument(collectionName, documentId);
            log.info("Deleted vectors for document: {}", documentId);
        } catch (Exception e) {
            log.warn("Failed to delete vectors from Qdrant", e);
        }

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