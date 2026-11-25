package com.edupulse.rag.preretrieval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SafeMetadataFilterTest {
    private final ObjectMapper json = new ObjectMapper();
    private final SafeMetadataFilter filter = new SafeMetadataFilter();

    @Test
    void acceptsOmittedAndEmptyFilters() {
        assertThat(filter.validate(null).publicFilters()).isEmpty();
        assertThat(filter.validate(json.readTree("{}")).publicFilters()).isEmpty();
    }

    @Test
    void preservesAStringAtTheLengthLimitAsALiteral() {
        String source = "' OR 1=1 --" + "a".repeat(245);
        var result = filter.validate(json.readTree("{\"source\":\"" + source + "\"}"));
        assertThat(source).hasSize(256);
        assertThat(result.source()).isEqualTo(source);
        assertThat(result.publicFilters()).isEqualTo(Map.of("source", source));
    }

    @Test
    void preparesAllSixEqualityConditionsTogetherAsATypedConjunction() {
        var result = filter.validate(json.readTree("""
                {"doc_id":"policy-4","source":"policy.pdf","version":"2.0","page":2147483647,
                 "is_latest":false,"valid_from":"2026-11-25T11:30:15+02:00"}
                """));
        assertThat(result).isEqualTo(new SafeMetadataFilter.Conjunction(
                "policy-4", "policy.pdf", "2.0", Integer.MAX_VALUE, false,
                OffsetDateTime.parse("2026-11-25T11:30:15+02:00")));
        assertThat(result.publicFilters()).isEqualTo(Map.of(
                "doc_id", "policy-4", "source", "policy.pdf", "version", "2.0", "page", Integer.MAX_VALUE,
                "is_latest", false, "valid_from", "2026-11-25T11:30:15+02:00"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {"unknown":"value"} | Unknown filter field
            {"tenant_id":"other"} | Unknown filter field
            {"org_unit":"other"} | Unknown filter field
            {"access_level":"admin"} | Unknown filter field
            {"or":[]} | Unknown filter field
            {"doc_id":1} | doc_id must be a string
            {"source":null} | source must be a string
            {"source":""} | source must not be blank
            {"source":"   "} | source must not be blank
            {"source":"\u00a0"} | source must not be blank
            {"source":{"$eq":"policy.pdf"}} | source must be a string
            {"version":["2.0"]} | version must be a string
            {"page":0} | page must be an integer
            {"page":-1} | page must be an integer
            {"page":1.0} | page must be an integer
            {"page":2147483648} | page must be an integer
            {"page":null} | page must be an integer
            {"is_latest":"true"} | is_latest must be a boolean
            {"is_latest":null} | is_latest must be a boolean
            {"valid_from":"2025-02-29T12:00:00Z"} | valid_from must be an ISO-8601 timestamp
            {"valid_from":"2026-11-25T12:00:00"} | valid_from must be an ISO-8601 timestamp
            {"a":1,"b":2,"c":3,"d":4,"e":5,"f":6,"g":7} | filters may contain at most 6 fields
            """)
    void rejectsInvalidFilters(String input, String message) {
        assertThatIllegalArgumentException().isThrownBy(() -> filter.validate(json.readTree(input)))
                .withMessageContaining(message);
    }

    @Test
    void rejectsOversizedStrings() {
        var input = json.readTree("{\"version\":\"" + "a".repeat(257) + "\"}");
        assertThatIllegalArgumentException().isThrownBy(() -> filter.validate(input))
                .withMessageContaining("version must be at most 256");
    }
}
