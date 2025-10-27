package com.example.backend.share.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "qdrant")
public class QdrantProperties {

    private String host = "localhost";
    private int port = 6334; // gRPC port
    private String collectionName = "smart_documents";
    private boolean useTls = false;
    private String apiKey;

    // Embedding configuration
    private int dimension = 3072; // OpenAI text-embedding-3-large dimension
    private String distance = "Cosine"; // Distance metric for similarity search
    
    // ⭐ הוסף את השורות האלה - פרמטרי חיפוש
    private int defaultMaxResults = 5;
    private double defaultMinScore = 0.7;
    
    // ⭐ הוסף את השורות האלה - HNSW optimization לדיוק
    private int hnswM = 16;
    private int hnswEfConstruct = 200;  // גבוה יותר = דיוק טוב יותר
    private int hnswEf = 128;  // מספר הוקטורים שנבדקים בחיפוש
}