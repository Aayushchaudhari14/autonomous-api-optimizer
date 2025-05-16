package com.project.autonomous_api_optimizer.controller;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TestApiController {

    private final MeterRegistry meterRegistry;

    public TestApiController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @GetMapping("/hello")
    public String hello() {
        meterRegistry.counter("api.hello.counter").increment();
        return "Hello from API!";
    }

    @PostMapping("/process")
    public String process(@RequestBody String input) {
        meterRegistry.counter("api.process.counter").increment();
        return "Processed: " + input;
    }

    @PutMapping("/update")
    public String update(@RequestBody String data) {
        meterRegistry.counter("api.update.counter").increment();
        return "Updated: " + data;
    }

    @DeleteMapping("/delete")
    public String delete() {
        meterRegistry.counter("api.delete.counter").increment();
        return "Deleted";
    }
}
