package com.project.autonomous_api_optimizer.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/health/redis")
    public String checkRedisHealth() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            logger.info("Redis connection successful");
            return "Redis is healthy";
        } catch (RedisConnectionFailureException e) {
            logger.error("Redis connection failed: {}", e.getMessage(), e);
            return "Redis is unavailable: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error checking Redis: {}", e.getMessage(), e);
            return "Redis check failed: " + e.getMessage();
        }
    }
}