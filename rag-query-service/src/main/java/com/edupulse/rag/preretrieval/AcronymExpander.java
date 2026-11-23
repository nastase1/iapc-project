package com.edupulse.rag.preretrieval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class AcronymExpander {
    private static final Logger log = LoggerFactory.getLogger(AcronymExpander.class);
    private static final int UNICODE_WORDS = Pattern.UNICODE_CHARACTER_CLASS;
    private static final Pattern WORD = Pattern.compile("\\w++", UNICODE_WORDS);
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{javaWhitespace}]++", UNICODE_WORDS);
    private static final Pattern ACRONYM = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}]*");

    private final Map<String, Entry> entries;

    public AcronymExpander(@Value("${rag.acronyms.path:}") String dictionaryPath) {
        entries = loadDictionary(dictionaryPath);
    }

    public String expand(String query) {
        if (query == null || query.isEmpty() || entries.isEmpty()) {
            return query;
        }

        Matcher words = WORD.matcher(query);
        if (!words.find()) {
            return query;
        }

        BitSet protectedText = new BitSet();
        Set<Entry> present = new HashSet<>();
        for (Entry entry : entries.values()) {
            if (protectMatches(entry.fullForm(), query, protectedText)) {
                present.add(entry);
            }
            // Protect optional synonyms too, including any acronyms inside them.
            protectMatches(entry.annotationPattern(), query, protectedText);
        }

        StringBuilder result = new StringBuilder();
        int copiedThrough = 0;
        int protectedIndex = protectedText.nextSetBit(0);
        do {
            Entry entry = entries.get(words.group());
            if (protectedIndex >= 0 && protectedIndex < words.start()) {
                protectedIndex = protectedText.nextSetBit(words.start());
            }
            if (entry != null && (protectedIndex < 0 || protectedIndex >= words.end()) && present.add(entry)) {
                result.append(query, copiedThrough, words.end()).append(' ').append(entry.annotation());
                copiedThrough = words.end();
            }
        } while (words.find());

        return copiedThrough == 0 ? query : result.append(query, copiedThrough, query.length()).toString();
    }

    private static boolean protectMatches(Pattern pattern, String query, BitSet protectedText) {
        Matcher matcher = pattern.matcher(query);
        boolean found = false;
        while (matcher.find()) {
            protectedText.set(matcher.start(), matcher.end());
            found = true;
        }
        return found;
    }

    private static Map<String, Entry> loadDictionary(String dictionaryPath) {
        if (dictionaryPath.isBlank()) {
            return Map.of();
        }

        Map<String, Entry> dictionary = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        try {
            int lineNumber = 0;
            for (String line : Files.readAllLines(Path.of(dictionaryPath))) {
                lineNumber++;
                if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                String content = line.strip();
                if (content.isEmpty() || content.startsWith("#")) {
                    continue;
                }
                if (!addEntry(dictionary, content)) {
                    log.warn("Invalid acronym dictionary entry at line {}; acronym expansion disabled", lineNumber);
                    return Map.of();
                }
            }
        } catch (IOException | IllegalArgumentException | SecurityException exception) {
            log.warn("Could not read acronym dictionary ({}); acronym expansion disabled",
                    exception.getClass().getSimpleName());
            return Map.of();
        }
        return dictionary;
    }

    private static boolean addEntry(Map<String, Entry> dictionary, String line) {
        String[] pair = line.split("=", 2);
        String acronym = pair[0].strip();
        if (pair.length != 2 || !ACRONYM.matcher(acronym).matches() || dictionary.containsKey(acronym)) {
            return false;
        }

        String[] terms = pair[1].split(";", -1);
        for (int i = 0; i < terms.length; i++) {
            terms[i] = WHITESPACE.matcher(terms[i]).replaceAll(" ").strip();
            if (terms[i].isEmpty()) {
                return false;
            }
        }
        String annotation = "(" + String.join("; ", terms) + ")";
        int flags = UNICODE_WORDS | Pattern.CASE_INSENSITIVE;
        String formPattern = Arrays.stream(terms[0].split(" "))
                .map(Pattern::quote)
                .collect(Collectors.joining(WHITESPACE.pattern()));
        Pattern fullForm = Pattern.compile("(?<!\\w)" + formPattern + "(?!\\w)", flags);
        Pattern annotationPattern = Pattern.compile(Pattern.quote(annotation), flags);
        dictionary.put(acronym, new Entry(fullForm, annotationPattern, annotation));
        return true;
    }

    private record Entry(Pattern fullForm, Pattern annotationPattern, String annotation) {}
}
