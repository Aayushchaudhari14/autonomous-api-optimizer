package com.project.autonomous_api_optimizer.config;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public Map<String, Bucket> rateLimiters() {
        Map<String, Bucket> limiters = new ConcurrentHashMap<>();
        String[] endpoints = {"/api/hello", "/api/process", "/api/update", "/api/delete"};
        for (String endpoint : endpoints) {
            Bandwidth limit = Bandwidth.classic(100, Refill.greedy(100, Duration.ofSeconds(1)));
            limiters.put(endpoint, Bucket4j.builder().addLimit(limit).build());
        }
        return limiters;
    }
}