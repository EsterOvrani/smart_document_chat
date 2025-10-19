package com.example.backend.shared.service;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.*;
import io.qdrant.client.grpc.Points.*;
import io.qdrant.client.grpc.JsonWithInt;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
@Slf4j
public class QdrantService {

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6334}")
    private int qdrantPort;

    @Value("${qdrant.api-key:}")
    private String apiKey;

    private QdrantClient client;

    @PostConstruct
    public void init() {
        try {
            log.info("Connecting to Qdrant at {}:{}", qdrantHost, qdrantPort);

            QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
                qdrantHost, 
                qdrantPort, 
                false
            );

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

    public void createCollection(String collectionName) {
        try {
            log.info("Creating Qdrant collection: {}", collectionName);

            if (collectionExists(collectionName)) {
                log.info("Collection already exists: {}", collectionName);
                return;
            }

            VectorParams vectorParams = VectorParams.newBuilder()
                .setSize(3072)
                .setDistance(Distance.Cosine)
                .build();

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

    public boolean collectionExists(String collectionName) {
        try {
            client.getCollectionInfoAsync(collectionName).get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

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

    public void upsertVector(
            String collectionName,
            String id,
            float[] vector,
            String text,
            Long documentId,
            String documentName) {

        try {
            log.debug("Upserting vector: {} to collection: {}", id, collectionName);

            List<Float> vectorList = new ArrayList<>();
            for (float v : vector) {
                vectorList.add(v);
            }

            Map<String, JsonWithInt.Value> payload = new HashMap<>();
            payload.put("text", 
                JsonWithInt.Value.newBuilder()
                    .setStringValue(text)
                    .build());
            payload.put("document_id", 
                JsonWithInt.Value.newBuilder()
                    .setIntegerValue(documentId)
                    .build());
            payload.put("document_name", 
                JsonWithInt.Value.newBuilder()
                    .setStringValue(documentName)
                    .build());

            PointStruct point = PointStruct.newBuilder()
                .setId(PointId.newBuilder().setUuid(id).build())
                .setVectors(Vectors.newBuilder().setVector(
                    io.qdrant.client.grpc.Points.Vector.newBuilder().addAllData(vectorList).build()
                ).build())
                .putAllPayload(payload)
                .build();

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

    public List<SearchResult> search(
            String collectionName,
            float[] queryVector,
            int limit) {

        try {
            log.info("Searching in collection: {} with limit: {}", collectionName, limit);

            List<Float> vectorList = new ArrayList<>();
            for (float v : queryVector) {
                vectorList.add(v);
            }

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

            List<SearchResult> searchResults = new ArrayList<>();
            for (ScoredPoint point : results) {
                SearchResult result = new SearchResult();
                result.setId(point.getId().getUuid());
                result.setScore((double) point.getScore());

                Map<String, JsonWithInt.Value> payload = point.getPayloadMap();
                
                if (payload.containsKey("text")) {
                    result.setText(payload.get("text").getStringValue());
                }
                if (payload.containsKey("document_id")) {
                    result.setDocumentId(payload.get("document_id").getIntegerValue());
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

    public void deleteVectorsByDocument(String collectionName, Long documentId) {
        try {
            log.info("Deleting vectors for document: {} from collection: {}", 
                documentId, collectionName);

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

            client.deleteAsync(collectionName, filter).get();

            log.info("Vectors deleted successfully for document: {}", documentId);

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to delete vectors for document: {}", documentId, e);
            throw new RuntimeException("Failed to delete vectors from Qdrant", e);
        }
    }

    public CollectionInfo getCollectionInfo(String collectionName) {
        try {
            io.qdrant.client.grpc.Collections.CollectionInfo description = client
                .getCollectionInfoAsync(collectionName)
                .get();

            CollectionInfo info = new CollectionInfo();
            info.setName(collectionName);
            info.setVectorsCount(description.getVectorsCount());
            info.setPointsCount(description.getPointsCount());
            info.setStatus(description.getStatus().toString());

            return info;

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get collection info: {}", collectionName, e);
            throw new RuntimeException("Failed to get collection info", e);
        }
    }

    @Data
    public static class SearchResult {
        private String id;
        private Double score;
        private String text;
        private Long documentId;
        private String documentName;
    }

    @Data
    public static class CollectionInfo {
        private String name;
        private Long vectorsCount;
        private Long pointsCount;
        private String status;
    }
}