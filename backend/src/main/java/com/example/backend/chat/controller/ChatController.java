package com.example.backend.chat.controller;

import com.example.backend.chat.dto.*;
import com.example.backend.chat.model.Message;
import com.example.backend.chat.service.ChatAIService;
import com.example.backend.chat.service.ChatService;
import com.example.backend.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing chats
 *
 * Endpoints:
 * - POST   /api/chats              - Create chat
 * - GET    /api/chats              - List chats
 * - GET    /api/chats/{id}         - Get chat details
 * - PUT    /api/chats/{id}         - Update chat
 * - DELETE /api/chats/{id}         - Delete chat
 * - POST   /api/chats/{id}/ask     - Ask question
 * - GET    /api/chats/{id}/messages - Get message history
 */
@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")  // In production - replace with specific origins!
public class ChatController {

    // ==================== Dependencies ====================
    
    private final ChatService chatService;
    private final ChatAIService chatAIService;

    // ==================== Create Chat ====================

    /**
     * Create new chat
     *
     * POST /api/chats
     * Content-Type: multipart/form-data
     *
     * Body:
     * - title: String (chat title)
     * - files: List<MultipartFile> (PDF files)
     *
     * Response: ChatResponse
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createChat(
            @RequestParam("title") String title,
            @RequestParam("files") List<MultipartFile> files) {

        try {
            log.info("========================================");
            log.info("🎯 ChatController.createChat() called");
            log.info("📝 Title: {}", title);
            log.info("📦 Files received: {}", files != null ? files.size() : "NULL!");
            
            if (files != null) {
                for (int i = 0; i < files.size(); i++) {
                    MultipartFile file = files.get(i);
                    log.info("   File {}: name='{}', size={}, isEmpty={}", 
                        i + 1, 
                        file.getOriginalFilename(), 
                        file.getSize(),
                        file.isEmpty());
                }
            }
            log.info("========================================");

            User currentUser = getCurrentUser();
            log.info("👤 Current user: {} (ID: {})", currentUser.getEmail(), currentUser.getId());

            CreateChatRequest request = new CreateChatRequest(title, files);
            log.info("📋 CreateChatRequest created with {} files", request.getFileCount());

            log.info("🚀 Calling chatService.createChat()...");
            ChatResponse chatResponse = chatService.createChat(request, currentUser);
            log.info("✅ chatService.createChat() returned successfully");
            log.info("✅ Chat ID: {}", chatResponse.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "שיחה נוצרה בהצלחה");
            response.put("chat", chatResponse);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("❌ Validation error: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {
            log.error("❌ Failed to create chat", e);
            log.error("❌ Exception type: {}", e.getClass().getName());
            log.error("❌ Exception message: {}", e.getMessage());
            e.printStackTrace();
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל ביצירת השיחה: " + e.getMessage()
            );
        }
    }

    // ==================== Get Chats ====================

    /**
     * Get all chats for the user
     *
     * GET /api/chats
     *
     * Response: ChatListResponse
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllChats() {
        try {
            User currentUser = getCurrentUser();
            
            ChatListResponse chatsResponse = chatService.getAllChats(currentUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", chatsResponse);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get chats", e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל בקבלת רשימת שיחות"
            );
        }
    }

    /**
     * Search chats
     *
     * GET /api/chats/search?q=contract
     *
     * Response: ChatListResponse
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchChats(
            @RequestParam("q") String searchTerm) {

        try {
            User currentUser = getCurrentUser();
            
            ChatListResponse chatsResponse = chatService.searchChats(searchTerm, currentUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", chatsResponse);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to search chats", e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל בחיפוש שיחות"
            );
        }
    }

    /**
     * Get specific chat
     *
     * GET /api/chats/{id}
     *
     * Response: ChatResponse
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getChat(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            
            ChatResponse chatResponse = chatService.getChat(id, currentUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", chatResponse);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.warn("Chat not found or unauthorized: {}", id);
            return buildErrorResponse(HttpStatus.NOT_FOUND, "שיחה לא נמצאה");

        } catch (Exception e) {
            log.error("Failed to get chat: {}", id, e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל בקבלת פרטי השיחה"
            );
        }
    }

    // ==================== Update Chat ====================

    /**
     * Update chat title
     *
     * PUT /api/chats/{id}
     * Body: { "title": "new title" }
     *
     * Response: ChatResponse
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateChat(
            @PathVariable Long id,
            @RequestBody Map<String, String> requestBody) {

        try {
            String newTitle = requestBody.get("title");
            
            if (newTitle == null || newTitle.trim().isEmpty()) {
                return buildErrorResponse(
                    HttpStatus.BAD_REQUEST,
                    "כותרת חדשה היא שדה חובה"
                );
            }

            User currentUser = getCurrentUser();
            ChatResponse chatResponse = chatService.updateChatTitle(id, newTitle, currentUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "שיחה עודכנה בהצלחה");
            response.put("data", chatResponse);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "שיחה לא נמצאה");

        } catch (Exception e) {
            log.error("Failed to update chat: {}", id, e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל בעדכון השיחה"
            );
        }
    }

    // ==================== Delete Chat ====================

    /**
     * Delete chat
     *
     * DELETE /api/chats/{id}
     *
     * Response: success message
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteChat(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            chatService.deleteChat(id, currentUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "שיחה נמחקה בהצלחה");

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "שיחה לא נמצאה");

        } catch (Exception e) {
            log.error("Failed to delete chat: {}", id, e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל במחיקת השיחה"
            );
        }
    }

    // ==================== Ask Question ====================

    /**
     * Ask a question
     *
     * POST /api/chats/{id}/ask
     * Body: AskQuestionRequest
     *
     * Response: AnswerResponse
     */
    @PostMapping("/{id}/ask")
    public ResponseEntity<Map<String, Object>> askQuestion(
            @PathVariable Long id,
            @Valid @RequestBody AskQuestionRequest request) {

        try {
            log.info("Asking question in chat: {}", id);

            User currentUser = getCurrentUser();
            
            AnswerResponse answerResponse = chatAIService.askQuestion(id, request, currentUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", answerResponse.getSuccess());
            response.put("data", answerResponse);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.warn("Failed to answer question: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {
            log.error("Failed to answer question in chat: {}", id, e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל בעיבוד השאלה: " + e.getMessage()
            );
        }
    }

    // ==================== Get Messages ====================

    /**
     * Get message history
     *
     * GET /api/chats/{id}/messages
     *
     * Response: List<Message>
     */
    @GetMapping("/{id}/messages")
    public ResponseEntity<Map<String, Object>> getChatMessages(@PathVariable Long id) {
        log.info("🔵 GET /api/chats/{}/messages called", id);
        
        try {
            User currentUser = getCurrentUser();
            log.info("✅ Current user: {}", currentUser.getEmail());
            
            List<Message> messages = chatAIService.getChatHistory(id, currentUser);
            log.info("✅ Retrieved {} messages", messages.size());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", messages);
            response.put("count", messages.size());

            log.info("✅ Returning response with {} messages", messages.size());
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("❌ RuntimeException: {}", e.getMessage(), e);
            return buildErrorResponse(HttpStatus.NOT_FOUND, "שיחה לא נמצאה");

        } catch (Exception e) {
            log.error("❌ Exception in getChatMessages for chat {}: {}", id, e.getMessage(), e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל בקבלת ההודעות: " + e.getMessage()
            );
        }
    }

    // ==================== Statistics ====================

    /**
     * Get user chat statistics
     *
     * GET /api/chats/statistics
     *
     * Response: GeneralStatistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getUserStatistics() {
        try {
            User currentUser = getCurrentUser();
            
            ChatListResponse.GeneralStatistics stats = 
                chatService.getUserStatistics(currentUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", stats);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get statistics", e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל בקבלת סטטיסטיקות"
            );
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Get currently authenticated user
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
     * Build error response
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String message) {

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);

        return ResponseEntity.status(status).body(response);
    }

    // ==================== Processing Status ====================
    /**
     * Get detailed processing status
     *
     * GET /api/chats/{id}/processing-status
     *
     * Response: ProcessingStatusResponse
     */
    @GetMapping("/{id}/processing-status")
    public ResponseEntity<Map<String, Object>> getProcessingStatus(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            
            ProcessingStatusResponse status = chatService.getProcessingStatus(id, currentUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", status);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.warn("Failed to get processing status for chat: {}", id);
            return buildErrorResponse(HttpStatus.NOT_FOUND, "שיחה לא נמצאה");

        } catch (Exception e) {
            log.error("Failed to get processing status for chat: {}", id, e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "שגיאה בקבלת סטטוס עיבוד"
            );
        }
    }
}