package org.demchenko.telegram.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.telegram.media")
public record TelegramMediaProperties(@NotBlank String lessonPath) {
}
