package org.demchenko.tg.exceptionHandler;

import lombok.RequiredArgsConstructor;
import org.demchenko.tg.service.impl.TelegramMessageService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

     private final TelegramMessageService telegramMessageService;

    @ExceptionHandler(NotFoundFileException.class)
     public void handleSomeSpecificException(NotFoundFileException ex) {
          telegramMessageService.sendMessage(ex.getChatId(), "Functional is not provided yet!");
     }
}
