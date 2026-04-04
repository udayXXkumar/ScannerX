package com.scanner.app.queue;

import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.queue.mode", havingValue = "rabbit")
public class RabbitConfig {
    
    public static final String SCAN_QUEUE = "scannerx.scan.jobs";

    @Bean
    public Queue scanQueue() {
        return new Queue(SCAN_QUEUE, true);
    }
}
