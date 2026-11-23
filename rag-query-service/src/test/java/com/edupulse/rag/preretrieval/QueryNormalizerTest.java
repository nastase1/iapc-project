package com.edupulse.rag.preretrieval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class QueryNormalizerTest {
    private final QueryNormalizer normalizer = new QueryNormalizer();

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("queries")
    void normalizesAndIsIdempotent(String input, String expected) {
        String normalized = normalizer.normalize(input);

        assertEquals(expected, normalized);
        assertEquals(normalized, normalizer.normalize(normalized));
    }

    static Stream<Arguments> queries() {
        return Stream.of(
                Arguments.of("GDPR data retention", "GDPR data retention"),
                Arguments.of("  Păstrarea   datelor  GDPR  ", "Păstrarea datelor GDPR"),
                Arguments.of("GDPR\tși\r\nFERPA", "GDPR și FERPA"),
                Arguments.of("\u00a0GDPR\u2003și\u202fFERPA\u3000", "GDPR și FERPA"),
                Arguments.of("Te rog, explică art. 5.", "explică art. 5."),
                Arguments.of("VĂ ROG: Care sunt obligațiile GDPR?", "Care sunt obligațiile GDPR"),
                Arguments.of("Va rog explicați FERPA", "explicați FERPA"),
                Arguments.of("pLeAsE summarize FERPA", "summarize FERPA"),
                Arguments.of("Please, te rog: vă rog GDPR???", "GDPR"),
                Arguments.of("GDPR? ?  ?? ", "GDPR"),
                Arguments.of("GDPR? Urmează FERPA!", "GDPR? Urmează FERPA!"),
                Arguments.of("Ce înseamnă „GDPR?”", "Ce înseamnă „GDPR?”"),
                Arguments.of("Art. 5(1)(a); ISO/IEC 27001:2022", "Art. 5(1)(a); ISO/IEC 27001:2022"),
                Arguments.of("Define «please»", "Define «please»"),
                Arguments.of("Pleaser is a name?", "Pleaser is a name"),
                Arguments.of("te rogi pentru ceva?", "te rogi pentru ceva"),
                Arguments.of("Please:8080", "Please:8080"),
                Arguments.of("Reguli pentru elevi 📚?", "Reguli pentru elevi 📚"),
                Arguments.of("Care sunt regulile pentru Știință și Învățământ?", "Care sunt regulile pentru Știință și Învățământ"),
                Arguments.of("Te rog \u001cPlease GDPR?", "GDPR"),
                Arguments.of("Please, ???", ""),
                Arguments.of("? ? ??", ""),
                Arguments.of("", ""),
                Arguments.of(" \n\t\u00a0", ""),
                Arguments.of(null, "")
        );
    }

    @Test
    void handlesLongNearMatchesAndRepeatedPrefixesWithinTimeout() {
        String internalQuestions = "? ".repeat(100_000);
        String expected = "GDPR " + internalQuestions + "FERPA";
        String input = "Please, ".repeat(50_000) + expected + " ?".repeat(100_000);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            String normalized = normalizer.normalize(input);
            assertEquals(expected, normalized);
            assertEquals(normalized, normalizer.normalize(normalized));
        });
    }
}
