package com.project.autonomous_api_optimizer.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.autonomous_api_optimizer.model.FlaskResult;

@Service
public class OptimizationService {

    private static final Logger logger = LoggerFactory.getLogger(OptimizationService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void optimizeEndpoint(String endpoint, FlaskResult flaskResult) {
        logger.info("Starting optimization for endpoint: {}", endpoint);
        String redisKey = endpoint + ":" + endpoint.hashCode();

        try {
            Map<String, Double> metrics = flaskResult.getMetrics();

            if (metrics == null || !metrics.containsKey("latency")) {
                logger.warn("Latency is missing in metrics for endpoint: {}. Full metrics: {}", endpoint, metrics);
                return;
            }

            Double latency = metrics.get("latency");
            boolean isAnomaly = flaskResult.isAnomaly();

            Map<String, Object> settings = new HashMap<>();
            if (isAnomaly) {
                settings.put("thread_pool_size", 20);
                settings.put("cache_ttl", 300);
                logger.info("Anomaly detected. Applying optimized settings for {} (latency: {} ms)", endpoint, latency);
            } else {
                settings.put("thread_pool_size", 10);
                settings.put("cache_ttl", 60);
                logger.info("No anomaly detected. Applying default settings for {} (latency: {} ms)", endpoint, latency);
            }

            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(settings));
            logger.info("Optimization completed and settings stored in Redis for endpoint: {}", endpoint);

        } catch (Exception e) {
            logger.error("Error optimizing endpoint: {}: {}", endpoint, e.getMessage(), e);
        }
    }
}
