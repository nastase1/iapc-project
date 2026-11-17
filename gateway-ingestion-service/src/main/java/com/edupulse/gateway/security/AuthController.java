package com.edupulse.gateway.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;

/**
 * Emitere token-uri JWT pentru utilizatorii demo.
 * TODO (Membru 1): autentificare pe baza unui user store real (parole hash-uite / IdP extern).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;

    public AuthController(JwtEncoder jwtEncoder, SecurityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public record TokenRequest(String username, String password) {
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> token(@RequestBody TokenRequest request) {
        return properties.demoUsers().stream()
                .filter(u -> u.username().equals(request.username()) && passwordMatches(u.password(), request.password()))
                .findFirst()
                .<ResponseEntity<Map<String, Object>>>map(user -> ResponseEntity.ok(issueToken(user)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "invalid_credentials")));
    }

    private Map<String, Object> issueToken(SecurityProperties.DemoUser user) {
        Instant now = Instant.now();
        var jwt = properties.jwt();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwt.issuer())
                .subject(user.username())
                .issuedAt(now)
                .expiresAt(now.plus(jwt.ttl()))
                .claim(SecurityConfig.ROLES_CLAIM, user.roles())
                .claim(SecurityConfig.TENANT_CLAIM, user.tenantId())
                .claim(SecurityConfig.ORG_UNIT_CLAIM, user.orgUnit())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return Map.of(
                "access_token", token,
                "token_type", "Bearer",
                "expires_in", jwt.ttl().toSeconds());
    }

    private static boolean passwordMatches(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
