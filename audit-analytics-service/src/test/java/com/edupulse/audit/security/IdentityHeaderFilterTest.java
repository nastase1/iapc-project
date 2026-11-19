package com.edupulse.audit.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IdentityHeaderFilterTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(String tenant, String subject) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "HS256").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (tenant != null) b.claim(SecurityConfig.TENANT_CLAIM, tenant);
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(b.build(), List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @Test
    void tenantHeaderFromClientIsReplacedByTokenClaim() throws Exception {
        authenticate("tenant-a", "alice");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "tenant-b-forged");
        MockFilterChain chain = new MockFilterChain();

        new IdentityHeaderFilter().doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest seen = (HttpServletRequest) chain.getRequest();
        assertEquals("tenant-a", seen.getHeader("X-Tenant-Id"));
        assertEquals("alice", seen.getHeader("x-user-id"));
    }

    @Test
    void tenantHeaderIsInjectedWhenClientSendsNone() throws Exception {
        authenticate("tenant-a", "alice");
        MockFilterChain chain = new MockFilterChain();

        new IdentityHeaderFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertEquals("tenant-a", ((HttpServletRequest) chain.getRequest()).getHeader("X-Tenant-Id"));
    }

    @Test
    void tokenWithoutTenantIsForbidden() throws Exception {
        authenticate(null, "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new IdentityHeaderFilter().doFilter(new MockHttpServletRequest(), response, chain);

        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void unauthenticatedRequestPassesThroughUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "whatever");
        MockFilterChain chain = new MockFilterChain();

        new IdentityHeaderFilter().doFilter(request, new MockHttpServletResponse(), chain);

        // la cererile neautentificate decide AuthorizationFilter (401), filtrul nu modifica nimic
        assertEquals("whatever", ((HttpServletRequest) chain.getRequest()).getHeader("X-Tenant-Id"));
    }
}
