package com.edupulse.gateway.ingestion.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;

/**
 * Downloads an external source under the security policy: validated URL (re-validated on every
 * redirect), timeout, content-type check and size limit (streamed read).
 */
@Component
public class SafeFetcher {

    private static final Logger log = LoggerFactory.getLogger(SafeFetcher.class);

    /** @param contentType content type without parameters (e.g. {@code text/html}) */
    public record FetchedContent(URI finalUri, String contentType, byte[] body) {
    }

    private final UrlValidator validator;
    private final FetchPolicyProperties policy;
    private final HttpClient client;

    public SafeFetcher(UrlValidator validator, FetchPolicyProperties policy) {
        this.validator = validator;
        this.policy = policy;
        // redirects are followed manually so that every target is re-validated
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(policy.timeout())
                .build();
    }

    public FetchedContent fetch(String url) {
        URI uri = validator.validate(url);
        for (int hop = 0; hop <= policy.maxRedirects(); hop++) {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(policy.timeout()).GET().build();
            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.headers().firstValue("Location")
                            .orElseThrow(() -> new UrlSafetyException("Redirect without Location header"));
                    response.body().close();
                    uri = validator.validate(uri.resolve(location).toString());
                    continue;
                }
                if (status != 200) {
                    response.body().close();
                    throw new UrlSafetyException("Source answered HTTP " + status);
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("")
                        .split(";")[0].trim().toLowerCase(Locale.ROOT);
                if (!policy.allowedContentTypes().contains(contentType)) {
                    response.body().close();
                    throw new UrlSafetyException("Content-Type '" + contentType + "' is not allowed");
                }
                byte[] body = readLimited(response.body());
                log.info("Fetched {} ({}, {} bytes)", uri.getHost(), contentType, body.length);
                return new FetchedContent(uri, contentType, body);
            } catch (IOException e) {
                throw new UrlSafetyException("Fetch failed: " + e.getClass().getSimpleName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UrlSafetyException("Fetch interrupted");
            }
        }
        throw new UrlSafetyException("Too many redirects (max " + policy.maxRedirects() + ")");
    }

    private byte[] readLimited(InputStream in) throws IOException {
        try (in) {
            byte[] data = in.readNBytes((int) Math.min(policy.maxBytes() + 1, Integer.MAX_VALUE - 8));
            if (data.length > policy.maxBytes()) {
                throw new UrlSafetyException("Response exceeds the size limit of " + policy.maxBytes() + " bytes");
            }
            return data;
        }
    }
}
