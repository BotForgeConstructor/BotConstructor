package org.demchenko.tg.service.input;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.demchenko.tg.service.BotInputService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateFactory {
    
    private List<BotInputService> handlers;
    
    /**
     * Універсальний метод для обробки даних. Спочатку пробує як callback, потім як повідомлення.
     * @param original оригінальний Update
     * @param data дані для обробки
     * @return true, якщо обробка була успішною
     */
    public boolean createAndDispatch(Update original, String data, List<BotInputService> handlers) {
        this.handlers = handlers;

        // Спочатку пробуємо як callback data
        Update callbackUpdate = createCallbackUpdate(original, data);
        if (dispatchToHandlers(callbackUpdate)) {
            return true;
        }
        
        // Якщо не вдалося обробити як callback, пробуємо як повідомлення
        Update messageUpdate = createMessageUpdate(original, data);
        if (dispatchToHandlers(messageUpdate)) {
            return true;
        }
        
        // Якщо і це не спрацювало, пробуємо як команду (додаємо / якщо його немає)
        String commandText = data.startsWith("/") ? data.substring(1) : data;
        Update commandUpdate = createCommandUpdate(original, commandText);
        if (dispatchToHandlers(commandUpdate)) {
            return true;
        }
        
        log.warn("No handler found for data: {}", data);
        return false;
    }
    
    /**
     * Створює Update з CallbackQuery
     */
    public Update createCallbackUpdate(Update original, String callbackData) {
        Update newUpdate = new Update();
        newUpdate.setCallbackQuery(new CallbackQuery(original.getCallbackQuery().getId(),
                original.getCallbackQuery().getFrom(),
                original.getCallbackQuery().getMessage(),
                original.getCallbackQuery().getInlineMessageId(),
                callbackData,
                original.getCallbackQuery().getGameShortName(),
                original.getCallbackQuery().getChatInstance()));
        return newUpdate;
    }
    
    /**
     * Створює Update з текстовим повідомленням
     */
    public Update createMessageUpdate(Update original, String messageText) {
        Update newUpdate = new Update();
        Message message = new Message();
        message.setText(messageText);
        message.setFrom(original.getCallbackQuery().getFrom());
        message.setChat(original.getCallbackQuery().getMessage().getChat());
        newUpdate.setMessage(message);
        return newUpdate;
    }
    
    /**
     * Створює Update з командою
     */
    public Update createCommandUpdate(Update original, String command) {
        Update newUpdate = new Update();
        Message message = new Message();
        message.setText("/" + command);
        message.setFrom(original.getCallbackQuery().getFrom());
        message.setChat(original.getCallbackQuery().getMessage().getChat());
        newUpdate.setMessage(message);
        return newUpdate;
    }
    
    /**
     * Відправляє Update на обробку підходящому обробнику
     * @param update Update для обробки
     * @return true, якщо знайшовся обробник, який зміг обробити Update
     */
    private boolean dispatchToHandlers(Update update) {
        for (BotInputService handler : handlers) {
            if (handler.canHandle(update)) {
                handler.handle(update);
                return true;
            }
        }
        return false;
    }
} 