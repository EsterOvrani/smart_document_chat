package com.example.backend.document.controller;

import com.example.backend.document.dto.DocumentResponse;
import com.example.backend.document.service.DocumentService;
import com.example.backend.shared.service.S3Service;
import com.example.backend.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller לניהול מסמכים
 * 
 * Endpoints:
 * - GET    /api/documents/chat/{chatId}  - מסמכים של שיחה
 * - GET    /api/documents/{id}           - פרטי מסמך
 * - GET    /api/documents/{id}/download  - הורדת מסמך
 * - DELETE /api/documents/{id}           - מחיקת מסמך
 * - GET    /api/documents/chat/{chatId}/stats - סטטיסטיקות
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class DocumentController {

    // ==================== Dependencies ====================
    
    private final DocumentService documentService;
    private final S3Service s3Service;

    // ==================== Get Documents ====================

    /**
     * קבלת כל המסמכים של שיחה
     * 
     * GET /api/documents/chat/{chatId}
     * 
     * Response: List<DocumentResponse>
     */
    @GetMapping("/chat/{chatId}")
    public ResponseEntity<Map<String, Object>> getDocumentsByChat(
            @PathVariable Long chatId) {

        try {
            User currentUser = getCurrentUser();
            
            List<DocumentResponse> documents = 
                documentService.getDocumentsByChat(chatId, currentUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", documents);
            response.put("count", documents.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get documents for chat: {}", chatId, e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל בקבלת מסמכים"
            );
        }
    }

    /**
     * קבלת מסמכים מעובדים בלבד
     * 
     * GET /api/documents/chat/{chatId}/processed
     * 
     * Response: List<DocumentResponse>
     */
    @GetMapping("/chat/{chatId}/processed")
    public ResponseEntity<Map<String, Object>> getProcessedDocuments(
            @PathVariable Long chatId) {

        try {
            User currentUser = getCurrentUser();
            
            List<DocumentResponse> documents = 
                documentService.getProcessedDocuments(chatId, currentUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", documents);
            response.put("count", documents.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get processed documents for chat: {}", chatId, e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל בקבלת מסמכים מעובדים"
            );
        }
    }

    /**
     * קבלת מסמך ספציפי
     * 
     * GET /api/documents/{id}
     * 
     * Response: DocumentResponse
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getDocument(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            
            DocumentResponse document = documentService.getDocument(id, currentUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", document);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.warn("Document not found or unauthorized: {}", id);
            return buildErrorResponse(HttpStatus.NOT_FOUND, "מסמך לא נמצא");

        } catch (Exception e) {
            log.error("Failed to get document: {}", id, e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל בקבלת המסמך"
            );
        }
    }

    // ==================== Download Document ====================

    /**
     * הורדת מסמך מקורי
     * 
     * GET /api/documents/{id}/download
     * 
     * Response: PDF file
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadDocument(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            
            // קבלת פרטי המסמך
            DocumentResponse document = documentService.getDocument(id, currentUser);

            // הורדה מ-MinIO
            InputStream fileStream = s3Service.downloadFile(document.getFilePath());

            // הכנת headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData(
                "attachment",
                document.getOriginalFileName()
            );

            return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(fileStream));

        } catch (RuntimeException e) {
            log.warn("Document not found or unauthorized: {}", id);
            return buildErrorResponse(HttpStatus.NOT_FOUND, "מסמך לא נמצא");

        } catch (Exception e) {
            log.error("Failed to download document: {}", id, e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל בהורדת המסמך"
            );
        }
    }

    /**
     * קבלת URL זמני להורדה
     * 
     * GET /api/documents/{id}/download-url
     * 
     * Response: { "url": "..." }
     */
    @GetMapping("/{id}/download-url")
    public ResponseEntity<Map<String, Object>> getDownloadUrl(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            
            // קבלת פרטי המסמך
            DocumentResponse document = documentService.getDocument(id, currentUser);

            // יצירת URL זמני (תקף ל-1 שעה)
            String presignedUrl = s3Service.getPresignedUrl(
                document.getFilePath(),
                3600  // 1 hour
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("url", presignedUrl);
            response.put("expiresIn", 3600);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "מסמך לא נמצא");

        } catch (Exception e) {
            log.error("Failed to generate download URL: {}", id, e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל ביצירת קישור הורדה"
            );
        }
    }

    // ==================== Delete Document ====================

    /**
     * מחיקת מסמך
     * 
     * DELETE /api/documents/{id}
     * 
     * Response: success message
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            documentService.deleteDocument(id, currentUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "מסמך נמחק בהצלחה");

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "מסמך לא נמצא");

        } catch (Exception e) {
            log.error("Failed to delete document: {}", id, e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל במחיקת המסמך"
            );
        }
    }

    // ==================== Statistics ====================

    /**
     * סטטיסטיקות מסמכים לשיחה
     * 
     * GET /api/documents/chat/{chatId}/stats
     * 
     * Response: DocumentStatistics
     */
    @GetMapping("/chat/{chatId}/stats")
    public ResponseEntity<Map<String, Object>> getDocumentStatistics(
            @PathVariable Long chatId) {

        try {
            DocumentService.DocumentStatistics stats = 
                documentService.getDocumentStatistics(chatId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", stats);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get document statistics for chat: {}", chatId, e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל בקבלת סטטיסטיקות"
            );
        }
    }

    // ==================== Helper Methods ====================

    /**
     * קבלת המשתמש המחובר
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("משתמש לא מחובר");
        }

        Object principal = authentication.getPrincipal();
        
        if (!(principal instanceof User)) {
            throw new SecurityException("משתמש לא תקין");
        }

        return (User) principal;
    }

    /**
     * בניית תגובת שגיאה
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String message) {

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);

        return ResponseEntity.status(status).body(response);
    }
}