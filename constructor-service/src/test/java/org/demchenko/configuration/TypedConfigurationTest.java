package org.demchenko.configuration;

import org.demchenko.telegram.config.ManagementTelegramProperties;
import org.demchenko.telegram.config.TelegramMediaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class TypedConfigurationTest {
    private static final String SYNTHETIC_VALID_TOKEN = "100000000:" + "T".repeat(35);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class)
            .withBean(jakarta.validation.Validator.class, LocalValidatorFactoryBean::new)
            .withPropertyValues(
                    "app.web.url=https://example.invalid/app",
                    "app.telegram.media.lesson-path=assets/videos/test.mp4"
            );

    @Test
    void bindsTypedValues() {
        runner.withPropertyValues(
                "app.telegram.management.enabled=true",
                "app.telegram.management.connection-mode=WEBHOOK",
                "app.telegram.management.token=" + SYNTHETIC_VALID_TOKEN,
                "app.telegram.management.username=test_bot"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WebApplicationProperties.class).url())
                    .isEqualTo(URI.create("https://example.invalid/app"));
            assertThat(context.getBean(ManagementTelegramProperties.class).connectionMode())
                    .isEqualTo(ManagementTelegramProperties.ConnectionMode.WEBHOOK);
            assertThat(context.getBean(TelegramMediaProperties.class).lessonPath())
                    .isEqualTo("assets/videos/test.mp4");
        });
    }

    @Test
    void enabledTelegramRequiresCredentials() {
        runner.withPropertyValues("app.telegram.management.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void validProdLikeConfigurationBinds() {
        runner.withPropertyValues(
                "app.telegram.management.enabled=true",
                "app.telegram.management.token=" + SYNTHETIC_VALID_TOKEN,
                "app.telegram.management.username=production_like_bot"
        ).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void validationFailureDoesNotExposeSecret() {
        String secret = "sensitive-secret-value";
        runner.withPropertyValues(
                "app.telegram.management.enabled=true",
                "app.telegram.management.token=" + secret,
                "app.telegram.management.username=valid_bot"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(stackTrace(context.getStartupFailure())).doesNotContain(secret);
        });
    }

    private String stackTrace(Throwable failure) {
        StringBuilder result = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            result.append(current.getClass().getName()).append(':').append(current.getMessage()).append('\n');
        }
        return result.toString();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            ManagementTelegramProperties.class,
            TelegramMediaProperties.class,
            WebApplicationProperties.class
    })
    static class PropertiesConfiguration {
    }
}
