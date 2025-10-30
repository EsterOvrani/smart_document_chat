package com.example.backend.common.infrastructure.document;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DocumentChunkingService {

    /**
     * פיצול מסמך ל-chunks עם overlap לשמירת קונטקסט
     */
    public List<TextSegment> chunkDocument(String content, String fileName, Long documentId) {
        log.info("Chunking document: {} (length: {})", fileName, content.length());

        // ⭐ פיצול עם overlap - חשוב מאוד לדיוק!
        DocumentSplitter splitter = DocumentSplitters.recursive(
            500,  // גודל chunk (התאם לפי הצורך)
            50    // overlap - שומר על קונטקסט בין chunks
        );

        Document document = Document.from(
            content,
            Metadata.from(Map.of(
                "fileName", fileName,
                "documentId", documentId.toString(),
                "timestamp", Instant.now().toString()
            ))
        );

        List<TextSegment> segments = splitter.split(document)
            .stream()
            .map(doc -> TextSegment.from(doc.text(), doc.metadata()))
            .collect(Collectors.toList());

        log.info("Created {} chunks from document", segments.size());
        return segments;
    }
}
