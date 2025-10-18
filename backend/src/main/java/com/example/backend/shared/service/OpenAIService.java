package com.example.backend.shared.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service לתקשורת עם OpenAI API
 * 
 * תפקידים:
 * - יצירת embeddings (text → vector)
 * - שליחת שאלות לצ'אט (GPT)
 * - ניהול טוקנים ועלויות
 */
@Service
@Slf4j
public class OpenAIService {

    // ==================== Configuration ====================
    
    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.embedding.model:text-embedding-3-large}")
    private String embeddingModel;

    @Value("${openai.chat.model:gpt-4}")
    private String chatModel;

    private static final String OPENAI_API_URL = "https://api.openai.com/v1";
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenAIService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    // ==================== Embeddings ====================

    /**
     * יצירת embedding לטקסט
     * 
     * @param text - הטקסט להמרה
     * @return vector של 3072 מספרים
     */
    public float[] createEmbedding(String text) {
        try {
            log.debug("Creating embedding for text of length: {}", text.length());

            // בניית הבקשה
            Map<String, Object> request = new HashMap<>();
            request.put("input", text);
            request.put("model", embeddingModel);

            // שליחת הבקשה
            String response = sendRequest(
                OPENAI_API_URL + "/embeddings",
                request
            );

            // פירסור התגובה
            JsonNode root = objectMapper.readTree(response);
            JsonNode embeddingNode = root
                .path("data")
                .get(0)
                .path("embedding");

            // המרה ל-float[]
            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }

            log.debug("Embedding created successfully, dimension: {}", embedding.length);
            return embedding;

        } catch (Exception e) {
            log.error("Failed to create embedding", e);
            throw new RuntimeException("Failed to create OpenAI embedding", e);
        }
    }

    /**
     * יצירת embeddings לרשימת טקסטים (batch)
     * 
     * יותר יעיל מאשר לקרוא בנפרד לכל אחד
     */
    public List<float[]> createEmbeddings(List<String> texts) {
        try {
            log.info("Creating embeddings for {} texts", texts.size());

            // בניית הבקשה
            Map<String, Object> request = new HashMap<>();
            request.put("input", texts);
            request.put("model", embeddingModel);

            // שליחת הבקשה
            String response = sendRequest(
                OPENAI_API_URL + "/embeddings",
                request
            );

            // פירסור התגובה
            JsonNode root = objectMapper.readTree(response);
            JsonNode dataArray = root.path("data");

            java.util.List<float[]> embeddings = new java.util.ArrayList<>();
            
            for (JsonNode item : dataArray) {
                JsonNode embeddingNode = item.path("embedding");
                float[] embedding = new float[embeddingNode.size()];
                
                for (int i = 0; i < embeddingNode.size(); i++) {
                    embedding[i] = (float) embeddingNode.get(i).asDouble();
                }
                
                embeddings.add(embedding);
            }

            log.info("Created {} embeddings successfully", embeddings.size());
            return embeddings;

        } catch (Exception e) {
            log.error("Failed to create embeddings", e);
            throw new RuntimeException("Failed to create OpenAI embeddings", e);
        }
    }

    // ==================== Chat Completion ====================

    /**
     * שליחת הודעה לצ'אט GPT
     * 
     * @param prompt - ההודעה/שאלה
     * @return התשובה מ-GPT
     */
    public String chat(String prompt) {
        try {
            log.info("Sending chat request, prompt length: {}", prompt.length());

            // בניית הבקשה
            Map<String, Object> request = new HashMap<>();
            request.put("model", chatModel);
            
            // הודעות
            List<Map<String, String>> messages = List.of(
                Map.of(
                    "role", "system",
                    "content", "אתה עוזר AI מועיל שעונה בעברית."
                ),
                Map.of(
                    "role", "user",
                    "content", prompt
                )
            );
            request.put("messages", messages);

            // הגדרות נוספות
            request.put("temperature", 0.7);  // יצירתיות (0-2)
            request.put("max_tokens", 2000);  // מקסימום אורך תשובה

            // שליחת הבקשה
            String response = sendRequest(
                OPENAI_API_URL + "/chat/completions",
                request
            );

            // פירסור התגובה
            JsonNode root = objectMapper.readTree(response);
            String answer = root
                .path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();

            // לוגים של שימוש
            JsonNode usage = root.path("usage");
            int totalTokens = usage.path("total_tokens").asInt();
            log.info("Chat completed, tokens used: {}", totalTokens);

            return answer;

        } catch (Exception e) {
            log.error("Failed to complete chat", e);
            throw new RuntimeException("Failed to get OpenAI chat response", e);
        }
    }

    /**
     * שליחת הודעה עם היסטוריה
     * 
     * @param messages - רשימת הודעות (system, user, assistant)
     * @return התשובה מ-GPT
     */
    public String chatWithHistory(List<ChatMessage> messages) {
        try {
            log.info("Sending chat request with {} messages", messages.size());

            // המרה לפורמט של OpenAI
            List<Map<String, String>> apiMessages = new java.util.ArrayList<>();
            for (ChatMessage msg : messages) {
                apiMessages.add(Map.of(
                    "role", msg.getRole(),
                    "content", msg.getContent()
                ));
            }

            // בניית הבקשה
            Map<String, Object> request = new HashMap<>();
            request.put("model", chatModel);
            request.put("messages", apiMessages);
            request.put("temperature", 0.7);
            request.put("max_tokens", 2000);

            // שליחת הבקשה
            String response = sendRequest(
                OPENAI_API_URL + "/chat/completions",
                request
            );

            // פירסור התגובה
            JsonNode root = objectMapper.readTree(response);
            return root
                .path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();

        } catch (Exception e) {
            log.error("Failed to complete chat with history", e);
            throw new RuntimeException("Failed to get OpenAI chat response", e);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * שליחת HTTP request ל-OpenAI
     */
    private String sendRequest(String url, Map<String, Object> requestBody) {
        try {
            // הכנת headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // המרה ל-JSON
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // יצירת entity
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            // שליחת הבקשה
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
            );

            // בדיקת status
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException(
                    "OpenAI API returned error: " + response.getStatusCode()
                );
            }

            return response.getBody();

        } catch (Exception e) {
            log.error("Failed to send request to OpenAI", e);
            throw new RuntimeException("Failed to communicate with OpenAI", e);
        }
    }

    /**
     * חישוב משוערת עלות
     * 
     * GPT-4: ~$0.03 per 1K tokens (input) + $0.06 per 1K tokens (output)
     * Embeddings: ~$0.13 per 1M tokens
     */
    public double estimateChatCost(int inputTokens, int outputTokens) {
        double inputCost = (inputTokens / 1000.0) * 0.03;
        double outputCost = (outputTokens / 1000.0) * 0.06;
        return inputCost + outputCost;
    }

    public double estimateEmbeddingCost(int tokens) {
        return (tokens / 1000000.0) * 0.13;
    }

    // ==================== Inner Classes ====================

    /**
     * הודעת צ'אט
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ChatMessage {
        private String role;     // "system", "user", "assistant"
        private String content;  // התוכן
    }
}