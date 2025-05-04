package org.demchenko.tg.handler;

import lombok.RequiredArgsConstructor;
import org.demchenko.tg.data.UserService;
import org.demchenko.tg.enums.Plan;
import org.demchenko.tg.model.UserData;
import org.demchenko.tg.service.impl.TelegramButtonService;
import org.demchenko.tg.service.impl.TelegramMessageService;
import org.demchenko.tg.service.impl.TelegramReplyKeyboardService;
import org.demchenko.tg.service.input.AbstractBtnUnderTextInputService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CreateBtnHandler extends AbstractBtnUnderTextInputService {

    private final TelegramButtonService telegramButtonService;
    private final TelegramReplyKeyboardService telegramReplyKeyboardService;
    private final TelegramMessageService telegramMessageService;
    private final UserService userService;

    @Override
    protected String getPrefix() {
        return "START_MENU";
    }

    @Override
    protected void processCallback(Update update, String[] params) {
        UserData userData = userService.findUserById(update.getCallbackQuery().getFrom().getId());

        if(userData.getCountOfBots() >= 1 && userData.getPlan() == Plan.FREE){
            telegramMessageService.sendMessage(update.getMessage().getChatId(),
                    "You need to update your plan to create new bot.",
                    telegramButtonService.buildButton(
                            List.of("MENU"),
                            List.of("CREATE_MENU:MENU")
                    )
            );
            return;
        }
        telegramMessageService.sendMessage(update.getCallbackQuery().getFrom().getId(),
                "1. Натисни сюди ➔ @BotFather\u20282. Натисни /newbot\u20283. Введи ім'я бота та посилання\u20284. Скопіюй токен\u20285. Відправ токен наступним повідомленням\u2028",
                telegramReplyKeyboardService.buildMainMenu()
        );
    }
}
