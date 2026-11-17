package com.edupulse.audit.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Comunicare inter-servicii prin Eureka: URL-urile de forma {@code http://<service-id>/...}
 * sunt rezolvate prin Spring Cloud LoadBalancer.
 * <p>
 * {@code defaultCandidate = false}: builder-ul este injectat doar in punctele marcate cu {@link LoadBalanced},
 * altfel ar fi preluat si de clientul Eureka (care trebuie sa apeleze direct serverul de discovery).
 */
@Configuration
public class RestClientConfig {

    @Bean(defaultCandidate = false)
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
