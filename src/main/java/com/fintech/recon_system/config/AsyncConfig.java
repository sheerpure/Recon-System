package com.fintech.recon_system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for Asynchronous Task Execution.
 * Enables the @Async annotation and defines a dedicated thread pool
 * to handle large-scale file imports without blocking the main web threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Custom Executor for file ingestion.
     * Prevents the system from being overwhelmed by multiple large uploads.
     */
    @Bean(name = "fileImportExecutor")
    public Executor fileImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);             // Base number of concurrent threads
        executor.setMaxPoolSize(8);              // Maximum threads under high load
        executor.setQueueCapacity(500);          // Queue size for pending tasks
        executor.setThreadNamePrefix("ReconAsync-");
        executor.initialize();
        return executor;
    }
}