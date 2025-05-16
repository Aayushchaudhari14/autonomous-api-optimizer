package com.project.autonomous_api_optimizer.controllers;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AlertController {
    private static final Logger logger = LoggerFactory.getLogger(AlertController.class);

    @PostMapping("/alert-webhook")
    public void handleAlert(@RequestBody String alertPayload) {
        logger.info("Received alert from Alertmanager: {}", alertPayload);
        // Add custom logic, e.g., store alert, trigger action
    }
}
