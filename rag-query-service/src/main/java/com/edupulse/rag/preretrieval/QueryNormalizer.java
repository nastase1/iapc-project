package com.edupulse.rag.preretrieval;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QueryNormalizer {
    private static final Pattern WHITESPACE = Pattern.compile(
            "[\\s\\p{javaWhitespace}]++", Pattern.UNICODE_CHARACTER_CLASS);
    // Try each run of spaces and question marks only at its beginning.
    private static final Pattern TRAILING_QUESTIONS = Pattern.compile("(?<![ ?])[ ?]++\\z");
    private static final Pattern POLITE_PREFIX = Pattern.compile(
            "(?:te rog|vă rog|va rog|please)(?:[,:])?(?: |\\z)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public String normalize(String query) {
        if (query == null) {
            return "";
        }

        String compact = WHITESPACE.matcher(query).replaceAll(" ").strip();
        String trimmed = TRAILING_QUESTIONS.matcher(compact).replaceFirst("");

        // Advance through consecutive prefixes without copying the remaining text on each match.
        Matcher prefix = POLITE_PREFIX.matcher(trimmed);
        int start = 0;
        while (prefix.region(start, trimmed.length()).lookingAt()) {
            start = prefix.end();
        }
        return trimmed.substring(start);
    }
}
