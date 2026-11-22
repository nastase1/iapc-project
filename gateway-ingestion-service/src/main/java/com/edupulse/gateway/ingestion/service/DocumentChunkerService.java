package com.edupulse.gateway.ingestion.service;

import com.edupulse.gateway.ingestion.config.IngestionProperties;
import com.edupulse.gateway.ingestion.model.IngestionMetadata;
import com.edupulse.gateway.ingestion.model.ParsedDocument;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocumentChunkerService {

    private final IngestionProperties properties;

    public DocumentChunkerService(IngestionProperties properties) {
        this.properties = properties;
    }

    public List<Document> chunk(ParsedDocument parsedDoc, IngestionMetadata metadata) {
        return chunk(parsedDoc, metadata, properties.chunkSize(), properties.chunkOverlap());
    }

    public List<Document> chunk(ParsedDocument parsedDoc, IngestionMetadata metadata, int chunkSize, int chunkOverlap) {
        List<Document> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (ParsedDocument.ParsedPage page : parsedDoc.pages()) {
            String text = page.text();
            if (text == null || text.isBlank()) {
                continue;
            }

            List<String> textSlices = sliceText(text, chunkSize, chunkOverlap);
            for (String slice : textSlices) {
                if (slice.isBlank()) {
                    continue;
                }
                Map<String, Object> metaMap = metadata.toMap(page.pageNumber(), chunkIndex);
                String docUid = metadata.docId() + ":" + metadata.version() + ":" + chunkIndex;
                Document chunkDoc = new Document(docUid, slice, metaMap);
                chunks.add(chunkDoc);
                chunkIndex++;
            }
        }

        return chunks;
    }

    private List<String> sliceText(String text, int chunkSize, int chunkOverlap) {
        List<String> slices = new ArrayList<>();
        if (text.length() <= chunkSize) {
            slices.add(text.trim());
            return slices;
        }

        int step = Math.max(1, chunkSize - chunkOverlap);
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String slice = text.substring(start, end).trim();
            if (!slice.isEmpty()) {
                slices.add(slice);
            }
            if (end == text.length()) {
                break;
            }
            start += step;
        }
        return slices;
    }
}
