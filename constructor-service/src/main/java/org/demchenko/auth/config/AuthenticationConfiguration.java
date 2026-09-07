package org.demchenko.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class AuthenticationConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtEncoder jwtEncoder(PlatformSessionProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(properties.decodedSigningKey()));
    }

}
