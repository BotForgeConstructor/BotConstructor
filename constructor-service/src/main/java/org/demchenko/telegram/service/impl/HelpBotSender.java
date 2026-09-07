package org.demchenko.telegram.service.impl;

import org.demchenko.telegram.config.ManagementTelegramProperties;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;

@Component
@ConditionalOnProperty(prefix = "app.telegram.management", name = "enabled", havingValue = "true")
public class HelpBotSender extends DefaultAbsSender {

    private final ManagementTelegramProperties properties;

    public HelpBotSender(ManagementTelegramProperties properties) {
        super(new DefaultBotOptions());
        this.properties = properties;
    }

    @Override
    public String getBotToken() {
        return properties.token();
    }
}
