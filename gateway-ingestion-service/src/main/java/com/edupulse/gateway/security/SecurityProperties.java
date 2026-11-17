package com.edupulse.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configurare JWT si utilizatori demo ({@code edupulse.security.*}).
 */
@ConfigurationProperties(prefix = "edupulse.security")
public record SecurityProperties(Jwt jwt, List<DemoUser> demoUsers) {

    public SecurityProperties {
        demoUsers = demoUsers == null ? List.of() : List.copyOf(demoUsers);
    }

    /**
     * @param secret cheie HMAC-SHA256 (minim 32 de octeti)
     * @param ttl    durata de viata a token-ului
     * @param issuer emitentul token-ului
     */
    public record Jwt(String secret, Duration ttl, String issuer) {
    }

    /**
     * Utilizator demo pentru dezvoltare locala.
     * TODO (Membru 1): inlocuire cu un user store real (tabela users / IdP extern).
     */
    public record DemoUser(String username, String password, String tenantId, String orgUnit, List<String> roles) {
    }
}
