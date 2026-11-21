package com.edupulse.gateway.ingestion.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Fetch policy for external sources ({@code edupulse.ingestion.fetch.*}).
 * Defaults are restrictive: the domain allowlist is empty, so no source is accepted until configured.
 *
 * @param allowedDomains      allowed domains (their subdomains are accepted too)
 * @param httpsOnly           accept HTTPS only
 * @param timeout             connect and response timeout
 * @param maxBytes            maximum response size
 * @param allowedContentTypes accepted content types (no parameters, e.g. {@code text/html})
 * @param maxRedirects        maximum redirects followed (each one is re-validated)
 */
@ConfigurationProperties(prefix = "edupulse.ingestion.fetch")
public record FetchPolicyProperties(List<String> allowedDomains,
                                    Boolean httpsOnly,
                                    Duration timeout,
                                    Long maxBytes,
                                    List<String> allowedContentTypes,
                                    Integer maxRedirects) {

    public FetchPolicyProperties {
        allowedDomains = allowedDomains == null ? List.of() : List.copyOf(allowedDomains);
        httpsOnly = httpsOnly == null || httpsOnly;
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        maxBytes = maxBytes == null ? 5L * 1024 * 1024 : maxBytes;
        allowedContentTypes = allowedContentTypes == null
                ? List.of("text/html", "application/pdf", "text/plain", "text/markdown")
                : List.copyOf(allowedContentTypes);
        maxRedirects = maxRedirects == null ? 3 : maxRedirects;
    }
}
