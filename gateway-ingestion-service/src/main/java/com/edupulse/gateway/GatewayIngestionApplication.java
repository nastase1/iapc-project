package com.edupulse.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Microserviciul 1 (Membru 1): Infrastructura Cloud, Securitate &amp; Ingestion Asincron.
 * <ul>
 *     <li>Eureka Server embedded (registry pentru celelalte 2 microservicii) - {@code /eureka}</li>
 *     <li>API Gateway (Spring Cloud Gateway Server MVC) cu rutare {@code lb://} - vezi {@code routing}</li>
 *     <li>Securitate stateless JWT + RBAC - vezi {@code security}</li>
 *     <li>Document Ingestion Pipeline (PDF/DOCX/TXT, async, hierarchical chunking) - vezi {@code ingestion}</li>
 * </ul>
 */
@SpringBootApplication
@EnableEurekaServer
@EnableAsync
@ConfigurationPropertiesScan
public class GatewayIngestionApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayIngestionApplication.class, args);
    }
}
