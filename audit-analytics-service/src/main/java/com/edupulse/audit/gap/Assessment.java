package com.edupulse.audit.gap;

/** Verdictul LLM pentru o cerinta: este acoperita de politicile existente sau nu. */
public record Assessment(boolean compliant, String severity, String finding, String evidence) {
}
