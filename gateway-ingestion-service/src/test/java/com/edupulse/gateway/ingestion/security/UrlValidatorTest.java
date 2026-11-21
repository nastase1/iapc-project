package com.edupulse.gateway.ingestion.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Negative tests: one per SSRF control (see "Ingestion source security" in the README).
 * DNS is injected so the tests never touch the network.
 */
class UrlValidatorTest {

    private static final Map<String, String> DNS = Map.of(
            "docs.example.com", "93.184.216.34",
            "localhost.example.com", "127.0.0.1",
            "intranet.example.com", "10.0.0.5",
            "rfc1918.example.com", "192.168.1.10",
            "metadata.example.com", "169.254.169.254",
            "cgnat.example.com", "100.64.0.1",
            "v6loop.example.com", "::1",
            "v6ula.example.com", "fd00:ec2::254",
            "mapped.example.com", "::ffff:127.0.0.1",
            "mixed.example.com", "93.184.216.34,10.0.0.5"
    );

    private static final List<String> ALLOWED = List.of(
            "example.com", "localhost", "127.0.0.1", "169.254.169.254");

    private UrlValidator validator(List<String> allowed, boolean httpsOnly) {
        FetchPolicyProperties policy = new FetchPolicyProperties(allowed, httpsOnly, null, null, null, null);
        return new UrlValidator(policy, host -> {
            String ips = DNS.get(host);
            if (ips == null) {
                return new InetAddress[0];
            }
            return java.util.Arrays.stream(ips.split(",")).map(UrlValidatorTest::address).toArray(InetAddress[]::new);
        });
    }

    private static InetAddress address(String literal) {
        try {
            return InetAddress.getByName(literal); // literal IP: no DNS lookup
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertBlocked(String url, String expectedReason) {
        UrlSafetyException e = assertThrows(UrlSafetyException.class, () -> validator(ALLOWED, true).validate(url));
        assertTrue(e.getMessage().contains(expectedReason),
                "expected message to contain '" + expectedReason + "' but was: " + e.getMessage());
    }

    // ---- success path ----

    @Test
    void acceptsAllowlistedHttpsUrl() {
        URI uri = validator(ALLOWED, true).validate("https://docs.example.com/policy/ferpa.html");
        assertEquals("docs.example.com", uri.getHost());
    }

    @Test
    void acceptsExactDomainOfAllowlistEntry() {
        validator(List.of("docs.example.com"), true).validate("https://docs.example.com/a");
    }

    // ---- scheme ----

    @ParameterizedTest
    @ValueSource(strings = {
            "http://docs.example.com/a", "ftp://docs.example.com/a", "file:///etc/passwd",
            "gopher://docs.example.com/", "javascript:alert(1)", "//docs.example.com/a"})
    void rejectsNonHttpsSchemes(String url) {
        assertBlocked(url, "Scheme");
    }

    // ---- localhost, private ranges, cloud metadata (the IP check, even for allowlisted hosts) ----

    @Test
    void blocksHostResolvingToLoopback() {
        assertBlocked("https://localhost.example.com/", "blocked address");
    }

    @Test
    void blocksHostResolvingToPrivateRange10() {
        assertBlocked("https://intranet.example.com/", "blocked address");
    }

    @Test
    void blocksHostResolvingToPrivateRange192() {
        assertBlocked("https://rfc1918.example.com/", "blocked address");
    }

    @Test
    void blocksCloudMetadataAddress() {
        assertBlocked("https://metadata.example.com/latest/meta-data/", "blocked address");
    }

    @Test
    void blocksCgnatRange() {
        assertBlocked("https://cgnat.example.com/", "blocked address");
    }

    @Test
    void blocksIpv6LoopbackAndUniqueLocal() {
        assertBlocked("https://v6loop.example.com/", "blocked address");
        assertBlocked("https://v6ula.example.com/", "blocked address");
    }

    @Test
    void blocksIpv4MappedIpv6Loopback() {
        assertBlocked("https://mapped.example.com/", "blocked address");
    }

    @Test
    void blocksWhenAnyResolvedAddressIsPrivate() {
        assertBlocked("https://mixed.example.com/", "blocked address");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://localhost/", "https://127.0.0.1/", "https://[::1]/", "https://169.254.169.254/latest/meta-data/",
            "https://2130706433/", "https://0x7f.0.0.1/", "https://0177.0.0.1/"})
    void blocksLiteralInternalHostsEvenWhenListedInAllowlist(String url) {
        // even if an operator mistakenly allowlists these names, the resolved address is still checked
        UrlValidator strict = new UrlValidator(
                new FetchPolicyProperties(ALLOWED, true, null, null, null, null), host -> {
                    try {
                        return InetAddress.getAllByName(host);
                    } catch (UnknownHostException e) {
                        return new InetAddress[0];
                    }
                });
        assertThrows(UrlSafetyException.class, () -> strict.validate(url));
    }

    // ---- allowlist ----

    @Test
    void rejectsDomainOutsideAllowlist() {
        assertBlocked("https://evil.test/", "allowlist");
    }

    @Test
    void rejectsLookalikeDomains() {
        // suffix match must be on a label boundary
        assertBlocked("https://notexample.com/", "allowlist");
        assertBlocked("https://example.com.evil.test/", "allowlist");
    }

    @Test
    void emptyAllowlistRejectsEverything() {
        assertThrows(UrlSafetyException.class,
                () -> validator(List.of(), true).validate("https://docs.example.com/"));
    }

    @Test
    void rejectsCredentialsInUrl() {
        assertBlocked("https://docs.example.com@evil.test/", "Credentials");
        assertBlocked("https://user:pass@docs.example.com/", "Credentials");
    }

    // ---- malformed / empty ----

    @Test
    void rejectsEmptyAndMalformedUrls() {
        assertBlocked("", "empty");
        assertBlocked("   ", "empty");
        assertBlocked("https://exa mple.com/", "malformed");
        assertThrows(UrlSafetyException.class, () -> validator(ALLOWED, true).validate(null));
    }

    @Test
    void unresolvableHostIsRejected() {
        assertBlocked("https://unknown.example.com/", "could not be resolved");
    }
}
