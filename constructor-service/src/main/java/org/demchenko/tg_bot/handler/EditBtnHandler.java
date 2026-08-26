package org.demchenko.tg_bot.handler;

import org.demchenko.tg_bot.service.input.AbstractInlineKeyboardHandler;
import org.telegram.telegrambots.meta.api.objects.Update;

public class EditBtnHandler extends AbstractInlineKeyboardHandler {
    @Override
    protected String getPrefix() {
        return "EDIT";
    }

    @Override
    protected void processCallback(Update update, String[] params) {

    }
}
