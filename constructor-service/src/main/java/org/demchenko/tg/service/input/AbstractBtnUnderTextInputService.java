package org.demchenko.tg.service.input;

import org.demchenko.tg.service.BotInputService;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Arrays;

@Service
public abstract class AbstractBtnUnderTextInputService implements BotInputService {

    private String buttonText;

    @Override
    public boolean canHandle(Update update) {
        return update.hasCallbackQuery()
                && update.getCallbackQuery().getData().startsWith(getPrefix());
    }

    @Override
    public void handle(Update update) {
        String data = update.getCallbackQuery().getData();
        String[] params = data.split(":");
        processCallback(update, Arrays.copyOfRange(params, 1, params.length));
    }

    protected abstract String getPrefix(); // наприклад "BOOK_CALL", "DELETE_USER"
    protected abstract void processCallback(Update update, String[] params);
}

