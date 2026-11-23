package com.edupulse.rag.controller;

import com.edupulse.rag.preretrieval.AcronymExpander;
import com.edupulse.rag.preretrieval.QueryNormalizer;
import org.junit.jupiter.api.Test;
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
                    new AcronymExpander(Path.of("config", "acronyms.txt").toString())))
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
}
