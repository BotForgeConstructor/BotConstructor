package org.demchenko.telegram.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.demchenko.telegram.service.BotInputService;
import org.demchenko.telegram.service.input.AbstractInlineKeyboardHandler;
import org.demchenko.telegram.service.input.UpdateFactory;
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
        // РџРµСЂРµРІС–СЂСЏС”РјРѕ РґРѕРІР¶РёРЅСѓ params, С‰РѕР± СѓРЅРёРєРЅСѓС‚Рё ArrayIndexOutOfBoundsException
        if (params.length < 3) {
            // РќРµРґРѕСЃС‚Р°С‚РЅСЊРѕ РїР°СЂР°РјРµС‚СЂС–РІ, РЅРµ РјРѕР¶РµРјРѕ РѕР±СЂРѕР±РёС‚Рё
            log.warn("Not enough parameters for BackBtnHandler: {}", String.join(":", params));
            return;
        }

        // РћС‚СЂРёРјСѓС”РјРѕ РґР°РЅС– РґР»СЏ С‚СЂРµС‚СЊРѕРіРѕ РїР°СЂР°РјРµС‚СЂР°
        String data = params[2];
        
        // Р’РёРєРѕСЂРёСЃС‚РѕРІСѓС”РјРѕ updateFactory РґР»СЏ СЃС‚РІРѕСЂРµРЅРЅСЏ С– РѕР±СЂРѕР±РєРё Update
        if (!updateFactory.createAndDispatch(update, data, handlers)) {
            log.warn("Could not process BackBtn with data: {}", data);
        }
    }
}
