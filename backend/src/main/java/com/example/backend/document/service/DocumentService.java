package com.example.backend.document.service;

import com.example.backend.chat.model.Chat;
import com.example.backend.chat.service.ChatService;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Service לעיבוד מסמכים
 * 
 * זרימת עיבוד:
 * 1. קבלת קובץ PDF
 * 2. שמירה ב-MinIO
 * 3. פירסור PDF → טקסט
 * 4. חלוקה ל-chunks
 * 5. יצירת embeddings (OpenAI)
 * 6. שמירה ב-Qdrant
 * 7. עדכון סטטוס
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DocumentService {

    // ==================== Dependencies ====================
    
    private final DocumentRepository documentRepository;
    private final DocumentMapper documentMapper;
    private final MinioService minioService;
    private final OpenAIService openAIService;
    private final QdrantService qdrantService;
    private final ChatService chatService;

    // ==================== Constants ====================
    
    private static final int CHUNK_SIZE = 1000;  // 1000 תווים לchunk
    private static final int CHUNK_OVERLAP = 200;  // חפיפה של 200 תווים

    // ==================== Process Document ====================

    /**
     * עיבוד מסמך - נקודת הכניסה הראשית
     * 
     * @Async - רץ באופן אסינכרוני (לא חוסם)
     */
    @Async
    public void processDocument(MultipartFile file, Chat chat) {
        log.info("Starting to process document: {} for chat: {}", 
            file.getOriginalFilename(), chat.getId());

        Document document = null;

        try {
            // ==================== 1. Create Document Entity ====================
            
            document = createDocumentEntity(file, chat);
            document = documentRepository.save(document);
            log.info("Document entity created with ID: {}", document.getId());

            // ==================== 2. Validate File ====================
            
            validateFile(file);
            
            // בדיקת כפילויות
            checkForDuplicates(file, chat);

            // ==================== 3. Upload to MinIO ====================
            
            String filePath = uploadToMinio(file, chat, document);
            document.setFilePath(filePath);

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

            // עדכן את סטטוס השיחה
            chatService.updateChatStatus(chat.getId());

            log.info("Document {} processed successfully", document.getId());

        } catch (Exception e) {
            log.error("Failed to process document: {}", file.getOriginalFilename(), e);

            if (document != null) {
                document.markAsFailed(e.getMessage());
                documentRepository.save(document);
            }

            // עדכן שיחה שנכשל
            chatService.markChatAsFailed(chat.getId(), 
                "נכשל בעיבוד מסמך: " + file.getOriginalFilename());
        }
    }

    // ==================== Helper Methods ====================

    /**
     * יצירת Document Entity
     */
    private Document createDocumentEntity(MultipartFile file, Chat chat) {
        Document document = new Document();
        document.setOriginalFileName(file.getOriginalFilename());
        document.setFileType("pdf");
        document.setFileSize(file.getSize());
        document.setProcessingStatus(ProcessingStatus.PENDING);
        document.setProcessingProgress(0);
        document.setChat(chat);
        document.setUser(chat.getUser());
        document.setActive(true);

        // חישוב hash
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

        // מקסימום 50MB
        if (file.getSize() > 50 * 1024 * 1024) {
            throw new IllegalArgumentException("הקובץ גדול מ-50MB");
        }
    }

    /**
     * בדיקת כפילויות
     */
    private void checkForDuplicates(MultipartFile file, Chat chat) {
        // בדיקה לפי שם
        documentRepository.findByChatAndOriginalFileNameAndActiveTrue(
            chat, file.getOriginalFilename()
        ).ifPresent(doc -> {
            throw new IllegalArgumentException(
                "קובץ עם שם זהה כבר קיים בשיחה זו");
        });

        // בדיקה לפי hash
        try {
            byte[] fileBytes = file.getBytes();
            String hash = calculateHash(fileBytes);
            
            documentRepository.findByChatAndContentHashAndActiveTrue(chat, hash)
                .ifPresent(doc -> {
                    throw new IllegalArgumentException(
                        "קובץ עם תוכן זהה כבר קיים בשיחה זו");
                });
        } catch (IOException e) {
            log.warn("Could not check for duplicate by hash", e);
        }
    }

    /**
     * העלאה ל-MinIO
     */
    private String uploadToMinio(MultipartFile file, Chat chat, Document document) 
            throws IOException {
        
        String path = String.format("users/%d/chats/%d/%s_%s",
            chat.getUser().getId(),
            chat.getId(),
            document.getId(),
            file.getOriginalFilename()
        );

        minioService.uploadFile(
            file.getInputStream(),
            path,
            file.getContentType(),
            file.getSize()
        );

        log.info("File uploaded to MinIO: {}", path);
        return path;
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
     * חלוקה ל-chunks עם חפיפה
     */
    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            
            // נסה למצוא סוף משפט
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

            // התקדם עם חפיפה
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

                // עדכון progress
                processed++;
                int progress = (processed * 80) / chunks.size();  // 0-80%
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
                    document.getId() + "_chunk_" + i,  // ID ייחודי
                    embeddings.get(i),
                    chunks.get(i),
                    document.getId(),
                    document.getOriginalFileName()
                );

                // עדכון progress
                int progress = 80 + ((i + 1) * 20) / chunks.size();  // 80-100%
                document.setProcessingProgress(progress);
                documentRepository.save(document);

            } catch (Exception e) {
                log.error("Failed to store vector in Qdrant", e);
                throw new RuntimeException("נכשל בשמירת וקטור", e);
            }
        }
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

    /**
     * קבלת כל המסמכים של שיחה
     */
    public List<DocumentResponse> getDocumentsByChat(Long chatId, User user) {
        log.info("Getting documents for chat: {}", chatId);

        // TODO: בדיקת הרשאות - וודא שהשיחה שייכת למשתמש

        Chat chat = new Chat();
        chat.setId(chatId);

        List<Document> documents = documentRepository
            .findByChatAndActiveTrueOrderByCreatedAtDesc(chat);

        return documentMapper.toResponseList(documents);
    }

    /**
     * קבלת מסמך ספציפי
     */
    public DocumentResponse getDocument(Long documentId, User user) {
        log.info("Getting document: {}", documentId);

        Document document = documentRepository.findByIdAndUserAndActiveTrue(documentId, user)
            .orElseThrow(() -> new RuntimeException("מסמך לא נמצא או אין הרשאה"));

        return documentMapper.toResponse(document);
    }

    /**
     * קבלת מסמכים מעובדים בלבד
     */
    public List<DocumentResponse> getProcessedDocuments(Long chatId, User user) {
        log.info("Getting processed documents for chat: {}", chatId);

        Chat chat = new Chat();
        chat.setId(chatId);

        List<Document> documents = documentRepository
            .findByChatAndProcessingStatusAndActiveTrue(chat, ProcessingStatus.COMPLETED);

        return documentMapper.toResponseList(documents);
    }

    // ==================== Delete Document ====================

    /**
     * מחיקת מסמך (Soft Delete)
     */
    public void deleteDocument(Long documentId, User user) {
        log.info("Deleting document: {}", documentId);

        Document document = documentRepository.findByIdAndUserAndActiveTrue(documentId, user)
            .orElseThrow(() -> new RuntimeException("מסמך לא נמצא או אין הרשאה"));

        // Soft delete
        document.setActive(false);
        documentRepository.save(document);

        // מחיקת הוקטורים מ-Qdrant
        try {
            String collectionName = document.getChat().getVectorCollectionName();
            qdrantService.deleteVectorsByDocument(collectionName, documentId);
            log.info("Deleted vectors for document: {}", documentId);
        } catch (Exception e) {
            log.warn("Failed to delete vectors from Qdrant", e);
        }

        // מחיקה מ-MinIO
        try {
            minioService.deleteFile(document.getFilePath());
            log.info("Deleted file from MinIO: {}", document.getFilePath());
        } catch (Exception e) {
            log.warn("Failed to delete file from MinIO", e);
        }
    }

    // ==================== Statistics ====================

    /**
     * סטטיסטיקות מסמכים לשיחה
     */
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

    // ==================== Inner Classes ====================

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