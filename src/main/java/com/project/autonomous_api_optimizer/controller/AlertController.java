package com.project.autonomous_api_optimizer.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

@RestController
public class AlertController {
    private static final Logger logger = LoggerFactory.getLogger(AlertController.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String flaskUrl = "http://flask-anomaly-detection:5000/detect-anomaly";// or use container name if in Docker network

    @PostMapping("/alert-webhook")
    public ResponseEntity<String> handleAlert(@RequestBody String alertPayload) {
        logger.info("🚨 Received alert from Alertmanager: {}", alertPayload);

        try {
            // Send POST to Flask ML model
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(alertPayload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(flaskUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();

                boolean isAnomaly = (Boolean) body.getOrDefault("is_anomaly", false);
                String reason = (String) body.getOrDefault("reason", "No reason provided");

                logger.info("Anomaly Detection Response: is_anomaly={}, reason={}", isAnomaly, reason);

                //  Apply backend optimization if anomaly is true
                if (isAnomaly) {
                    performOptimization(reason);
                }

                return ResponseEntity.ok("Alert processed. Anomaly: " + isAnomaly + ", Reason: " + reason);
            } else {
                logger.warn("ML model returned non-2xx or null body: {}", response.getStatusCode());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Failed to get a valid response from ML model.");
            }

        } catch (Exception e) {
            logger.error("Exception in processing alert: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Exception: " + e.getMessage());
        }
    }

    // Placeholder for actual optimization logic
    private void performOptimization(String reason) {
        logger.info(" Performing optimization based on reason: {}", reason);
        // TODO: Add logic to scale services, restart components, adjust parameters, etc.
    }
}

//@RestController
//public class AlertController {
//    private static final Logger logger = LoggerFactory.getLogger(AlertController.class);
//
//    @PostMapping("/alert-webhook")
//    public void handleAlert(@RequestBody String alertPayload) {
//        logger.info("Received alert from Alertmanager: {}", alertPayload);
//        // Add custom logic, e.g., store alert, trigger action
//    }
//}