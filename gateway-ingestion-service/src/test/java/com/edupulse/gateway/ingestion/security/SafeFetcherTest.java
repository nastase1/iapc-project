package com.edupulse.gateway.ingestion.security;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controls enforced during the download: timeout, size limit, content-type, redirect re-validation.
 * A local HTTP server is used; the validator's DNS is stubbed so "localhost" counts as a public host
 * (SSRF address checks are covered in {@link UrlValidatorTest}).
 */
class SafeFetcherTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", ex -> reply(ex, 200, "text/html; charset=utf-8", "<p>hello</p>".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/exe", ex -> reply(ex, 200, "application/x-msdownload", new byte[]{1, 2, 3}));
        server.createContext("/big", ex -> reply(ex, 200, "text/plain", new byte[5000]));
        server.createContext("/missing", ex -> reply(ex, 404, "text/plain", new byte[0]));
        server.createContext("/slow", ex -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            reply(ex, 200, "text/plain", new byte[]{1});
        });
        server.createContext("/to-ok", ex -> redirect(ex, "/ok"));
        server.createContext("/to-evil", ex -> redirect(ex, "http://evil.test/"));
        server.createContext("/to-private", ex -> redirect(ex, "http://intranet.test/secret"));
        server.createContext("/loop", ex -> redirect(ex, "/loop"));
        server.start();
        base = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private SafeFetcher fetcher(long maxBytes, Duration timeout) {
        FetchPolicyProperties policy = new FetchPolicyProperties(
                List.of("localhost", "intranet.test"), false, timeout, maxBytes, null, 3);
        UrlValidator validator = new UrlValidator(policy, host -> {
            try {
                // "intranet.test" is private, everything else counts as public
                return new InetAddress[]{InetAddress.getByName(host.equals("intranet.test") ? "10.0.0.5" : "93.184.216.34")};
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return new SafeFetcher(validator, policy);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            ex.getResponseBody().write(body);
        }
        ex.close();
    }

    private static void redirect(com.sun.net.httpserver.HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().add("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    private void assertBlocked(String path, String expected) {
        UrlSafetyException e = assertThrows(UrlSafetyException.class,
                () -> fetcher(1024, Duration.ofSeconds(5)).fetch(base + path));
        assertTrue(e.getMessage().contains(expected),
                "expected '" + expected + "' but was: " + e.getMessage());
    }

    // ---- success path ----

    @Test
    void fetchesAllowedContent() {
        SafeFetcher.FetchedContent content = fetcher(1024, Duration.ofSeconds(5)).fetch(base + "/ok");
        assertEquals("text/html", content.contentType());
        assertEquals("<p>hello</p>", new String(content.body(), StandardCharsets.UTF_8));
    }

    @Test
    void followsRedirectToAllowedHost() {
        SafeFetcher.FetchedContent content = fetcher(1024, Duration.ofSeconds(5)).fetch(base + "/to-ok");
        assertEquals("/ok", content.finalUri().getPath());
    }

    // ---- negative tests, one per control ----

    @Test
    void rejectsDisallowedContentType() {
        assertBlocked("/exe", "Content-Type");
    }

    @Test
    void rejectsResponseOverSizeLimit() {
        assertBlocked("/big", "size limit");
    }

    @Test
    void enforcesTimeout() {
        UrlSafetyException e = assertThrows(UrlSafetyException.class,
                () -> fetcher(1024, Duration.ofMillis(300)).fetch(base + "/slow"));
        assertTrue(e.getMessage().contains("Fetch failed"), e.getMessage());
    }

    @Test
    void rejectsNon200Status() {
        assertBlocked("/missing", "HTTP 404");
    }

    @Test
    void revalidatesRedirectTarget() {
        assertBlocked("/to-evil", "allowlist");
        assertBlocked("/to-private", "blocked address");
    }

    @Test
    void stopsRedirectLoops() {
        assertBlocked("/loop", "Too many redirects");
    }

    @Test
    void rejectsInitialUrlOutsideAllowlist() {
        assertThrows(UrlSafetyException.class, () -> fetcher(1024, Duration.ofSeconds(5)).fetch("http://evil.test/"));
    }
}
