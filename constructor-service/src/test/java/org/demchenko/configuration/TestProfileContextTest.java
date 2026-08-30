package org.demchenko.configuration;

import org.demchenko.telegram.config.TelegramBotConnection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
)
@ActiveProfiles("test")
class TestProfileContextTest {
    @Autowired
    private ApplicationContext context;

    @Test
    void startsWithoutSecretsAndDoesNotRegisterTelegramAdapter() {
        assertThat(context.getBeansOfType(TelegramBotConnection.class)).isEmpty();
    }
}
