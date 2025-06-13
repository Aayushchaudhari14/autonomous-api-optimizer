package com.project.autonomous_api_optimizer.controller;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.project.autonomous_api_optimizer.service.ApiService;

import io.github.bucket4j.Bucket;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;

@RestController
@RequestMapping("/api")
public class TestApiController {

    private final MeterRegistry meterRegistry;
    private final ApiService apiService;
    private final Map<String, Bucket> rateLimiters;
    private final ThreadPoolExecutor dynamicExecutor;
    private static final Logger logger = LoggerFactory.getLogger(TestApiController.class);

    @Autowired
    public TestApiController(MeterRegistry meterRegistry,
                             ApiService apiService,
                             Map<String, Bucket> rateLimiters,
                             ThreadPoolExecutor dynamicExecutor) {
        this.meterRegistry = meterRegistry;
        this.apiService = apiService;
        this.rateLimiters = rateLimiters;
        this.dynamicExecutor = dynamicExecutor;
    }

    @GetMapping("/hello")
    @Timed(value = "http_server_requests_seconds", description = "Time taken to process /api/hello")
    public CompletableFuture<String> hello() {
        checkRateLimit("/api/hello");
        meterRegistry.counter("api.hello.counter").increment();

        return CompletableFuture.supplyAsync(() ->
                apiService.processRequest("/api/hello", "hello"), dynamicExecutor);
    }

    @PostMapping("/process")
    @Timed(value = "http_server_requests_seconds", description = "Time taken to process /api/process")
    public CompletableFuture<String> process(@RequestBody String input) {
        checkRateLimit("/api/process");
        meterRegistry.counter("api.process.counter").increment();

        String cachedResult = apiService.getFromCache("/api/process", input);
        if (cachedResult != null) {
            logger.info("Cache hit for /api/process with input hash: {}", input.hashCode());
            return CompletableFuture.completedFuture(cachedResult);
        } else {
            logger.info("Cache miss for /api/process with input hash: {}", input.hashCode());
        }

        try {
            Thread.sleep(150); // Simulate delay only on cache miss
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return apiService.processAndCacheAsync("/api/process", input);
    }

    @PutMapping("/update")
    @Timed(value = "http_server_requests_seconds", description = "Time taken to process /api/update")
    public CompletableFuture<String> update(@RequestBody String data) {
        checkRateLimit("/api/update");
        meterRegistry.counter("api.update.counter").increment();

        String cachedResult = apiService.getFromCache("/api/update", data);
        if (cachedResult != null) {
            logger.info("Cache hit for /api/update with input hash: {}", data.hashCode());
            return CompletableFuture.completedFuture(cachedResult);
        } else {
            logger.info("Cache miss for /api/update with input hash: {}", data.hashCode());
        }

        try {
            Thread.sleep(300); // Simulate delay only on cache miss
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return apiService.processAndCacheAsync("/api/update", data);
    }


    @DeleteMapping("/delete")
    @Timed(value = "http_server_requests_seconds", description = "Time taken to process /api/delete")
    public CompletableFuture<String> delete() {
        checkRateLimit("/api/delete");
        meterRegistry.counter("api.delete.counter").increment();

        return CompletableFuture.supplyAsync(() ->
                apiService.processRequest("/api/delete", "delete"), dynamicExecutor);
    }

    private void checkRateLimit(String endpoint) {
        Bucket bucket = rateLimiters.get(endpoint);
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded for " + endpoint);
        }
    }
}




