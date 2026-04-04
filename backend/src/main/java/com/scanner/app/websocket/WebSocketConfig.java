package com.scanner.app.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.stream.Stream;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOrigins;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(
            @Value("${app.cors.allowed-origins:}") String allowedOrigins,
            @Value("${app.cors.allowed-origin-patterns:}") String allowedOriginPatterns
    ) {
        this.allowedOrigins = splitCsv(allowedOrigins);
        this.allowedOriginPatterns = splitCsv(allowedOriginPatterns);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var registration = registry.addEndpoint("/ws/scans");
        if (allowedOrigins.length > 0) {
            registration.setAllowedOrigins(allowedOrigins);
        }
        if (allowedOriginPatterns.length > 0) {
            registration.setAllowedOriginPatterns(allowedOriginPatterns);
        }
        registration.withSockJS();
    }

    private static String[] splitCsv(String rawValue) {
        return Stream.ofNullable(rawValue)
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }
}
