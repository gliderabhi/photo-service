package com.sevis.photoservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    // Face detection (network call to face-service + CPU-bound HOG detection there)
    // must never block the upload response — this is the executor @Async methods
    // run on. Small pool: this is a single-user personal system, not a fleet.
    @Bean(name = "faceDetectionExecutor")
    public Executor faceDetectionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("face-detect-");
        executor.initialize();
        return executor;
    }
}
