package com.example.backend.common.infrastructure.vectordb;

import com.example.backend.common.exception.ExternalServiceException;
import com.example.backend.config.QdrantProperties;

import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;
import java.util.List;

// ✅ הוסף את אלה:
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class QdrantVectorService {
    private final QdrantProperties qdrantProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    // כתובת Qdrant REST API
    private String qdrantUrl;

    // מיפוי של קולקשין ל-EmbeddingStore
    private final Map<String, EmbeddingStore<TextSegment>> collectionStoreMap = new ConcurrentHashMap<>();

    private String currentActiveCollectionName;

    @PostConstruct
    public void initialize() {
        try {
            // בנה את כתובת Qdrant
            qdrantUrl = String.format("http://%s:6333", qdrantProperties.getHost());

            log.info("Initializing Qdrant Vector service");
            log.info("Qdrant URL: {}", qdrantUrl);
            log.info("Qdrant Port (gRPC): {}", qdrantProperties.getPort());

            // בדוק קישוריות ל-Qdrant
            try {
                String healthUrl = qdrantUrl + "/health";
                ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("✅ Connected to Qdrant successfully");
                }
            } catch (Exception e) {
                log.warn("⚠️ Could not connect to Qdrant: {}", e.getMessage());
            }

            log.info("Qdrant Vector service initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Qdrant Vector service: {}", e.getMessage(), e);
            throw ExternalServiceException.vectorDbError("נכשל באתחול Qdrant: " + e.getMessage());
        }
    }

    /**
     * יצירת קולקשין חדש להעלאה
     * @param chatTitle שם השיחה
     */
    public String createNewCollectionForUpload(String chatTitle) {
        String collectionName = "it still didnt created";
        try {
            // ✅ ניקוי שם השיחה - הסרת תווים לא חוקיים
            String cleanTitle = chatTitle
                .replaceAll("[^a-zA-Z0-9א-ת\\s]", "") // רק אותיות ומספרים
                .replaceAll("\\s+", "_")              // רווחים -> קו תחתון
                .toLowerCase();                        // אותיות קטנות

            // ✅ הגבלת אורך (Qdrant לא אוהב שמות ארוכים מדי)
            if (cleanTitle.length() > 50) {
                cleanTitle = cleanTitle.substring(0, 50);
            }

            String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

            collectionName = cleanTitle + "_" + timestamp;

            log.info("Creating new collection: {}", collectionName);

            // יצור את הקולקשין דרך REST API
            createCollectionIfNotExists(collectionName);
            

            // ✅ המתן עד שהקולקשן מוכן (מקסימום 30 שניות)
            if (!waitForCollectionReady(collectionName, 30)) {
                throw ExternalServiceException.vectorDbError("פג זמן ההמתנה ליצירת קולקשן: " + collectionName);
            }

            // יצירת EmbeddingStore
            EmbeddingStore<TextSegment> newStore = QdrantEmbeddingStore.builder()
                    .host(qdrantProperties.getHost())
                    .port(qdrantProperties.getPort())
                    .collectionName(collectionName)
                    .build();

            // שמירת המידע
            collectionStoreMap.put(collectionName, newStore);
            currentActiveCollectionName = collectionName;

            log.info("✅ Collection created and configured: {}", collectionName);
            return collectionName;

        } catch (Exception e) {
            log.error("❌ Failed to create collection: {}", e.getMessage(), e);
            throw ExternalServiceException.vectorDbError("נכשל ביצירת קולקשן: " + e.getMessage());
        }
    }

    /**
     * בדיקה אם קולקשן קיים וזמין
     */
    private boolean waitForCollectionReady(String collectionName, int maxWaitSeconds) {
        String checkUrl = qdrantUrl + "/collections/" + collectionName;

        int attempts = 0;
        int maxAttempts = maxWaitSeconds * 2; // כל 500ms

        while (attempts < maxAttempts) {
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(checkUrl, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("✅ Collection '{}' is ready (attempt {}/{})",
                        collectionName, attempts + 1, maxAttempts);
                    return true;
                }

            } catch (Exception e) {
                log.debug("Collection not ready yet, waiting... (attempt {}/{})",
                    attempts + 1, maxAttempts);
            }

            try {
                Thread.sleep(500); // המתן חצי שנייה
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }

            attempts++;
        }

        log.error("❌ Collection '{}' not ready after {} seconds", collectionName, maxWaitSeconds);
        return false;
    }

    /**
     * יצור קולקשין אם הוא לא קיים
     */
    private void createCollectionIfNotExists(String collectionName) {
        try {
            // בדיקה אם הקולקשין קיים
            String getUrl = qdrantUrl + "/collections/" + collectionName;
            try {
                ResponseEntity<String> checkResponse = restTemplate.getForEntity(getUrl, String.class);
                if (checkResponse.getStatusCode().is2xxSuccessful()) {
                    log.info("✅ Collection '{}' already exists", collectionName);
                    return;
                }
            } catch (Exception e) {
                log.debug("Collection '{}' not found, creating new one", collectionName);
            }

            // יצור קולקשין חדש דרך PUT
            String createUrl = qdrantUrl + "/collections/" + collectionName;

            Map<String, Object> body = Map.of(
                    "vectors", Map.of(
                            "size", qdrantProperties.getDimension(),
                            "distance", qdrantProperties.getDistance()
                    ),
                    "hnsw_config", Map.of(
                            "m", qdrantProperties.getHnswM(),
                            "ef_construct", qdrantProperties.getHnswEfConstruct()
                    ),
                    "optimizers_config", Map.of(
                            "indexing_threshold", 10000
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    createUrl,
                    HttpMethod.PUT,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Collection '{}' created with HNSW optimization", collectionName);
            } else {
                log.error("❌ Failed to create collection '{}': {}", collectionName, response.getBody());
            }

        } catch (Exception e) {
            log.error("⚠️ Error managing collection '{}': {}", collectionName, e.getMessage(), e);
        }
    }

    /**
     * קבלת ה-EmbeddingStore של הקולקשין הנוכחי
     */
    public EmbeddingStore<TextSegment> getCurrentEmbeddingStore() {
        if (currentActiveCollectionName == null) {
            log.warn("⚠️ No active collection found");
            return null;
        }
        EmbeddingStore<TextSegment> store = collectionStoreMap.get(currentActiveCollectionName);
        if (store == null) {
            log.warn("⚠️ Store not found for collection: {}", currentActiveCollectionName);
        }
        return store;
    }

    /**
     * קבלת ה-EmbeddingStore של קולקשין ספציפי
     */
    public EmbeddingStore<TextSegment> getEmbeddingStoreForCollection(String collectionName) {
        log.info("🔍 Looking for collection: {}", collectionName);
        log.info("📊 Available collections: {}", collectionStoreMap.keySet());

        EmbeddingStore<TextSegment> store = collectionStoreMap.get(collectionName);

        if (store == null) {
            log.warn("❌ Collection not in cache, trying to create...");
            // נסה ליצור אותו אם הוא לא קיים
            createCollectionIfNotExists(collectionName);

            // אחרי יצירה, צור EmbeddingStore חדש
            store = QdrantEmbeddingStore.builder()
                    .host(qdrantProperties.getHost())
                    .port(qdrantProperties.getPort())
                    .collectionName(collectionName)
                    .build();

            collectionStoreMap.put(collectionName, store);
        }

        return store;
    }

    /**
     * קבלת שם הקולקשין הנוכחי
     */
    public String getCurrentCollectionName() {
        return currentActiveCollectionName;
    }

    /**
     * בדיקה אם הקולקשין קיים ופעיל
     */
    public boolean isCollectionActive(String collectionName) {
        return collectionStoreMap.containsKey(collectionName);
    }

    /**
     * הסרת קולקשין מה-cache
     */
    public void removeCollectionFromCache(String collectionName) {
        collectionStoreMap.remove(collectionName);

        if (currentActiveCollectionName != null &&
                currentActiveCollectionName.equals(collectionName)) {
            currentActiveCollectionName = null;
        }

        log.info("Collection removed from cache: {}", collectionName);
    }

    public String getCollectionInfo() {
        return String.format("Current Collection: %s, Host: %s, REST API: %s, Active Collections: %d",
                currentActiveCollectionName != null ? currentActiveCollectionName : "None",
                qdrantProperties.getHost(),
                qdrantUrl,
                collectionStoreMap.size());
    }

    public boolean isReady() {
        return qdrantProperties != null;
    }

    /**
     * יצירת קולקשין חדש לקובץ בודד (legacy method - לתאימות לאחור)
     */
    @Deprecated
    public String createNewCollectionForFile(String fileId, String fileName) {
        return createNewCollectionForUpload(fileName);
    }

    /**
     * מחיקת קולקשין מ-Qdrant לחלוטין (לא רק מה-cache)
     */
    public void deleteCollection(String collectionName) {
        if (collectionName == null || collectionName.isEmpty()) {
            log.warn("⚠️ Cannot delete collection - name is null or empty");
            return;
        }

        try {
            log.info("🗑️ Deleting Qdrant collection: {}", collectionName);

            // מחיקה מ-Qdrant עצמו
            String deleteUrl = qdrantUrl + "/collections/" + collectionName;
            restTemplate.delete(deleteUrl);

            // מחיקה מה-cache (משתמש בפונקציה הקיימת!)
            removeCollectionFromCache(collectionName);

            log.info("✅ Collection '{}' deleted successfully", collectionName);

        } catch (Exception e) {
            log.error("❌ Failed to delete collection: {}", collectionName, e);
            throw ExternalServiceException.vectorDbError("נכשל במחיקת קולקשן: " + collectionName);
        }
    }
}
