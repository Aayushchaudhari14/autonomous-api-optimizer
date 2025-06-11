package com.project.autonomous_api_optimizer.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.autonomous_api_optimizer.model.FlaskResult;
import com.project.autonomous_api_optimizer.service.OptimizationService;

@RestController
public class AlertController {

    private static final Logger logger = LoggerFactory.getLogger(AlertController.class);

    private final RestTemplate restTemplate;
    private final OptimizationService optimizationService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AlertController(RestTemplate restTemplate,
                           OptimizationService optimizationService,
                           ObjectMapper objectMapper) {
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

                String flaskUrl = "http://flask-anomaly-detection:5000/detect-anomaly";

                Map<String, String> flaskRequest = new HashMap<>();
                flaskRequest.put("endpoint", endpoint);
                flaskRequest.put("alertname", alertName);

                // ✅ Request Flask and get response as raw JSON string
                ResponseEntity<String> flaskResponse = restTemplate.postForEntity(flaskUrl, flaskRequest, String.class);

                String json = flaskResponse.getBody();
                FlaskResult flaskResult = objectMapper.readValue(json, FlaskResult.class);  // ✅ Safe


                if (flaskResult.isAnomaly()) {
                    logger.info("Triggering optimization for endpoint: {}", endpoint);
                    optimizationService.optimizeEndpoint("/api/" + endpoint, flaskResult);
                } else {
                    logger.info("No anomaly detected for endpoint: {}", endpoint);
                }
            }

            return ResponseEntity.ok("Alert processed");

        } catch (Exception e) {
            logger.error("Exception while processing alert", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error");
        }
    }
}
