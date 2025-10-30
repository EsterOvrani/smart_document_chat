package com.example.backend.chat.service;

import com.example.backend.chat.dto.AskQuestionRequest;
import com.example.backend.chat.dto.AnswerResponse;
import com.example.backend.chat.mapper.MessageMapper;
import com.example.backend.chat.model.Chat;
import com.example.backend.chat.model.Message;
import com.example.backend.chat.model.Message.MessageRole;
import com.example.backend.chat.repository.ChatRepository;
import com.example.backend.chat.repository.MessageRepository;
import com.example.backend.common.infrastructure.vectordb.QdrantVectorService;
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
 * Service for handling AI questions and answers
 * Uses LangChain4j for AI operations
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
     * Ask a question - main entry point
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
     * Validate chat and permissions
     */
    private Chat validateAndGetChat(Long chatId, User user) {
        return chatRepository.findByIdAndUserAndActiveTrue(chatId, user)
            .orElseThrow(() -> new RuntimeException("שיחה לא נמצאה או אין הרשאה"));
    }

    /**
     * Validate request
     */
    private void validateRequest(AskQuestionRequest request) {
        request.validateContextCount();

        if (!request.isValid()) {
            throw new IllegalArgumentException("השאלה לא תקינה");
        }
    }

    /**
     * Save user question
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
     * Get context - recent messages
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
     * Search relevant documents in Qdrant via LangChain4j
     */
    private List<RelevantDocument> searchRelevantDocuments(Chat chat, String question) {
        try {
            // Get the EmbeddingStore for the chat
            EmbeddingStore<TextSegment> embeddingStore = 
                qdrantVectorService.getEmbeddingStoreForCollection(chat.getVectorCollectionName());

            if (embeddingStore == null) {
                log.error("No embedding store found for collection: {}", chat.getVectorCollectionName());
                return new ArrayList<>();
            }

            // Create embedding for the question
            Embedding queryEmbedding = embeddingModel.embed(question).content();

            // Search for relevant documents
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(MAX_RELEVANT_CHUNKS)
                .minScore(0.5) // Only results with similarity above 0.5
                .build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            log.info("Found {} relevant chunks", matches.size());

            // Convert to internal data structure
            List<RelevantDocument> relevantDocs = new ArrayList<>();
            for (EmbeddingMatch<TextSegment> match : matches) {
                RelevantDocument doc = new RelevantDocument();
                doc.setText(match.embedded().text());
                doc.setScore(match.score());
                
                // Try to extract metadata
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
     * Build chat messages for LangChain4j - bilingual support
     */
    private List<dev.langchain4j.data.message.ChatMessage> buildChatMessages(
            String question,
            List<Message> contextMessages,
            List<RelevantDocument> relevantDocs) {

        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

        // Bilingual system message
        messages.add(SystemMessage.from(
            "אתה עוזר AI שעונה על שאלות על סמך מסמכים. " +
            "חשוב: ענה באותה שפה שבה נשאלת השאלה! " +
            "אם השאלה בעברית - ענה בעברית. אם השאלה באנגלית - ענה באנגלית. " +
            "ענה בצורה ברורה ומדויקת. התבסס רק על המידע שסופק מהמסמכים. " +
            "אם אין מספיק מידע, אמר זאת בבירור.\n\n" +
            
            "You are a helpful AI assistant that answers questions based on documents. " +
            "IMPORTANT: Answer in the SAME LANGUAGE as the question! " +
            "If the question is in Hebrew - answer in Hebrew. If in English - answer in English. " +
            "Answer clearly and accurately. Base your answer only on the provided document information. " +
            "If there isn't enough information, say so clearly."
        ));

        // Context from history
        for (Message msg : contextMessages) {
            if (msg.getRole() == MessageRole.USER) {
                messages.add(UserMessage.from(msg.getContent()));
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                messages.add(AiMessage.from(msg.getContent()));
            }
        }

        // Relevant information from documents (bilingual)
        StringBuilder context = new StringBuilder();
        context.append("מידע רלוונטי מהמסמכים / Relevant information from documents:\n\n");
        
        for (int i = 0; i < relevantDocs.size(); i++) {
            RelevantDocument doc = relevantDocs.get(i);
            context.append(String.format("[מסמך / Document %d - %s]:\n%s\n\n",
                i + 1,
                doc.getDocumentName(),
                doc.getText()
            ));
        }

        // Question with context
        messages.add(UserMessage.from(context.toString() + "\nשאלה / Question: " + question));

        return messages;
    }
    
    /**
     * Save AI response
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

        // Convert sources to String
        if (sources != null && !sources.isEmpty()) {
            String sourcesStr = sources.stream()
                .map(AnswerResponse.Source::getDocumentName)
                .collect(Collectors.joining(", "));
            message.setSources(sourcesStr);
        }

        return messageRepository.save(message);
    }

    /**
     * Build sources list
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
     * Calculate confidence level
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
     * Truncate text
     */
    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * Response when no results found
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
     * Error response
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
     * Get message history
     */
    @Transactional(readOnly = true)
    public List<Message> getChatHistory(Long chatId, User user) {
        log.info("🔵 getChatHistory called for chatId: {}, user: {}", chatId, user.getEmail());
        
        try {
            Chat chat = validateAndGetChat(chatId, user);
            log.info("✅ Chat found: {}, Title: {}", chat.getId(), chat.getTitle());
            
            List<Message> messages = messageRepository.findByChatOrderByCreatedAtAsc(chat);
            log.info("✅ Found {} messages for chat {}", messages.size(), chatId);
            
            if (messages.isEmpty()) {
                log.warn("⚠️ No messages found for chat {}", chatId);
            }
            
            // Force eager loading of lazy relationships
            messages.forEach(msg -> {
                log.debug("Message {}: role={}, content length={}", 
                    msg.getId(), 
                    msg.getRole(), 
                    msg.getContent() != null ? msg.getContent().length() : 0);
            });
            
            return messages;
            
        } catch (Exception e) {
            log.error("❌ Error getting chat history for chatId: {}", chatId, e);
            throw new RuntimeException("Failed to get chat history: " + e.getMessage(), e);
        }
    }

    // ==================== Inner Classes ====================
    
    /**
     * Relevant document (search result)
     */
    @lombok.Data
    private static class RelevantDocument {
        private String text;
        private Double score;
        private Long documentId;
        private String documentName;
    }
}