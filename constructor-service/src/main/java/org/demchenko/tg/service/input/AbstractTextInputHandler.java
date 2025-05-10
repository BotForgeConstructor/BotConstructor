package org.demchenko.tg.service.input;

import org.demchenko.tg.service.BotInputService;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

@Service
public abstract class AbstractTextInputHandler implements BotInputService {

    private Update currentUpdate;

    @Override
    public boolean canHandle(Update update) {
        this.currentUpdate = update;
        return update.hasMessage()
                && update.getMessage().hasText()
                && matches(update.getMessage().getText());
    }

    @Override
    public void handle(Update update) {
        this.currentUpdate = update;
        processText(update, update.getMessage().getText());
    }

    protected Long getUserId() {
        if (currentUpdate != null && currentUpdate.hasMessage() && currentUpdate.getMessage().getFrom() != null) {
            return currentUpdate.getMessage().getFrom().getId();
        }
        return null;
    }

    protected abstract boolean matches(String text);
    protected abstract void processText(Update update, String text);
}
