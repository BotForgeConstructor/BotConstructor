package org.demchenko.tg.service.input;

import org.demchenko.tg.service.BotInputService;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Arrays;

@Service
public abstract class AbstractInlineKeyboardHandler implements BotInputService {

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

    // example "BOOK:CREATE" | where BOOK is redirect directory, CREATE is current directory
    protected abstract String getPrefix();
    protected abstract void processCallback(Update update, String[] params);
}

