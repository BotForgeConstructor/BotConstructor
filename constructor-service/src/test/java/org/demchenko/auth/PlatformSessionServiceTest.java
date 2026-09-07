package org.demchenko.auth;

import org.demchenko.auth.application.PlatformSessionIssuer;
import org.demchenko.auth.config.PlatformSessionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
@ActiveProfiles("test")
class PlatformSessionServiceTest {
    @Autowired PlatformSessionIssuer sessions;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;
    @Autowired PlatformSessionProperties properties;

    @Test
    void issuesUniqueShortLivedTokenWithRequiredClaims() {
        UUID userId = UUID.randomUUID();
        var first = sessions.issue(userId);
        var second = sessions.issue(userId);
        var jwt = decoder.decode(first.accessToken());

        assertThat(first.accessToken()).isNotEqualTo(second.accessToken());
        assertThat(first.expiresInSeconds()).isEqualTo(900);
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getIssuer().toString()).isEqualTo(properties.issuer());
        assertThat(jwt.getAudience()).containsExactly(properties.audience());
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void rejectsTamperedExpiredWrongIssuerAndWrongAudienceTokens() {
        String valid = sessions.issue(UUID.randomUUID()).accessToken();
        assertThatThrownBy(() -> decoder.decode(valid.substring(0, valid.length() - 2) + "xx"))
                .isInstanceOf(JwtException.class);
        assertRejected(token("other", properties.audience(), Instant.now().plusSeconds(30), UUID.randomUUID().toString()));
        assertRejected(token(properties.issuer(), "other", Instant.now().plusSeconds(30), UUID.randomUUID().toString()));
        assertRejected(token(properties.issuer(), properties.audience(), Instant.now().minusSeconds(120), UUID.randomUUID().toString()));
    }

    @Test
    void weakSigningKeyIsRejectedWithoutEchoingIt() {
        String weak = "d2Vhaw==";
        PlatformSessionProperties invalid = new PlatformSessionProperties(weak, "issuer", "audience", java.time.Duration.ofMinutes(15));
        assertThat(invalid.isValid()).isFalse();
        assertThat(invalid.toString()).doesNotContain(weak);
    }

    @Test
    void rejectsNoneWrongAlgorithmMissingSubjectAndFutureIssuedAt() {
        assertRejected(unsignedToken("none"));
        assertRejected(unsignedToken("HS512"));
        assertRejected(token(properties.issuer(), properties.audience(), Instant.now().plusSeconds(30), null));
        assertRejected(tokenWithIssuedAt(Instant.now().plusSeconds(120)));
    }

    private String token(String issuer, String audience, Instant expiresAt, String subject) {
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
                .issuedAt(expiresAt.minusSeconds(60)).expiresAt(expiresAt).id(UUID.randomUUID().toString());
        if (subject != null) builder.subject(subject);
        JwtClaimsSet claims = builder.build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String tokenWithIssuedAt(Instant issuedAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.issuer())
                .audience(List.of(properties.audience())).issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(300)).subject(UUID.randomUUID().toString())
                .id(UUID.randomUUID().toString()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String unsignedToken(String algorithm) {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"alg\":\"" + algorithm + "\"}").getBytes(StandardCharsets.UTF_8));
        String claims = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"00000000-0000-0000-0000-000000000001\"}".getBytes(StandardCharsets.UTF_8));
        return header + "." + claims + ".";
    }

    private void assertRejected(String token) {
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
}
