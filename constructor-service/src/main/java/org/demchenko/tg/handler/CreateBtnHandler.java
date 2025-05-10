package org.demchenko.tg.handler;

import lombok.RequiredArgsConstructor;
import org.demchenko.tg.data.UserService;
import org.demchenko.tg.enums.Plan;
import org.demchenko.tg.model.UserData;
import org.demchenko.tg.service.impl.TelegramInlineKeyboardService;
import org.demchenko.tg.service.impl.TelegramMessageService;
import org.demchenko.tg.service.impl.TelegramReplyKeyboardService;
import org.demchenko.tg.service.input.AbstractInlineKeyboardHandler;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CreateBtnHandler extends AbstractInlineKeyboardHandler {

    private final TelegramInlineKeyboardService telegramInlineKeyboardService;
    private final TelegramReplyKeyboardService telegramReplyKeyboardService;
    private final TelegramMessageService telegramMessageService;
    private final UserService userService;

    @Override
    protected String getPrefix() {
        return "CREATE";
    }

    @Override
    protected void processCallback(Update update, String[] params) {
        UserData userData = userService.findUserById(update.getCallbackQuery().getFrom().getId());

        if(userData.getCountOfBots() >= 1 && userData.getPlan() == Plan.FREE){
            telegramMessageService.sendMessage(update.getMessage().getChatId(),
                    "You need to update your plan to create new bot.",
                    telegramInlineKeyboardService.buildButton(
                            List.of("MENU"),
                            List.of("CREATE_MENU:MENU")
                    )
            );
            return;
        }
        telegramMessageService.sendMessage(update.getCallbackQuery().getFrom().getId(), "To create a bot, follow these steps:\u2028");

        telegramMessageService.sendMessage(update.getCallbackQuery().getFrom().getId(),
            "MarkdownV2",
                 "1\\. Натисни сюди ➔ @BotFather\u2028\n" +
                      "2\\. Напиши `/newbot`\n" +
                      "3\\. Введи ім'я бота та посилання\n" +
                      "4\\. Скопіюй токен\n" +
                      "5\\. Відправ токен наступним повідомленням\uD83D\uDC47\uD83C\uDFFB",
                telegramInlineKeyboardService.buildButton(
                        List.of("Video lesson", "Slide instruction", "Back"),
                        List.of("VIDEO:CREATE", "SLIDE:CREATE", "BACK:CREATE")
                )
        );
    }
}
