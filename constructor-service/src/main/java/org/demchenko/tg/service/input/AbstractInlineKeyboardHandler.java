package org.demchenko.tg.service.input;

import org.demchenko.tg.service.BotInputService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;


@Service
public abstract class AbstractInlineKeyboardHandler implements BotInputService {

    private String buttonText;

    @Autowired
    private UpdateFactory updateFactory;

    @Override
    public boolean canHandle(Update update) {
        return update.hasCallbackQuery()
                && update.getCallbackQuery().getData().startsWith(getPrefix());
    }

    @Override
    public void handle(Update update) {
        String data = update.getCallbackQuery().getData();
        processCallback(update, data.split(":"));
    }

    // example "WRITE:MASSAGE:SEND" | where WRITE is redirect directory (future), MASSAGE is current directory, SEND is PREVIOUS directory
    //    WRITE     ->     MASSAGE     ->     SEND
    //future callback    now we here    previous callback
    protected abstract String getPrefix();
    protected abstract void processCallback(Update update, String[] params);
}

