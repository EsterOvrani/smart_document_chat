package com.example.backend.shared.service;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.*;
import io.qdrant.client.grpc.Points.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Service לניהול Qdrant Vector Database
 * 
 * תפקידים:
 * - יצירת collections (מאגרי וקטורים)
 * - הוספת vectors (embeddings)
 * - חיפוש vectors דומים
 * - מחיקת vectors
 */
@Service
@Slf4j
public class QdrantService {

    // ==================== Configuration ====================
    
    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6334}")  // gRPC port
    private int qdrantPort;

    @Value("${qdrant.api-key:}")
    private String apiKey;

    private QdrantClient client;

    // ==================== Initialization ====================

    /**
     * אתחול חיבור ל-Qdrant
     */
    @PostConstruct
    public void init() {
        try {
            log.info("Connecting to Qdrant at {}:{}", qdrantHost, qdrantPort);

            // יצירת client
            QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
                qdrantHost, 
                qdrantPort, 
                false  // useTls
            );

            // אם יש API key
            if (apiKey != null && !apiKey.isEmpty()) {
                builder.withApiKey(apiKey);
            }

            client = new QdrantClient(builder.build());

            log.info("Successfully connected to Qdrant");

        } catch (Exception e) {
            log.error("Failed to connect to Qdrant", e);
            throw new RuntimeException("Failed to initialize Qdrant client", e);
        }
    }

    // ==================== Collection Management ====================

    /**
     * יצירת collection חדש
     * 
     * @param collectionName - שם ייחודי (למשל: "chat_123_user_5")
     */
    public void createCollection(String collectionName) {
        try {
            log.info("Creating Qdrant collection: {}", collectionName);

            // בדיקה אם כבר קיים
            if (collectionExists(collectionName)) {
                log.info("Collection already exists: {}", collectionName);
                return;
            }

            // הגדרות ה-collection
            VectorParams vectorParams = VectorParams.newBuilder()
                .setSize(3072)  // OpenAI text-embedding-3-large dimension
                .setDistance(Distance.Cosine)  // Cosine similarity
                .build();

            // יצירה
            client.createCollectionAsync(
                collectionName,
                vectorParams
            ).get();

            log.info("Collection created successfully: {}", collectionName);

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to create collection: {}", collectionName, e);
            throw new RuntimeException("Failed to create Qdrant collection", e);
        }
    }

    /**
     * בדיקה אם collection קיים
     */
    public boolean collectionExists(String collectionName) {
        try {
            client.getCollectionInfoAsync(collectionName).get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * מחיקת collection
     */
    public void deleteCollection(String collectionName) {
        try {
            log.info("Deleting Qdrant collection: {}", collectionName);

            if (!collectionExists(collectionName)) {
                log.warn("Collection does not exist: {}", collectionName);
                return;
            }

            client.deleteCollectionAsync(collectionName).get();
            log.info("Collection deleted successfully: {}", collectionName);

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to delete collection: {}", collectionName, e);
            throw new RuntimeException("Failed to delete Qdrant collection", e);
        }
    }

    // ==================== Vector Operations ====================

    /**
     * הוספת vector (embedding + metadata)
     * 
     * @param collectionName - שם ה-collection
     * @param id - מזהה ייחודי (למשל: "doc_1_chunk_0")
     * @param vector - embedding (3072 מספרים)
     * @param text - הטקסט המקורי
     * @param documentId - מזהה המסמך
     * @param documentName - שם המסמך
     */
    public void upsertVector(
            String collectionName,
            String id,
            float[] vector,
            String text,
            Long documentId,
            String documentName) {

        try {
            log.debug("Upserting vector: {} to collection: {}", id, collectionName);

            // המרה ל-List<Float>
            List<Float> vectorList = new ArrayList<>();
            for (float v : vector) {
                vectorList.add(v);
            }

            // בניית metadata
            Map<String, com.google.protobuf.Value> payload = new HashMap<>();
            payload.put("text", 
                com.google.protobuf.Value.newBuilder()
                    .setStringValue(text)
                    .build());
            payload.put("document_id", 
                com.google.protobuf.Value.newBuilder()
                    .setNumberValue(documentId)
                    .build());
            payload.put("document_name", 
                com.google.protobuf.Value.newBuilder()
                    .setStringValue(documentName)
                    .build());

            // בניית Point
            PointStruct point = PointStruct.newBuilder()
                .setId(PointId.newBuilder().setUuid(id).build())
                .setVectors(Vectors.newBuilder().setVector(
                    Vector.newBuilder().addAllData(vectorList).build()
                ).build())
                .putAllPayload(payload)
                .build();

            // העלאה ל-Qdrant
            client.upsertAsync(
                collectionName,
                Collections.singletonList(point)
            ).get();

            log.debug("Vector upserted successfully: {}", id);

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to upsert vector: {}", id, e);
            throw new RuntimeException("Failed to upsert vector to Qdrant", e);
        }
    }

    /**
     * חיפוש vectors דומים
     * 
     * @param collectionName - שם ה-collection
     * @param queryVector - embedding של השאלה
     * @param limit - כמה תוצאות להחזיר
     * @return רשימת תוצאות ממוינות לפי relevance
     */
    public List<SearchResult> search(
            String collectionName,
            float[] queryVector,
            int limit) {

        try {
            log.info("Searching in collection: {} with limit: {}", collectionName, limit);

            // המרה ל-List<Float>
            List<Float> vectorList = new ArrayList<>();
            for (float v : queryVector) {
                vectorList.add(v);
            }

            // ביצוע חיפוש
            List<ScoredPoint> results = client.searchAsync(
                SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(vectorList)
                    .setLimit(limit)
                    .setWithPayload(WithPayloadSelector.newBuilder()
                        .setEnable(true)
                        .build())
                    .build()
            ).get();

            // המרה לSearchResult
            List<SearchResult> searchResults = new ArrayList<>();
            for (ScoredPoint point : results) {
                SearchResult result = new SearchResult();
                result.setId(point.getId().getUuid());
                result.setScore(point.getScore());

                // חילוץ metadata
                Map<String, com.google.protobuf.Value> payload = point.getPayloadMap();
                
                if (payload.containsKey("text")) {
                    result.setText(payload.get("text").getStringValue());
                }
                if (payload.containsKey("document_id")) {
                    result.setDocumentId((long) payload.get("document_id").getNumberValue());
                }
                if (payload.containsKey("document_name")) {
                    result.setDocumentName(payload.get("document_name").getStringValue());
                }

                searchResults.add(result);
            }

            log.info("Found {} results", searchResults.size());
            return searchResults;

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to search in collection: {}", collectionName, e);
            throw new RuntimeException("Failed to search in Qdrant", e);
        }
    }

    /**
     * מחיקת vectors של מסמך ספציפי
     */
    public void deleteVectorsByDocument(String collectionName, Long documentId) {
        try {
            log.info("Deleting vectors for document: {} from collection: {}", 
                documentId, collectionName);

            // יצירת filter
            Filter filter = Filter.newBuilder()
                .addMust(Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                        .setKey("document_id")
                        .setMatch(Match.newBuilder()
                            .setInteger(documentId)
                            .build())
                        .build())
                    .build())
                .build();

            // מחיקה
            client.deleteAsync(collectionName, filter).get();

            log.info("Vectors deleted successfully for document: {}", documentId);

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to delete vectors for document: {}", documentId, e);
            throw new RuntimeException("Failed to delete vectors from Qdrant", e);
        }
    }

    /**
     * קבלת מידע על collection
     */
    public CollectionInfo getCollectionInfo(String collectionName) {
        try {
            CollectionDescription description = client
                .getCollectionInfoAsync(collectionName)
                .get();

            CollectionInfo info = new CollectionInfo();
            info.setName(collectionName);
            info.setVectorsCount(description.getVectorsCount());
            info.setPointsCount(description.getPointsCount());
            info.setStatus(description.getStatus().name());

            return info;

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get collection info: {}", collectionName, e);
            throw new RuntimeException("Failed to get collection info", e);
        }
    }

    // ==================== Inner Classes ====================

    /**
     * תוצאת חיפוש
     */
    @Data
    public static class SearchResult {
        private String id;
        private Double score;  // 0.0 - 1.0
        private String text;
        private Long documentId;
        private String documentName;
    }

    /**
     * מידע על collection
     */
    @Data
    public static class CollectionInfo {
        private String name;
        private Long vectorsCount;
        private Long pointsCount;
        private String status;
    }
}