package org.demchenko.auth.security;

import lombok.RequiredArgsConstructor;
import org.demchenko.auth.application.PlatformSession;
import org.demchenko.auth.application.PlatformSessionIssuer;
import org.demchenko.auth.config.PlatformSessionProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtPlatformSessionIssuer implements PlatformSessionIssuer {
    private final JwtEncoder encoder;
    private final PlatformSessionProperties properties;
    private final Clock clock;

    @Override
    public PlatformSession issue(UUID userId) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(userId.toString()).issuer(properties.issuer())
                .audience(List.of(properties.audience())).issuedAt(issuedAt).expiresAt(expiresAt)
                .id(UUID.randomUUID().toString()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new PlatformSession(token, expiresAt, properties.ttl().toSeconds());
    }
}
