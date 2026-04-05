package com.scanner.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class FindingEnrichmentConfig {

    @Bean(name = "findingEnrichmentExecutor")
    public Executor findingEnrichmentExecutor(
            @Value("${app.ai.finding-enrichment.concurrency:2}") int concurrency
    ) {
        int normalizedConcurrency = Math.max(1, concurrency);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("finding-ai-");
        executor.setCorePoolSize(normalizedConcurrency);
        executor.setMaxPoolSize(normalizedConcurrency);
        executor.setQueueCapacity(Math.max(8, normalizedConcurrency * 8));
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
