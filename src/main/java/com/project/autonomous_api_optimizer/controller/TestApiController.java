package com.project.autonomous_api_optimizer.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;

@RestController
@RequestMapping("/api")
public class TestApiController {

    private final MeterRegistry meterRegistry;

    public TestApiController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @GetMapping("/hello")
    @Timed(value = "http_request_duration_seconds", description = "Time taken to process /api/hello")
    public String hello() {
        meterRegistry.counter("api.hello.counter").increment();
        return "Hello from API!";
    }

    @PostMapping("/process")
    @Timed(value = "http_request_duration_seconds", description = "Time taken to process /api/process")
    public String process(@RequestBody String input) throws InterruptedException {
        meterRegistry.counter("api.process.counter").increment();
        Thread.sleep(150); // Simulate 150ms delay
        return "Processed: " + input;
    }

    @PutMapping("/update")
    @Timed(value = "http_request_duration_seconds", description = "Time taken to process /api/update")
    public String update(@RequestBody String data) throws InterruptedException {
        meterRegistry.counter("api.update.counter").increment();
        Thread.sleep(500); // Simulate 500ms delay
        return "Updated: " + data;
    }

    @DeleteMapping("/delete")
    @Timed(value = "http_request_duration_seconds", description = "Time taken to process /api/delete")
    public String delete() {
        meterRegistry.counter("api.delete.counter").increment();
        return "Deleted";
    }
}