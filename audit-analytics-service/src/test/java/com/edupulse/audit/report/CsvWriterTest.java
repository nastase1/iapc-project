package com.edupulse.audit.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvWriterTest {

    @Test
    void escapesQuotes() {
        assertEquals("\"a\"\"b\"", CsvWriter.cell("a\"b"));
    }

    @Test
    void neutralizesFormulaInjection() {
        assertEquals("\"'=SUM(A1)\"", CsvWriter.cell("=SUM(A1)"));
    }

    @Test
    void nullIsEmpty() {
        assertEquals("", CsvWriter.cell(null));
    }
}
