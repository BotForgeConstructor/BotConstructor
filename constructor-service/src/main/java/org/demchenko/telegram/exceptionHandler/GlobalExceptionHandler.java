package org.demchenko.telegram.exceptionHandler;

import lombok.RequiredArgsConstructor;
import org.demchenko.telegram.service.impl.TelegramMessageService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@ControllerAdvice
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.telegram.management", name = "enabled", havingValue = "true")
public class GlobalExceptionHandler {

     private final TelegramMessageService telegramMessageService;

    @ExceptionHandler(NotFoundFileException.class)
     public void handleSomeSpecificException(NotFoundFileException ex) {
          telegramMessageService.sendMessage(ex.getChatId(), "Functional is not provided yet!");
     }
}
