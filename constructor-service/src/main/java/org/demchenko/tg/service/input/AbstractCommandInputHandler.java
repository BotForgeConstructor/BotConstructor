package org.demchenko.tg.service.input;

import org.demchenko.tg.service.BotInputService;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

@Service
public abstract class AbstractCommandInputHandler implements BotInputService {

    @Override
    public boolean canHandle(Update update) {
        return update.hasMessage()
                && update.getMessage().hasText()
                && update.getMessage().getText().equalsIgnoreCase("/" + getCommandText()); //   / + start
    }

    @Override
    public void handle(Update update) {
        processCommand(update);
    }

    protected abstract String getCommandText(); // типу "/start"
    protected abstract void processCommand(Update update);
}
