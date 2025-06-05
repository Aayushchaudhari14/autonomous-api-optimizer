package com.project.autonomous_api_optimizer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import com.project.autonomous_api_optimizer.service.OptimizationService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class AlertController {

    private static final Logger logger = LoggerFactory.getLogger(AlertController.class);
    private final RestTemplate restTemplate;
    private final OptimizationService optimizationService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AlertController(RestTemplate restTemplate, OptimizationService optimizationService, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.optimizationService = optimizationService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/alert-webhook")
    public ResponseEntity<String> handleAlert(@RequestBody Map<String, Object> alertData) {
        try {
            logger.info("Received alert from Alertmanager: {}", alertData);
            String status = (String) alertData.get("status");
            if (!"firing".equals(status)) {
                logger.info("Alert status is not 'firing', skipping: {}", status);
                return ResponseEntity.ok("Alert not firing, skipped");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> alerts = (List<Map<String, Object>>) alertData.get("alerts");
            for (Map<String, Object> alert : alerts) {
                @SuppressWarnings("unchecked")
                Map<String, String> labels = (Map<String, String>) alert.get("labels");
                String endpoint = labels.get("endpoint");
                String alertName = labels.get("alertname");

                logger.info("Processing alert for endpoint: {}", endpoint);

                // Call Flask API
                String flaskUrl = "http://flask-anomaly-detection:5000/detect-anomaly";
                Map<String, String> flaskRequest = new HashMap<>();
                flaskRequest.put("endpoint", endpoint);
                flaskRequest.put("alertname", alertName);
                ResponseEntity<String> flaskResponse = restTemplate.postForEntity(flaskUrl, flaskRequest, String.class);

                logger.info("Flask API response for endpoint {}: {}", endpoint, flaskResponse.getBody());

                // Parse Flask response
                Map<String, Object> flaskResult = objectMapper.readValue(flaskResponse.getBody(), Map.class);
                Boolean isAnomaly = (Boolean) flaskResult.get("is_anomaly");
                if (Boolean.TRUE.equals(isAnomaly)) {
                    logger.info("Triggering optimization for endpoint: {}", endpoint);
                    optimizationService.optimizeEndpoint("/api/" + endpoint, flaskResult);
                    logger.info("Optimization invoked for endpoint: {}", endpoint);
                } else {
                    logger.info("No anomaly detected for endpoint: {}, skipping optimization", endpoint);
                }
            }
            return ResponseEntity.ok("Alert processed");
        } catch (Exception e) {
            logger.error("Error processing alert: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing alert");
        }
    }
}