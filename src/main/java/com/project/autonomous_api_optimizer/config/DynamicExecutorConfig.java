// File: config/DynamicExecutorConfig.java
package com.project.autonomous_api_optimizer.config;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DynamicExecutorConfig {

    private volatile ThreadPoolExecutor executor;

    @Bean
    public ThreadPoolExecutor dynamicExecutor() {
        if (executor == null) {
            synchronized (this) {
                if (executor == null) {
                    executor = new ThreadPoolExecutor(
                            10,  // core pool size
                            40,  // max pool size
                            60L, TimeUnit.SECONDS,
                            new java.util.concurrent.LinkedBlockingQueue<>()
                    );
                }
            }
        }
        return executor;
    }
}
