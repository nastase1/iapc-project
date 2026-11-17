package com.edupulse.audit.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Client catre {@code rag-query-service} (Microserviciul 2), folosit de agentul de Gap Analysis
 * pentru a interoga baza de politici existente.
 */
@Component
public class RagQueryClient {

    private static final String RAG_QUERY_SERVICE = "http://rag-query-service";

    private final RestClient restClient;

    public RagQueryClient(@LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl(RAG_QUERY_SERVICE).build();
    }

    public Map<String, Object> query(String question, String tenantId) {
        return restClient.post()
                .uri("/api/rag/query")
                .header("X-Tenant-Id", tenantId)
                .body(Map.of("query", question))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}
