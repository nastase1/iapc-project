package com.edupulse.audit.eval;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates the live system: {@code POST /api/rag/query} through the gateway, reading the
 * {@code citations[].source} values in the order returned.
 * TODO: switch to the retrieved-chunk list once rag-query-service exposes it (S3/S4 pipeline).
 */
public class HttpRagRetriever implements Retriever {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final URI endpoint;
    private final String bearerToken;

    public HttpRagRetriever(String baseUrl, String bearerToken) {
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/api/rag/query");
        this.bearerToken = bearerToken;
    }

    @Override
    public List<String> retrieve(String question, int k) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("query", question, "k", k))));
        if (bearerToken != null && !bearerToken.isBlank()) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " from " + endpoint);
        }
        List<String> sources = new ArrayList<>();
        for (JsonNode citation : mapper.readTree(response.body()).path("citations")) {
            String source = citation.path("source").asString("");
            if (!source.isBlank()) {
                sources.add(source);
            }
        }
        return sources;
    }
}
