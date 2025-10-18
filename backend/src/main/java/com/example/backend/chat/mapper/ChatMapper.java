package com.example.backend.chat.mapper;

import com.example.backend.chat.dto.ChatResponse;
import com.example.backend.chat.dto.ChatListResponse;
import com.example.backend.chat.model.Chat;
import com.example.backend.document.model.Document;
import com.example.backend.document.mapper.DocumentMapper;  
import org.mapstruct.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Mapper להמרה בין Chat Entity ל-DTOs
 * 
 * MapStruct יוצר את כל קוד ההמרה אוטומטית!
 * 
 * @Mapper - אומר ל-MapStruct "צור mapper"
 * componentModel = "spring" - הופך את זה ל-Spring Bean (@Component)
 * injectionStrategy = CONSTRUCTOR - Dependency Injection דרך Constructor
 */
@Mapper(
    componentModel = "spring",
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    uses = {DocumentMapper.class}  // משתמש גם ב-DocumentMapper
)
public interface ChatMapper {

    /**
     * המרה מ-Chat Entity ל-ChatResponse DTO
     * 
     * @Mapping - מגדיר איך להמיר שדות ספציפיים
     * 
     * source = "user.username" - קח את username מתוך user
     * target = "userName" - שים אותו ב-userName
     * 
     * expression = "java(...)" - קוד Java מותאם אישית
     */
    @Mapping(source = "user.username", target = "userName")
    @Mapping(source = "user.id", target = "userId")
    @Mapping(target = "documentCount", expression = "java(chat.getDocumentCount())")
    @Mapping(target = "messageCount", expression = "java(chat.getMessageCount())")
    @Mapping(target = "isReady", expression = "java(chat.isReady())")
    @Mapping(target = "documents", ignore = true)  // לא ממיר את הרשימה המלאה
    @Mapping(target = "statistics", ignore = true)  // נמלא אותו בנפרד
    ChatResponse toResponse(Chat chat);

    /**
     * המרה של רשימת Chats ל-List<ChatResponse>
     * 
     * MapStruct יוצר את זה אוטומטית!
     */
    List<ChatResponse> toResponseList(List<Chat> chats);

    /**
     * המרה מ-Chat ל-ChatSummary (גרסה מקוצרת)
     * 
     * ChatSummary = Inner class ב-ChatListResponse
     */
    @Mapping(source = "status", target = "status", qualifiedByName = "statusToString")
    @Mapping(target = "documentCount", expression = "java(chat.getDocumentCount())")
    @Mapping(target = "messageCount", expression = "java(chat.getMessageCount())")
    @Mapping(target = "isReady", expression = "java(chat.isReady())")
    @Mapping(target = "lastActivityAt", expression = "java(formatDateTime(chat.getLastActivityAt()))")
    @Mapping(target = "createdAt", expression = "java(formatDateTime(chat.getCreatedAt()))")
    @Mapping(target = "lastMessagePreview", ignore = true)  // נמלא בService
    @Mapping(target = "progressPercentage", expression = "java(calculateProgress(chat))")
    ChatListResponse.ChatSummary toChatSummary(Chat chat);

    /**
     * המרה של רשימה ל-ChatSummary
     */
    List<ChatListResponse.ChatSummary> toChatSummaryList(List<Chat> chats);

    /**
     * המרה מ-Chat ל-DocumentInfo
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "originalFileName", target = "originalFileName")
    @Mapping(source = "processingStatus", target = "processingStatus", qualifiedByName = "statusToString")
    @Mapping(source = "fileSize", target = "fileSize")
    @Mapping(source = "processingProgress", target = "processingProgress")
    @Mapping(source = "createdAt", target = "uploadedAt")
    ChatResponse.DocumentInfo toDocumentInfo(Document document);

    /**
     * המרה של רשימת Documents ל-DocumentInfo
     */
    List<ChatResponse.DocumentInfo> toDocumentInfoList(List<Document> documents);

    // ==================== Helper Methods ====================

    /**
     * המרת Enum ל-String
     * 
     * @Named - נותן שם לפונקציה כדי לקרוא לה ב-@Mapping
     */
    @Named("statusToString")
    default String statusToString(Enum<?> status) {
        return status != null ? status.name() : null;
    }

    /**
     * פורמט תאריך לString
     */
    default String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        return dateTime.format(formatter);
    }

    /**
     * חישוב אחוז התקדמות
     */
    default Integer calculateProgress(Chat chat) {
        if (chat.getDocumentCount() == 0) {
            return 0;
        }

        Integer pending = chat.getPendingDocuments() != null ? chat.getPendingDocuments() : 0;
        int completed = chat.getDocumentCount() - pending;
        
        return (completed * 100) / chat.getDocumentCount();
    }

    /**
     * בניית ChatResponse מלא עם סטטיסטיקות
     * 
     * @AfterMapping - רץ אחרי ההמרה הבסיסית
     */
    @AfterMapping
    default void enrichChatResponse(@MappingTarget ChatResponse response, Chat chat) {
        // כאן אפשר להוסיף לוגיקה נוספת אחרי ההמרה
        // למשל: טעינת סטטיסטיקות מתקדמות
    }

    // ==================== Update Methods ====================

    /**
     * עדכון Chat Entity מ-DTO
     * 
     * @MappingTarget - עדכן את האובייקט הקיים (לא צור חדש)
     * @BeanMapping(nullValuePropertyMappingStrategy = IGNORE) - אל תעדכן null
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateChatFromResponse(ChatResponse response, @MappingTarget Chat chat);
}