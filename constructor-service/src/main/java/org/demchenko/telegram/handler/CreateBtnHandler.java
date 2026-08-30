package org.demchenko.telegram.handler;

import lombok.RequiredArgsConstructor;
import org.demchenko.identity.data.UserService;
import org.demchenko.identity.domain.LegacyUserView;
import org.demchenko.identity.domain.Plan;
import org.demchenko.telegram.service.impl.TelegramInlineKeyboardService;
import org.demchenko.telegram.service.impl.TelegramMessageService;
import org.demchenko.telegram.service.impl.TelegramReplyKeyboardService;
import org.demchenko.telegram.service.input.AbstractInlineKeyboardHandler;
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
        LegacyUserView userData = userService.findUserById(update.getCallbackQuery().getFrom().getId());

        if(userData.countOfBots() >= 1 && userData.plan() == Plan.FREE){
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
                 "1\\. РќР°С‚РёСЃРЅРё СЃСЋРґРё вћ” @BotFather\u2028\n" +
                      "2\\. РќР°РїРёС€Рё `/newbot`\n" +
                      "3\\. Р’РІРµРґРё С–Рј'СЏ Р±РѕС‚Р° С‚Р° РїРѕСЃРёР»Р°РЅРЅСЏ\n" +
                      "4\\. РЎРєРѕРїС–СЋР№ С‚РѕРєРµРЅ\n" +
                      "5\\. Р’С–РґРїСЂР°РІ С‚РѕРєРµРЅ РЅР°СЃС‚СѓРїРЅРёРј РїРѕРІС–РґРѕРјР»РµРЅРЅСЏРј\uD83D\uDC47\uD83C\uDFFB",
                telegramInlineKeyboardService.buildButton(
                        List.of("Video lesson", "Slide instruction", "Back"),
                        List.of("GIF:CREATE:start", "SLIDE:CREATE:start", "BACK:CREATE:start")
                )
        );
    }
}
