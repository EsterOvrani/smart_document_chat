package com.example.backend.chat.service;

import com.example.backend.chat.dto.AskQuestionRequest;
import com.example.backend.chat.dto.AnswerResponse;
import com.example.backend.chat.mapper.MessageMapper;
import com.example.backend.chat.model.Chat;
import com.example.backend.chat.model.Message;
import com.example.backend.chat.model.Message.MessageRole;
import com.example.backend.chat.repository.ChatRepository;
import com.example.backend.chat.repository.MessageRepository;
import com.example.backend.shared.service.OpenAIService;
import com.example.backend.shared.service.QdrantService;
import com.example.backend.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service לטיפול בשאלות ותשובות AI
 * 
 * זרימה:
 * 1. קבלת שאלה מהמשתמש
 * 2. שמירת השאלה כ-Message
 * 3. חיפוש מסמכים רלוונטיים ב-Qdrant
 * 4. שליחת הקשר + שאלה ל-OpenAI
 * 5. קבלת תשובה
 * 6. שמירת התשובה כ-Message
 * 7. החזרת AnswerResponse
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChatAIService {

    // ==================== Dependencies ====================
    
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;
    private final QdrantService qdrantService;
    private final OpenAIService openAIService;

    // ==================== Constants ====================
    
    private static final int DEFAULT_CONTEXT_MESSAGES = 5;
    private static final int MAX_RELEVANT_CHUNKS = 5;

    // ==================== Ask Question ====================

    /**
     * שאילת שאלה - נקודת הכניסה הראשית
     */
    public AnswerResponse askQuestion(Long chatId, AskQuestionRequest request, User user) {
        log.info("Processing question for chat: {} from user: {}", 
            chatId, user.getUsername());

        long startTime = System.currentTimeMillis();

        try {
            // ==================== 1. Validate ====================
            
            Chat chat = validateAndGetChat(chatId, user);
            validateRequest(request);

            if (!chat.isReady()) {
                throw new RuntimeException(
                    "השיחה עדיין לא מוכנה. סטטוס: " + chat.getStatus());
            }

            // ==================== 2. Save User Question ====================
            
            Message userMessage = saveUserMessage(request.getQuestion(), chat, user);
            log.info("User message saved with ID: {}", userMessage.getId());

            // ==================== 3. Get Context ====================
            
            List<Message> contextMessages = getContextMessages(
                chat, 
                request.getContextMessageCount() != null 
                    ? request.getContextMessageCount() 
                    : DEFAULT_CONTEXT_MESSAGES
            );

            // ==================== 4. Search Relevant Documents ====================
            
            List<QdrantService.SearchResult> relevantChunks = 
                searchRelevantDocuments(chat, request.getQuestion());

            if (relevantChunks.isEmpty()) {
                return createNoResultsResponse(userMessage);
            }

            // ==================== 5. Build AI Prompt ====================
            
            String prompt = buildPrompt(
                request.getQuestion(),
                contextMessages,
                relevantChunks
            );

            // ==================== 6. Call OpenAI ====================
            
            String answer = openAIService.chat(prompt);
            
            // ==================== 7. Calculate Metrics ====================
            
            long responseTime = System.currentTimeMillis() - startTime;
            Double confidence = calculateConfidence(relevantChunks);
            Integer tokensUsed = estimateTokens(prompt, answer);

            // ==================== 8. Build Sources ====================
            
            List<AnswerResponse.Source> sources = buildSources(relevantChunks);

            // ==================== 9. Save Assistant Message ====================
            
            Message assistantMessage = saveAssistantMessage(
                answer,
                sources,
                confidence,
                tokensUsed,
                responseTime,
                chat,
                userMessage.getId()
            );

            // ==================== 10. Build Response ====================
            
            AnswerResponse response = AnswerResponse.builder()
                .answer(answer)
                .success(true)
                .confidence(confidence)
                .sources(sources)
                .messageId(assistantMessage.getId())
                .tokensUsed(tokensUsed)
                .responseTimeMs(responseTime)
                .timestamp(LocalDateTime.now())
                .build();

            log.info("Question answered successfully in {}ms", responseTime);
            return response;

        } catch (Exception e) {
            log.error("Failed to answer question for chat: {}", chatId, e);
            return createErrorResponse(e.getMessage());
        }
    }

    // ==================== Helper Methods ====================

    /**
     * בדיקת תקינות שיחה והרשאות
     */
    private Chat validateAndGetChat(Long chatId, User user) {
        return chatRepository.findByIdAndUserAndActiveTrue(chatId, user)
            .orElseThrow(() -> new RuntimeException("שיחה לא נמצאה או אין הרשאה"));
    }

    /**
     * בדיקת תקינות בקשה
     */
    private void validateRequest(AskQuestionRequest request) {
        request.validateContextCount();

        if (!request.isValid()) {
            throw new IllegalArgumentException("השאלה לא תקינה");
        }
    }

    /**
     * שמירת שאלת המשתמש
     */
    private Message saveUserMessage(String question, Chat chat, User user) {
        Message message = new Message();
        message.setContent(question);
        message.setRole(MessageRole.USER);
        message.setChat(chat);
        message.setUser(user);
        message.setCreatedAt(LocalDateTime.now());

        return messageRepository.save(message);
    }

    /**
     * קבלת הקשר - הודעות אחרונות
     */
    private List<Message> getContextMessages(Chat chat, int count) {
        if (count <= 0) {
            return new ArrayList<>();
        }

        // קח את X ההודעות האחרונות
        List<Message> messages = messageRepository
            .findByChatOrderByCreatedAtDesc(chat, PageRequest.of(0, count));

        // הפוך את הסדר (מהישן לחדש)
        java.util.Collections.reverse(messages);

        log.info("Retrieved {} context messages", messages.size());
        return messages;
    }

    /**
     * חיפוש מסמכים רלוונטיים ב-Qdrant
     */
    private List<QdrantService.SearchResult> searchRelevantDocuments(
            Chat chat, String question) {
        
        try {
            // יצירת embedding לשאלה
            float[] questionEmbedding = openAIService.createEmbedding(question);

            // חיפוש ב-Qdrant
            List<QdrantService.SearchResult> results = qdrantService.search(
                chat.getVectorCollectionName(),
                questionEmbedding,
                MAX_RELEVANT_CHUNKS
            );

            log.info("Found {} relevant chunks", results.size());
            return results;

        } catch (Exception e) {
            log.error("Failed to search relevant documents", e);
            return new ArrayList<>();
        }
    }

    /**
     * בניית prompt ל-OpenAI
     */
    private String buildPrompt(
            String question,
            List<Message> contextMessages,
            List<QdrantService.SearchResult> relevantChunks) {

        StringBuilder prompt = new StringBuilder();

        // הוראות למערכת
        prompt.append("אתה עוזר AI שעונה על שאלות על סמך מסמכים.\n\n");

        // הקשר מההיסטוריה
        if (!contextMessages.isEmpty()) {
            prompt.append("=== הקשר מהשיחה ===\n");
            for (Message msg : contextMessages) {
                prompt.append(msg.getRole() == MessageRole.USER ? "משתמש: " : "עוזר: ");
                prompt.append(msg.getContent()).append("\n");
            }
            prompt.append("\n");
        }

        // מידע רלוונטי מהמסמכים
        prompt.append("=== מידע רלוונטי מהמסמכים ===\n");
        for (int i = 0; i < relevantChunks.size(); i++) {
            QdrantService.SearchResult result = relevantChunks.get(i);
            prompt.append(String.format("[מסמך %d - %s]:\n%s\n\n",
                i + 1,
                result.getDocumentName(),
                result.getText()
            ));
        }

        // השאלה
        prompt.append("=== שאלה ===\n");
        prompt.append(question).append("\n\n");

        // הוראות לתשובה
        prompt.append("=== הוראות ===\n");
        prompt.append("1. ענה על השאלה בעברית בצורה ברורה ומדויקת\n");
        prompt.append("2. התבסס רק על המידע שסופק מהמסמכים\n");
        prompt.append("3. אם אין מספיק מידע, אמר זאת בבירור\n");
        prompt.append("4. ציין את מקור המידע (מסמך X) בתשובה\n");

        return prompt.toString();
    }

    /**
     * שמירת תשובת ה-AI
     */
    private Message saveAssistantMessage(
            String answer,
            List<AnswerResponse.Source> sources,
            Double confidence,
            Integer tokensUsed,
            Long responseTime,
            Chat chat,
            Long parentMessageId) {

        Message message = new Message();
        message.setContent(answer);
        message.setRole(MessageRole.ASSISTANT);
        message.setChat(chat);
        message.setParentMessageId(parentMessageId);
        message.setConfidenceScore(confidence);
        message.setTokensUsed(tokensUsed);
        message.setResponseTimeMs(responseTime);
        message.setCreatedAt(LocalDateTime.now());

        // המרת sources ל-String (JSON)
        if (sources != null && !sources.isEmpty()) {
            String sourcesStr = sources.stream()
                .map(AnswerResponse.Source::getDocumentName)
                .collect(Collectors.joining(", "));
            message.setSources(sourcesStr);
        }

        return messageRepository.save(message);
    }

    /**
     * בניית רשימת מקורות
     */
    private List<AnswerResponse.Source> buildSources(
            List<QdrantService.SearchResult> relevantChunks) {

        List<AnswerResponse.Source> sources = new ArrayList<>();

        for (int i = 0; i < relevantChunks.size(); i++) {
            QdrantService.SearchResult result = relevantChunks.get(i);

            AnswerResponse.Source source = AnswerResponse.Source.builder()
                .documentId(result.getDocumentId())
                .documentName(result.getDocumentName())
                .excerpt(truncateText(result.getText(), 200))
                .relevanceScore(result.getScore())
                .isPrimary(i == 0)  // הראשון הוא העיקרי
                .build();

            sources.add(source);
        }

        return sources;
    }

    /**
     * חישוב רמת ביטחון
     */
    private Double calculateConfidence(List<QdrantService.SearchResult> results) {
        if (results.isEmpty()) {
            return 0.0;
        }

        // ממוצע של scores
        double avgScore = results.stream()
            .mapToDouble(QdrantService.SearchResult::getScore)
            .average()
            .orElse(0.0);

        return Math.min(avgScore, 1.0);
    }

    /**
     * הערכת כמות tokens
     */
    private Integer estimateTokens(String prompt, String response) {
        // הערכה גסה: 1 token ≈ 4 characters
        int totalChars = prompt.length() + response.length();
        return totalChars / 4;
    }

    /**
     * קיצור טקסט
     */
    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * תגובה כשאין תוצאות
     */
    private AnswerResponse createNoResultsResponse(Message userMessage) {
        String answer = "מצטער, לא מצאתי מידע רלוונטי במסמכים לשאלה שלך. " +
                       "נסה לנסח את השאלה אחרת או לוודא שהמידע קיים במסמכים.";

        Message assistantMessage = new Message();
        assistantMessage.setContent(answer);
        assistantMessage.setRole(MessageRole.ASSISTANT);
        assistantMessage.setChat(userMessage.getChat());
        assistantMessage.setParentMessageId(userMessage.getId());
        assistantMessage.setConfidenceScore(0.0);
        assistantMessage = messageRepository.save(assistantMessage);

        return AnswerResponse.builder()
                .answer(answer)
                .success(true)
                .confidence(0.0)
                .messageId(assistantMessage.getId())
                .timestamp(LocalDateTime.now())
                .suggestions(List.of(
                    "נסח את השאלה בצורה אחרת",
                    "בדוק אם המידע קיים במסמכים",
                    "העלה מסמכים נוספים"
                ))
                .build();
    }

    /**
     * תגובת שגיאה
     */
    private AnswerResponse createErrorResponse(String errorMessage) {
        return AnswerResponse.builder()
                .success(false)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ==================== Get Messages ====================

    /**
     * קבלת היסטוריית הודעות
     */
    public List<Message> getChatHistory(Long chatId, User user) {
        Chat chat = validateAndGetChat(chatId, user);
        return messageRepository.findByChatOrderByCreatedAtAsc(chat);
    }
}