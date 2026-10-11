package com.pi.frioVale.security;

import com.pi.frioVale.config.JwtProperties;
import com.pi.frioVale.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    public record IssuedToken(String token, Instant expiresAt) {
    }

    private final JwtEncoder jwtEncoder;
    private final JwtProperties props;
    private final Clock clock;

    public IssuedToken issue(User user) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(Duration.ofMinutes(props.expirationMinutes()));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("friovale")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(String.valueOf(user.getId()))
                .claim("name", user.getName())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .build();

        // O algoritmo precisa ser informado, senao o encoder tenta RS256 e falha com chave HMAC
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
