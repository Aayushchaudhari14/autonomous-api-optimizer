package com.project.autonomous_api_optimizer.controller;

import com.project.autonomous_api_optimizer.service.ApiService;
import io.github.bucket4j.Bucket;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class TestApiController {

    private final MeterRegistry meterRegistry;
    private final ApiService apiService;
    private final Map<String, Bucket> rateLimiters;

    @Autowired
    public TestApiController(MeterRegistry meterRegistry, ApiService apiService, Map<String, Bucket> rateLimiters) {
        this.meterRegistry = meterRegistry;
        this.apiService = apiService;
        this.rateLimiters = rateLimiters;
    }

    @GetMapping("/hello")
    @Timed(value = "http_server_requests_seconds", description = "Time taken to process /api/hello")
    public String hello() {
        checkRateLimit("/api/hello");
        meterRegistry.counter("api.hello.counter").increment();
        return apiService.processRequest("/api/hello", "hello");
    }

    // @PostMapping("/process")
    // @Timed(value = "http_server_requests_seconds", description = "Time taken to process /api/process")
    // public String process(@RequestBody String input) throws InterruptedException {
    //     checkRateLimit("/api/process");
    //     meterRegistry.counter("api.process.counter").increment();
    //     Thread.sleep(150);
    //     return apiService.processRequest("/api/process", input);
    // }
    @PostMapping("/process")
    public String process(@RequestBody String input) throws InterruptedException {
        checkRateLimit("/api/process");
        meterRegistry.counter("api.process.counter").increment();

        String cachedResult = apiService.getFromCache("/api/process", input);
        if (cachedResult != null) {
            return cachedResult;
        }

        Thread.sleep(150); // Simulate processing only on cache miss
        return apiService.processAndCache("/api/process", input);
    }


    // @PutMapping("/update")
    // @Timed(value = "http_server_requests_seconds", description = "Time taken to process /api/update")
    // public String update(@RequestBody String data) throws InterruptedException {
    //     checkRateLimit("/api/update");
    //     meterRegistry.counter("api.update.counter").increment();
    //     Thread.sleep(500);
    //     return apiService.processRequest("/api/update", data);
    // }
    @PutMapping("/update")
    public String update(@RequestBody String data) throws InterruptedException {
        checkRateLimit("/api/update");
        meterRegistry.counter("api.update.counter").increment();

        String cachedResult = apiService.getFromCache("/api/update", data);
        if (cachedResult != null) {
            return cachedResult;
        }

        Thread.sleep(500); // Simulate delay only if no cache
        return apiService.processAndCache("/api/update", data);
    }


    @DeleteMapping("/delete")
    @Timed(value = "http_server_requests_seconds", description = "Time taken to process /api/delete")
    public String delete() {
        checkRateLimit("/api/delete");
        meterRegistry.counter("api.delete.counter").increment();
        return apiService.processRequest("/api/delete", "delete");
    }

    private void checkRateLimit(String endpoint) {
        Bucket bucket = rateLimiters.get(endpoint);
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded for " + endpoint);
        }
    }
}