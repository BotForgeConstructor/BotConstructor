package org.demchenko.configuration;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.demchenko.bot.config.BotWebhookProperties;
import org.demchenko.telegram.config.ManagementMiniAppProperties;
import org.demchenko.telegram.config.ManagementWebhookProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class PublicEndpointPropertiesTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsHttpsAndLoopbackButRejectsPublicPlainHttp() {
        assertThat(validator.validate(new WebApplicationProperties(URI.create("https://backend.invalid")))).isEmpty();
        assertThat(validator.validate(new ManagementMiniAppProperties(
                URI.create("http://localhost:5173"), "Open"))).isEmpty();
        assertThat(validator.validate(new BotWebhookProperties(
                URI.create("http://127.0.0.1:8080/hooks")))).isEmpty();

        assertThat(validator.validate(new WebApplicationProperties(URI.create("http://public.invalid"))))
                .isNotEmpty();
        assertThat(validator.validate(new ManagementMiniAppProperties(
                URI.create("http://public.invalid/app"), "Open"))).isNotEmpty();
        assertThat(validator.validate(new BotWebhookProperties(
                URI.create("http://public.invalid/hooks")))).isNotEmpty();
    }

    @Test
    void managementWebhookRequiresTelegramSafeSecretAndRelativeSecretFreePath() {
        assertThat(validator.validate(new ManagementWebhookProperties(true, "Safe_secret-123",
                URI.create("/api/v1/telegram/management/webhook")))).isEmpty();
        assertThat(validator.validate(new ManagementWebhookProperties(true, "unsafe secret",
                URI.create("/api/v1/telegram/management/webhook")))).isNotEmpty();
        assertThat(validator.validate(new ManagementWebhookProperties(true, "Safe_secret-123",
                URI.create("https://backend.invalid/secret")))).isNotEmpty();
        assertThat(validator.validate(new ManagementWebhookProperties(true, "Safe_secret-123",
                URI.create("/webhook?secret=bad")))).isNotEmpty();
    }
}
