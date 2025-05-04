package org.demchenko.tg.exceptionHandler;

import lombok.Getter;

@Getter
public class NotFoundFileException extends RuntimeException {

    private final Long chatId;

    public NotFoundFileException(String message, Long chatId) {
        super(message);
        this.chatId = chatId;
    }
}
