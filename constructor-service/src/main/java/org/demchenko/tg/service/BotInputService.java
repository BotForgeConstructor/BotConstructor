package org.demchenko.tg.service;


import org.telegram.telegrambots.meta.api.objects.Update;

public interface BotInputService {
    boolean canHandle(Update update);
    void handle(Update update);
}
