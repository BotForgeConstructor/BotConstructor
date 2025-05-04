package org.demchenko.tg.dispetcher;

import lombok.extern.slf4j.Slf4j;
import org.demchenko.tg.service.BotInputService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Slf4j
@Component
public class BotUpdateDispatcher {

    private final List<BotInputService> handlers;

    @Autowired
    public BotUpdateDispatcher(List<BotInputService> handlers) {
        this.handlers = handlers;
    }

    public void dispatch(Update update) {
        for (BotInputService handler : handlers) {
            if (handler.canHandle(update)) {
                handler.handle(update);
                return;
            }
        }
        //todo: log error or send message to user about no handler
        System.out.println("No handler for: " + update);
    }
}

