package com.edupulse.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DocumentIngestionApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentIngestionApplication.class, args);
    }
}
