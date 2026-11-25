package com.edupulse.rag.preretrieval;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class SafeMetadataFilter {
    private static final Set<String> ALLOWED_FIELDS =
            Set.of("doc_id", "source", "version", "page", "is_latest", "valid_from");
    private static final int MAX_STRING_LENGTH = 256;

    public Conjunction validate(JsonNode filters) {
        if (filters == null) {
            return new Conjunction(null, null, null, null, null, null);
        }
        if (!filters.isObject()) {
            throw new IllegalArgumentException("filters must be a JSON object.");
        }
        if (filters.size() > ALLOWED_FIELDS.size()) {
            throw new IllegalArgumentException("filters may contain at most 6 fields.");
        }
        for (String field : filters.propertyNames()) {
            if (!ALLOWED_FIELDS.contains(field)) {
                throw new IllegalArgumentException(
                        "Unknown filter field. Allowed fields are doc_id, source, version, page, is_latest, valid_from.");
            }
        }
        return new Conjunction(
                readString(filters, "doc_id"), readString(filters, "source"), readString(filters, "version"),
                readPage(filters.get("page")), readLatest(filters.get("is_latest")), readTimestamp(filters));
    }

    private static String readString(JsonNode filters, String field) {
        JsonNode value = filters.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isString()) {
            throw new IllegalArgumentException(field + " must be a string.");
        }
        String text = value.asString();
        if (text.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException(field + " must be at most 256 UTF-16 code units.");
        }
        if (text.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp))) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return text;
    }

    private static Integer readPage(JsonNode value) {
        if (value == null) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 1) {
            throw new IllegalArgumentException("page must be an integer from 1 to 2147483647.");
        }
        return value.asInt();
    }

    private static Boolean readLatest(JsonNode value) {
        if (value == null) {
            return null;
        }
        if (!value.isBoolean()) {
            throw new IllegalArgumentException("is_latest must be a boolean.");
        }
        return value.asBoolean();
    }

    private static OffsetDateTime readTimestamp(JsonNode filters) {
        String timestamp = readString(filters, "valid_from");
        if (timestamp == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(timestamp);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("valid_from must be an ISO-8601 timestamp with a UTC offset.");
        }
    }

    /** All supplied fields are equality conditions combined with AND. */
    public record Conjunction(String docId, String source, String version, Integer page,
                              Boolean isLatest, OffsetDateTime validFrom) {
        public Map<String, Object> publicFilters() {
            Map<String, Object> filters = new HashMap<>();
            putIfPresent(filters, "doc_id", docId);
            putIfPresent(filters, "source", source);
            putIfPresent(filters, "version", version);
            putIfPresent(filters, "page", page);
            putIfPresent(filters, "is_latest", isLatest);
            putIfPresent(filters, "valid_from", validFrom == null ? null : validFrom.toString());
            return Map.copyOf(filters);
        }

        private static void putIfPresent(Map<String, Object> filters, String field, Object value) {
            if (value != null) {
                filters.put(field, value);
            }
        }
    }
}
