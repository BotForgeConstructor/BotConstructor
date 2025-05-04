package org.demchenko.tg.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TelegramButtonService {

    //btnsText - naming of buttons
    //callBackData - data that will be sent to the bot when the button is pressed
    public InlineKeyboardMarkup buildButton(List<String> btnsText, List<String> callBackData) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (int i = 0; i < btnsText.size(); i++) {
            InlineKeyboardButton button = new InlineKeyboardButton(btnsText.get(i));
            button.setCallbackData(callBackData.get(i));
            row.add(button);
        }

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row);

        return InlineKeyboardMarkup.builder()
                .keyboard(keyboard)
                .build();
    }


    public InlineKeyboardMarkup buildSkipButton() {
        InlineKeyboardButton button = new InlineKeyboardButton("Skip");
        button.setCallbackData("SKIP");

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(button);

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row);

        return InlineKeyboardMarkup.builder()
                .keyboard(keyboard)
                .build();
    }
}
