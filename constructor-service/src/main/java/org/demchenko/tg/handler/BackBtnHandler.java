package org.demchenko.tg.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.demchenko.tg.service.BotInputService;
import org.demchenko.tg.service.input.AbstractInlineKeyboardHandler;
import org.demchenko.tg.service.input.UpdateFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BackBtnHandler extends AbstractInlineKeyboardHandler {

    private final UpdateFactory updateFactory;
    private final List<BotInputService> handlers;

    @Override
    protected String getPrefix() {
        return "BACK";
    }

    @Override
    protected void processCallback(Update update, String[] params) {
        // Перевіряємо довжину params, щоб уникнути ArrayIndexOutOfBoundsException
        if (params.length < 3) {
            // Недостатньо параметрів, не можемо обробити
            log.warn("Not enough parameters for BackBtnHandler: {}", String.join(":", params));
            return;
        }

        // Отримуємо дані для третього параметра
        String data = params[2];
        
        // Використовуємо updateFactory для створення і обробки Update
        if (!updateFactory.createAndDispatch(update, data, handlers)) {
            log.warn("Could not process BackBtn with data: {}", data);
        }
    }
}
