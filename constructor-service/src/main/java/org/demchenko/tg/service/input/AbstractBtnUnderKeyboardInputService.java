package org.demchenko.tg.service.input;

import org.demchenko.tg.service.BotInputService;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

@Service
public abstract class AbstractBtnUnderKeyboardInputService implements BotInputService {

    @Override
    public boolean canHandle(Update update) {
        return update.hasMessage()
                && update.getMessage().hasText()
                && update.getMessage().getText().equalsIgnoreCase(getButtonText());
    }

    @Override
    public void handle(Update update) {
        processButton(update);
    }

    protected abstract String getButtonText();
    protected abstract void processButton(Update update);
}
