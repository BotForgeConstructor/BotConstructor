package org.demchenko.telegram.service.input;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.demchenko.telegram.service.BotInputService;
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
     * РЈРЅС–РІРµСЂСЃР°Р»СЊРЅРёР№ РјРµС‚РѕРґ РґР»СЏ РѕР±СЂРѕР±РєРё РґР°РЅРёС…. РЎРїРѕС‡Р°С‚РєСѓ РїСЂРѕР±СѓС” СЏРє callback, РїРѕС‚С–Рј СЏРє РїРѕРІС–РґРѕРјР»РµРЅРЅСЏ.
     * @param original РѕСЂРёРіС–РЅР°Р»СЊРЅРёР№ Update
     * @param data РґР°РЅС– РґР»СЏ РѕР±СЂРѕР±РєРё
     * @return true, СЏРєС‰Рѕ РѕР±СЂРѕР±РєР° Р±СѓР»Р° СѓСЃРїС–С€РЅРѕСЋ
     */
    public boolean createAndDispatch(Update original, String data, List<BotInputService> handlers) {
        this.handlers = handlers;

        // РЎРїРѕС‡Р°С‚РєСѓ РїСЂРѕР±СѓС”РјРѕ СЏРє callback data
        Update callbackUpdate = createCallbackUpdate(original, data);
        if (dispatchToHandlers(callbackUpdate)) {
            return true;
        }
        
        // РЇРєС‰Рѕ РЅРµ РІРґР°Р»РѕСЃСЏ РѕР±СЂРѕР±РёС‚Рё СЏРє callback, РїСЂРѕР±СѓС”РјРѕ СЏРє РїРѕРІС–РґРѕРјР»РµРЅРЅСЏ
        Update messageUpdate = createMessageUpdate(original, data);
        if (dispatchToHandlers(messageUpdate)) {
            return true;
        }
        
        // РЇРєС‰Рѕ С– С†Рµ РЅРµ СЃРїСЂР°С†СЋРІР°Р»Рѕ, РїСЂРѕР±СѓС”РјРѕ СЏРє РєРѕРјР°РЅРґСѓ (РґРѕРґР°С”РјРѕ / СЏРєС‰Рѕ Р№РѕРіРѕ РЅРµРјР°С”)
        String commandText = data.startsWith("/") ? data.substring(1) : data;
        Update commandUpdate = createCommandUpdate(original, commandText);
        if (dispatchToHandlers(commandUpdate)) {
            return true;
        }
        
        log.warn("No handler found for data: {}", data);
        return false;
    }
    
    /**
     * РЎС‚РІРѕСЂСЋС” Update Р· CallbackQuery
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
     * РЎС‚РІРѕСЂСЋС” Update Р· С‚РµРєСЃС‚РѕРІРёРј РїРѕРІС–РґРѕРјР»РµРЅРЅСЏРј
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
     * РЎС‚РІРѕСЂСЋС” Update Р· РєРѕРјР°РЅРґРѕСЋ
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
     * Р’С–РґРїСЂР°РІР»СЏС” Update РЅР° РѕР±СЂРѕР±РєСѓ РїС–РґС…РѕРґСЏС‰РѕРјСѓ РѕР±СЂРѕР±РЅРёРєСѓ
     * @param update Update РґР»СЏ РѕР±СЂРѕР±РєРё
     * @return true, СЏРєС‰Рѕ Р·РЅР°Р№С€РѕРІСЃСЏ РѕР±СЂРѕР±РЅРёРє, СЏРєРёР№ Р·РјС–Рі РѕР±СЂРѕР±РёС‚Рё Update
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