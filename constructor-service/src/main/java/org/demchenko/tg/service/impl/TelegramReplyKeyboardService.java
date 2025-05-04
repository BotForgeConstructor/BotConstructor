package org.demchenko.tg.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TelegramReplyKeyboardService {

    public ReplyKeyboardMarkup buildMainMenu() {
        KeyboardRow keyboardRow = new KeyboardRow();
        keyboardRow.add("Carrier");
        keyboardRow.add("Customer");

        return ReplyKeyboardMarkup.builder()
                .keyboard(List.of(keyboardRow))
                .selective(true)
                .resizeKeyboard(true)
                .oneTimeKeyboard(false)
                .build();
    }

    public ReplyKeyboardMarkup buildAccountInputMenu() {
        KeyboardRow keyboardRow = new KeyboardRow();
        keyboardRow.add("CANCEL");

        return ReplyKeyboardMarkup.builder()
                .keyboard(List.of(keyboardRow))
                .selective(true)
                .resizeKeyboard(true)
                .oneTimeKeyboard(false)
                .build();
    }

    public ReplyKeyboardMarkup buildSkipMenu() {
        KeyboardRow keyboardRow = new KeyboardRow();
        keyboardRow.add("SKIP");
        keyboardRow.add("CANCEL");

        return ReplyKeyboardMarkup.builder()
                .keyboard(List.of(keyboardRow))
                .selective(true)
                .resizeKeyboard(true)
                .oneTimeKeyboard(false)
                .build();
    }

    public ReplyKeyboardMarkup buildCustomMenu(List<String> buttons, int buttonsPerRow) {
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow currentRow = new KeyboardRow();
        
        for (int i = 0; i < buttons.size(); i++) {
            currentRow.add(buttons.get(i));
            
            // Якщо досягли максимальної кількості кнопок у рядку або це остання кнопка
            if ((i + 1) % buttonsPerRow == 0 || i == buttons.size() - 1) {
                keyboard.add(currentRow);
                currentRow = new KeyboardRow();
            }
        }

        return ReplyKeyboardMarkup.builder()
                .keyboard(keyboard)
                .selective(true)
                .resizeKeyboard(true)
                .oneTimeKeyboard(false)
                .build();
    }

    public ReplyKeyboardMarkup buildCustomMenu(List<String> buttons) {
        return buildCustomMenu(buttons, 1); // За замовчуванням 1 кнопка в рядку
    }
} 