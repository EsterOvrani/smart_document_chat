package com.example.backend.chat.mapper;

import com.example.backend.chat.dto.AnswerResponse;
import com.example.backend.chat.model.Message;
import org.mapstruct.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mapper להמרה בין Message Entity ל-AnswerResponse DTO
 */
@Mapper(
    componentModel = "spring",
    injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
public interface MessageMapper {

    /**
     * המרה מ-Message Entity ל-AnswerResponse DTO
     * 
     * רק הודעות של ASSISTANT (תשובות AI)
     */
    @Mapping(source = "content", target = "answer")
    @Mapping(source = "id", target = "messageId")
    @Mapping(source = "confidenceScore", target = "confidence")
    @Mapping(source = "tokensUsed", target = "tokensUsed")
    @Mapping(source = "responseTimeMs", target = "responseTimeMs")
    @Mapping(source = "createdAt", target = "timestamp")
    @Mapping(target = "success", constant = "true")
    @Mapping(target = "sources", expression = "java(parseSources(message))")
    @Mapping(target = "errorMessage", ignore = true)
    @Mapping(target = "suggestions", ignore = true)
    AnswerResponse toAnswerResponse(Message message);

    /**
     * המרה של רשימת Messages
     */
    List<AnswerResponse> toAnswerResponseList(List<Message> messages);

    // ==================== Helper Methods ====================

    /**
     * פירסור Sources מString ל-List<Source>
     * 
     * Message שומר את Sources כJSON string
     * צריך להמיר חזרה לאובייקטים
     */
    default List<AnswerResponse.Source> parseSources(Message message) {
        String sourcesStr = message.getSources();
        
        if (sourcesStr == null || sourcesStr.isEmpty()) {
            return new ArrayList<>();
        }

        // TODO: פירסור JSON מתקדם
        // כרגע פשוט מפצל לפי פסיק
        List<AnswerResponse.Source> sources = new ArrayList<>();
        
        String[] parts = sourcesStr.split(",");
        for (String part : parts) {
            if (part != null && !part.trim().isEmpty()) {
                AnswerResponse.Source source = AnswerResponse.Source.builder()
                        .documentName(part.trim())
                        .relevanceScore(0.9)  // ברירת מחדל
                        .build();
                sources.add(source);
            }
        }
        
        return sources;
    }

    /**
     * המרת List<Source> ל-JSON string
     * (לשמירה ב-Message Entity)
     */
    default String sourcesToString(List<AnswerResponse.Source> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }

        // TODO: המרה ל-JSON מתקדמת
        // כרגע פשוט מצרף בפסיק
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(sources.get(i).getDocumentName());
        }
        
        return sb.toString();
    }

    /**
     * יצירת Message Entity מ-AskQuestionRequest
     */
    default Message createUserMessage(String question, Long chatId, Long userId) {
        Message message = new Message();
        message.setContent(question);
        message.setRole(Message.MessageRole.USER);
        message.setCreatedAt(LocalDateTime.now());
        // Chat ו-User יוגדרו ב-Service
        return message;
    }

    /**
     * יצירת Message Entity מתשובת AI
     */
    default Message createAssistantMessage(
            String answer,
            List<AnswerResponse.Source> sources,
            Double confidence,
            Integer tokens,
            Long responseTime
    ) {
        Message message = new Message();
        message.setContent(answer);
        message.setRole(Message.MessageRole.ASSISTANT);
        message.setSources(sourcesToString(sources));
        message.setConfidenceScore(confidence);
        message.setTokensUsed(tokens);
        message.setResponseTimeMs(responseTime);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    /**
     * המרה מ-AnswerResponse ל-Message (לשמירה)
     */
    @Mapping(source = "answer", target = "content")
    @Mapping(source = "confidence", target = "confidenceScore")
    @Mapping(source = "tokensUsed", target = "tokensUsed")
    @Mapping(source = "responseTimeMs", target = "responseTimeMs")
    @Mapping(source = "timestamp", target = "createdAt")
    @Mapping(target = "role", constant = "ASSISTANT")
    @Mapping(target = "sources", expression = "java(sourcesToString(response.getSources()))")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "chat", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "parentMessageId", ignore = true)
    Message toMessage(AnswerResponse response);

    /**
     * עדכון Message מ-AnswerResponse
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateMessageFromResponse(AnswerResponse response, @MappingTarget Message message);

    /**
     * העשרת AnswerResponse אחרי המרה
     */
    @AfterMapping
    default void enrichAnswerResponse(@MappingTarget AnswerResponse response, Message message) {
        // וודא שיש success
        if (response.getSuccess() == null) {
            response.setSuccess(true);
        }

        // אם אין timestamp, שים את created_at
        if (response.getTimestamp() == null) {
            response.setTimestamp(message.getCreatedAt());
        }
    }
}