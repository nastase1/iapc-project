package com.edupulse.gateway.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Securitate stateless la nivel de API Gateway: validare JWT (HS256) + RBAC.
 * Rolurile sunt citite din claim-ul {@code roles} (ex. {@code ROLE_ADMIN}, {@code ROLE_COMPLIANCE_OFFICER}).
 */
@Configuration
public class SecurityConfig {

    public static final String ROLES_CLAIM = "roles";
    public static final String TENANT_CLAIM = "tenant_id";
    public static final String ORG_UNIT_CLAIM = "org_unit";

    private static final String ADMIN = "ADMIN";
    private static final String COMPLIANCE_OFFICER = "COMPLIANCE_OFFICER";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Autentificare & infrastructura
                        .requestMatchers(HttpMethod.POST, "/api/auth/token").permitAll()
                        // Eureka: inregistrarea celorlalte 2 microservicii + dashboard (doar retea interna!)
                        .requestMatchers("/eureka/**", "/eureka-ui/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Microserviciul 1 - ingestie documente
                        .requestMatchers("/api/ingestion/**").hasAnyRole(ADMIN, COMPLIANCE_OFFICER)
                        // Microserviciul 2 - RAG
                        .requestMatchers("/api/rag/**").hasAnyRole(ADMIN, COMPLIANCE_OFFICER)
                        // Microserviciul 3 - Gap Analysis & Audit Trail
                        .requestMatchers("/api/gap-analysis/**").hasAnyRole(ADMIN, COMPLIANCE_OFFICER)
                        .requestMatchers("/api/audit/**").hasRole(ADMIN)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties) {
        return NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(SecurityProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLES_CLAIM);
        authorities.setAuthorityPrefix("");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static SecretKey secretKey(SecurityProperties properties) {
        byte[] bytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("edupulse.security.jwt.secret must have at least 32 bytes for HS256");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
