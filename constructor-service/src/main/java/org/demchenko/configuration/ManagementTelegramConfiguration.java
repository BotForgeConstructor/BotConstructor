package org.demchenko.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.telegram.management", name = "enabled", havingValue = "true")
@ComponentScan("org.demchenko.telegram")
public class ManagementTelegramConfiguration {
}
