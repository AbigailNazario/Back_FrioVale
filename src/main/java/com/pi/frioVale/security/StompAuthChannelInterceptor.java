package com.pi.frioVale.security;

import com.pi.frioVale.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seguranca do STOMP:
 * - CONNECT exige o header "Authorization: Bearer <jwt>";
 * - SUBSCRIBE: /topic/alerts so para gestor; /topic/trips/{id}/alerts para gestor ou o motorista dono da viagem;
 * - SEND de clientes e bloqueado (so o servidor publica, evita alertas forjados).
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern TRIP_ALERTS = Pattern.compile("^/topic/trips/(\\d+)/alerts$");

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final TripService tripService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscription(accessor);
        } else if (StompCommand.SEND.equals(command)) {
            throw new AccessDeniedException("Clientes nao podem publicar mensagens");
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new AccessDeniedException("Token ausente");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring("Bearer ".length()).trim());
            accessor.setUser(jwtAuthenticationConverter.convert(jwt));
        } catch (JwtException ex) {
            throw new AccessDeniedException("Token invalido ou expirado");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (!(principal instanceof JwtAuthenticationToken auth)) {
            throw new AccessDeniedException("Nao autenticado");
        }
        boolean company = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_COMPANY".equals(a.getAuthority()));
        Long userId = Long.valueOf(auth.getToken().getSubject());
        String destination = accessor.getDestination() == null ? "" : accessor.getDestination();

        if ("/topic/alerts".equals(destination)) {
            if (!company) {
                throw new AccessDeniedException("Apenas o gestor pode assinar todos os alertas");
            }
            return;
        }

        Matcher matcher = TRIP_ALERTS.matcher(destination);
        if (matcher.matches()) {
            Long tripId = Long.valueOf(matcher.group(1));
            if (!tripService.canAccessTrip(tripId, userId, company)) {
                throw new AccessDeniedException("Sem acesso a esta viagem");
            }
            return;
        }
        throw new AccessDeniedException("Destino nao permitido");
    }
}
