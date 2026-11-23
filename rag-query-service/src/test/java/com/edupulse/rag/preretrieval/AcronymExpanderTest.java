package com.edupulse.rag.preretrieval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AcronymExpanderTest {
    @TempDir
    Path directory;
    private AcronymExpander expander;

    @BeforeEach
    void createDictionary() throws IOException {
        expander = new AcronymExpander(writeDictionary("""
                # Example entries
                GDPR = General Data Protection Regulation
                FERPA = Family Educational Rights and Privacy Act
                ÎS = Învățământ Secundar; studii secundare
                """).toString());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            GDPR și FERPA|GDPR (General Data Protection Regulation) și FERPA (Family Educational Rights and Privacy Act)
            (gDpR), FERPA.|(gDpR (General Data Protection Regulation)), FERPA (Family Educational Rights and Privacy Act).
            preGDPR GDPR2 GDPR_name șGDPR GDPRă|preGDPR GDPR2 GDPR_name șGDPR GDPRă
            GDPR–FERPA|GDPR (General Data Protection Regulation)–FERPA (Family Educational Rights and Privacy Act)
            Normele pentru îs|Normele pentru îs (Învățământ Secundar; studii secundare)
            îs_2026 și șîs|îs_2026 și șîs
            GDPR și gdpr|GDPR (General Data Protection Regulation) și gdpr
            GDPR (General Data Protection Regulation)|GDPR (General Data Protection Regulation)
            GDPR și general data protection regulation|GDPR și general data protection regulation
            général data protection regulation, GDPR|général data protection regulation, GDPR (General Data Protection Regulation)
            General Data Protection Regulations și GDPR|General Data Protection Regulations și GDPR (General Data Protection Regulation)
            Rămâne textul inițial.|Rămâne textul inițial.
            """)
    void expandsAndIsIdempotent(String query, String expected) {
        String expanded = expander.expand(query);
        assertEquals(expected, expanded);
        assertEquals(expanded, expander.expand(expanded));
    }

    @Test
    void preservesWhitespaceCombiningMarksAndNull() {
        String query = "  GDPR\u0301\t🙂  ";
        assertSame(query, expander.expand(query));
        assertEquals("  gdpr (General Data Protection Regulation)\t🙂  ", expander.expand("  gdpr\t🙂  "));
        String alreadyPresent = "GDPR și General\u00a0Data\tProtection  Regulation";
        assertSame(alreadyPresent, expander.expand(alreadyPresent));
        assertEquals("", expander.expand(""));
        assertNull(expander.expand(null));
    }

    @Test
    void leavesAcronymsInsideFullFormsAndGeneratedSynonymsAlone() throws IOException {
        AcronymExpander nested = new AcronymExpander(writeDictionary("""
                ABC = Program GDPR; see FERPA
                GDPR = General Data Protection Regulation
                FERPA = Family Educational Rights and Privacy Act
                """).toString());
        String expanded = nested.expand("ABC și FERPA");
        assertEquals("ABC (Program GDPR; see FERPA) și FERPA (Family Educational Rights and Privacy Act)", expanded);
        assertEquals(expanded, nested.expand(expanded));
        assertEquals("ABC în Program GDPR", nested.expand("ABC în Program GDPR"));
    }

    @Test
    void loadsFromSpringPropertyAndReadsEditsOnNewStartup() throws IOException {
        Path path = writeDictionary("\uFEFFGDPR = First\u00a0  wording");
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment().withProperty("rag.acronyms.path", path.toString()));
            context.register(AcronymExpander.class);
            context.refresh();
            AcronymExpander running = context.getBean(AcronymExpander.class);

            writeDictionary("GDPR = Updated wording");
            assertEquals("GDPR (First wording)", running.expand("GDPR"));
            assertEquals("GDPR (Updated wording)", new AcronymExpander(path.toString()).expand("GDPR"));
        }
    }

    @Test
    void leavesQueriesUnchangedWhenDictionaryIsMissingOrUnreadable() throws IOException {
        Path badEncoding = Files.write(directory.resolve("bad-utf8.txt"), new byte[]{(byte) 0xff});
        assertEquals("GDPR", new AcronymExpander("").expand("GDPR"));
        assertEquals("GDPR", new AcronymExpander(directory.resolve("missing.txt").toString()).expand("GDPR"));
        assertEquals("GDPR", new AcronymExpander(directory.toString()).expand("GDPR"));
        assertEquals("GDPR", new AcronymExpander(badEncoding.toString()).expand("GDPR"));
        assertEquals("GDPR", new AcronymExpander("bad\0path").expand("GDPR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed line", "FERPA =", "FERPA = Full form;", "gdpr = Duplicate", "T WO = Full form"})
    void disablesEntireDictionaryOnInvalidEntry(String invalidLine) throws IOException {
        Path path = writeDictionary("GDPR = General Data Protection Regulation\n" + invalidLine);
        assertEquals("GDPR", new AcronymExpander(path.toString()).expand("GDPR"));
    }

    private Path writeDictionary(String contents) throws IOException {
        return Files.writeString(directory.resolve("acronyms.txt"), contents);
    }
}
