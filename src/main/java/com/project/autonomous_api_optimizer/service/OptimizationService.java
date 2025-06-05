package com.project.autonomous_api_optimizer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class OptimizationService {

    private static final Logger logger = LoggerFactory.getLogger(OptimizationService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void optimizeEndpoint(String endpoint, Map<String, Object> flaskResult) {
        logger.info("Starting optimization for endpoint: {}", endpoint);
        String redisKey = endpoint + ":" + endpoint.hashCode();
        try {
            Map<String, Object> settings = new HashMap<>();
            Boolean isAnomaly = (Boolean) flaskResult.get("is_anomaly");
            Double originalLatency = flaskResult.containsKey("latency") ? ((Number) flaskResult.get("latency")).doubleValue() : null;

            if (originalLatency == null) {
                logger.warn("No latency provided in flaskResult for endpoint: {}. Skipping optimization.", endpoint);
                return;
            }

            // Apply optimization based on anomaly
            if (Boolean.TRUE.equals(isAnomaly)) {
                settings.put("thread_pool_size", 20);
                settings.put("cache_ttl", 300);
                logger.info("Applied high-latency optimization for {}: cache_ttl=300s, thread_pool_size=20", endpoint);
            } else {
                settings.put("thread_pool_size", 10);
                settings.put("cache_ttl", 60);
                logger.info("Applied default optimization for {}: cache_ttl=60s, thread_pool_size=10", endpoint);
            }

            // Store optimized settings
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(settings));
            logger.debug("Stored optimization settings in Redis for key: {}", redisKey);

            // Simulate checking current latency (in practice, this could be delayed)
            // For simplicity, assume Flask provides current latency post-optimization
            Double currentLatency = flaskResult.containsKey("latency") ? ((Number) flaskResult.get("latency")).doubleValue() : originalLatency;

            // Rollback logic: revert if currentLatency >= originalLatency
            if (currentLatency >= originalLatency) {
                logger.info("Rollback triggered for {}: currentLatency={}ms >= originalLatency={}ms", endpoint, currentLatency, originalLatency);
                settings.put("thread_pool_size", 10);
                settings.put("cache_ttl", 60);
                redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(settings));
                logger.info("Rolled back to default settings for {}: cache_ttl=60s, thread_pool_size=10", endpoint);
            } else {
                logger.info("No rollback needed for {}: currentLatency={}ms < originalLatency={}ms", endpoint, currentLatency, originalLatency);
            }

            logger.info("Optimization completed successfully for endpoint: {}", endpoint);
        } catch (Exception e) {
            logger.error("Error during optimization for endpoint: {}. Error: {}", endpoint, e.getMessage(), e);
        }
    }
}
