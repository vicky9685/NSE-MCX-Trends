package com.nsemcx.trading.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket configuration.
 *
 * <p>Client connection flow:
 * <ol>
 *   <li>Connect to {@code /ws} (with SockJS fallback for browsers that do not support
 *       raw WebSocket).</li>
 *   <li>Subscribe to broadcast topics under {@code /topic/**} (market data, signals,
 *       alerts, dashboard, paper trades, backtest progress).</li>
 *   <li>Subscribe to user-specific queues under {@code /queue/**} for server-to-client
 *       targeted messages.</li>
 *   <li>Send messages to the server using destination prefix {@code /app}
 *       (e.g. {@code /app/subscribe}).</li>
 * </ol>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory STOMP broker for broadcast (/topic) and point-to-point (/queue)
        registry.enableSimpleBroker("/topic", "/queue");

        // Prefix for messages routed to @MessageMapping controller methods
        registry.setApplicationDestinationPrefixes("/app");

        // Prefix for messages addressed to a specific user (server → client)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // Allow all origins; tighten in production via spring.websocket.allowed-origins
                .setAllowedOriginPatterns("*")
                // SockJS fallback for browsers / environments without WebSocket support
                .withSockJS();
    }
}
