package com.edupulse.gateway.ingestion.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;

/**
 * Anti-SSRF validation for external source URLs. Checks, in order: scheme (HTTPS), no credentials
 * in the URL, domain allowlist, then every resolved IP address (localhost, private ranges,
 * link-local / cloud metadata, CGNAT, multicast).
 * <p>
 * Known limitation: validation and the actual connection resolve DNS separately (TOCTOU / DNS
 * rebinding); the domain allowlist reduces the risk and {@link SafeFetcher} re-validates redirects.
 */
@Component
public class UrlValidator {

    private static final Logger log = LoggerFactory.getLogger(UrlValidator.class);

    private final FetchPolicyProperties policy;
    private final Function<String, InetAddress[]> resolver;

    public UrlValidator(FetchPolicyProperties policy) {
        this(policy, UrlValidator::resolveAll);
    }

    /** Test constructor: lets tests inject DNS resolution. */
    UrlValidator(FetchPolicyProperties policy, Function<String, InetAddress[]> resolver) {
        this.policy = policy;
        this.resolver = resolver;
    }

    /** @return the validated URI; throws {@link UrlSafetyException} naming the blocking control */
    public URI validate(String rawUrl) {
        try {
            return doValidate(rawUrl);
        } catch (UrlSafetyException e) {
            log.warn("Blocked ingestion source '{}': {}", abbreviate(rawUrl), e.getMessage());
            throw e;
        }
    }

    private URI doValidate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new UrlSafetyException("URL is empty");
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new UrlSafetyException("URL is malformed");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean schemeOk = policy.httpsOnly()
                ? scheme.equals("https")
                : scheme.equals("https") || scheme.equals("http");
        if (!schemeOk) {
            throw new UrlSafetyException("Scheme '" + scheme + "' is not allowed (HTTPS only)");
        }
        if (uri.getRawUserInfo() != null) {
            throw new UrlSafetyException("Credentials in URL are not allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UrlSafetyException("URL has no host");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (!isAllowedDomain(host)) {
            throw new UrlSafetyException("Host '" + host + "' is not in the domain allowlist");
        }
        InetAddress[] addresses = resolver.apply(host);
        if (addresses == null || addresses.length == 0) {
            throw new UrlSafetyException("Host '" + host + "' could not be resolved");
        }
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new UrlSafetyException("Host '" + host + "' resolves to a blocked address ("
                        + address.getHostAddress() + ")");
            }
        }
        return uri;
    }

    private boolean isAllowedDomain(String host) {
        for (String allowed : policy.allowedDomains()) {
            String domain = allowed.trim().toLowerCase(Locale.ROOT);
            if (!domain.isEmpty() && (host.equals(domain) || host.endsWith("." + domain))) {
                return true;
            }
        }
        return false;
    }

    /** Addresses that must never be an ingestion source: loopback, private, link-local, metadata, etc. */
    static boolean isBlockedAddress(InetAddress address) {
        if (address instanceof Inet6Address v6 && isIpv4Mapped(v6)) {
            try {
                address = InetAddress.getByAddress(Arrays.copyOfRange(v6.getAddress(), 12, 16));
            } catch (UnknownHostException e) {
                return true;
            }
        }
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first == 0                                          // 0.0.0.0/8
                    || (first == 100 && second >= 64 && second <= 127) // CGNAT 100.64.0.0/10
                    || (first == 169 && second == 254)                 // link-local + metadata 169.254.169.254
                    || first >= 240;                                   // reserved / broadcast
        }
        // IPv6 unique local fc00::/7 (includes the AWS metadata address fd00:ec2::254)
        return (b[0] & 0xfe) == 0xfc;
    }

    private static boolean isIpv4Mapped(Inet6Address v6) {
        byte[] b = v6.getAddress();
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;
    }

    private static InetAddress[] resolveAll(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            return new InetAddress[0];
        }
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "null";
        }
        String oneLine = s.replaceAll("[\\r\\n]", " ");
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "..." : oneLine;
    }
}
