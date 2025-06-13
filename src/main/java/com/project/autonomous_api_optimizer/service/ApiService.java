package com.project.autonomous_api_optimizer.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ApiService {

    private static final Logger logger = LoggerFactory.getLogger(ApiService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    @Qualifier("dynamicExecutor")
    private ThreadPoolExecutor dynamicExecutor;

    public String processRequest(String endpoint, String input) {
        String cacheKey = endpoint + ":" + input.hashCode();
        try {
            logger.debug("Attempting to connect to Redis for key: {}", cacheKey);
            String cachedResult = (String) redisTemplate.opsForValue().get(cacheKey);
            if (cachedResult != null) {
                logger.info("Cache hit for key: {}", cacheKey);
                return cachedResult;
            }
            logger.info("Cache miss for key: {}", cacheKey);
        } catch (RedisConnectionFailureException e) {
            logRedisConnectionFailure(cacheKey, e);
        } catch (DataAccessException e) {
            logger.error("DataAccessException accessing Redis for key: {}. Error: {}", cacheKey, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error accessing Redis for key: {}. Error: {}", cacheKey, e.getMessage(), e);
        }

        logger.info("Processing request for key: {}", cacheKey);
        try {
            Future<String> future = dynamicExecutor.submit(() -> simulateProcessing(endpoint, input));
            String result = future.get(); // synchronous call
            redisTemplate.opsForValue().set(cacheKey, result);
            logger.debug("Cached result for key: {}", cacheKey);
            return result;
        } catch (Exception e) {
            logger.error("Error during processing with executor: {}", e.getMessage(), e);
            return "Processing failed for " + endpoint;
        }
    }

    public void invalidateCache(String endpoint) {
        try {
            logger.info("Invalidating cache for endpoint: {}", endpoint);
            redisTemplate.delete(redisTemplate.keys(endpoint + ":*"));
            logger.debug("Cache invalidated for endpoint: {}", endpoint);
        } catch (RedisConnectionFailureException e) {
            logRedisConnectionFailure(endpoint, e);
        } catch (Exception e) {
            logger.error("Unexpected error invalidating cache for endpoint: {}. Error: {}", endpoint, e.getMessage(), e);
        }
    }

    private void logRedisConnectionFailure(String keyContext, RedisConnectionFailureException e) {
        String host = "unknown";
        int port = -1;
        if (redisTemplate.getConnectionFactory() instanceof LettuceConnectionFactory) {
            LettuceConnectionFactory factory = (LettuceConnectionFactory) redisTemplate.getConnectionFactory();
            host = factory.getHostName();
            port = factory.getPort();
        }
        logger.warn("Redis unavailable for context: {}. Host: {}, Port: {}, Error: {}",
                keyContext, host, port, e.getMessage(), e);
    }

    private String simulateProcessing(String endpoint, String input) {
                // try {
                //     // Simulate latency
                //     Thread.sleep(200);
                // } catch (InterruptedException ignored) {}
        return "Processed " + endpoint + ": " + input;
    }

    public String getFromCache(String endpoint, String input) {
        String cacheKey = endpoint + ":" + input.hashCode();
        try {
            return (String) redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            return null;
        }
    }

    // public String processAndCache(String endpoint, String input) {
    //     String result = simulateProcessing(endpoint, input);
    //     String cacheKey = endpoint + ":" + input.hashCode();
    //     try {
    //         redisTemplate.opsForValue().set(cacheKey, result);
    //     } catch (Exception ignored) {}
    //     return result;
    // }


    public CompletableFuture<String> processAndCacheAsync(String endpoint, String input) {
        return CompletableFuture.supplyAsync(() -> {
            String result = simulateProcessing(endpoint, input);
            String cacheKey = endpoint + ":" + input.hashCode();
            try {
                redisTemplate.opsForValue().set(cacheKey, result);
            } catch (Exception ignored) {}
            return result;
        }, dynamicExecutor);
    }

}
