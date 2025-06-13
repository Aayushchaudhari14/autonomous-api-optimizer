package com.project.autonomous_api_optimizer.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.autonomous_api_optimizer.model.FlaskResult;

@Service
public class OptimizationService {

    private static final Logger logger = LoggerFactory.getLogger(OptimizationService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ThreadPoolExecutor dynamicExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Object lock = new Object(); // 🔐 Synchronization lock

    public void optimizeEndpoint(String endpoint, FlaskResult flaskResult) {
        synchronized (lock) {
            logger.info("Optimizing endpoint: {}", endpoint);
            String redisKey = "optimization:" + endpoint;

            try {
                Map<String, Double> metrics = flaskResult.getMetrics();
                if (metrics == null || !metrics.containsKey("latency")) {
                    logger.warn("Missing latency for endpoint: {}", endpoint);
                    return;
                }

                double latency = metrics.get("latency");
                boolean isAnomaly = flaskResult.isAnomaly();

                // Load previous optimization settings from Redis
                Map<String, Object> currentSettings = new HashMap<>();
                try {
                    Object raw = redisTemplate.opsForValue().get(redisKey);
                    if (raw != null) {
                        currentSettings = objectMapper.convertValue(raw, new TypeReference<Map<String, Object>>() {});
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse previous optimization settings for {}. Proceeding fresh.", endpoint);
                }

                int currentSize = dynamicExecutor.getCorePoolSize();
                int previousSize = ((Number) currentSettings.getOrDefault("current_thread_pool_size", currentSize)).intValue();
                int lastSize = ((Number) currentSettings.getOrDefault("previous_thread_pool_size", currentSize)).intValue();
                int attemptCount = ((Number) currentSettings.getOrDefault("attempt_count", 0)).intValue();

                int maxAllowed = 40;
                int minAllowed = 5;
                int stepSize = 5;
                int newSize;

                if (isAnomaly || latency > 200) {
                    attemptCount++;

                    if (attemptCount <= 6 && currentSize + stepSize <= maxAllowed) {
                        newSize = currentSize + stepSize;
                        logger.info("High latency detected. Increasing thread pool size from {} to {}", currentSize, newSize);
                    } else {
                        logger.warn("Repeated anomalies. Rolling back to previous safe size: {}", lastSize);
                        newSize = lastSize;
                        attemptCount = 0;
                    }

                } else {
                    logger.info("No anomaly detected. Reducing thread pool size gradually if needed.");
                    newSize = Math.max(minAllowed, currentSize - stepSize);
                    attemptCount = 0;
                }

                // Apply new settings
                dynamicExecutor.setCorePoolSize(newSize);

                // Store updated state
                Map<String, Object> updatedSettings = new HashMap<>();
                updatedSettings.put("previous_thread_pool_size", currentSize);
                updatedSettings.put("current_thread_pool_size", newSize);
                updatedSettings.put("attempt_count", attemptCount);
                updatedSettings.put("latency", latency);

                redisTemplate.opsForValue().set(redisKey, updatedSettings);
                logger.info("Applied dynamic optimization for {}. New pool size: {}", endpoint, newSize);

            } catch (Exception e) {
                logger.error("Optimization failed for endpoint {}: {}", endpoint, e.getMessage(), e);
            }
        }
    }
}
