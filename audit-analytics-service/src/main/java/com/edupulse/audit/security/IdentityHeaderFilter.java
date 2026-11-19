package com.edupulse.audit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Dupa validarea JWT, suprascrie X-Tenant-Id si X-User-Id cu valorile din token. Controllerele citesc
 * aceste headere, deci tenantul unei cereri autentificate nu poate fi niciodata cel trimis de client.
 * Un token fara claim-ul de tenant este refuzat (403).
 */
public class IdentityHeaderFilter extends OncePerRequestFilter {

    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String USER_HEADER = "X-User-Id";

    private static final Logger log = LoggerFactory.getLogger(IdentityHeaderFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            chain.doFilter(request, response);
            return;
        }
        String tenant = jwtAuth.getToken().getClaimAsString(SecurityConfig.TENANT_CLAIM);
        if (tenant == null || tenant.isBlank()) {
            log.warn("rejected token without tenant claim subject={}", jwtAuth.getToken().getSubject());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Token has no tenant");
            return;
        }
        Map<String, String> overrides = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        overrides.put(TENANT_HEADER, tenant);
        String subject = jwtAuth.getToken().getSubject();
        if (subject != null) overrides.put(USER_HEADER, subject);
        chain.doFilter(new OverriddenHeadersRequest(request, overrides), response);
    }

    private static final class OverriddenHeadersRequest extends HttpServletRequestWrapper {

        private final Map<String, String> overrides;

        OverriddenHeadersRequest(HttpServletRequest request, Map<String, String> overrides) {
            super(request);
            this.overrides = overrides;
        }

        @Override
        public String getHeader(String name) {
            String v = overrides.get(name);
            return v != null ? v : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String v = overrides.get(name);
            return v != null ? Collections.enumeration(Set.of(v)) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            names.addAll(overrides.keySet());
            return Collections.enumeration(names);
        }
    }
}
