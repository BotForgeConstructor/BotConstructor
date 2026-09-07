package org.demchenko.bot.config;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
import java.net.URI;
@Validated @ConfigurationProperties("app.telegram.api")
public record TelegramBotApiProperties(@NotBlank String baseUrl, Duration connectTimeout, Duration readTimeout) {
 public TelegramBotApiProperties { if(connectTimeout==null) connectTimeout=Duration.ofSeconds(3); if(readTimeout==null) readTimeout=Duration.ofSeconds(5); }
 public URI methodUri(String method){ return URI.create(baseUrl.replaceAll("/$","")+"/botTOKEN/"+method); }
}
