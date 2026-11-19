package com.edupulse.audit.gap;

@FunctionalInterface
public interface GapAssessor {

    /**
     * @param requirement textul cerintei din reglementare
     * @param policyContext fragmentele de politici interne regasite prin RAG (poate fi gol)
     * @throws RuntimeException daca evaluarea nu a putut fi facuta (apelantul marcheaza NOT_ASSESSED)
     */
    Assessment assess(String requirement, String policyContext);
}
