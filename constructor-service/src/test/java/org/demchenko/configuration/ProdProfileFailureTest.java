package org.demchenko.configuration;

import org.demchenko.ConstructorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdProfileFailureTest {
    @Test
    void prodProfileFailsFastWhenRequiredSettingsAreMissing() {
        String suppliedSecret = "must-not-appear-in-validation-output";
        assertThatThrownBy(() -> new SpringApplicationBuilder(ConstructorApplication.class)
                .profiles("prod")
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
                        "SPRING_DATASOURCE_URL=",
                        "SPRING_DATASOURCE_USERNAME=",
                        "SPRING_DATASOURCE_PASSWORD=",
                        "APP_WEB_URL=",
                        "APP_TELEGRAM_MANAGEMENT_TOKEN=" + suppliedSecret,
                        "APP_TELEGRAM_MANAGEMENT_USERNAME="
                )
                .run())
                .isInstanceOf(Exception.class)
                .satisfies(error -> {
                    String failures = causalMessages(error);
                    org.assertj.core.api.Assertions.assertThat(failures)
                            .contains("ConfigurationPropertiesBindException")
                            .doesNotContain(suppliedSecret, "TelegramApiException");
                });
    }

    private String causalMessages(Throwable error) {
        StringBuilder result = new StringBuilder();
        for (Throwable current = error; current != null; current = current.getCause()) {
            result.append(current.getClass().getSimpleName())
                    .append(':').append(current.getMessage()).append('\n');
        }
        return result.toString();
    }
}
