package com.edupulse.gateway.routing;

import com.edupulse.gateway.security.SecurityConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.stream.Collectors;

import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

/**
 * Rutare dinamica pe baza Service ID inregistrat in Eureka ({@code lb://...}).
 * Endpoint-urile {@code /api/ingestion/**} si {@code /api/auth/**} sunt servite local de acest microserviciu.
 * <p>
 * Contextul de tenancy este extras din JWT si propagat catre microserviciile din spate prin headere
 * (orice valoare trimisa de client pentru aceste headere este suprascrisa).
 */
@Configuration
public class GatewayRoutesConfig {

    public static final String RAG_QUERY_SERVICE = "rag-query-service";
    public static final String AUDIT_ANALYTICS_SERVICE = "audit-analytics-service";

    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String ORG_UNIT_HEADER = "X-Org-Unit";
    public static final String USER_HEADER = "X-User-Id";
    public static final String ROLES_HEADER = "X-User-Roles";

    @Bean
    RouterFunction<ServerResponse> ragQueryRoute() {
        return route(RAG_QUERY_SERVICE)
                .route(RequestPredicates.path("/api/rag/**"), http())
                .before(GatewayRoutesConfig::propagateIdentity)
                .filter(lb(RAG_QUERY_SERVICE))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> auditAnalyticsRoute() {
        return route(AUDIT_ANALYTICS_SERVICE)
                .route(RequestPredicates.path("/api/gap-analysis/**")
                        .or(RequestPredicates.path("/api/audit/**")), http())
                .before(GatewayRoutesConfig::propagateIdentity)
                .filter(lb(AUDIT_ANALYTICS_SERVICE))
                .build();
    }

    private static ServerRequest propagateIdentity(ServerRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return ServerRequest.from(request)
                .headers(headers -> {
                    headers.remove(TENANT_HEADER);
                    headers.remove(ORG_UNIT_HEADER);
                    headers.remove(USER_HEADER);
                    headers.remove(ROLES_HEADER);
                    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                        var jwt = jwtAuth.getToken();
                        setIfPresent(headers, TENANT_HEADER, jwt.getClaimAsString(SecurityConfig.TENANT_CLAIM));
                        setIfPresent(headers, ORG_UNIT_HEADER, jwt.getClaimAsString(SecurityConfig.ORG_UNIT_CLAIM));
                        setIfPresent(headers, USER_HEADER, jwt.getSubject());
                        setIfPresent(headers, ROLES_HEADER, jwtAuth.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .collect(Collectors.joining(",")));
                    }
                })
                .build();
    }

    private static void setIfPresent(org.springframework.http.HttpHeaders headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.set(name, value);
        }
    }
}
