package com.edupulse.gateway.ingestion.model;

import java.util.List;

public record ParsedDocument(
        String docId,
        String source,
        String checksum,
        List<ParsedPage> pages
) {
    public record ParsedPage(Integer pageNumber, String text) {}
}
