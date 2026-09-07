package org.demchenko.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.demchenko.telegram.config.ManagementMiniAppProperties;
import org.demchenko.telegram.handler.StartCommandHandler;
import org.demchenko.telegram.service.impl.TelegramInlineKeyboardService;
import org.demchenko.telegram.service.impl.TelegramMessageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StartCommandHandlerTest {
    @Test
    void startUsesNativeWebAppButtonWithoutBootstrappingPlatformState() throws Exception {
        TelegramMessageService messages = mock(TelegramMessageService.class);
        var handler = new StartCommandHandler(messages, mock(TelegramInlineKeyboardService.class),
                new ManagementMiniAppProperties(URI.create("https://mini-app.invalid/"), "Open Builder"));
        Message message = new Message();
        Chat chat = new Chat();
        chat.setId(123L);
        chat.setType("private");
        message.setChat(chat);
        message.setText("/start");
        Update update = new Update();
        update.setMessage(message);

        assertThat(handler.canHandle(update)).isTrue();
        handler.handle(update);

        ArgumentCaptor<ReplyKeyboard> keyboard = ArgumentCaptor.forClass(ReplyKeyboard.class);
        verify(messages).sendMessage(org.mockito.ArgumentMatchers.eq(123L),
                org.mockito.ArgumentMatchers.eq("Open Bot Constructor:"), keyboard.capture());
        String json = new ObjectMapper().writeValueAsString(keyboard.getValue());
        assertThat(json).contains("\"web_app\"", "https://mini-app.invalid/")
                .doesNotContain("token", "secret", "initData");
    }
}
