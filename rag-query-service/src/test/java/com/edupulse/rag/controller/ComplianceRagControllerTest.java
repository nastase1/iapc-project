package com.edupulse.rag.controller;

import com.edupulse.rag.preretrieval.AcronymExpander;
import com.edupulse.rag.preretrieval.QueryNormalizer;
import com.edupulse.rag.preretrieval.SafeMetadataFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ComplianceRagControllerTest {
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ComplianceRagController(new QueryNormalizer(),
                    new AcronymExpander(Path.of("config", "acronyms.txt").toString()), new SafeMetadataFilter()))
            .build();

    @Test
    void returnsNormalizedQueryWithExplicitTenantAndDemoResponse() throws Exception {
        mvc.perform(post("/api/rag/query")
                        .header("X-Tenant-Id", "school-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"  Vă rog,   ce spune GDPR despre păstrarea datelor???  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("ce spune GDPR despre păstrarea datelor"))
                .andExpect(jsonPath("$.expandedQuery")
                        .value("ce spune GDPR (General Data Protection Regulation) despre păstrarea datelor"))
                .andExpect(jsonPath("$.tenantId").value("school-a"))
                .andExpect(jsonPath("$.appliedFilters").isEmpty())
                .andExpect(jsonPath("$.answer").value("Sample compliance response with citation."))
                .andExpect(jsonPath("$.citations[0].source").value("FERPA_Policy.pdf"))
                .andExpect(jsonPath("$.citations[0].page").value(4))
                .andExpect(jsonPath("$.citations[0].snippet").value("Student record data privacy..."));
    }

    @Test
    void acceptsExplicitNullWithDefaultTenant() throws Exception {
        mvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value(""))
                .andExpect(jsonPath("$.expandedQuery").value(""))
                .andExpect(jsonPath("$.tenantId").value("default-org"));
    }

    @Test
    void acceptsMissingQuery() throws Exception {
        mvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value(""));
    }

    @Test
    void returnsValidatedFiltersAndStillPreprocessesTheQuery() throws Exception {
        mvc.perform(post("/api/rag/query")
                        .header("X-Tenant-Id", "school-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"  Please,   explain GDPR???  ",
                                 "filters":{"source":"regulation.pdf","version":"2.0","is_latest":true,"page":4}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("explain GDPR"))
                .andExpect(jsonPath("$.expandedQuery").value("explain GDPR (General Data Protection Regulation)"))
                .andExpect(jsonPath("$.tenantId").value("school-a"))
                .andExpect(jsonPath("$.appliedFilters.source").value("regulation.pdf"))
                .andExpect(jsonPath("$.appliedFilters.version").value("2.0"))
                .andExpect(jsonPath("$.appliedFilters.is_latest").value(true))
                .andExpect(jsonPath("$.appliedFilters.page").value(4))
                .andExpect(jsonPath("$.appliedFilters.tenant_id").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "true", "\"source\"", "{\"source\":null}",
            "{\"page\":\"4\"}", "{\"tenant_id\":\"other-school\"}", "{\"access_level\":\"admin\"}"})
    void rejectsInvalidFiltersWithoutReturningTheDemoAnswer(String filters) throws Exception {
        mvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"GDPR\",\"filters\":" + filters + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_filters"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.answer").doesNotExist())
                .andExpect(jsonPath("$.citations").doesNotExist());
    }
}
