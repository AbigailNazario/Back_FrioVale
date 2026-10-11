package com.pi.frioVale.config;

import com.pi.frioVale.security.StompAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP sobre WebSocket.
 * Conexao do cliente: ws://host:8080/ws com o header STOMP "Authorization: Bearer <token>".
 * Assinaturas: /topic/trips/{tripId}/alerts (gestor ou motorista da viagem) e /topic/alerts (gestor).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOrigins;
    private final StompAuthChannelInterceptor authInterceptor;

    public WebSocketConfig(@Value("${friovale.cors.allowed-origins:*}") String[] allowedOrigins,
                           StompAuthChannelInterceptor authInterceptor) {
        this.allowedOrigins = allowedOrigins;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
