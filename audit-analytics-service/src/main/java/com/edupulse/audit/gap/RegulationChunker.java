package com.edupulse.audit.gap;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Imparte textul unei reglementari in bucati de maximum {@code maxChars} caractere, respectand
 * structura: un heading (Article/Section/1.2/#) incepe o bucata noua si da numele sectiunii.
 * Regex-urile sunt marginite si se aplica doar pe linii scurte (fara risc ReDoS).
 */
public final class RegulationChunker {

    public record Chunk(int index, String section, String text) {
    }

    private static final int MAX_HEADING_LENGTH = 120;
    private static final Pattern HEADING = Pattern.compile(
            "^(?:(?:Article|Section|Chapter|Art\\.)\\s+\\S.*|\\d{1,3}(?:\\.\\d{1,3}){0,4}[.)]?\\s+\\p{Lu}.*|#{1,6}\\s+\\S.*)$");

    private RegulationChunker() {
    }

    public static List<Chunk> chunk(String text, int maxChars) {
        if (maxChars < 100) {
            throw new IllegalArgumentException("maxChars must be >= 100");
        }
        List<Chunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String section = "Preamble";
        StringBuilder current = new StringBuilder();

        for (String rawLine : text.split("\\R", -1)) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                if (!current.isEmpty()) current.append('\n');
                continue;
            }
            if (isHeading(line)) {
                flush(chunks, section, current);
                section = line;
            }
            // linii mai lungi decat limita: taiate in bucati
            for (int start = 0; start < line.length(); start += maxChars) {
                String piece = line.substring(start, Math.min(line.length(), start + maxChars));
                if (current.length() + piece.length() + 1 > maxChars) {
                    flush(chunks, section, current);
                }
                if (!current.isEmpty()) current.append('\n');
                current.append(piece);
            }
        }
        flush(chunks, section, current);
        return chunks;
    }

    private static boolean isHeading(String line) {
        return line.length() <= MAX_HEADING_LENGTH && HEADING.matcher(line).matches();
    }

    private static void flush(List<Chunk> chunks, String section, StringBuilder current) {
        String text = current.toString().strip();
        current.setLength(0);
        if (!text.isEmpty()) {
            chunks.add(new Chunk(chunks.size(), section, text));
        }
    }
}
