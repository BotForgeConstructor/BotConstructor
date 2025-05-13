package org.demchenko.tg.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;

import java.io.InputStream;

//import static org.demchenko.ConstructorApplication.cachedVideoFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramMessageService  {

    private final HelpBotSender helpBotSender;

    public void sendMessage(Long chatId, String text) {
        sendMessage(chatId, "", text, null);
    }

    public void sendMessage(Long chatId, String text, ReplyKeyboard replyKeyboard) {
        sendMessage(chatId, "", text, replyKeyboard);
    }

    public void sendMessage(Long chatId, String parseMode, String text) {
        sendMessage(chatId, parseMode, text, null);
    }

    /**
     * Sends a message to a specific chat with optional reply keyboard.
     *
     * @param chatId        the ID of the chat to send the message to
     * @param text          the text of the message
     * @param replyKeyboard the reply keyboard markup (optional)
     * In markdownV2 format all symbols should be escaped
     * . → \.
     * ( → \(
     * " → \"
     */

    public void sendMessage(Long chatId, String parseMode, String text, ReplyKeyboard replyKeyboard) {
        SendMessage sendMessage = SendMessage
                .builder()
                .chatId(chatId.toString())
                .parseMode(parseMode.isEmpty() ? "Markdown" : parseMode)
                .text(text)
                .replyMarkup(replyKeyboard)
                .build();
        executeMessage(sendMessage);
    }
    

    public void sendAnimation(Long chatId, ReplyKeyboard replyKeyboard, String videoPath, String fileName) {
        sendAnimation(chatId, "", "", replyKeyboard, videoPath, fileName);
    }

    public void sendAnimation(Long chatId, String parseMode, String text, ReplyKeyboard replyKeyboard, String videoPath, String fileName) {
        try {

            InputStream gifStream = getClass().getClassLoader().getResourceAsStream(videoPath);

            SendAnimation sendAnimation = SendAnimation
                    .builder()
                    .chatId(chatId.toString())
                    .parseMode(parseMode.isEmpty() ? "Markdown" : parseMode)
                    .caption(text)
                    .replyMarkup(replyKeyboard)
                    .animation(new InputFile(gifStream, fileName))
                    .build();
            executeAnimation(sendAnimation);
        } catch (Exception e) {
            log.error("Failed to send video: {}", videoPath, e);
        }
    }

    // Виконання методу SendMessage
    private void executeMessage(SendMessage sendMessage) {
        try {
            helpBotSender.execute(sendMessage);
        } catch (Exception e) {
            log.error("Exception executing SendMessage: ", e);
        }
    }

    // Виконання методу SendVideo
    private void executeAnimation(SendAnimation sendAnimation) {
        try {
            helpBotSender.execute(sendAnimation);
        } catch (Exception e) {
            log.error("Exception executing SendVideo: ", e);
        }
    }
}
