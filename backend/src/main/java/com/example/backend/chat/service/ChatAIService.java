package com.example.backend.chat.service;

import com.example.backend.chat.dto.AskQuestionRequest;
import com.example.backend.chat.dto.AnswerResponse;
import com.example.backend.chat.mapper.MessageMapper;
import com.example.backend.chat.model.Chat;
import com.example.backend.chat.model.Message;
import com.example.backend.chat.model.Message.MessageRole;
import com.example.backend.chat.repository.ChatRepository;
import com.example.backend.chat.repository.MessageRepository;
import com.example.backend.share.service.QdrantVectorService;
import com.example.backend.user.model.User;

// LangChain4j imports
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

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
 * משתמש ב-LangChain4j במקום QdrantService ו-OpenAIService
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
    private final QdrantVectorService qdrantVectorService;
    private final EmbeddingModel embeddingModel; // From QdrantConfig
    private final OpenAiChatModel chatModel; // From QdrantConfig

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
            
            List<RelevantDocument> relevantDocs = searchRelevantDocuments(chat, request.getQuestion());

            if (relevantDocs.isEmpty()) {
                return createNoResultsResponse(userMessage);
            }

            // ==================== 5. Build Messages for LangChain4j ====================
            
            List<dev.langchain4j.data.message.ChatMessage> messages = buildChatMessages(
                request.getQuestion(),
                contextMessages,
                relevantDocs
            );

            // ==================== 6. Call OpenAI via LangChain4j ====================
            
            Response<AiMessage> response = chatModel.generate(messages);
            String answer = response.content().text();
            
            // ==================== 7. Calculate Metrics ====================
            
            long responseTime = System.currentTimeMillis() - startTime;
            Double confidence = calculateConfidence(relevantDocs);
            
            // Calculate tokens using OpenAiTokenizer
            OpenAiTokenizer tokenizer = new OpenAiTokenizer("gpt-4");
            int tokensUsed = tokenizer.estimateTokenCountInMessage(response.content());

            // ==================== 8. Build Sources ====================
            
            List<AnswerResponse.Source> sources = buildSources(relevantDocs);

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
            
            AnswerResponse answerResponse = AnswerResponse.builder()
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
            return answerResponse;

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

        List<Message> messages = messageRepository
            .findByChatOrderByCreatedAtDesc(chat, PageRequest.of(0, count));

        java.util.Collections.reverse(messages);

        log.info("Retrieved {} context messages", messages.size());
        return messages;
    }

    /**
     * חיפוש מסמכים רלוונטיים ב-Qdrant דרך LangChain4j
     */
    private List<RelevantDocument> searchRelevantDocuments(Chat chat, String question) {
        try {
            // קבלת ה-EmbeddingStore של השיחה
            EmbeddingStore<TextSegment> embeddingStore = 
                qdrantVectorService.getEmbeddingStoreForCollection(chat.getVectorCollectionName());

            if (embeddingStore == null) {
                log.error("No embedding store found for collection: {}", chat.getVectorCollectionName());
                return new ArrayList<>();
            }

            // יצירת embedding לשאלה
            Embedding queryEmbedding = embeddingModel.embed(question).content();

            // חיפוש מסמכים רלוונטיים
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(MAX_RELEVANT_CHUNKS)
                .minScore(0.7) // רק תוצאות עם דמיון מעל 0.7
                .build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            log.info("Found {} relevant chunks", matches.size());

            // המרה למבנה נתונים פנימי
            List<RelevantDocument> relevantDocs = new ArrayList<>();
            for (EmbeddingMatch<TextSegment> match : matches) {
                RelevantDocument doc = new RelevantDocument();
                doc.setText(match.embedded().text());
                doc.setScore(match.score());
                
                // נסה לחלץ metadata
                if (match.embedded().metadata() != null) {
                    String docIdStr = match.embedded().metadata().getString("document_id");
                    if (docIdStr != null) {
                        doc.setDocumentId(Long.parseLong(docIdStr));
                    } else {
                        doc.setDocumentId(0L);
                    }
                    
                    String docName = match.embedded().metadata().getString("document_name");
                    doc.setDocumentName(docName != null ? docName : "Unknown");
                }
                
                relevantDocs.add(doc);
            }

            return relevantDocs;

        } catch (Exception e) {
            log.error("Failed to search relevant documents", e);
            return new ArrayList<>();
        }
    }

    /**
     * בניית הודעות לצ'אט עבור LangChain4j
     */
    private List<dev.langchain4j.data.message.ChatMessage> buildChatMessages(
            String question,
            List<Message> contextMessages,
            List<RelevantDocument> relevantDocs) {

        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

        // הודעת מערכת
        messages.add(SystemMessage.from(
            "אתה עוזר AI שעונה על שאלות על סמך מסמכים. " +
            "ענה בעברית בצורה ברורה ומדויקת. " +
            "התבסס רק על המידע שסופק מהמסמכים. " +
            "אם אין מספיק מידע, אמר זאת בבירור."
        ));

        // הקשר מההיסטוריה
        for (Message msg : contextMessages) {
            if (msg.getRole() == MessageRole.USER) {
                messages.add(UserMessage.from(msg.getContent()));
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                messages.add(AiMessage.from(msg.getContent()));
            }
        }

        // מידע רלוונטי מהמסמכים
        StringBuilder context = new StringBuilder();
        context.append("מידע רלוונטי מהמסמכים:\n\n");
        
        for (int i = 0; i < relevantDocs.size(); i++) {
            RelevantDocument doc = relevantDocs.get(i);
            context.append(String.format("[מסמך %d - %s]:\n%s\n\n",
                i + 1,
                doc.getDocumentName(),
                doc.getText()
            ));
        }

        // השאלה עם ההקשר
        messages.add(UserMessage.from(context.toString() + "\nשאלה: " + question));

        return messages;
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

        // המרת sources ל-String
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
    private List<AnswerResponse.Source> buildSources(List<RelevantDocument> relevantDocs) {
        List<AnswerResponse.Source> sources = new ArrayList<>();

        for (int i = 0; i < relevantDocs.size(); i++) {
            RelevantDocument doc = relevantDocs.get(i);

            AnswerResponse.Source source = AnswerResponse.Source.builder()
                .documentId(doc.getDocumentId())
                .documentName(doc.getDocumentName())
                .excerpt(truncateText(doc.getText(), 200))
                .relevanceScore(doc.getScore())
                .isPrimary(i == 0)
                .build();

            sources.add(source);
        }

        return sources;
    }

    /**
     * חישוב רמת ביטחון
     */
    private Double calculateConfidence(List<RelevantDocument> results) {
        if (results.isEmpty()) {
            return 0.0;
        }

        double avgScore = results.stream()
            .mapToDouble(RelevantDocument::getScore)
            .average()
            .orElse(0.0);

        return Math.min(avgScore, 1.0);
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

    // ==================== Inner Classes ====================
    
    /**
     * מסמך רלוונטי (תוצאת חיפוש)
     */
    @lombok.Data
    private static class RelevantDocument {
        private String text;
        private Double score;
        private Long documentId;
        private String documentName;
    }
}