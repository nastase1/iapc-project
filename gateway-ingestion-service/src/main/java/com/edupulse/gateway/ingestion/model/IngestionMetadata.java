package com.edupulse.gateway.ingestion.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public record IngestionMetadata(
        String docId,
        String version,
        String source,
        String checksum,
        boolean isLatest,
        String validFrom,
        String tenantId,
        String orgUnit,
        String accessLevel
) {
    public static final String DOC_ID = "doc_id";
    public static final String VERSION = "version";
    public static final String SOURCE = "source";
    public static final String PAGE = "page";
    public static final String CHUNK_INDEX = "chunk_index";
    public static final String CHECKSUM = "checksum";
    public static final String IS_LATEST = "is_latest";
    public static final String VALID_FROM = "valid_from";
    public static final String TENANT_ID = "tenant_id";
    public static final String ORG_UNIT = "org_unit";
    public static final String ACCESS_LEVEL = "access_level";

    public IngestionMetadata {
        version = (version == null || version.isBlank()) ? "1.0" : version;
        validFrom = (validFrom == null || validFrom.isBlank()) ? Instant.now().toString() : validFrom;
        tenantId = (tenantId == null || tenantId.isBlank()) ? "default-org" : tenantId;
        orgUnit = (orgUnit == null || orgUnit.isBlank()) ? "compliance" : orgUnit;
        accessLevel = (accessLevel == null || accessLevel.isBlank()) ? "PUBLIC" : accessLevel;
    }

    public Map<String, Object> toMap(Integer page, int chunkIndex) {
        Map<String, Object> map = new HashMap<>();
        map.put(DOC_ID, docId);
        map.put(VERSION, version);
        map.put(SOURCE, source);
        if (page != null) {
            map.put(PAGE, page);
        }
        map.put(CHUNK_INDEX, chunkIndex);
        map.put(CHECKSUM, checksum);
        map.put(IS_LATEST, isLatest);
        map.put(VALID_FROM, validFrom);
        map.put(TENANT_ID, tenantId);
        map.put(ORG_UNIT, orgUnit);
        map.put(ACCESS_LEVEL, accessLevel);
        return map;
    }
}
