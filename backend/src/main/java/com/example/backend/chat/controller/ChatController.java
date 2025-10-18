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
 * Controller לניהול שיחות
 * 
 * Endpoints:
 * - POST   /api/chats              - יצירת שיחה
 * - GET    /api/chats              - רשימת שיחות
 * - GET    /api/chats/{id}         - פרטי שיחה
 * - PUT    /api/chats/{id}         - עדכון שיחה
 * - DELETE /api/chats/{id}         - מחיקת שיחה
 * - POST   /api/chats/{id}/ask     - שאילת שאלה
 * - GET    /api/chats/{id}/messages - היסטוריית הודעות
 */
@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")  // בפרודקשן - החלף ל-origins ספציפיים!
public class ChatController {

    // ==================== Dependencies ====================
    
    private final ChatService chatService;
    private final ChatAIService chatAIService;

    // ==================== Create Chat ====================

    /**
     * יצירת שיחה חדשה
     * 
     * POST /api/chats
     * Content-Type: multipart/form-data
     * 
     * Body:
     * - title: String (כותרת השיחה)
     * - files: List<MultipartFile> (קבצי PDF)
     * 
     * Response: ChatResponse
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createChat(
            @RequestParam("title") String title,
            @RequestParam("files") List<MultipartFile> files) {

        try {
            log.info("Creating new chat with title: {}", title);

            // קבלת המשתמש המחובר
            User currentUser = getCurrentUser();

            // בניית בקשה
            CreateChatRequest request = new CreateChatRequest(title, files);

            // יצירת השיחה
            ChatResponse chatResponse = chatService.createChat(request, currentUser);

            // בניית תגובה
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "שיחה נוצרה בהצלחה");
            response.put("chat", chatResponse);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request: {}", e.getMessage());
            return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {
            log.error("Failed to create chat", e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל ביצירת השיחה: " + e.getMessage()
            );
        }
    }

    // ==================== Get Chats ====================

    /**
     * קבלת כל השיחות של המשתמש
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
     * חיפוש שיחות
     * 
     * GET /api/chats/search?q=חוזה
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
     * קבלת שיחה ספציפית
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
     * עדכון כותרת שיחה
     * 
     * PUT /api/chats/{id}
     * Body: { "title": "כותרת חדשה" }
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
     * מחיקת שיחה
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
     * שאילת שאלה
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
     * קבלת היסטוריית הודעות
     * 
     * GET /api/chats/{id}/messages
     * 
     * Response: List<Message>
     */
    @GetMapping("/{id}/messages")
    public ResponseEntity<Map<String, Object>> getChatMessages(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            
            List<Message> messages = chatAIService.getChatHistory(id, currentUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", messages);
            response.put("count", messages.size());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, "שיחה לא נמצאה");

        } catch (Exception e) {
            log.error("Failed to get messages for chat: {}", id, e);
            return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "נכשל בקבלת ההודעות"
            );
        }
    }

    // ==================== Statistics ====================

    /**
     * סטטיסטיקות על שיחות המשתמש
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